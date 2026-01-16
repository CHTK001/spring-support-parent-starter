package com.chua.starter.ai.support.engine;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * AI客户端配置信息
 *
 * @author CH
 * @since 2024-01-01
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiClientConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 是否启用AI模块
     */
    private boolean enabled;

    /**
     * 模型存放路径
     */
    private String modelPath;

    /**
     * 人脸检测配置信息
     */
    private FaceDetectionConfig faceDetection;

    /**
     * OCR配置信息
     */
    private OcrConfig ocr;

    /**
     * 图像配置信息
     */
    private ImageConfig image;

    /**
     * 文本配置信息
     */
    private TextConfig text;

    /**
     * 人脸检测配置信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FaceDetectionConfig implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 是否启用
         */
        private boolean enabled;

        /**
         * 厂商标识
         */
        private String provider;

        /**
         * 置信度阈值
         */
        private float confidenceThreshold;

        /**
         * NMS阈值
         */
        private float nmsThreshold;
    }

    /**
     * OCR配置信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OcrConfig implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 是否启用
         */
        private boolean enabled;

        /**
         * 厂商标识
         */
        private String provider;

        /**
         * 语言
         */
        private String language;
    }

    /**
     * 图像配置信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImageConfig implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 是否启用
         */
        private boolean enabled;

        /**
         * 厂商标识
         */
        private String provider;

        /**
         * 特征维度
         */
        private int featureDimension;
    }

    /**
     * 文本配置信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TextConfig implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 是否启用
         */
        private boolean enabled;

        /**
         * 厂商标识
         */
        private String provider;

        /**
         * 最大序列长度
         */
        private int maxSequenceLength;
    }
}

