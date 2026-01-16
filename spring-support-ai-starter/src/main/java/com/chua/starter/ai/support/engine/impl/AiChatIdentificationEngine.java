package com.chua.starter.ai.support.engine.impl;

import com.chua.starter.ai.support.chat.AiChat;
import com.chua.starter.ai.support.engine.DetectionConfiguration;
import com.chua.starter.ai.support.engine.IdentificationEngine;
import com.chua.starter.ai.support.model.FaceDetectionResult;
import com.chua.starter.ai.support.model.FeatureResult;
import com.chua.starter.ai.support.service.AiService;
import lombok.RequiredArgsConstructor;

import java.io.File;

/**
 * 基于AiChat的身份识别引擎实现
 *
 * @author CH
 * @since 2024-01-01
 */
@RequiredArgsConstructor
public class AiChatIdentificationEngine implements IdentificationEngine {

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
        // 目前底层 AiService 不支持配置，使用默认实现
        // 后续可以在 AiService 中添加带配置的方法，或在这里处理配置逻辑
        var result = detectFaces(imageData);
        // 可以根据 config 过滤结果
        if (config != null && result.getFaces() != null) {
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
        }
        return result;
    }

    @Override
    public FaceDetectionResult detectFaces(File imageFile, DetectionConfiguration config) {
        var result = detectFaces(imageFile);
        // 可以根据 config 过滤结果
        if (config != null && result.getFaces() != null) {
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
        }
        return result;
    }

    @Override
    public FeatureResult extractFaceFeature(byte[] imageData, DetectionConfiguration config) {
        // 目前底层 AiService 不支持配置，使用默认实现
        // 后续可以在 AiService 中添加带配置的方法
        return extractFaceFeature(imageData);
    }

    @Override
    public FeatureResult extractFaceFeature(File imageFile, DetectionConfiguration config) {
        // 目前底层 AiService 不支持配置，使用默认实现
        // 后续可以在 AiService 中添加带配置的方法
        return extractFaceFeature(imageFile);
    }
}

