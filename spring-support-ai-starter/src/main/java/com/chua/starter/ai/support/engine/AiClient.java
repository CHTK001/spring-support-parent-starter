package com.chua.starter.ai.support.engine;

import com.chua.starter.ai.support.model.FaceDetectionResult;
import com.chua.starter.ai.support.model.FeatureResult;
import reactor.core.publisher.Mono;

import java.io.File;

/**
 * AI身份识别客户端接口
 * <p>
 * 提供人脸检测、特征提取等功能的简化操作接口
 * <p>
 * 支持同步、响应式两种调用方式，以及链式调用
 *
 * @author CH
 * @since 2024-01-01
 */
public interface AiClient {

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
     * 响应式检测人脸
     *
     * @param imageData 图片字节数据
     * @return 人脸检测结果Mono
     */
    Mono<FaceDetectionResult> detectFacesMono(byte[] imageData);

    /**
     * 响应式检测人脸
     *
     * @param imageFile 图片文件
     * @return 人脸检测结果Mono
     */
    Mono<FaceDetectionResult> detectFacesMono(File imageFile);

    /**
     * 响应式提取人脸特征
     *
     * @param imageData 图片字节数据
     * @return 人脸特征结果Mono
     */
    Mono<FeatureResult> extractFaceFeatureMono(byte[] imageData);

    /**
     * 响应式提取人脸特征
     *
     * @param imageFile 图片文件
     * @return 人脸特征结果Mono
     */
    Mono<FeatureResult> extractFaceFeatureMono(File imageFile);

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

    /**
     * 响应式检测人脸（带配置）
     *
     * @param imageData 图片字节数据
     * @param config    检测配置
     * @return 人脸检测结果Mono
     */
    default Mono<FaceDetectionResult> detectFacesMono(byte[] imageData, DetectionConfiguration config) {
        return detectFacesMono(imageData);
    }

    /**
     * 响应式检测人脸（带配置）
     *
     * @param imageFile 图片文件
     * @param config    检测配置
     * @return 人脸检测结果Mono
     */
    default Mono<FaceDetectionResult> detectFacesMono(File imageFile, DetectionConfiguration config) {
        return detectFacesMono(imageFile);
    }

    /**
     * 响应式提取人脸特征（带配置）
     *
     * @param imageData 图片字节数据
     * @param config    检测配置
     * @return 人脸特征结果Mono
     */
    default Mono<FeatureResult> extractFaceFeatureMono(byte[] imageData, DetectionConfiguration config) {
        return extractFaceFeatureMono(imageData);
    }

    /**
     * 响应式提取人脸特征（带配置）
     *
     * @param imageFile 图片文件
     * @param config    检测配置
     * @return 人脸特征结果Mono
     */
    default Mono<FeatureResult> extractFaceFeatureMono(File imageFile, DetectionConfiguration config) {
        return extractFaceFeatureMono(imageFile);
    }

