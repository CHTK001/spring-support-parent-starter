package com.chua.starter.ai.support.engine.impl;

import com.chua.starter.ai.support.engine.AiClient;
import com.chua.starter.ai.support.engine.AiClientConfig;
import com.chua.starter.ai.support.engine.DetectionConfiguration;
import com.chua.starter.ai.support.engine.IdentificationEngine;
import com.chua.starter.ai.support.model.FaceDetectionResult;
import com.chua.starter.ai.support.model.FeatureResult;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.io.File;

/**
 * 默认AI客户端实现
 *
 * @author CH
 * @since 2024-01-01
 */
@RequiredArgsConstructor
class DefaultAiClient implements AiClient {

    private final IdentificationEngine identificationEngine;
    private final Scheduler scheduler;
    private final AiClientConfig config;

    public DefaultAiClient(IdentificationEngine identificationEngine) {
        this(identificationEngine, Schedulers.boundedElastic(), null);
    }

    public DefaultAiClient(IdentificationEngine identificationEngine, AiClientConfig config) {
        this(identificationEngine, Schedulers.boundedElastic(), config);
    }

    @Override
    public FaceDetectionResult detectFaces(byte[] imageData) {
        return identificationEngine.detectFaces(imageData);
    }

    @Override
    public FaceDetectionResult detectFaces(File imageFile) {
        return identificationEngine.detectFaces(imageFile);
    }

    @Override
    public FeatureResult extractFaceFeature(byte[] imageData) {
        return identificationEngine.extractFaceFeature(imageData);
    }

    @Override
    public FeatureResult extractFaceFeature(File imageFile) {
        return identificationEngine.extractFaceFeature(imageFile);
    }

    @Override
    public Mono<FaceDetectionResult> detectFacesMono(byte[] imageData) {
        return Mono.fromCallable(() -> identificationEngine.detectFaces(imageData))
                .subscribeOn(scheduler);
    }

    @Override
    public Mono<FaceDetectionResult> detectFacesMono(File imageFile) {
        return Mono.fromCallable(() -> identificationEngine.detectFaces(imageFile))
                .subscribeOn(scheduler);
    }

    @Override
    public Mono<FeatureResult> extractFaceFeatureMono(byte[] imageData) {
        return Mono.fromCallable(() -> identificationEngine.extractFaceFeature(imageData))
                .subscribeOn(scheduler);
    }

    @Override
    public Mono<FeatureResult> extractFaceFeatureMono(File imageFile) {
        return Mono.fromCallable(() -> identificationEngine.extractFaceFeature(imageFile))
                .subscribeOn(scheduler);
    }

    @Override
    public FaceDetectionResult detectFaces(byte[] imageData, DetectionConfiguration config) {
        return identificationEngine.detectFaces(imageData, config);
    }

    @Override
    public FaceDetectionResult detectFaces(File imageFile, DetectionConfiguration config) {
        return identificationEngine.detectFaces(imageFile, config);
    }

    @Override
    public FeatureResult extractFaceFeature(byte[] imageData, DetectionConfiguration config) {
        return identificationEngine.extractFaceFeature(imageData, config);
    }

    @Override
    public FeatureResult extractFaceFeature(File imageFile, DetectionConfiguration config) {
        return identificationEngine.extractFaceFeature(imageFile, config);
    }

    @Override
    public Mono<FaceDetectionResult> detectFacesMono(byte[] imageData, DetectionConfiguration config) {
        return Mono.fromCallable(() -> identificationEngine.detectFaces(imageData, config))
                .subscribeOn(scheduler);
    }

    @Override
    public Mono<FaceDetectionResult> detectFacesMono(File imageFile, DetectionConfiguration config) {
        return Mono.fromCallable(() -> identificationEngine.detectFaces(imageFile, config))
                .subscribeOn(scheduler);
    }

    @Override
    public Mono<FeatureResult> extractFaceFeatureMono(byte[] imageData, DetectionConfiguration config) {
        return Mono.fromCallable(() -> identificationEngine.extractFaceFeature(imageData, config))
                .subscribeOn(scheduler);
    }

    @Override
    public Mono<FeatureResult> extractFaceFeatureMono(File imageFile, DetectionConfiguration config) {
        return Mono.fromCallable(() -> identificationEngine.extractFaceFeature(imageFile, config))
                .subscribeOn(scheduler);
    }

    @Override
    public AiClientConfig getConfig() {
        return config;
    }
}

