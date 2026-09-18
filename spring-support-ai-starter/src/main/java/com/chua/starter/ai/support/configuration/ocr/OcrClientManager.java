package com.chua.starter.ai.support.configuration.ocr;

import com.chua.common.support.utils.StringUtils;
import com.chua.deeplearning.support.ocr.OcrPipeline;
import com.chua.starter.ai.support.configuration.client.GlobalSettingReader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
* OCR 识别客户端管理器。
*
* <p>按 {@code ocr} 分组全局设置（sys_ai_global_setting）懒加载
* {@link OcrPipeline}（检测 → 裁剪 → 方向矫正 → 修复 → 识别），配置通过
* {@link OcrSettingsChangedEvent} 热重建，实时生效。模型懒加载：
* 仅在首次调用对应能力时由引擎创建模型实例，避免启动阻塞。</p>
*
* @since 4.0.0.42
* @author CH
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OcrClientManager {

    /**
    * 全局设置键值分组
    */
    private static final String GROUP = "ocr";

    /**
    * 全局设置读取工具
    */
    private final GlobalSettingReader settingReader;

    /**
    * 当前生效的 OCR 管线（懒构建，热更新时替换）
    */
    private volatile OcrPipeline pipeline;

    /**
    * 获取当前 OCR 管线（懒加载）。
    *
    * @return OCR 管线
    * @throws IllegalStateException 检测/识别模型未配置时
    */
    public OcrPipeline current() {
        OcrPipeline current = pipeline;
        if (current == null) {
            synchronized (this) {
                if (pipeline == null) {
                    pipeline = build();
                }
                current = pipeline;
            }
        }
        return current;
    }

    /**
    * 整图识别（拼接全部文本块）。
    *
    * @param imageData 图片字节
    * @return 识别文本
    */
    public String recognize(byte[] imageData) {
        return current().recognize(imageData);
    }

    /**
    * 详细识别（文本块 + 置信度 + 位置）。
    *
    * @param imageData 图片字节
    * @return 识别结果列表
    */
    public List<com.chua.deeplearning.support.ocr.OcrResult> recognizeDetail(byte[] imageData) {
        return current().recognizeDetail(imageData);
    }

    /**
    * 绘制检测标注图（框 + 识别文字）。
    *
    * @param imageData 图片字节
    * @return 标注后 JPEG 字节
    */
    public byte[] annotate(byte[] imageData) {
        return current().toDrawer(imageData);
    }

    /**
    * 枚举可用 OCR 模型清单（按能力分组）。
    *
    * @return 能力分组 → 模型 标识 列表
    */
    public Map<String, List<String>> listModels() {
        OcrPipeline probe = new OcrPipeline(
                com.chua.deeplearning.support.image.ImageDetector.create("none"),
                com.chua.deeplearning.support.ocr.OcrRecognizer.create("none"),
                null, null, false, true, 0f, 2, 40, 25f, false, 100f, false, false);
        return probe.listModels();
    }

    /**
    * 全局配置变更监听：ocr 分组更新后热重建，实时生效。
    *
    * @param event 配置变更事件
    */
    @EventListener
    public void onOcrSettingsChanged(OcrSettingsChangedEvent event) {
        if (event == null || !GROUP.equals(event.getGroup())) {
            return;
        }
        log.info("[OcrClientManager] 收到 ocr 分组配置变更，开始热重建");
        /**
        * rebuild。
        */
        rebuild();
    }

    /**
    * 配置热更新：按最新配置重建 OCR 管线。
    */
    public synchronized void rebuild() {
        this.pipeline = build();
        log.info("[OcrClientManager] OCR 管线热更新完成");
    }

    /**
    * 按 ocr 分组配置构建管线。
    *
    * @return OCR 管线
    */
    private OcrPipeline build() {
        String detector = settingReader.getStr(GROUP, "detector_model");
        String recognizer = settingReader.getStr(GROUP, "recognizer_model");
        if (StringUtils.isBlank(detector) && StringUtils.isBlank(recognizer)) {
            /**
            * illegal状态异常。
            * @param recognizer_model" recognizer_模型"
            * @return illegal状态异常的结果
            */
            throw new IllegalStateException("OCR 模型未配置：请在设置中配置 ocr 分组的 detector_model / recognizer_model");
        }
        OcrPipeline.Builder builder = OcrPipeline.builder();
        if (StringUtils.isNotBlank(detector)) {
            builder.detector(detector);
        }
        if (StringUtils.isNotBlank(recognizer)) {
            builder.recognizer(recognizer);
        }
        builder.minConfidence(settingReader.getDecimalOrNull(GROUP, "detector_threshold") == null
                ? 0f
                : settingReader.getDecimalOrNull(GROUP, "detector_threshold").floatValue());
        builder.sortReadingOrder(settingReader.getBool(GROUP, "sort_reading_order", true));
        String direction = settingReader.getStr(GROUP, "direction_model");
        if (StringUtils.isNotBlank(direction)) {
            builder.direction(direction);
        }
        String restorer = settingReader.getStr(GROUP, "restorer_model");
        if (StringUtils.isNotBlank(restorer)) {
            builder.enhancer(restorer);
            builder.enhanceInPipeline(true);
        }
        return builder.build();
    }
}
