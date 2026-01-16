package com.chua.starter.ai.support.engine.impl;

import com.chua.starter.ai.support.engine.DetectionConfiguration;
import com.chua.starter.ai.support.engine.FaceClient;
import com.chua.starter.ai.support.engine.IdentificationEngine;
import com.chua.starter.ai.support.model.FaceDetectionResult;
import com.chua.starter.ai.support.model.FeatureResult;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

import java.io.File;

/**
 * 默认人脸识别客户端实现
 *
 * @author CH
 * @since 2024-01-01
 */
@RequiredArgsConstructor
class DefaultFaceClient implements FaceClient {

    private final IdentificationEngine identificationEngine;
    private final Scheduler scheduler;

    @Override
    public FaceDetectionResult detect(byte[] imageData) {
        return identificationEngine.detectFaces(imageData);
    }

    @Override
    public FaceDetectionResult detect(File imageFile) {
        return identificationEngine.detectFaces(imageFile);
    }

    @Override
    public FaceDetectionResult detect(byte[] imageData, DetectionConfiguration config) {
        return identificationEngine.detectFaces(imageData, config);
    }

    @Override
    public FaceDetectionResult detect(File imageFile, DetectionConfiguration config) {
        return identificationEngine.detectFaces(imageFile, config);
    }

    @Override
    public FeatureResult feature(byte[] imageData) {
        return identificationEngine.extractFaceFeature(imageData);
    }

    @Override
    public FeatureResult feature(File imageFile) {
        return identificationEngine.extractFaceFeature(imageFile);
    }

    @Override
    public FeatureResult feature(byte[] imageData, DetectionConfiguration config) {
        return identificationEngine.extractFaceFeature(imageData, config);
    }

    @Override
    public FeatureResult feature(File imageFile, DetectionConfiguration config) {
        return identificationEngine.extractFaceFeature(imageFile, config);
    }

    @Override
    public Mono<FaceDetectionResult> detectMono(byte[] imageData) {
        return Mono.fromCallable(() -> identificationEngine.detectFaces(imageData))
                .subscribeOn(scheduler);
    }

    @Override
    public Mono<FaceDetectionResult> detectMono(File imageFile) {
        return Mono.fromCallable(() -> identificationEngine.detectFaces(imageFile))
                .subscribeOn(scheduler);
    }

    @Override
    public Mono<FaceDetectionResult> detectMono(byte[] imageData, DetectionConfiguration config) {
        return Mono.fromCallable(() -> identificationEngine.detectFaces(imageData, config))
                .subscribeOn(scheduler);
    }

    @Override
    public Mono<FaceDetectionResult> detectMono(File imageFile, DetectionConfiguration config) {
        return Mono.fromCallable(() -> identificationEngine.detectFaces(imageFile, config))
                .subscribeOn(scheduler);
    }

    @Override
    public Mono<FeatureResult> featureMono(byte[] imageData) {
        return Mono.fromCallable(() -> identificationEngine.extractFaceFeature(imageData))
                .subscribeOn(scheduler);
    }

    @Override
    public Mono<FeatureResult> featureMono(File imageFile) {
        return Mono.fromCallable(() -> identificationEngine.extractFaceFeature(imageFile))
                .subscribeOn(scheduler);
    }

    @Override
    public Mono<FeatureResult> featureMono(byte[] imageData, DetectionConfiguration config) {
        return Mono.fromCallable(() -> identificationEngine.extractFaceFeature(imageData, config))
                .subscribeOn(scheduler);
    }

    @Override
    public Mono<FeatureResult> featureMono(File imageFile, DetectionConfiguration config) {
        return Mono.fromCallable(() -> identificationEngine.extractFaceFeature(imageFile, config))
                .subscribeOn(scheduler);
    }
}

