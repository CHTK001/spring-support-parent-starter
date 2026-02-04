package com.chua.starter.ai.support.engine.impl;

import com.chua.starter.ai.support.chat.AiChat;
import com.chua.starter.ai.support.engine.DetectionConfiguration;
import com.chua.starter.ai.support.engine.FaceEngine;
import com.chua.starter.ai.support.model.FaceDetectionResult;
import com.chua.starter.ai.support.model.FeatureResult;
import com.chua.starter.ai.support.service.AiService;
import lombok.RequiredArgsConstructor;

import java.io.File;

/**
 * 基于AiChat的人脸识别引擎实现
 *
 * @author CH
 * @since 2024-01-01
 */
@RequiredArgsConstructor
public class AiChatFaceEngine implements FaceEngine {

    private final AiService aiService;

    @Override
    public FaceDetectionResult detectFaces(byte[] imageData) {
        return AiChat.of(aiService)
                .image(imageData)
                .detectFaces();
    }

    @Override
    public FaceDetectionResult detectFaces(File imageFile) {
        return AiChat.of(aiService)
                .image(imageFile)
                .detectFaces();
    }

    @Override
    public FeatureResult extractFaceFeature(byte[] imageData) {
        return AiChat.of(aiService)
                .image(imageData)
                .faceFeature();
    }

    @Override
    public FeatureResult extractFaceFeature(File imageFile) {
        return AiChat.of(aiService)
                .image(imageFile)
                .faceFeature();
    }

    @Override
    public FaceDetectionResult detectFaces(byte[] imageData, DetectionConfiguration config) {
        var result = detectFaces(imageData);
        return filterByConfig(result, config);
    }

    @Override
    public FaceDetectionResult detectFaces(File imageFile, DetectionConfiguration config) {
        var result = detectFaces(imageFile);
        return filterByConfig(result, config);
    }

    @Override
    public FeatureResult extractFaceFeature(byte[] imageData, DetectionConfiguration config) {
        return extractFaceFeature(imageData);
    }

    @Override
    public FeatureResult extractFaceFeature(File imageFile, DetectionConfiguration config) {
        return extractFaceFeature(imageFile);
    }

    /**
     * 根据配置过滤检测结果
     *
     * @param result 原始结果
     * @param config 检测配置
     * @return 过滤后的结果
     */
    private FaceDetectionResult filterByConfig(FaceDetectionResult result, DetectionConfiguration config) {
        if (config == null || result.getFaces() == null) {
            return result;
        }

        var filteredFaces = result.getFaces().stream()
                .filter(face -> {
                    if (config.getConfidenceThreshold() != null && face.getConfidence() < config.getConfidenceThreshold()) {
                        return false;
                    }
                    if (config.getMinFaceSize() != null) {
                        var faceSize = Math.min(face.getWidth(), face.getHeight());
                        if (faceSize < config.getMinFaceSize()) {
                            return false;
                        }
                    }
                    return true;
                })
                .limit(config.getMaxFaceCount() != null ? config.getMaxFaceCount() : Integer.MAX_VALUE)
                .toList();
        result.setFaces(filteredFaces);
        return result;
    }
}

