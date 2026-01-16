package com.chua.starter.ai.support.engine;

import com.chua.starter.ai.support.model.FaceDetectionResult;
import com.chua.starter.ai.support.model.FeatureResult;
import reactor.core.publisher.Mono;

/**
 * 人脸识别客户端接口
 *
 * @author CH
 * @since 2024-01-01
 */
public interface FaceClient {

    /**
     * 检测人脸
     *
     * @param imagePath 图片文件路径
     * @return 人脸检测结果
     */
    FaceDetectionResult detect(String imagePath);

    /**
     * 检测人脸（带配置）
     *
     * @param imagePath 图片文件路径
     * @param config    检测配置
     * @return 人脸检测结果
     */
    default FaceDetectionResult detect(String imagePath, DetectionConfiguration config) {
        return detect(imagePath);
    }

    /**
     * 提取人脸特征
     *
     * @param imagePath 图片文件路径
     * @return 人脸特征结果
     */
    FeatureResult feature(String imagePath);

    /**
     * 提取人脸特征（带配置）
     *
     * @param imagePath 图片文件路径
     * @param config    检测配置
     * @return 人脸特征结果
     */
    default FeatureResult feature(String imagePath, DetectionConfiguration config) {
        return feature(imagePath);
    }

    /**
     * 响应式检测人脸
     *
     * @param imagePath 图片文件路径
     * @return 人脸检测结果Mono
     */
    Mono<FaceDetectionResult> detectMono(String imagePath);

    /**
     * 响应式检测人脸（带配置）
     *
     * @param imagePath 图片文件路径
     * @param config    检测配置
     * @return 人脸检测结果Mono
     */
    default Mono<FaceDetectionResult> detectMono(String imagePath, DetectionConfiguration config) {
        return detectMono(imagePath);
    }

    /**
     * 响应式提取人脸特征
     *
     * @param imagePath 图片文件路径
     * @return 人脸特征结果Mono
     */
    Mono<FeatureResult> featureMono(String imagePath);

    /**
     * 响应式提取人脸特征（带配置）
     *
     * @param imagePath 图片文件路径
     * @param config    检测配置
     * @return 人脸特征结果Mono
     */
    default Mono<FeatureResult> featureMono(String imagePath, DetectionConfiguration config) {
        return featureMono(imagePath);
    }

    /**
     * 比对两张人脸是否相同
     *
     * @param imagePath1 第一张图片文件路径
     * @param imagePath2 第二张图片文件路径
     * @return 相似度（0-1之间，越接近1越相似）
     */
    default float compare(String imagePath1, String imagePath2) {
        var feature1 = feature(imagePath1);
        var feature2 = feature(imagePath2);

        if (!feature1.isSuccess() || !feature2.isSuccess()) {
            return 0.0f;
        }

        return feature1.cosineSimilarity(feature2);
    }

    /**
     * 响应式比对两张人脸是否相同
     *
     * @param imagePath1 第一张图片文件路径
     * @param imagePath2 第二张图片文件路径
     * @return 相似度Mono（0-1之间，越接近1越相似）
     */
    default Mono<Float> compareMono(String imagePath1, String imagePath2) {
        return featureMono(imagePath1)
                .zipWith(featureMono(imagePath2))
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

