package com.chua.starter.ai.support.service;

import com.chua.starter.ai.support.model.*;
import reactor.core.publisher.Mono;

import java.io.File;
import java.io.InputStream;

/**
 * Reactor响应式AI服务接口
 * <p>
 * 提供基于Reactor的响应式AI能力调用
 *
 * @author CH
 * @since 2024-01-01
 */
public interface ReactiveAiService {

    // ==================== 人脸相关 ====================

    /**
     * 响应式人脸检测
     *
     * @param imageData 图片字节数据
     * @return 人脸检测结果Mono
     */
    Mono<FaceDetectionResult> detectFacesMono(byte[] imageData);

    /**
     * 响应式人脸检测
     *
     * @param imageFile 图片文件
     * @return 人脸检测结果Mono
     */
    Mono<FaceDetectionResult> detectFacesMono(File imageFile);

    /**
     * 响应式人脸检测
     *
     * @param inputStream 图片输入流
     * @return 人脸检测结果Mono
     */
    Mono<FaceDetectionResult> detectFacesMono(InputStream inputStream);

    /**
     * 响应式提取人脸特征值
     *
     * @param imageData 图片字节数据
     * @return 人脸特征值结果Mono
     */
    Mono<FeatureResult> extractFaceFeatureMono(byte[] imageData);

    /**
     * 响应式提取人脸特征值
     *
     * @param imageFile 图片文件
     * @return 人脸特征值结果Mono
     */
    Mono<FeatureResult> extractFaceFeatureMono(File imageFile);

    /**
     * 响应式性别年龄检测
     *
     * @param imageData 图片字节数据
     * @return 性别年龄检测结果Mono
     */
    Mono<GenderAgeResult> detectGenderAgeMono(byte[] imageData);

    /**
     * 响应式性别年龄检测
     *
     * @param imageFile 图片文件
     * @return 性别年龄检测结果Mono
     */
    Mono<GenderAgeResult> detectGenderAgeMono(File imageFile);

    // ==================== 图片相关 ====================

    /**
     * 响应式图片物体检测
     *
     * @param imageData 图片字节数据
     * @return 图片检测结果Mono
     */
    Mono<ImageDetectionResult> detectImageMono(byte[] imageData);

    /**
     * 响应式图片物体检测
     *
     * @param imageFile 图片文件
     * @return 图片检测结果Mono
     */
    Mono<ImageDetectionResult> detectImageMono(File imageFile);

    /**
     * 响应式提取图片特征值
     *
     * @param imageData 图片字节数据
     * @return 图片特征值结果Mono
     */
    Mono<FeatureResult> extractImageFeatureMono(byte[] imageData);

    /**
     * 响应式提取图片特征值
     *
     * @param imageFile 图片文件
     * @return 图片特征值结果Mono
     */
    Mono<FeatureResult> extractImageFeatureMono(File imageFile);

    // ==================== 文本相关 ====================

    /**
     * 响应式提取文本特征值
     *
     * @param text 文本内容
     * @return 文本特征值结果Mono
     */
    Mono<FeatureResult> extractTextFeatureMono(String text);

    // ==================== OCR相关 ====================

    /**
     * 响应式OCR文字识别
     *
     * @param imageData 图片字节数据
     * @return OCR识别结果Mono
     */
    Mono<OcrResult> ocrMono(byte[] imageData);

    /**
     * 响应式OCR文字识别
     *
     * @param imageFile 图片文件
     * @return OCR识别结果Mono
     */
    Mono<OcrResult> ocrMono(File imageFile);

    /**
     * 响应式OCR文字识别
     *
     * @param inputStream 图片输入流
     * @return OCR识别结果Mono
     */
    Mono<OcrResult> ocrMono(InputStream inputStream);

    // ==================== 版面分析 ====================

    /**
     * 响应式版面分析
     *
     * @param imageData 图片字节数据
     * @return 版面分析结果Mono
     */
    Mono<LayoutAnalysisResult> analyzeLayoutMono(byte[] imageData);

    /**
     * 响应式版面分析
     *
     * @param imageFile 图片文件
     * @return 版面分析结果Mono
     */
    Mono<LayoutAnalysisResult> analyzeLayoutMono(File imageFile);
}
