package com.chua.starter.ai.support.engine;

import com.chua.starter.ai.support.model.FaceDetectionResult;
import com.chua.starter.ai.support.model.FeatureResult;

import java.io.File;

/**
 * 人脸识别引擎接口
 * <p>
 * 定义人脸检测和特征提取的核心功能
 *
 * @author CH
 * @since 2024-01-01
 */
public interface FaceEngine {

    /**
     * 检测人脸
     *
     * @param imageData 图片字节数据
     * @return 人脸检测结果
     */
    FaceDetectionResult detectFaces(byte[] imageData);

    /**
     * 检测人脸
     *
     * @param imageFile 图片文件
     * @return 人脸检测结果
     */
    FaceDetectionResult detectFaces(File imageFile);

    /**
     * 检测人脸（带配置）
     *
     * @param imageData 图片字节数据
     * @param config    检测配置
     * @return 人脸检测结果
     */
    default FaceDetectionResult detectFaces(byte[] imageData, DetectionConfiguration config) {
        return detectFaces(imageData);
    }

    /**
     * 检测人脸（带配置）
     *
     * @param imageFile 图片文件
     * @param config    检测配置
     * @return 人脸检测结果
     */
    default FaceDetectionResult detectFaces(File imageFile, DetectionConfiguration config) {
        return detectFaces(imageFile);
    }

    /**
     * 提取人脸特征
     *
     * @param imageData 图片字节数据
     * @return 人脸特征结果
     */
    FeatureResult extractFaceFeature(byte[] imageData);

    /**
     * 提取人脸特征
     *
     * @param imageFile 图片文件
     * @return 人脸特征结果
     */
    FeatureResult extractFaceFeature(File imageFile);

    /**
     * 提取人脸特征（带配置）
     *
     * @param imageData 图片字节数据
     * @param config    检测配置
     * @return 人脸特征结果
     */
    default FeatureResult extractFaceFeature(byte[] imageData, DetectionConfiguration config) {
        return extractFaceFeature(imageData);
    }

    /**
     * 提取人脸特征（带配置）
     *
     * @param imageFile 图片文件
     * @param config    检测配置
     * @return 人脸特征结果
     */
    default FeatureResult extractFaceFeature(File imageFile, DetectionConfiguration config) {
        return extractFaceFeature(imageFile);
    }
}

