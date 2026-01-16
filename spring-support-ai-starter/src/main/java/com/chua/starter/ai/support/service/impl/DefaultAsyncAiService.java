package com.chua.starter.ai.support.service.impl;

import com.chua.starter.ai.support.model.*;
import com.chua.starter.ai.support.service.AiService;
import com.chua.starter.ai.support.service.AsyncAiService;

import java.io.File;
import java.io.InputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * 默认异步AI服务实现
 * <p>
 * 基于同步AiService封装的异步实现
 *
 * @author CH
 * @since 2024-01-01
 */
public class DefaultAsyncAiService implements AsyncAiService {

    private final AiService aiService;
    private final Executor executor;

    public DefaultAsyncAiService(AiService aiService) {
        this(aiService, null);
    }

    public DefaultAsyncAiService(AiService aiService, Executor executor) {
        this.aiService = aiService;
        this.executor = executor;
    }

    private Executor getExecutor() {
        return executor != null ? executor : getDefaultExecutor();
    }

    // ==================== 人脸相关 ====================

    @Override
    public CompletableFuture<FaceDetectionResult> detectFacesAsync(byte[] imageData) {
        return CompletableFuture.supplyAsync(() -> aiService.detectFaces(imageData), getExecutor());
    }

    @Override
    public CompletableFuture<FaceDetectionResult> detectFacesAsync(File imageFile) {
        return CompletableFuture.supplyAsync(() -> aiService.detectFaces(imageFile), getExecutor());
    }

    @Override
    public CompletableFuture<FaceDetectionResult> detectFacesAsync(InputStream inputStream) {
        return CompletableFuture.supplyAsync(() -> aiService.detectFaces(inputStream), getExecutor());
    }

    @Override
    public CompletableFuture<FeatureResult> extractFaceFeatureAsync(byte[] imageData) {
        return CompletableFuture.supplyAsync(() -> aiService.extractFaceFeature(imageData), getExecutor());
    }

    @Override
    public CompletableFuture<FeatureResult> extractFaceFeatureAsync(File imageFile) {
        return CompletableFuture.supplyAsync(() -> aiService.extractFaceFeature(imageFile), getExecutor());
    }

    @Override
    public CompletableFuture<GenderAgeResult> detectGenderAgeAsync(byte[] imageData) {
        return CompletableFuture.supplyAsync(() -> aiService.detectGenderAge(imageData), getExecutor());
    }

    @Override
    public CompletableFuture<GenderAgeResult> detectGenderAgeAsync(File imageFile) {
        return CompletableFuture.supplyAsync(() -> aiService.detectGenderAge(imageFile), getExecutor());
    }

    // ==================== 图片相关 ====================

    @Override
    public CompletableFuture<ImageDetectionResult> detectImageAsync(byte[] imageData) {
        return CompletableFuture.supplyAsync(() -> aiService.detectImage(imageData), getExecutor());
    }

    @Override
    public CompletableFuture<ImageDetectionResult> detectImageAsync(File imageFile) {
        return CompletableFuture.supplyAsync(() -> aiService.detectImage(imageFile), getExecutor());
    }

    @Override
    public CompletableFuture<FeatureResult> extractImageFeatureAsync(byte[] imageData) {
        return CompletableFuture.supplyAsync(() -> aiService.extractImageFeature(imageData), getExecutor());
    }

    @Override
    public CompletableFuture<FeatureResult> extractImageFeatureAsync(File imageFile) {
        return CompletableFuture.supplyAsync(() -> aiService.extractImageFeature(imageFile), getExecutor());
    }

    // ==================== 文本相关 ====================

    @Override
    public CompletableFuture<FeatureResult> extractTextFeatureAsync(String text) {
        return CompletableFuture.supplyAsync(() -> aiService.extractTextFeature(text), getExecutor());
    }

    // ==================== OCR相关 ====================

    @Override
    public CompletableFuture<OcrResult> ocrAsync(byte[] imageData) {
        return CompletableFuture.supplyAsync(() -> aiService.ocr(imageData), getExecutor());
    }

    @Override
    public CompletableFuture<OcrResult> ocrAsync(File imageFile) {
        return CompletableFuture.supplyAsync(() -> aiService.ocr(imageFile), getExecutor());
    }

    @Override
    public CompletableFuture<OcrResult> ocrAsync(InputStream inputStream) {
        return CompletableFuture.supplyAsync(() -> aiService.ocr(inputStream), getExecutor());
    }

    // ==================== 版面分析 ====================

    @Override
    public CompletableFuture<LayoutAnalysisResult> analyzeLayoutAsync(byte[] imageData) {
        return CompletableFuture.supplyAsync(() -> aiService.analyzeLayout(imageData), getExecutor());
    }

    @Override
    public CompletableFuture<LayoutAnalysisResult> analyzeLayoutAsync(File imageFile) {
        return CompletableFuture.supplyAsync(() -> aiService.analyzeLayout(imageFile), getExecutor());
    }
}
