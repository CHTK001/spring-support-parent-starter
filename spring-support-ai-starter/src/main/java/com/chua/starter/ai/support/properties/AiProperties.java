package com.chua.starter.ai.support.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * AI 模块配置属性
 * <p>
 * 支持多云厂商配置和统一的 AI 能力管理。
 *
 * @author CH
 * @since 2025/01/XX
 */
@Data
@ConfigurationProperties(prefix = AiProperties.PREFIX)
public class AiProperties {

    public static final String PREFIX = "spring.ai";

    /**
     * 是否启用 AI 模块
     */
    private boolean enabled = true;

    /**
     * 默认提供商
     */
    private String defaultProvider;

    /**
     * LLM 配置
     */
    private LlmProperties llm = new LlmProperties();

    /**
     * OCR 配置
     */
    private OcrProperties ocr = new OcrProperties();

    /**
     * 人脸识别配置
     */
    private FaceProperties face = new FaceProperties();

    /**
     * 云厂商配置
     * <p>
     * key: 提供商名称（如 openai, aliyun, baidu, tencent）
     * value: 提供商配置
     */
    private Map<String, ProviderProperties> providers = new HashMap<>();

    // ========== 向后兼容：保留旧的配置结构 ==========

    /**
     * 模型存放路径（向后兼容）
     * @deprecated 使用 providers[xxx].modelPath 替代
     */
    @Deprecated
    private String modelPath;

    /**
     * 人脸检测配置（向后兼容）
     * @deprecated 使用 face 替代
     */
    @Deprecated
    private FaceDetection faceDetection = new FaceDetection();

    /**
     * 图像配置（向后兼容）
     */
    private Image image = new Image();

    /**
     * 文本配置（向后兼容）
     */
    private Text text = new Text();

    /**
     * 人脸检测配置（向后兼容）
     * @deprecated 使用 FaceProperties 替代
     */
    @Deprecated
    @Data
    public static class FaceDetection {
        private boolean enabled = true;
        private String provider = "default";
        private float confidenceThreshold = 0.5f;
        private float nmsThreshold = 0.4f;
    }

    /**
     * 图像配置（向后兼容）
     */
    @Data
    public static class Image {
        private boolean enabled = true;
        private String provider = "default";
        private int featureDimension = 512;
    }

    /**
     * 文本配置（向后兼容）
     */
    @Data
    public static class Text {
        private boolean enabled = true;
        private String provider = "default";
        private int maxSequenceLength = 512;
    }
}
