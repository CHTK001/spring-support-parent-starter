package com.chua.starter.ai.support.configuration.layout;

import com.chua.common.support.utils.StringUtils;
import com.chua.deeplearning.support.layout.LayoutDetector;
import com.chua.deeplearning.support.model.PredictRectangle;
import com.chua.deeplearning.support.ocr.OcrResult;
import com.chua.starter.ai.support.configuration.client.GlobalSettingReader;
import com.chua.starter.ai.support.configuration.ocr.OcrClientManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 版面检测客户端管理器。
 *
 * <p>按 {@code layout} 分组全局设置（sys_ai_global_setting）构建
 * {@link LayoutDetector}，检测文档中文字/表格/图片等区域布局。
 * 检测器为轻量句柄（模型由识别引擎按名称缓存），管理器对配置做指纹比对，
 * 配置变更后自动重建，无需重启。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LayoutClientManager {

    /**
     * 全局设置键值分组
     */
    private static final String GROUP = "layout";

    /**
     * 需要送 OCR 的版面类别关键字（小写匹配）。
     * <p>覆盖 PP-DocLayoutV3 / PP-DocLayout-L / DocLayout-YOLO / RT-DETR(doclaynet)
     * 四套模型的文本类标签名。</p>
     */
    private static final List<String> TEXT_REGION_LABELS = List.of(
            "text", "plain_text", "paragraph_title", "doc_title", "content", "abstract",
            "table", "table_title", "figure_title", "caption", "section-header",
            "list-item", "key-value", "form", "reference", "footnote");

    /**
     * 全局设置读取工具
     */
    private final GlobalSettingReader settingReader;

    /**
     * OCR 客户端管理器（可选依赖：OCR 未启用时版面联动自动降级）
     */
    private final ObjectProvider<OcrClientManager> ocrClientManagerProvider;

    /**
     * 当前生效的版面检测器
     */
    private volatile LayoutDetector detector;

    /**
     * 当前检测器对应的配置指纹（模型 + 阈值 + 设备）
     */
    private volatile String fingerprint;

    /**
     * 获取当前版面检测器（配置变更自动重建）。
     *
     * @return 版面检测器
     * @throws IllegalStateException 版面模型未配置时
     */
    public LayoutDetector current() {
        String fp = readFingerprint();
        if (!fp.equals(fingerprint)) {
            synchronized (this) {
                String currentFp = readFingerprint();
                if (!currentFp.equals(fingerprint)) {
                    log.info("[LayoutClientManager] 配置变更，重建版面检测器: {}", currentFp);
                    this.detector = build();
                    this.fingerprint = currentFp;
                }
            }
        }
        if (detector == null) {
            /**
             * illegal状态异常。
             * @param layout_model" layout_模型"
             * @return illegal状态异常的结果
             */
            throw new IllegalStateException("版面分析模型未配置：请在设置中配置 layout 分组的 layout_model");
        }
        return detector;
    }

    /**
     * 检测文档版面区域。
     *
     * @param imageData 图片字节
     * @return 按区域类型分组的检测框
     */
    public Map<String, List<PredictRectangle>> detect(byte[] imageData) {
        return current().detect(imageData);
    }

    /**
     * 解析文档版面为结构化文本（Markdown）。
     * <p>当 {@code ocr_link_enabled} 为 true 且 OCR 已配置时，先做版面分析、
     * 再按「文本类区域」裁剪并逐区域 OCR，最后按阅读顺序（y 升序、x 升序）拼装为
     * 一行一行的纯文本；否则退回 {@link LayoutDetector#parse(byte[])} 的标签拼装结果。</p>
     *
     * @param imageData 图片字节
     * @return Markdown 文本
     */
    public String parse(byte[] imageData) {
        OcrClientManager ocr = ocrClientManager();
        if (!ocrLinkEnabled() || ocr == null || !ocr.available()) {
            return current().parse(imageData);
        }
        List<PredictRectangle> textRegions = textRegions(imageData);
        if (textRegions.isEmpty()) {
            return current().parse(imageData);
        }
        List<OcrResult> items = ocr.recognizeRegions(imageData, textRegions);
        if (items.isEmpty()) {
            return current().parse(imageData);
        }
        return items.stream()
                .sorted(Comparator.comparingDouble((OcrResult o) -> o.boundingBox() == null ? 0d : o.boundingBox().y())
                        .thenComparingDouble(o -> o.boundingBox() == null ? 0d : o.boundingBox().x()))
                .map(OcrResult::text)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.joining("\n"));
    }

    /**
     * 提取需要 OCR 的文本类版面区域。
     * <p>仅取行内文本类区域（text / plain_text / paragraph_title / doc_title / content /
     * table / figure_caption 等）；页眉页脚、公式、图表等不参与文字识别。
     * {@code reading_order_enabled} 为 false 时不按阅读顺序重排。</p>
     *
     * @param imageData 图片字节
     * @return 文本类区域列表
     */
    private List<PredictRectangle> textRegions(byte[] imageData) {
        Map<String, List<PredictRectangle>> grouped = detect(imageData);
        List<PredictRectangle> regions = new ArrayList<>();
        for (Map.Entry<String, List<PredictRectangle>> entry : grouped.entrySet()) {
            if (isTextRegion(entry.getKey())) {
                regions.addAll(entry.getValue());
            }
        }
        if (regions.isEmpty()) {
            return regions;
        }
        boolean readingOrder = settingReader.getBool(GROUP, "reading_order_enabled", true);
        if (readingOrder) {
            regions.sort(Comparator.comparingDouble(PredictRectangle::y)
                    .thenComparingDouble(PredictRectangle::x));
        }
        return regions;
    }

    /**
     * 判断版面联动 OCR 是否启用。
     *
     * @return true 表示启用
     */
    private boolean ocrLinkEnabled() {
        return settingReader.getBool(GROUP, "ocr_link_enabled", true);
    }

    /**
     * 获取 OCR 客户端管理器（可能为 空）。
     *
     * @return OCR 客户端管理器或 空
     */
    private OcrClientManager ocrClientManager() {
        return ocrClientManagerProvider == null ? null : ocrClientManagerProvider.getIfAvailable();
    }

    /**
     * 判断版面类别是否属于需要 OCR 的文本类。
     *
     * @param label 版面类别名
     * @return true 表示该区域应送 OCR
     */
    private static boolean isTextRegion(String label) {
        if (StringUtils.isBlank(label)) {
            return false;
        }
        String key = label.toLowerCase();
        return TEXT_REGION_LABELS.stream().anyMatch(key::contains);
    }

    /**
     * 枚举可用版面模型清单。
     *
     * @return 模型 标识 列表
     */
    public List<String> listModels() {
        return LayoutDetector.listModels();
    }

    /**
     * 读取配置指纹（模型 + 阈值 + 设备）。
     *
     * @return 指纹字符串
     */
    private String readFingerprint() {
        return String.join("|",
                StringUtils.defaultString(settingReader.getStr(GROUP, "layout_model"), ""),
                String.valueOf(settingReader.getDecimalOrNull(GROUP, "layout_threshold")),
                StringUtils.defaultString(settingReader.getStr(GROUP, "device"), ""));
    }

    /**
     * 按最新配置构建检测器。
     *
     * @return 版面检测器，模型未配置时返回 空
     */
    private LayoutDetector build() {
        String model = settingReader.getStr(GROUP, "layout_model");
        if (StringUtils.isBlank(model)) {
            return null;
        }
        LayoutDetector detector = LayoutDetector.create(model);
        var threshold = settingReader.getDecimalOrNull(GROUP, "layout_threshold");
        if (threshold != null) {
            detector.threshold(threshold.floatValue());
        }
        String device = settingReader.getStr(GROUP, "device");
        if (StringUtils.isNotBlank(device)) {
            detector.device(device);
        }
        // 桥接为模型级参数：使 device / threshold 能在引擎侧被 DJL 与裸 ORT 两条链路共同读取，
        // 并允许同一进程内不同模型使用不同设备
        Float thresholdValue = threshold == null ? null : threshold.floatValue();
        com.chua.starter.ai.support.configuration.client.ModelParamBridge.bridge(model, device, thresholdValue);
        return detector;
    }
}
