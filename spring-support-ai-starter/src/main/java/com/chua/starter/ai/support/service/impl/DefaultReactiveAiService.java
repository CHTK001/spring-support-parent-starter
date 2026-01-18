package com.chua.starter.ai.support.service.impl;

import com.chua.starter.ai.support.model.*;
import com.chua.starter.ai.support.service.AiService;
import com.chua.starter.ai.support.service.ReactiveAiService;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.io.File;
import java.io.InputStream;

/**
 * 默认Reactor响应式AI服务实现
 * <p>
 * 基于同步AiService封装的响应式实现
 *
 * @author CH
 * @since 2024-01-01
 */
public class DefaultReactiveAiService implements ReactiveAiService {

    private final AiService aiService;
    private final Scheduler scheduler;

    public DefaultReactiveAiService(AiService aiService) {
        this(aiService, Schedulers.boundedElastic());
    }

    public DefaultReactiveAiService(AiService aiService, Scheduler scheduler) {
        this.aiService = aiService;
        this.scheduler = scheduler;
    }

    // ==================== 人脸相关 ====================

    @Override
    public Mono<FaceDetectionResult> detectFacesMono(byte[] imageData) {
        return Mono.fromCallable(() -> aiService.detectFaces(imageData))
                .subscribeOn(scheduler);
    }

    @Override
    public Mono<FaceDetectionResult> detectFacesMono(File imageFile) {
        return Mono.fromCallable(() -> aiService.detectFaces(imageFile))
                .subscribeOn(scheduler);
    }

    @Override
    public Mono<FaceDetectionResult> detectFacesMono(InputStream inputStream) {
        return Mono.fromCallable(() -> aiService.detectFaces(inputStream))
                .subscribeOn(scheduler);
    }

    @Override
    public Mono<FeatureResult> extractFaceFeatureMono(byte[] imageData) {
        return Mono.fromCallable(() -> aiService.extractFaceFeature(imageData))
                .subscribeOn(scheduler);
    }

    @Override
    public Mono<FeatureResult> extractFaceFeatureMono(File imageFile) {
        return Mono.fromCallable(() -> aiService.extractFaceFeature(imageFile))
                .subscribeOn(scheduler);
    }

    @Override
    public Mono<GenderAgeResult> detectGenderAgeMono(byte[] imageData) {
        return Mono.fromCallable(() -> aiService.detectGenderAge(imageData))
                .subscribeOn(scheduler);
    }

    @Override
    public Mono<GenderAgeResult> detectGenderAgeMono(File imageFile) {
        return Mono.fromCallable(() -> aiService.detectGenderAge(imageFile))
                .subscribeOn(scheduler);
    }

    // ==================== 图片相关 ====================

    @Override
    public Mono<ImageDetectionResult> detectImageMono(byte[] imageData) {
        return Mono.fromCallable(() -> aiService.detectImage(imageData))
                .subscribeOn(scheduler);
    }

    @Override
    public Mono<ImageDetectionResult> detectImageMono(File imageFile) {
        return Mono.fromCallable(() -> aiService.detectImage(imageFile))
                .subscribeOn(scheduler);
    }

    @Override
    public Mono<FeatureResult> extractImageFeatureMono(byte[] imageData) {
        return Mono.fromCallable(() -> aiService.extractImageFeature(imageData))
                .subscribeOn(scheduler);
    }

    @Override
    public Mono<FeatureResult> extractImageFeatureMono(File imageFile) {
        return Mono.fromCallable(() -> aiService.extractImageFeature(imageFile))
                .subscribeOn(scheduler);
    }

    // ==================== 文本相关 ====================

    @Override
    public Mono<FeatureResult> extractTextFeatureMono(String text) {
        return Mono.fromCallable(() -> aiService.extractTextFeature(text))
                .subscribeOn(scheduler);
    }

    // ==================== OCR相关 ====================

    @Override
    public Mono<OcrResult> ocrMono(byte[] imageData) {
        return Mono.fromCallable(() -> aiService.ocr(imageData))
                .subscribeOn(scheduler);
    }

    @Override
    public Mono<OcrResult> ocrMono(File imageFile) {
        return Mono.fromCallable(() -> aiService.ocr(imageFile))
                .subscribeOn(scheduler);
    }

    @Override
    public Mono<OcrResult> ocrMono(InputStream inputStream) {
        return Mono.fromCallable(() -> aiService.ocr(inputStream))
                .subscribeOn(scheduler);
    }

    // ==================== 版面分析 ====================

    @Override
    public Mono<LayoutAnalysisResult> analyzeLayoutMono(byte[] imageData) {
        return Mono.fromCallable(() -> aiService.analyzeLayout(imageData))
                .subscribeOn(scheduler);
    }

    @Override
    public Mono<LayoutAnalysisResult> analyzeLayoutMono(File imageFile) {
        return Mono.fromCallable(() -> aiService.analyzeLayout(imageFile))
                .subscribeOn(scheduler);
    }
}
