package com.chua.starter.ai.support.configuration.layout;

import com.chua.common.support.utils.StringUtils;
import com.chua.deeplearning.support.layout.LayoutDetector;
import com.chua.deeplearning.support.model.PredictRectangle;
import com.chua.starter.ai.support.configuration.client.GlobalSettingReader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

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
     * 全局设置读取工具
     */
    private final GlobalSettingReader settingReader;

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
     *
     * @param imageData 图片字节
     * @return Markdown 文本
     */
    public String parse(byte[] imageData) {
        return current().parse(imageData);
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
        return detector;
    }
}
