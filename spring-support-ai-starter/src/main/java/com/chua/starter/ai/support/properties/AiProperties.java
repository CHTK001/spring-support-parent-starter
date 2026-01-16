package com.chua.starter.ai.support.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI模块配置属性
 *
 * @author CH
 * @since 2024-01-01
 */
@Data
@ConfigurationProperties(prefix = AiProperties.PREFIX)
public class AiProperties {

    public static final String PREFIX = "spring.ai";

    /**
     * 是否启用AI模块
     */
    private boolean enabled = true;

    /**
     * 模型存放路径
     */
    private String modelPath;

    /**
     * 人脸检测配置
     */
    private FaceDetection faceDetection = new FaceDetection();

    /**
     * OCR配置
     */
    private Ocr ocr = new Ocr();

    /**
     * 图像配置
     */
    private Image image = new Image();

    /**
     * 文本配置
     */
    private Text text = new Text();

    /**
     * 人脸检测配置
     */
    @Data
    public static class FaceDetection {
        /**
         * 是否启用
         */
        private boolean enabled = true;

        /**
         * 厂商标识（如：default, baidu, megvii, sensetime）
         */
        private String provider = "default";

        /**
         * 置信度阈值
         */
        private float confidenceThreshold = 0.5f;

        /**
         * NMS阈值
         */
        private float nmsThreshold = 0.4f;
    }

    /**
     * OCR配置
     */
    @Data
    public static class Ocr {
        /**
         * 是否启用
         */
        private boolean enabled = true;

        /**
         * 厂商标识（如：default, baidu, tesseract, paddleocr）
         */
        private String provider = "default";

        /**
         * 语言
         */
        private String language = "chi_sim";
    }

    /**
     * 图像配置
     */
    @Data
    public static class Image {
        /**
         * 是否启用
         */
        private boolean enabled = true;

        /**
         * 厂商标识（如：default, baidu, aliyun）
         */
        private String provider = "default";

        /**
         * 特征维度
         */
        private int featureDimension = 512;
    }

    /**
     * 文本配置
     */
    @Data
    public static class Text {
        /**
         * 是否启用
         */
        private boolean enabled = true;

        /**
         * 厂商标识（如：default, openai, baidu）
         */
        private String provider = "default";

        /**
         * 最大序列长度
         */
        private int maxSequenceLength = 512;
    }
}
