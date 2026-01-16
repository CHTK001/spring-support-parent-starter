package com.chua.starter.ai.support.engine;

import com.chua.starter.ai.support.chat.AiChat;
import com.chua.starter.ai.support.model.FaceDetectionResult;
import com.chua.starter.ai.support.model.FeatureResult;

import java.io.File;

/**
 * 身份识别引擎接口
 * <p>
 * 基于AiChat实现的身份识别功能封装
 *
 * @author CH
 * @since 2024-01-01
 */
public interface IdentificationEngine {

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
     * @param imageFile 图片文件
     * @return 人脸特征结果
     */
    FeatureResult extractFaceFeature(File imageFile);

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

