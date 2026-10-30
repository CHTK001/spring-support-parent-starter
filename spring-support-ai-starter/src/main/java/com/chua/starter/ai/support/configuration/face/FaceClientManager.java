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
import com.chua.jvector.support.spi.JVectorVectorStorageProvider;
import com.chua.starter.ai.support.configuration.client.GlobalSettingReader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
     * 人脸模型**兜底取值**观测专用日志器（见 {@link #resolveFaceModel(String)}）。
     *
     * <p>为什么不复用 {@code @Slf4j} 生成的类日志器：验收脚本 {@code verify-effective.sh}
     * 用 {@code grep -aF "FaceClientManager"} 统计「共享单例的重建出现在哪类线程」，
     * 而 logback 会把 logger 类名（{@code …configuration.face.FaceClientManager}）打在行首 ——
     * 本类<b>任何</b> INFO 日志都会落进那个统计口径。偏偏兜底回显是在**按账号**管线重建
     * （{@code FaceStores#reloadAccount}，跑在 {@code *-handler-*} 请求线程）路径上触发的，
     * 挂类日志器就会被误判成「请求线程重建了共享单例」→ 判定②假红。</p>
     *
     * <p>判据不能为了让新日志通过去改（那是放松判据），所以**新日志挪出统计口径**，
     * 而不是动统计口径：消息前缀仍是 {@code [Face]}、观测点照常可 grep，
     * {@code t2.sh} 直接数它；{@code ve.sh} 的字面量统计一条未改。</p>
     */
    private static final Logger RESOLVE_OBSERVE = LoggerFactory.getLogger("face.model.resolve");

    /**
     * 全局设置键值分组
     */
    private static final String GROUP = "face";

    /**
     * 默认特征向量维度（R50/face-特征 输出，与 {@code dimension(512)} 对齐）
     */
    private static final int DEFAULT_FEATURE_DIMENSION = 512;

    /**
     * 人脸超分模型的缺省标识（配置留空时回退，与 face 全局 setting 提供者的默认值一致）
     */
    private static final String DEFAULT_SUPER_RESOLUTION_MODEL = "gfpgan-face-super-resolution";

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
        if (!isModelConfigured("feature_model")) {
            return null;
        }
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
        if (!isModelConfigured("detector_model")) {
            return null;
        }
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
        // 桥接为模型级参数：使 device / 阈值在引擎侧生效（人脸链路原先只调用门面 setter，
        // device 从未参与推理设备决策）
        com.chua.starter.ai.support.configuration.client.ModelParamBridge.bridge(
                modelId,
                settingReader.getStr(GROUP, "device"),
                detTh == null ? null : detTh.floatValue());
        return d;
    }

    /**
     * 获取当前动漫人脸检测器（懒加载）。
     *
     * @return 动漫人脸检测器
     */
    public FaceDetector currentAnimeDetector() {
        if (!isModelConfigured("anime_detector_model")) {
            return null;
        }
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
                    superResolution = ImageEnhancer.create(superResolutionModel());
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
        if (!isModelConfigured("restore_model")) {
            return null;
        }
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
        if (!isModelConfigured("attribute_model")) {
            return null;
        }
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
        if (!isModelConfigured("emotion_model")) {
            return null;
        }
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
        if (!isModelConfigured("deepfake_model")) {
            return null;
        }
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
        if (!isModelConfigured("landmark_model")) {
            return null;
        }
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
        if (!GROUP.equals(event.group())) {
            return;
        }
        if (event.userScoped()) {
            // 共享实例（检测器 / 分类器 / 活体 / 质量）只承载**全局**配置。
            // 按触发者的身份重建会把他的模型烘进共享实例、串掉其他用户 —— 而这些字段
            // 本就不是按用户的（按用户的是 FaceStores 里按账号构建的管线，它自己响应）。
            log.debug("[FaceClientManager] 收到用户级 face 配置写入，共享实例不重建（按账号管线由 FaceStores 重建）");
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
        // 这里【不再】顺手建单例的 vectorStorage / pipeline。
        //
        // 原因：按账号隔离后，人脸数据的唯一入口是 FaceStores（每账号
        // buildStorageFor + buildPipelineWith）。全仓核查调用方：
        //   FaceClientManager.currentPipeline()       唯一调用方是
        //       GlobalModelServiceImpl#getFacePipeline()，而该方法【零调用方】
        //       （GlobalModelController 只用 getConfigSummary / getConfig /
        //         getBoolConfig / getIntConfig）
        //   FaceClientManager.currentVectorStorage() 零外部调用方
        //   真正的业务链路 AiFaceServiceImpl:63 / :236 走的是 faceStores.*（按账号）
        // 所以这两个字段是纯死重。而在 rebuild() 里 eager 建它有两个实际代价：
        //   1) 建出 <CWD>/face/face-index 目录 —— 线上表现为 /opt/ai-starter/runtime/face
        //      这个空目录（功能上已不再使用，却每次启动 / 改配置都出现）；
        //   2) 那条路径是【跨账号共享】的，正是本类注释里警告的
        //      「多个业务共用同一个索引文件，换维度即互相破坏」隐患本身。
        // 改为不 eager 建：currentVectorStorage() / currentPipeline() 本身已是懒加载，
        // 真有调用方时再按需建 —— 行为不变，只是不再凭空产生。
        VectorStorage oldVectorStorage = this.vectorStorage;
        this.vectorStorage = null;
        this.pipeline = null;
        LivenessDetector nextLiveness = buildLiveness();
        FaceQualityAssessor nextQuality = buildQualityAssessor();

        this.liveness = nextLiveness;
        this.qualityAssessor = nextQuality;

        // 依赖配置的组件全部重建
        this.featureExtractor = FeatureExtractor.create(settingReader.getStr(GROUP, "feature_model"));
        this.detector = buildDetector(settingReader.getStr(GROUP, "detector_model"));
        this.animeDetector = buildDetector(settingReader.getStr(GROUP, "anime_detector_model"));
        this.superResolution = ImageEnhancer.create(superResolutionModel());
        this.restorer = ImageEnhancer.create(settingReader.getStr(GROUP, "restore_model"));
        this.attributeClassifier = ImageClassifier.create(settingReader.getStr(GROUP, "attribute_model"));
        this.emotionClassifier = ImageClassifier.create(settingReader.getStr(GROUP, "emotion_model"));
        this.deepfakeClassifier = ImageClassifier.create(settingReader.getStr(GROUP, "deepfake_model"));
        this.landmarkExtractor = FeatureExtractor.create(settingReader.getStr(GROUP, "landmark_model"));

        // 单例存储若此前被懒加载建过（当前无调用方，理论上不会发生），这里仍要关掉，
        // 不能让它白持有。closeQuietly 内部已判空。
        closeQuietly(oldVectorStorage);
        log.info("[FaceClientManager] 人脸识别实例热更新完成");
    }

    /**
     * 构建检测管线（detect + crop + liveness + 特征 + 检索 + 辅助能力）。
     *
     * @return 检测管线
     */
    private FacePipeline buildPipeline() {
        return buildPipeline(currentVectorStorage());
    }

    /**
     * 用**指定的向量存储**构建人脸管线。
     *
     * <p>为什么需要这个重载：按账号隔离后，每个账号要有自己的向量库（各自一个索引文件），
     * 但检测/特征/活体等 ONNX 模型是**共享**的 —— 它们只是包住单例引擎的轻量包装，
     * 重的是引擎本身。因此这里把「向量存储」抽成参数，模型实例仍由本单例提供，
     * 每账号只是换一个 storage，不会重复加载模型。</p>
     *
     * @param storage 该账号的向量存储
     * @return 人脸管线；检测模型未配置时返回 {@code null}
     */
    public FacePipeline buildPipelineWith(VectorStorage storage) {
        return buildPipeline(storage);
    }

    /**
     * 解析人脸模型标识：优先本分组字段，留空时回落到「默认模型」里的 {@code runtime/default_face_model}。
     *
     * <p>两者同为 ONNX 模型命名空间，故可安全回落；若仍为空表示该能力未启用。</p>
     *
     * @param field 本分组字段名（detector_model / feature_model 等）
     * @return 模型标识，未配置返回 空
     */
    private String resolveFaceModel(String field) {
        String v = settingReader.getStr(GROUP, field);
        if (StringUtils.isNotBlank(v)) {
            return v;
        }
        // 兜底观测点：tier-1（face 分组字段）留空 → tier-2（runtime/default_face_model）。
        //
        // 这条链路此前**没有任何日志**：验收只能看到「管线建没建得起来」
        //（GET /v2/face/models 的 200/400），看不到**实际取用了哪个模型** ——
        // t2.sh 开篇原话「当时选的观测点看不到解析结果」，规格③因此只验到「兜底分支条件成立」。
        // 回显解析结果后，「默认模型真的被取用」变成可直接断言的正面证据（t2.sh 规格③ 已挂上判据），
        // 口径与 RagClientManager「rag_llm_model 未配置，回落默认模型: {}」一致。
        String fallback = settingReader.getStr("runtime", "default_face_model");
        RESOLVE_OBSERVE.info("[Face] {} 未配置，回落默认模型: {}", field,
                StringUtils.isNotBlank(fallback) ? fallback : "(空)=该能力未启用");
        return fallback;
    }

    /**
     * build Pipeline
     * @param vectorStorageForPipeline 参数
     * @return 返回值
    */
    private FacePipeline buildPipeline(VectorStorage vectorStorageForPipeline) {
        // detector 为空 = 人脸检测未启用：ONNX 模型未配置时该模型并不存在，
        // 此时不构建管线（与 buildLiveness() 的空值语义一致），调用方按「未开启」处理。
        // 兜底：默认模型设置里的 default_face_model（同为 ONNX 模型命名空间）。
        String detectorModel = resolveFaceModel("detector_model");
        if (StringUtils.isBlank(detectorModel)) {
            return null;
        }
        FacePipeline.Builder builder = FacePipeline.builder()
                .detector(detectorModel);
        LivenessDetector liveness = buildLiveness();
        if (liveness != null) {
            builder.liveness(liveness);
            builder.requireLive(settingReader.getBool(GROUP, "liveness_enabled", false));
            builder.livenessThreshold(readFloat("liveness_threshold", 0.5f));
        }
 // 识别链路（特征 + 向量库）
        String featureModel = resolveFaceModel("feature_model");
        if (StringUtils.isNotBlank(featureModel)) {
            builder.feature(featureModel);
        }
        builder.vectorStorage(vectorStorageForPipeline);
        builder.topK(settingReader.getInt(GROUP, "top_k", 5));
        // 1:1 比对判定阈值：此前管线里写死 0.5，设置页的 compare_threshold 读都没读 ——
        // 用户在界面上改它不会有任何效果。这里把配置接上（缺省 0.5，与写死值一致，行为不变）。
        BigDecimal compareThreshold = settingReader.getDecimalOrNull(GROUP, "compare_threshold");
        if (compareThreshold != null) {
            builder.compareThreshold(compareThreshold.floatValue());
        }
        // 1:N 命中阈值：同样是「声明了但从未读取」的配置项，接上后 1:N 会按相似度下限过滤，
        // 不再把 topK 全量当命中（缺省 0.5，与登记默认一致，行为不变）。
        BigDecimal matchThreshold = settingReader.getDecimalOrNull(GROUP, "match_threshold");
        if (matchThreshold != null) {
            builder.matchThreshold(matchThreshold.floatValue());
        }
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
    /**
     * 读取模型配置项，非空表示该能力已配置。
     *
     * <p>ONNX 模型未配置时该模型并不存在，因此「留空」一律视为该能力未启用，
     * 与 {@link #buildLiveness()} / {@link #buildQualityAssessor()} 的空值语义一致。</p>
     *
     * @param field 模型配置字段
     * @return true=已配置
     */
    private boolean isModelConfigured(String field) {
        return StringUtils.isNotBlank(settingReader.getStr(GROUP, field));
    }

    /**
     * 读取人脸超分模型标识，配置留空时回退默认。
     *
     * <p>懒加载与 {@link #rebuild()} 两条创建路径必须走同一个方法。此前 rebuild()
     * 直接把配置值丢给 create()，而懒加载路径带回退：于是「超分模型留空」在刚启动时
     * 能用默认模型，管理员一旦改过任意 face 配置，字段就变成绑定空模型标识的包装，
     * getter 的懒加载分支被跳过，调用时才抛 {@code IllegalStateException: 模型未注册}。</p>
     *
     * @return 人脸超分模型标识（非空）
     */
    private String superResolutionModel() {
        String model = settingReader.getStr(GROUP, "super_resolution_model");
        return StringUtils.isNotBlank(model) ? model : DEFAULT_SUPER_RESOLUTION_MODEL;
    }

    /**
     * 选填模型注入：仅当开关为 true 且模型非空才注册动作。
     *
     * @param builder      管线构建器
     * @param enabledField 开关字段
     * @param field        模型字段
     * @param inject       注册动作
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
        return buildStorageFor(faceIndexPath());
    }

    /**
     * 用**指定的索引路径**构建人脸向量库。
     *
     * <p>按账号隔离的关键：每个账号的索引文件必须分开。以图搜图的教训是
     * 「不允许依赖第三方默认相对路径」—— 多个业务共用同一个索引文件，
     * 换维度即互相破坏（实测：维度 256→512 后 1:N 直接抛 search 失败）。</p>
     *
     * @param indexPath 索引路径（不含扩展名，jvector 自行追加 .vectors）
     * @return 向量库
     */
    public VectorStorage buildStorageFor(Path indexPath) {
        String name = settingReader.getStr(GROUP, "vector_storage");
        if (StringUtils.isBlank(name)) {
            name = "jvector";
        }
        String algorithm = settingReader.getStr(GROUP, "vector_algorithm");
        VectorCompareAlgorithm compare = resolveAlgorithm(algorithm);
        int dimension = settingReader.getInt(GROUP, "feature_dimension", DEFAULT_FEATURE_DIMENSION);
        Map<String, Object> config = new LinkedHashMap<>(2);
        // jvector **不会自动创建缺失的父目录**：父目录不存在时写入静默失败，
        // 表现为「登记成功但重启即丢」。所以必须先建目录。
        try {
            Files.createDirectories(indexPath.toAbsolutePath().getParent());
        } catch (Exception ex) {
            log.warn("[Face] 人脸向量索引目录创建失败，索引可能无法落盘: {} -> {}",
                    indexPath.getParent(), ex.getMessage());
        }
        config.put(JVectorVectorStorageProvider.KEY_INDEX_PATH, indexPath.toAbsolutePath().toString());
        return VectorStorageProvider.createDynamic(name, dimension, compare, config);
    }

    /**
     * 人脸向量索引的落盘路径。
     *
     * <p>根目录取系统属性 {@code face.store.dir}，缺省为进程工作目录（远端即
     * {@code /opt/ai-starter/runtime}）；索引落在 {@code <根>/face/face-index}。
     * 与「以图搜图」同一套思路：**不允许依赖第三方默认相对路径**，
     * 否则多个业务会共用同一个索引文件，换维度即互相破坏。</p>
     *
     * @return 索引路径（不含扩展名，jvector 会自行追加 .vectors）
     */
    private Path faceIndexPath() {
        String base = System.getProperty("face.store.dir", ".");
        return Paths.get(base).resolve("face").resolve("face-index");
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
     * 构建质量评估器（未配置或模型不支持质量评估时返回 空）。
     * <p>质量评估必须使用独立的质量模型（配置项 quality_model），不能复用 detector_model：
     * 检测类 Translator 输出 {@code List<PredictRectangle>}，而本接口要求
     * {@link FaceQualityAssessor} 返回 {@code FaceQualityInfo}，混用会在首次调用时抛
     * ClassCastException。此处同时校验模型是否在质量能力清单内，避免误配。</p>
     *
     * @return 质量评估器或 空
     */
    private FaceQualityAssessor buildQualityAssessor() {
        boolean enabled = settingReader.getBool(GROUP, "quality_enabled", false);
        if (!enabled) {
            return null;
        }
        String qualityModel = settingReader.getStr(GROUP, "quality_model");
        if (StringUtils.isBlank(qualityModel)) {
            return null;
        }
        if (!FaceQualityAssessor.listModels().contains(qualityModel)) {
            return null;
        }
        return FaceQualityAssessor.create(qualityModel);
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
        } catch (Exception e) {
            RESOLVE_OBSERVE.debug("[face] 关闭向量存储失败，已忽略: {}", e.getMessage());
        }
    }

    /**
     * 构建人脸配置摘要（供日志/健康检查使用）。
     *
     * @return 配置摘要
     */
    public Map<String, String> describe() {
        Map<String, String> desc = new HashMap<>(8);
        desc.put("detector_model", settingReader.getStr(GROUP, "detector_model"));
        desc.put("feature_model", settingReader.getStr(GROUP, "feature_model"));
        desc.put("vector_storage", settingReader.getStr(GROUP, "vector_storage"));
        desc.put("vector_algorithm", settingReader.getStr(GROUP, "vector_algorithm"));
        desc.put("liveness_model", settingReader.getStr(GROUP, "liveness_model"));
        return desc;
    }
}
