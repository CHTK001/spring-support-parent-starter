package com.chua.starter.ai.support.service;

import com.chua.starter.ai.support.model.*;

import java.io.File;
import java.io.InputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

/**
 * 异步AI服务接口
 * <p>
 * 提供基于CompletableFuture的异步AI能力调用
 *
 * @author CH
 * @since 2024-01-01
 */
public interface AsyncAiService {

    /**
     * 获取默认执行器
     *
     * @return 默认执行器
     */
    default Executor getDefaultExecutor() {
        return ForkJoinPool.commonPool();
    }

    // ==================== 人脸相关 ====================

    /**
     * 异步人脸检测
     *
     * @param imageData 图片字节数据
     * @return 人脸检测结果Future
     */
    CompletableFuture<FaceDetectionResult> detectFacesAsync(byte[] imageData);

    /**
     * 异步人脸检测
     *
     * @param imageFile 图片文件
     * @return 人脸检测结果Future
     */
    CompletableFuture<FaceDetectionResult> detectFacesAsync(File imageFile);

    /**
     * 异步人脸检测
     *
     * @param inputStream 图片输入流
     * @return 人脸检测结果Future
     */
    CompletableFuture<FaceDetectionResult> detectFacesAsync(InputStream inputStream);

    /**
     * 异步提取人脸特征值
     *
     * @param imageData 图片字节数据
     * @return 人脸特征值结果Future
     */
    CompletableFuture<FeatureResult> extractFaceFeatureAsync(byte[] imageData);

    /**
     * 异步提取人脸特征值
     *
     * @param imageFile 图片文件
     * @return 人脸特征值结果Future
     */
    CompletableFuture<FeatureResult> extractFaceFeatureAsync(File imageFile);

    /**
     * 异步性别年龄检测
     *
     * @param imageData 图片字节数据
     * @return 性别年龄检测结果Future
     */
    CompletableFuture<GenderAgeResult> detectGenderAgeAsync(byte[] imageData);

    /**
     * 异步性别年龄检测
     *
     * @param imageFile 图片文件
     * @return 性别年龄检测结果Future
     */
    CompletableFuture<GenderAgeResult> detectGenderAgeAsync(File imageFile);

    // ==================== 图片相关 ====================

    /**
     * 异步图片物体检测
     *
     * @param imageData 图片字节数据
     * @return 图片检测结果Future
     */
    CompletableFuture<ImageDetectionResult> detectImageAsync(byte[] imageData);

    /**
     * 异步图片物体检测
     *
     * @param imageFile 图片文件
     * @return 图片检测结果Future
     */
    CompletableFuture<ImageDetectionResult> detectImageAsync(File imageFile);

    /**
     * 异步提取图片特征值
     *
     * @param imageData 图片字节数据
     * @return 图片特征值结果Future
     */
    CompletableFuture<FeatureResult> extractImageFeatureAsync(byte[] imageData);

    /**
     * 异步提取图片特征值
     *
     * @param imageFile 图片文件
     * @return 图片特征值结果Future
     */
    CompletableFuture<FeatureResult> extractImageFeatureAsync(File imageFile);

    // ==================== 文本相关 ====================

    /**
     * 异步提取文本特征值
     *
     * @param text 文本内容
     * @return 文本特征值结果Future
     */
    CompletableFuture<FeatureResult> extractTextFeatureAsync(String text);

    // ==================== OCR相关 ====================

    /**
     * 异步OCR文字识别
     *
     * @param imageData 图片字节数据
     * @return OCR识别结果Future
     */
    CompletableFuture<OcrResult> ocrAsync(byte[] imageData);

    /**
     * 异步OCR文字识别
     *
     * @param imageFile 图片文件
     * @return OCR识别结果Future
     */
    CompletableFuture<OcrResult> ocrAsync(File imageFile);

    /**
     * 异步OCR文字识别
     *
     * @param inputStream 图片输入流
     * @return OCR识别结果Future
     */
    CompletableFuture<OcrResult> ocrAsync(InputStream inputStream);

    // ==================== 版面分析 ====================

    /**
     * 异步版面分析
     *
     * @param imageData 图片字节数据
     * @return 版面分析结果Future
     */
    CompletableFuture<LayoutAnalysisResult> analyzeLayoutAsync(byte[] imageData);

    /**
     * 异步版面分析
     *
     * @param imageFile 图片文件
     * @return 版面分析结果Future
     */
    CompletableFuture<LayoutAnalysisResult> analyzeLayoutAsync(File imageFile);
}
