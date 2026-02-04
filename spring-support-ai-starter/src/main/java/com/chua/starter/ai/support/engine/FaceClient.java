package com.chua.starter.ai.support.engine;

import com.chua.starter.ai.support.model.FaceDetectionResult;
import com.chua.starter.ai.support.model.FeatureResult;
import reactor.core.publisher.Mono;

import java.io.File;

/**
 * 人脸识别客户端接口
 * <p>
 * 提供人脸检测、特征提取和比对功能
 *
 * @author CH
 * @since 2024-01-01
 */
public interface FaceClient {

    /**
     * 检测人脸
     *
     * @param imageData 图片字节数据
     * @return 人脸检测结果
     */
    FaceDetectionResult detect(byte[] imageData);

    /**
     * 检测人脸
     *
     * @param imageFile 图片文件
     * @return 人脸检测结果
     */
    FaceDetectionResult detect(File imageFile);

    /**
     * 检测人脸（带配置）
     *
     * @param imageData 图片字节数据
     * @param config    检测配置
     * @return 人脸检测结果
     */
    default FaceDetectionResult detect(byte[] imageData, DetectionConfiguration config) {
        return detect(imageData);
    }

    /**
     * 检测人脸（带配置）
     *
     * @param imageFile 图片文件
     * @param config    检测配置
     * @return 人脸检测结果
     */
    default FaceDetectionResult detect(File imageFile, DetectionConfiguration config) {
        return detect(imageFile);
    }

    /**
     * 提取人脸特征
     *
     * @param imageData 图片字节数据
     * @return 人脸特征结果
     */
    FeatureResult feature(byte[] imageData);

    /**
     * 提取人脸特征
     *
     * @param imageFile 图片文件
     * @return 人脸特征结果
     */
    FeatureResult feature(File imageFile);

    /**
     * 提取人脸特征（带配置）
     *
     * @param imageData 图片字节数据
     * @param config    检测配置
     * @return 人脸特征结果
     */
    default FeatureResult feature(byte[] imageData, DetectionConfiguration config) {
        return feature(imageData);
    }

    /**
     * 提取人脸特征（带配置）
     *
     * @param imageFile 图片文件
     * @param config    检测配置
     * @return 人脸特征结果
     */
    default FeatureResult feature(File imageFile, DetectionConfiguration config) {
        return feature(imageFile);
    }

    /**
     * 响应式检测人脸
     *
     * @param imageData 图片字节数据
     * @return 人脸检测结果Mono
     */
    Mono<FaceDetectionResult> detectMono(byte[] imageData);

    /**
     * 响应式检测人脸
     *
     * @param imageFile 图片文件
     * @return 人脸检测结果Mono
     */
    Mono<FaceDetectionResult> detectMono(File imageFile);

    /**
     * 响应式检测人脸（带配置）
     *
     * @param imageData 图片字节数据
     * @param config    检测配置
     * @return 人脸检测结果Mono
     */
    default Mono<FaceDetectionResult> detectMono(byte[] imageData, DetectionConfiguration config) {
        return detectMono(imageData);
    }

    /**
     * 响应式检测人脸（带配置）
     *
     * @param imageFile 图片文件
     * @param config    检测配置
     * @return 人脸检测结果Mono
     */
    default Mono<FaceDetectionResult> detectMono(File imageFile, DetectionConfiguration config) {
        return detectMono(imageFile);
    }

    /**
     * 响应式提取人脸特征
     *
     * @param imageData 图片字节数据
     * @return 人脸特征结果Mono
     */
    Mono<FeatureResult> featureMono(byte[] imageData);

    /**
     * 响应式提取人脸特征
     *
     * @param imageFile 图片文件
     * @return 人脸特征结果Mono
     */
    Mono<FeatureResult> featureMono(File imageFile);

    /**
     * 响应式提取人脸特征（带配置）
     *
     * @param imageData 图片字节数据
     * @param config    检测配置
     * @return 人脸特征结果Mono
     */
    default Mono<FeatureResult> featureMono(byte[] imageData, DetectionConfiguration config) {
        return featureMono(imageData);
    }

    /**
     * 响应式提取人脸特征（带配置）
     *
     * @param imageFile 图片文件
     * @param config    检测配置
     * @return 人脸特征结果Mono
     */
    default Mono<FeatureResult> featureMono(File imageFile, DetectionConfiguration config) {
        return featureMono(imageFile);
    }

    /**
     * 比对两张人脸是否相同
     *
     * @param image1 第一张图片字节数据
     * @param image2 第二张图片字节数据
     * @return 相似度（0-1之间，越接近1越相似）
     */
    default float compare(byte[] image1, byte[] image2) {
        var feature1 = feature(image1);
        var feature2 = feature(image2);

        if (!feature1.isSuccess() || !feature2.isSuccess()) {
            return 0.0f;
        }

        return feature1.cosineSimilarity(feature2);
    }

    /**
     * 比对两张人脸是否相同
     *
     * @param imageFile1 第一张图片文件
     * @param imageFile2 第二张图片文件
     * @return 相似度（0-1之间，越接近1越相似）
     */
    default float compare(File imageFile1, File imageFile2) {
        var feature1 = feature(imageFile1);
        var feature2 = feature(imageFile2);

        if (!feature1.isSuccess() || !feature2.isSuccess()) {
            return 0.0f;
        }

        return feature1.cosineSimilarity(feature2);
    }

    /**
     * 响应式比对两张人脸是否相同
     *
     * @param image1 第一张图片字节数据
     * @param image2 第二张图片字节数据
     * @return 相似度Mono（0-1之间，越接近1越相似）
     */
    default Mono<Float> compareMono(byte[] image1, byte[] image2) {
        return featureMono(image1)
                .zipWith(featureMono(image2))
                .map(tuple -> {
                    var feature1 = tuple.getT1();
                    var feature2 = tuple.getT2();
                    if (!feature1.isSuccess() || !feature2.isSuccess()) {
                        return 0.0f;
                    }
                    return feature1.cosineSimilarity(feature2);
                });
    }

    /**
     * 响应式比对两张人脸是否相同
     *
     * @param imageFile1 第一张图片文件
     * @param imageFile2 第二张图片文件
     * @return 相似度Mono（0-1之间，越接近1越相似）
     */
    default Mono<Float> compareMono(File imageFile1, File imageFile2) {
        return featureMono(imageFile1)
                .zipWith(featureMono(imageFile2))
                .map(tuple -> {
                    var feature1 = tuple.getT1();
                    var feature2 = tuple.getT2();
                    if (!feature1.isSuccess() || !feature2.isSuccess()) {
                        return 0.0f;
                    }
                    return feature1.cosineSimilarity(feature2);
                });
    }
}
