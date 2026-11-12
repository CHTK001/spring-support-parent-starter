package com.chua.starter.ai.support.configuration.client;

import com.chua.deeplearning.support.engine.ModelRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 外部服务器目录模型绑定支持。
 *
 * <p>ONNX / 本地推理模型允许通过 ReFilePicker 选择服务器端模型目录（而非云端地址）。
 * 当读取到的配置值是一个已存在的绝对目录、且尚未在 {@link ModelRegistry} 注册时，
 * 按「分组.字段」找到同族模板模型，复用其 Translator / 输入输出 / 能力接口，
 * 把该目录动态注册为可运行模型。</p>
 *
 * <p>目录内模型结构需与模板模型同族（相同 Translator 可解析）。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Component
public class ExternalModelSupport {

    /**
     * 「分组.字段」→ 同族模板模型标识（取该字段默认 / 描述中首个模型）。
     */
    private static final Map<String, String> TEMPLATES = Map.ofEntries(
            // 人脸识别
            Map.entry("face.detector_model", "scrfd-face-detector"),
            Map.entry("face.feature_model", "faceplugin-face-feature"),
            Map.entry("face.liveness_model", "face-anti-spoof"),
            Map.entry("face.anime_detector_model", "anime-face-detector"),
            Map.entry("face.super_resolution_model", "gfpgan-face-super-resolution"),
            Map.entry("face.restore_model", "codeformer"),
            Map.entry("face.attribute_model", "age-race-gender"),
            Map.entry("face.emotion_model", "emotion-ferplus"),
            Map.entry("face.landmark_model", "faceplugin-face-landmark"),
            Map.entry("face.deepfake_model", "deepfake-detector"),
            // OCR 文字识别
            Map.entry("ocr.detector_model", "paddleocrv6-det"),
            Map.entry("ocr.recognizer_model", "paddleocrv6-rec"),
            Map.entry("ocr.direction_model", "doc-orientation"),
            Map.entry("ocr.restorer_model", "text-bsr"),
            Map.entry("ocr.corrector_model", "macbert4csc"),
            // 布局识别
        Map.entry("layout.layout_model", "pp-doc-layout"),
            // 图片识别
            Map.entry("image.classify_model", "mobilenetv4-classification"),
            Map.entry("image.detect_model", "yolo26n"),
            // 2026-11-12：image.segment_model 删除 —— 图片识别分组没有分割能力，
            // 该字段已从 ImageRecognitionGlobalSettingProvider 移除，模板表同步。
            Map.entry("image.captioning_model", "vit-gpt2-captioning"),
            Map.entry("image.super_resolution_model", "real-esrgan")
    );

    /**
     * 准备外部目录模型：若值为已存在且未注册的服务器目录，则按字段模板动态注册。
     *
     * @param group 分组
     * @param field 字段
     * @param value 原始配置值
     * @return 处理后的模型键（目录路径原样返回，注册为副作用）；非目录值原样返回
     */
    public String prepare(String group, String field, String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        try {
            String v = value.trim();
            if (!ModelRegistry.isExistingDirectory(v)) {
                return value;
            }
            if (ModelRegistry.isRegistered(v)) {
                return v;
            }
            String template = TEMPLATES.get(group + "." + field);
            if (template == null) {
                log.warn("[ExternalModel] 字段 {}.{} 无同族模板，外部目录无法绑定: {}", group, field, v);
                return v;
            }
            return ModelRegistry.registerExternalDirectory(v, template);
        } catch (Exception e) {
            // 外部目录绑定属于增强能力，任何异常都不应阻断正常的配置读取
            log.debug("[ExternalModel] prepare 跳过(group={},field={}): {}", group, field, e.getMessage());
            return value;
        }
    }
}
