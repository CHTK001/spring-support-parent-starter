package com.chua.starter.ai.support.configuration.ocr;

import com.chua.common.support.utils.StringUtils;
import com.chua.deeplearning.support.ocr.OcrPipeline;
import com.chua.deeplearning.support.ocr.OcrResult;
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
     * OCR 是否已配置可用（detector_model / recognizer_model 至少其一非空）。
     * <p>供版面联动等上层逻辑判断能否降级，避免直接触发未配置异常。</p>
     *
     * @return true 表示 OCR 可用
     */
    public boolean available() {
        String detector = settingReader.getStr(GROUP, "detector_model");
        String recognizer = settingReader.getStr(GROUP, "recognizer_model");
        return StringUtils.isNotBlank(detector) || StringUtils.isNotBlank(recognizer);
    }

    /**
     * 在给定区域集合内识别（供版面联动：先版面分析裁剪，再逐区域识别）。
     *
     * @param imageData 图片字节
     * @param regions  需要识别的区域（像素坐标）
     * @return 识别结果列表（位置已回填为原图坐标）
     */
    public List<OcrResult> recognizeRegions(byte[] imageData,
                                           List<com.chua.deeplearning.support.model.PredictRectangle> regions) {
        if (regions == null || regions.isEmpty()) {
            return List.of();
        }
        OcrPipeline ocr = current();
        List<OcrResult> results = new java.util.ArrayList<>();
        for (com.chua.deeplearning.support.model.PredictRectangle region : regions) {
            byte[] crop = com.chua.deeplearning.support.utils.ImageCropUtils.crop(imageData, region);
            if (crop == null || crop.length == 0) {
                continue;
            }
            for (OcrResult item : ocr.recognizeDetail(crop)) {
                results.add(offset(item, region));
            }
        }
        return results;
    }

    /**
     * 将区域内的识别结果坐标回填为原图坐标。
     *
     * @param item   区域内识别结果
     * @param region 区域
     * @return 坐标已平移的结果
     */
    private static OcrResult offset(OcrResult item, com.chua.deeplearning.support.model.PredictRectangle region) {
        com.chua.deeplearning.support.model.PredictRectangle box = item.boundingBox();
        if (box == null) {
            return item;
        }
        com.chua.deeplearning.support.model.PredictRectangle moved =
                new com.chua.deeplearning.support.model.PredictRectangle(
                        box.x() + region.x(),
                        box.y() + region.y(),
                        box.width(),
                        box.height(),
                        box.confidence(),
                        box.label(),
                        box.labelName(),
                        box.keypoints());
        return new OcrResult(item.text(), item.confidence(), moved, item.angle());
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
        var detectorThreshold = settingReader.getDecimalOrNull(GROUP, "detector_threshold");
        builder.minConfidence(detectorThreshold == null ? 0f : detectorThreshold.floatValue());
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
        // 桥接为模型级参数：device 与置信度阈值对 det / rec / direction / restorer 全部生效，
        // 使 OCR 各模型可分别配置设备（原先 device 只被读取却从未使用）
        String device = settingReader.getStr(GROUP, "device");
        Float thresholdValue = detectorThreshold == null ? null : detectorThreshold.floatValue();
        for (String modelId : new String[]{detector, recognizer, direction, restorer}) {
            com.chua.starter.ai.support.configuration.client.ModelParamBridge
                    .bridge(modelId, device, thresholdValue);
        }
        return builder.build();
    }
}
