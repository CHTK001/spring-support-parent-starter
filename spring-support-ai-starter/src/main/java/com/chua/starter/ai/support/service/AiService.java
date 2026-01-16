package com.chua.starter.ai.support.service;

import com.chua.starter.ai.support.model.*;

import java.io.File;
import java.io.InputStream;

/**
 * AI服务接口
 * <p>
 * 提供各种AI能力的统一接口，包括人脸检测、特征提取、OCR、版面分析等
 *
 * @author CH
 * @since 2024-01-01
 */
public interface AiService {

    // ==================== 人脸相关 ====================

    /**
     * 人脸检测
     *
     * @param imageData 图片字节数据
     * @return 人脸检测结果
     */
    FaceDetectionResult detectFaces(byte[] imageData);

    /**
     * 人脸检测
     *
     * @param imageFile 图片文件
     * @return 人脸检测结果
     */
    FaceDetectionResult detectFaces(File imageFile);

    /**
     * 人脸检测
     *
     * @param inputStream 图片输入流
     * @return 人脸检测结果
     */
    FaceDetectionResult detectFaces(InputStream inputStream);

    /**
     * 提取人脸特征值
     *
     * @param imageData 图片字节数据
     * @return 人脸特征值结果
     */
    FeatureResult extractFaceFeature(byte[] imageData);

    /**
     * 提取人脸特征值
     *
     * @param imageFile 图片文件
     * @return 人脸特征值结果
     */
    FeatureResult extractFaceFeature(File imageFile);

    /**
     * 性别年龄检测
     *
     * @param imageData 图片字节数据
     * @return 性别年龄检测结果
     */
    GenderAgeResult detectGenderAge(byte[] imageData);

    /**
     * 性别年龄检测
     *
     * @param imageFile 图片文件
     * @return 性别年龄检测结果
     */
    GenderAgeResult detectGenderAge(File imageFile);

    // ==================== 图片相关 ====================

    /**
     * 图片物体检测
     *
     * @param imageData 图片字节数据
     * @return 图片检测结果
     */
    ImageDetectionResult detectImage(byte[] imageData);

    /**
     * 图片物体检测
     *
     * @param imageFile 图片文件
     * @return 图片检测结果
     */
    ImageDetectionResult detectImage(File imageFile);

    /**
     * 提取图片特征值
     *
     * @param imageData 图片字节数据
     * @return 图片特征值结果
     */
    FeatureResult extractImageFeature(byte[] imageData);

    /**
     * 提取图片特征值
     *
     * @param imageFile 图片文件
     * @return 图片特征值结果
     */
    FeatureResult extractImageFeature(File imageFile);

    // ==================== 文本相关 ====================

    /**
     * 提取文本特征值
     *
     * @param text 文本内容
     * @return 文本特征值结果
     */
    FeatureResult extractTextFeature(String text);

    // ==================== OCR相关 ====================

    /**
     * OCR文字识别
     *
     * @param imageData 图片字节数据
     * @return OCR识别结果
     */
    OcrResult ocr(byte[] imageData);

    /**
     * OCR文字识别
     *
     * @param imageFile 图片文件
     * @return OCR识别结果
     */
    OcrResult ocr(File imageFile);

    /**
     * OCR文字识别
     *
     * @param inputStream 图片输入流
     * @return OCR识别结果
     */
    OcrResult ocr(InputStream inputStream);

    // ==================== 版面分析 ====================

    /**
     * 版面分析
     *
     * @param imageData 图片字节数据
     * @return 版面分析结果
     */
    LayoutAnalysisResult analyzeLayout(byte[] imageData);

    /**
     * 版面分析
     *
     * @param imageFile 图片文件
     * @return 版面分析结果
     */
    LayoutAnalysisResult analyzeLayout(File imageFile);
}
