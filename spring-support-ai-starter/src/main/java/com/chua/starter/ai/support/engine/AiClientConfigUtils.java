package com.chua.starter.ai.support.engine;

import com.chua.starter.ai.support.properties.AiProperties;

/**
 * AI客户端配置工具类
 *
 * @author CH
 * @since 2024-01-01
 */
public class AiClientConfigUtils {

    /**
     * 从AiProperties转换为AiClientConfig
     *
     * @param aiProperties AI配置属性
     * @return AI客户端配置
     */
    public static AiClientConfig fromProperties(AiProperties aiProperties) {
        if (aiProperties == null) {
            return null;
        }

        var faceDetectionConfig = aiProperties.getFaceDetection() != null
                ? AiClientConfig.FaceDetectionConfig.builder()
                .enabled(aiProperties.getFaceDetection().isEnabled())
                .provider(aiProperties.getFaceDetection().getProvider())
                .confidenceThreshold(aiProperties.getFaceDetection().getConfidenceThreshold())
                .nmsThreshold(aiProperties.getFaceDetection().getNmsThreshold())
                .build()
                : null;

        var ocrConfig = aiProperties.getOcr() != null
                ? AiClientConfig.OcrConfig.builder()
                .enabled(aiProperties.getOcr().isEnabled())
                .provider(aiProperties.getOcr().getProvider())
                .language(aiProperties.getOcr().getLanguage())
                .build()
                : null;

        var imageConfig = aiProperties.getImage() != null
                ? AiClientConfig.ImageConfig.builder()
                .enabled(aiProperties.getImage().isEnabled())
                .provider(aiProperties.getImage().getProvider())
                .featureDimension(aiProperties.getImage().getFeatureDimension())
                .build()
                : null;

        var textConfig = aiProperties.getText() != null
                ? AiClientConfig.TextConfig.builder()
                .enabled(aiProperties.getText().isEnabled())
                .provider(aiProperties.getText().getProvider())
                .maxSequenceLength(aiProperties.getText().getMaxSequenceLength())
                .build()
                : null;

        return AiClientConfig.builder()
                .enabled(aiProperties.isEnabled())
                .modelPath(aiProperties.getModelPath())
                .faceDetection(faceDetectionConfig)
                .ocr(ocrConfig)
                .image(imageConfig)
                .text(textConfig)
                .build();
    }
}