    /**
     * 比对两张人脸是否相同
     *
     * @param image1 第一张图片字节数据
     * @param image2 第二张图片字节数据
     * @return 相似度（0-1之间，越接近1越相似）
     */
    default float compareFaces(byte[] image1, byte[] image2) {
        FeatureResult feature1 = extractFaceFeature(image1);
        FeatureResult feature2 = extractFaceFeature(image2);

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
    default float compareFaces(File imageFile1, File imageFile2) {
        FeatureResult feature1 = extractFaceFeature(imageFile1);
        FeatureResult feature2 = extractFaceFeature(imageFile2);

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
    default Mono<Float> compareFacesMono(byte[] image1, byte[] image2) {
        return extractFaceFeatureMono(image1)
                .zipWith(extractFaceFeatureMono(image2))
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
    default Mono<Float> compareFacesMono(File imageFile1, File imageFile2) {
        return extractFaceFeatureMono(imageFile1)
                .zipWith(extractFaceFeatureMono(imageFile2))
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
     * 获取客户端配置信息
     *
     * @return 配置信息
     */
    AiClientConfig getConfig();

    /**
     * 获取当前使用的厂商标识
     *
     * @param module 模块名称（faceDetection, ocr, image, text）
     * @return 厂商标识
     */
    default String getProvider(String module) {
        var config = getConfig();
        if (config == null) {
            return null;
        }
        return switch (module) {
            case "faceDetection" -> config.getFaceDetection() != null ? config.getFaceDetection().getProvider() : null;
            case "ocr" -> config.getOcr() != null ? config.getOcr().getProvider() : null;
            case "image" -> config.getImage() != null ? config.getImage().getProvider() : null;
            case "text" -> config.getText() != null ? config.getText().getProvider() : null;
            default -> null;
        };
    }

    /**
     * 检查模块是否启用
     *
     * @param module 模块名称（faceDetection, ocr, image, text）
     * @return 是否启用
     */
    default boolean isModuleEnabled(String module) {
        var config = getConfig();
        if (config == null || !config.isEnabled()) {
            return false;
        }
        return switch (module) {
            case "faceDetection" -> config.getFaceDetection() != null && config.getFaceDetection().isEnabled();
            case "ocr" -> config.getOcr() != null && config.getOcr().isEnabled();
            case "image" -> config.getImage() != null && config.getImage().isEnabled();
            case "text" -> config.getText() != null && config.getText().isEnabled();
            default -> false;
        };
    }

    /**
     * 获取模型路径
     *
     * @return 模型路径
     */
    default String getModelPath() {
        var config = getConfig();
        return config != null ? config.getModelPath() : null;
    }

    /**
     * 创建链式调用构建器
     *
     * @return 链式调用构建器
     */
    default ChainBuilder chain() {
        return new ChainBuilder(this);
    }

    /**
     * 链式调用构建器
     * <p>
     * 支持延迟执行，最后调用execute()或executeMono()才真正执行
     */
    class ChainBuilder {

        private final AiClient client;
        private Operation operation;
        private Object imageData;
        private File imageFile;
        private Object imageData2;
        private File imageFile2;
        private DetectionConfiguration config;

        ChainBuilder(AiClient client) {
            this.client = client;
        }

        /**
         * 链式调用：检测人脸
         *
         * @param imageData 图片字节数据
         * @return 构建器
         */
        public ChainBuilder detectFaces(byte[] imageData) {
            this.operation = Operation.DETECT_FACES;
            this.imageData = imageData;
            this.imageFile = null;
            return this;
        }

        /**
         * 链式调用：检测人脸
         *
         * @param imageFile 图片文件
         * @return 构建器
         */
        public ChainBuilder detectFaces(File imageFile) {
            this.operation = Operation.DETECT_FACES;
            this.imageFile = imageFile;
            this.imageData = null;
            return this;
        }

        /**
         * 链式调用：提取人脸特征
         *
         * @param imageData 图片字节数据
         * @return 构建器
         */
        public ChainBuilder extractFaceFeature(byte[] imageData) {
            this.operation = Operation.EXTRACT_FACE_FEATURE;
            this.imageData = imageData;
            this.imageFile = null;
            return this;
        }

        /**
         * 链式调用：提取人脸特征
         *
         * @param imageFile 图片文件
         * @return 构建器
         */
        public ChainBuilder extractFaceFeature(File imageFile) {
            this.operation = Operation.EXTRACT_FACE_FEATURE;
            this.imageFile = imageFile;
            this.imageData = null;
            return this;
        }

        /**
         * 链式调用：比对两张人脸
         *
         * @param image1 第一张图片字节数据
         * @param image2 第二张图片字节数据
         * @return 构建器
         */
        public ChainBuilder compareFaces(byte[] image1, byte[] image2) {
            this.operation = Operation.COMPARE_FACES;
            this.imageData = image1;
            this.imageData2 = image2;
            this.imageFile = null;
            this.imageFile2 = null;
            return this;
        }

        /**
         * 链式调用：比对两张人脸
         *
         * @param imageFile1 第一张图片文件
         * @param imageFile2 第二张图片文件
         * @return 构建器
         */
        public ChainBuilder compareFaces(File imageFile1, File imageFile2) {
            this.operation = Operation.COMPARE_FACES;
            this.imageFile = imageFile1;
            this.imageFile2 = imageFile2;
            this.imageData = null;
            this.imageData2 = null;
            return this;
        }

        /**
         * 设置检测配置
         *
         * @param config 检测配置
         * @return 构建器
         */
        public ChainBuilder withConfig(DetectionConfiguration config) {
            this.config = config;
            return this;
        }

        /**
         * 执行链式调用（同步）
         *
         * @param <T> 返回类型
         * @return 执行结果
         */
        @SuppressWarnings("unchecked")
        public <T> T execute() {
            if (operation == null) {
                throw new IllegalStateException("未设置操作类型");
            }

            var finalConfig = config != null ? config : DetectionConfiguration.defaultConfig();

            return switch (operation) {
                case DETECT_FACES -> {
                    if (imageFile != null) {
                        yield (T) client.detectFaces(imageFile, finalConfig);
                    } else if (imageData != null) {
                        yield (T) client.detectFaces((byte[]) imageData, finalConfig);
                    } else {
                        throw new IllegalStateException("图片数据或文件不能为空");
                    }
                }
                case EXTRACT_FACE_FEATURE -> {
                    if (imageFile != null) {
                        yield (T) client.extractFaceFeature(imageFile, finalConfig);
                    } else if (imageData != null) {
                        yield (T) client.extractFaceFeature((byte[]) imageData, finalConfig);
                    } else {
                        throw new IllegalStateException("图片数据或文件不能为空");
                    }
                }
                case COMPARE_FACES -> {
                    if (imageFile != null && imageFile2 != null) {
                        yield (T) Float.valueOf(client.compareFaces(imageFile, imageFile2));
                    } else if (imageData != null && imageData2 != null) {
                        yield (T) Float.valueOf(client.compareFaces((byte[]) imageData, (byte[]) imageData2));
                    } else {
                        throw new IllegalStateException("图片数据或文件不能为空");
                    }
                }
            };
        }

        /**
         * 执行链式调用（响应式）
         *
         * @param <T> 返回类型
         * @return 执行结果Mono
         */
        @SuppressWarnings("unchecked")
        public <T> Mono<T> executeMono() {
            if (operation == null) {
                return Mono.error(new IllegalStateException("未设置操作类型"));
            }

            var finalConfig = config != null ? config : DetectionConfiguration.defaultConfig();

            return switch (operation) {
                case DETECT_FACES -> {
                    if (imageFile != null) {
                        yield (Mono<T>) client.detectFacesMono(imageFile, finalConfig);
                    } else if (imageData != null) {
                        yield (Mono<T>) client.detectFacesMono((byte[]) imageData, finalConfig);
                    } else {
                        yield Mono.error(new IllegalStateException("图片数据或文件不能为空"));
                    }
                }
                case EXTRACT_FACE_FEATURE -> {
                    if (imageFile != null) {
                        yield (Mono<T>) client.extractFaceFeatureMono(imageFile, finalConfig);
                    } else if (imageData != null) {
                        yield (Mono<T>) client.extractFaceFeatureMono((byte[]) imageData, finalConfig);
                    } else {
                        yield Mono.error(new IllegalStateException("图片数据或文件不能为空"));
                    }
                }
                case COMPARE_FACES -> {
                    if (imageFile != null && imageFile2 != null) {
                        yield (Mono<T>) client.compareFacesMono(imageFile, imageFile2);
                    } else if (imageData != null && imageData2 != null) {
                        yield (Mono<T>) client.compareFacesMono((byte[]) imageData, (byte[]) imageData2);
                    } else {
                        yield Mono.error(new IllegalStateException("图片数据或文件不能为空"));
                    }
                }
            };
        }

        /**
         * 操作类型枚举
         */
        private enum Operation {
            DETECT_FACES,
            EXTRACT_FACE_FEATURE,
            COMPARE_FACES
        }
    }

    /**
     * AI客户端构建器
     */
    class Builder {

        private IdentificationEngine identificationEngine;
        private AiClientConfig config;

        /**
         * 设置身份识别引擎实现
         *
         * @param identificationEngine 身份识别引擎
         * @return 构建器
         */
        public Builder engine(IdentificationEngine identificationEngine) {
            this.identificationEngine = identificationEngine;
            return this;
        }

        /**
         * 设置配置信息
         *
         * @param config 配置信息
         * @return 构建器
         */
        public Builder config(AiClientConfig config) {
            this.config = config;
            return this;
        }

        /**
         * 构建AI客户端
         *
         * @return AI客户端
         */
        public AiClient build() {
            if (identificationEngine == null) {
                throw new IllegalStateException("IdentificationEngine实现不能为空");
            }

            return new DefaultAiClient(identificationEngine, config);
        }
    }

    /**
     * 创建构建器
     *
     * @return 构建器
     */
    static Builder builder() {
        return new Builder();
    }
}

