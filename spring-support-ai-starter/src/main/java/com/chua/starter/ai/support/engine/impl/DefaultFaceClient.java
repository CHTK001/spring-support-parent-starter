package com.chua.starter.ai.support.engine.impl;

import com.chua.starter.ai.support.engine.DetectionConfiguration;
import com.chua.starter.ai.support.engine.FaceClient;
import com.chua.starter.ai.support.engine.FaceEngine;
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
public class DefaultFaceClient implements FaceClient {

    private final FaceEngine faceEngine;
    private final Scheduler scheduler;

    @Override
    public FaceDetectionResult detect(byte[] imageData) {
        return faceEngine.detectFaces(imageData);
    }

    @Override
    public FaceDetectionResult detect(File imageFile) {
        return faceEngine.detectFaces(imageFile);
    }

    @Override
    public FaceDetectionResult detect(byte[] imageData, DetectionConfiguration config) {
        return faceEngine.detectFaces(imageData, config);
    }

    @Override
    public FaceDetectionResult detect(File imageFile, DetectionConfiguration config) {
        return faceEngine.detectFaces(imageFile, config);
    }

    @Override
    public FeatureResult feature(byte[] imageData) {
        return faceEngine.extractFaceFeature(imageData);
    }

    @Override
    public FeatureResult feature(File imageFile) {
        return faceEngine.extractFaceFeature(imageFile);
    }

    @Override
    public FeatureResult feature(byte[] imageData, DetectionConfiguration config) {
        return faceEngine.extractFaceFeature(imageData, config);
    }

    @Override
    public FeatureResult feature(File imageFile, DetectionConfiguration config) {
        return faceEngine.extractFaceFeature(imageFile, config);
    }

    @Override
    public Mono<FaceDetectionResult> detectMono(byte[] imageData) {
        return Mono.fromCallable(() -> faceEngine.detectFaces(imageData))
                .subscribeOn(scheduler);
    }

    @Override
    public Mono<FaceDetectionResult> detectMono(File imageFile) {
        return Mono.fromCallable(() -> faceEngine.detectFaces(imageFile))
                .subscribeOn(scheduler);
    }

    @Override
    public Mono<FaceDetectionResult> detectMono(byte[] imageData, DetectionConfiguration config) {
        return Mono.fromCallable(() -> faceEngine.detectFaces(imageData, config))
                .subscribeOn(scheduler);
    }

    @Override
    public Mono<FaceDetectionResult> detectMono(File imageFile, DetectionConfiguration config) {
        return Mono.fromCallable(() -> faceEngine.detectFaces(imageFile, config))
                .subscribeOn(scheduler);
    }

    @Override
    public Mono<FeatureResult> featureMono(byte[] imageData) {
        return Mono.fromCallable(() -> faceEngine.extractFaceFeature(imageData))
                .subscribeOn(scheduler);
    }

    @Override
    public Mono<FeatureResult> featureMono(File imageFile) {
        return Mono.fromCallable(() -> faceEngine.extractFaceFeature(imageFile))
                .subscribeOn(scheduler);
    }

    @Override
    public Mono<FeatureResult> featureMono(byte[] imageData, DetectionConfiguration config) {
        return Mono.fromCallable(() -> faceEngine.extractFaceFeature(imageData, config))
                .subscribeOn(scheduler);
    }

    @Override
    public Mono<FeatureResult> featureMono(File imageFile, DetectionConfiguration config) {
        return Mono.fromCallable(() -> faceEngine.extractFaceFeature(imageFile, config))
                .subscribeOn(scheduler);
    }
}
