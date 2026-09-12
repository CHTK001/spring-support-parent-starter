package com.chua.starter.ai.support.configuration.face;

import com.chua.common.support.utils.StringUtils;
import com.chua.common.support.vector.VectorCompareAlgorithm;
import com.chua.common.support.vector.VectorStorage;
import com.chua.common.support.vector.VectorStorageProvider;
import com.chua.deeplearning.support.face.FaceDetector;
import com.chua.deeplearning.support.face.FacePipeline;
import com.chua.deeplearning.support.face.FaceQualityAssessor;
import com.chua.deeplearning.support.feature.FeatureExtractor;
import com.chua.deeplearning.support.image.ImageClassifier;
import com.chua.deeplearning.support.image.ImageEnhancer;
import com.chua.deeplearning.support.liveness.LivenessDetector;
import com.chua.starter.ai.support.configuration.client.GlobalSettingReader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
* 人脸识别客户端管理器。
*
* <p>引入 AI 模块即提供人脸识别能力，配置读取自 {@code sys_ai_global_setting}
* 键值表（分组 {@code face}：检测模型、特征模型、向量库、算法、阈值、活体、质量、超分、修复、
* 属性、表情、关键点、换脸检测）。
* 配置通过全局配置面板修改后调用 {@link #rebuild()} 热重建运行实例，无需重启。</p>
*
* <p>模型采用懒加载：仅在首次调用对应能力时才创建 deeplearning 模型实例，
* 避免应用启动即加载 ONNX 模型导致启动阻塞或崩溃（与 OCR 提取器一致）。</p>
*
* @since 4.0.0.42
* @author CH
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FaceClientManager {

    /**
    * 全局设置键值分组
     */
    private static final String GROUP = "face";

    /**
    * 默认特征向量维度（R50/face-特征 输出，与 {@code dimension(512)} 对齐）
     */
    private static final int DEFAULT_FEATURE_DIMENSION = 512;

    /**
    * 全局设置读取工具
     */
    private final GlobalSettingReader settingReader;

    /**
    * Spring Bean工厂（预留动态注册 Bean）
     */
    private final DefaultListableBeanFactory beanFactory;

    /**
    * 当前生效的识别管线（懒构建，热更新时替换）
     */
    private volatile FacePipeline pipeline;

    /**
    * 当前生效的特征提取器（懒构建，热更新时替换）
     */
    private volatile FeatureExtractor featureExtractor;

    /**
    * 当前生效的人脸向量库（懒构建，热更新时替换）
     */
    private volatile VectorStorage vectorStorage;

    /**
    * 当前生效的活体检测器（懒构建，可为 空）
     */
    private volatile LivenessDetector liveness;

    /**
    * 当前生效的质量评估器（懒构建，可为 空）
     */
    private volatile FaceQualityAssessor qualityAssessor;

    /**
    * 当前生效的人脸检测器（单独能力）
     */
    private volatile FaceDetector detector;

    /**
    * 当前生效的动漫人脸检测器（单独能力）
     */
    private volatile FaceDetector animeDetector;

    /**
    * 当前生效的人脸超分器（单独能力）
     */
    private volatile ImageEnhancer superResolution;

    /**
    * 当前生效的人脸修复器（单独能力）
     */
    private volatile ImageEnhancer restorer;

    /**
    * 当前生效的人脸属性分类器（年龄/性别/种族）
     */
    private volatile ImageClassifier attributeClassifier;

    /**
    * 当前生效的人脸表情分类器
     */
    private volatile ImageClassifier emotionClassifier;

    /**
    * 当前生效的换脸检测分类器
     */
    private volatile ImageClassifier deepfakeClassifier;

    /**
    * 当前生效的人脸关键点提取器（单独能力）
     */
    private volatile FeatureExtractor landmarkExtractor;

    /**
    * 获取当前检测管线（懒加载）。
    *
    * @return 人脸检测管线
     */
    public FacePipeline currentPipeline() {
        FacePipeline current = pipeline;
        if (current == null) {
            synchronized (this) {
                if (pipeline == null) {
                    pipeline = buildPipeline();
                }
                current = pipeline;
            }
        }
        return current;
    }

    /**
    * 获取当前特征提取器（懒加载）。
    *
    * @return 特征提取器
     */
    public FeatureExtractor currentFeatureExtractor() {
        FeatureExtractor current = featureExtractor;
        if (current == null) {
            synchronized (this) {
                if (featureExtractor == null) {
                    featureExtractor = FeatureExtractor.create(settingReader.getStr(GROUP, "feature_model"));
                }
                current = featureExtractor;
            }
        }
        return current;
    }

    /**
    * 获取当前向量库（懒加载）。
    *
    * @return 人脸向量库
     */
    public VectorStorage currentVectorStorage() {
        VectorStorage current = vectorStorage;
        if (current == null) {
            synchronized (this) {
                if (vectorStorage == null) {
                    vectorStorage = buildVectorStorage();
                }
                current = vectorStorage;
            }
        }
        return current;
    }

    /**
    * 获取当前活体检测器（可为 空）。
    *
    * @return 活体检测器或 空
     */
    public LivenessDetector currentLiveness() {
        if (liveness == null) {
            synchronized (this) {
                if (liveness == null) {
                    liveness = buildLiveness();
                }
            }
        }
        return liveness;
    }

    /**
    * 获取当前质量评估器（可为 空）。
    *
    * @return 质量评估器或 空
     */
    public FaceQualityAssessor currentQualityAssessor() {
        if (qualityAssessor == null) {
            synchronized (this) {
                if (qualityAssessor == null) {
                    qualityAssessor = buildQualityAssessor();
                }
            }
        }
        return qualityAssessor;
    }

    /**
    * 获取当前人脸检测器（单独能力，懒加载）。
    *
    * @return 人脸检测器
     */
    public FaceDetector currentDetector() {
        FaceDetector current = detector;
        if (current == null) {
            synchronized (this) {
                if (detector == null) {
                    detector = buildDetector(settingReader.getStr(GROUP, "detector_model"));
                }
                current = detector;
            }
        }
        return current;
    }

    /**
    * 构建带阈值的 facedetector（置信度阈值 + NMS + 最小人脸尺寸）。
    * @param modelId 模型标识
    * @return 构建detector的结果
     */
    private FaceDetector buildDetector(String modelId) {
        FaceDetector d = FaceDetector.create(modelId);
        BigDecimal detTh = settingReader.getDecimalOrNull(GROUP, "detector_threshold");
        BigDecimal nmsTh = settingReader.getDecimalOrNull(GROUP, "nms_threshold");
        if (detTh != null) {
            d.threshold(detTh.floatValue());
        }
        if (nmsTh != null) {
            d.nms(nmsTh.floatValue());
        }
        int minSize = settingReader.getInt(GROUP, "min_face_size", 20);
        d.minFaceSize(minSize);
        return d;
    }

    /**
    * 获取当前动漫人脸检测器（懒加载）。
    *
    * @return 动漫人脸检测器
     */
    public FaceDetector currentAnimeDetector() {
        FaceDetector current = animeDetector;
        if (current == null) {
            synchronized (this) {
                if (animeDetector == null) {
                    animeDetector = buildDetector(settingReader.getStr(GROUP, "anime_detector_model"));
                }
                current = animeDetector;
            }
        }
        return current;
    }

    /**
    * 获取当前人脸超分器（懒加载）。
    *
    * @return 人脸超分器
     */
    public ImageEnhancer currentSuperResolution() {
        ImageEnhancer current = superResolution;
        if (current == null) {
            synchronized (this) {
                if (superResolution == null) {
                    String model = settingReader.getStr(GROUP, "super_resolution_model");
 // 未配置时回退默认（与 face全局setting提供者 默认一致）
                    if (model == null || model.isBlank()) {
                        model = "gfpgan-face-super-resolution";
                    }
                    superResolution = ImageEnhancer.create(model);
                }
                current = superResolution;
            }
        }
        return current;
    }

    /**
    * 获取当前人脸修复器（懒加载）。
    *
    * @return 人脸修复器
     */
    public ImageEnhancer currentRestorer() {
        ImageEnhancer current = restorer;
        if (current == null) {
            synchronized (this) {
                if (restorer == null) {
                    restorer = ImageEnhancer.create(settingReader.getStr(GROUP, "restore_model"));
                }
                current = restorer;
            }
        }
        return current;
    }

    /**
    * 获取当前属性分类器（懒加载）。
    *
    * @return 属性分类器
     */
    public ImageClassifier currentAttributeClassifier() {
        ImageClassifier current = attributeClassifier;
        if (current == null) {
            synchronized (this) {
                if (attributeClassifier == null) {
                    attributeClassifier = ImageClassifier.create(settingReader.getStr(GROUP, "attribute_model"));
                }
                current = attributeClassifier;
            }
        }
        return current;
    }

    /**
    * 获取当前表情分类器（懒加载）。
    *
    * @return 表情分类器
     */
    public ImageClassifier currentEmotionClassifier() {
        ImageClassifier current = emotionClassifier;
        if (current == null) {
            synchronized (this) {
                if (emotionClassifier == null) {
                    emotionClassifier = ImageClassifier.create(settingReader.getStr(GROUP, "emotion_model"));
                }
                current = emotionClassifier;
            }
        }
        return current;
    }

    /**
    * 获取当前换脸检测分类器（懒加载）。
    *
    * @return 换脸检测分类器
     */
    public ImageClassifier currentDeepfakeClassifier() {
        ImageClassifier current = deepfakeClassifier;
        if (current == null) {
            synchronized (this) {
                if (deepfakeClassifier == null) {
                    deepfakeClassifier = ImageClassifier.create(settingReader.getStr(GROUP, "deepfake_model"));
                }
                current = deepfakeClassifier;
            }
        }
        return current;
    }

    /**
    * 获取当前人脸关键点提取器（懒加载）。
    *
    * @return 关键点提取器
     */
    public FeatureExtractor currentLandmarkExtractor() {
        FeatureExtractor current = landmarkExtractor;
        if (current == null) {
            synchronized (this) {
                if (landmarkExtractor == null) {
                    landmarkExtractor = FeatureExtractor.create(settingReader.getStr(GROUP, "landmark_model"));
                }
                current = landmarkExtractor;
            }
        }
        return current;
    }

    /**
    * 全局配置变更监听：face 分组更新后热重建，实时生效。
    *
    * @param event 配置变更事件
     */
    @EventListener
    public void onFaceSettingsChanged(FaceSettingsChangedEvent event) {
        if (event == null) {
            return;
        }
        if (!GROUP.equals(event.getGroup())) {
            return;
        }
        log.info("[FaceClientManager] 收到 face 分组配置变更，开始热重建");
        /**
        * rebuild。
         */
        rebuild();
    }

    /**
    * 配置热更新：按最新配置重建全部人脸实例，替换引用并关闭旧实例。
     */
    public synchronized void rebuild() {
        VectorStorage oldVectorStorage = this.vectorStorage;
        VectorStorage nextVectorStorage = buildVectorStorage();
        this.vectorStorage = nextVectorStorage;
        FacePipeline nextPipeline = buildPipeline();
        LivenessDetector nextLiveness = buildLiveness();
        FaceQualityAssessor nextQuality = buildQualityAssessor();

        FacePipeline oldPipeline = pipeline;
        LivenessDetector oldLiveness = liveness;
        FaceQualityAssessor oldQuality = qualityAssessor;

        this.pipeline = nextPipeline;
        this.liveness = nextLiveness;
        this.qualityAssessor = nextQuality;

        // 依赖配置的组件全部重建
        this.featureExtractor = FeatureExtractor.create(settingReader.getStr(GROUP, "feature_model"));
        this.detector = buildDetector(settingReader.getStr(GROUP, "detector_model"));
        this.animeDetector = buildDetector(settingReader.getStr(GROUP, "anime_detector_model"));
        this.superResolution = ImageEnhancer.create(settingReader.getStr(GROUP, "super_resolution_model"));
        this.restorer = ImageEnhancer.create(settingReader.getStr(GROUP, "restore_model"));
        this.attributeClassifier = ImageClassifier.create(settingReader.getStr(GROUP, "attribute_model"));
        this.emotionClassifier = ImageClassifier.create(settingReader.getStr(GROUP, "emotion_model"));
        this.deepfakeClassifier = ImageClassifier.create(settingReader.getStr(GROUP, "deepfake_model"));
        this.landmarkExtractor = FeatureExtractor.create(settingReader.getStr(GROUP, "landmark_model"));

        /**
        * 关闭quietly。
        * @param oldVectorStorage 旧向量storage
         */
        closeQuietly(oldVectorStorage);
        log.info("[FaceClientManager] 人脸识别实例热更新完成");
    }

    /**
    * 构建检测管线（detect + crop + liveness + 特征 + 检索 + 辅助能力）。
    *
    * @return 检测管线
     */
    private FacePipeline buildPipeline() {
        FacePipeline.Builder builder = FacePipeline.builder()
                .detector(settingReader.getStr(GROUP, "detector_model"));
        LivenessDetector liveness = buildLiveness();
        if (liveness != null) {
            builder.liveness(liveness);
            builder.requireLive(settingReader.getBool(GROUP, "liveness_enabled", false));
            builder.livenessThreshold(readFloat("liveness_threshold", 0.5f));
        }
 // 识别链路（特征 + 向量库）
        String featureModel = settingReader.getStr(GROUP, "feature_model");
        if (StringUtils.isNotBlank(featureModel)) {
            builder.feature(featureModel);
        }
        builder.vectorStorage(currentVectorStorage());
        builder.topK(settingReader.getInt(GROUP, "top_k", 5));
 // 辅助能力（已启用 开关 + 配置模型均满足才注入，保持懒加载）
        injectOptional(builder, "anime_enabled", "anime_detector_model", (m) -> builder.anime(m));
        injectOptional(builder, "super_resolution_enabled", "super_resolution_model", (m) -> builder.superResolution(m));
        injectOptional(builder, "restore_enabled", "restore_model", (m) -> builder.restore(m));
        injectOptional(builder, "attribute_enabled", "attribute_model", (m) -> builder.attribute(m));
        injectOptional(builder, "emotion_enabled", "emotion_model", (m) -> builder.emotion(m));
        injectOptional(builder, "landmark_enabled", "landmark_model", (m) -> builder.landmark(m));
        injectOptional(builder, "deepfake_enabled", "deepfake_model", (m) -> builder.deepfake(m));
        return builder.build();
    }

    /**
    * 可选模型注入：启用开关为 true（未配置时默认开启）且配置了模型 标识 才注入构建器。
    *
    * @param builder      构建器
    * @param enabledField 启用开关字段
    * @param field        模型字段
    * @param inject       注入动作
     */
    private void injectOptional(FacePipeline.Builder builder, String enabledField, String field,
                                java.util.function.Consumer<String> inject) {
        if (!settingReader.getBool(GROUP, enabledField, true)) {
            return;
        }
        String model = settingReader.getStr(GROUP, field);
        if (StringUtils.isNotBlank(model)) {
            inject.accept(model);
        }
    }

    /**
    * 构建人脸向量库。
    *
    * @return 向量库
     */
    private VectorStorage buildVectorStorage() {
        String name = settingReader.getStr(GROUP, "vector_storage");
        if (StringUtils.isBlank(name)) {
            name = "jvector";
        }
        String algorithm = settingReader.getStr(GROUP, "vector_algorithm");
        VectorCompareAlgorithm compare = resolveAlgorithm(algorithm);
        int dimension = settingReader.getInt(GROUP, "feature_dimension", DEFAULT_FEATURE_DIMENSION);
        return VectorStorageProvider.create(name, dimension, compare);
    }

    /**
    * 构建活体检测器（未配置时返回 空）。
    *
    * @return 活体检测器或 空
     */
    private LivenessDetector buildLiveness() {
        String model = settingReader.getStr(GROUP, "liveness_model");
        if (StringUtils.isBlank(model)) {
            return null;
        }
        return LivenessDetector.create(model);
    }

    /**
    * 构建质量评估器（未配置时返回 空）。
    *
    * @return 质量评估器或 空
     */
    private FaceQualityAssessor buildQualityAssessor() {
        boolean enabled = settingReader.getBool(GROUP, "quality_enabled", false);
        if (!enabled) {
            return null;
        }
        return FaceQualityAssessor.create(settingReader.getStr(GROUP, "detector_model"));
    }

    /**
    * 解析向量算法名称。
    *
    * @param name 算法名（COSINE / EUCLIDEAN / DOT）
    * @return 算法实例
     */
    private VectorCompareAlgorithm resolveAlgorithm(String name) {
        return switch (Objects.requireNonNullElse(name, "COSINE").toUpperCase()) {
            case "EUCLIDEAN" -> VectorCompareAlgorithm.euclidean();
            case "DOT" -> VectorCompareAlgorithm.dotProduct();
            default -> VectorCompareAlgorithm.cosine();
        };
    }

    /**
    * 读取浮点配置，未配置或非法时返回默认值。
    *
    * @param field        字段名
    * @param defaultValue 默认值
    * @return 浮点值
     */
    private float readFloat(String field, float defaultValue) {
        BigDecimal value = settingReader.getDecimalOrNull(GROUP, field);
        if (value == null) {
            return defaultValue;
        }
        try {
            return value.floatValue();
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
    * 静默关闭资源。
    *
    * @param storage 向量库
     */
    private static void closeQuietly(VectorStorage storage) {
        if (storage == null) {
            return;
        }
        try {
            storage.close();
        } catch (Exception ignored) {
        }
    }

    /**
    * 构建人脸配置摘要（供日志/健康检查使用）。
    *
    * @return 配置摘要
     */
    public Map<String, String> describe() {
        Map<String, String> desc = new HashMap<>();
        desc.put("detector_model", settingReader.getStr(GROUP, "detector_model"));
        desc.put("feature_model", settingReader.getStr(GROUP, "feature_model"));
        desc.put("vector_storage", settingReader.getStr(GROUP, "vector_storage"));
        desc.put("vector_algorithm", settingReader.getStr(GROUP, "vector_algorithm"));
        desc.put("liveness_model", settingReader.getStr(GROUP, "liveness_model"));
        return desc;
    }
}
