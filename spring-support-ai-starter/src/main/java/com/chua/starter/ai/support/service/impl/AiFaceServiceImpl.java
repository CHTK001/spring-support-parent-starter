package com.chua.starter.ai.support.service.impl;

import com.chua.common.support.utils.StringUtils;
import com.chua.deeplearning.support.face.FaceAlignResult;
import com.chua.deeplearning.support.face.FaceCompareResult;
import com.chua.deeplearning.support.face.FaceDetectionHit;
import com.chua.deeplearning.support.face.FaceDetector;
import com.chua.deeplearning.support.face.FaceFeaturePipelineResult;
import com.chua.deeplearning.support.face.FaceIdentifyHit;
import com.chua.deeplearning.support.face.FaceLandmarkResult;
import com.chua.deeplearning.support.face.FaceSearchHit;
import com.chua.deeplearning.support.face.FacePipeline;
import com.chua.deeplearning.support.feature.FeatureExtractor;
import com.chua.deeplearning.support.image.ImageClassifier;
import com.chua.deeplearning.support.image.ImageEnhancer;
import com.chua.starter.ai.support.manage.common.ApiErrorCode;
import com.chua.starter.ai.support.manage.common.ApiException;
import com.chua.deeplearning.support.model.DetectionInfo;
import com.chua.deeplearning.support.model.FaceQualityInfo;
import com.chua.deeplearning.support.model.PredictRectangle;
import com.chua.starter.ai.support.configuration.face.FaceClientManager;
import com.chua.starter.ai.support.service.AiLibraryGroups;
import com.chua.starter.ai.support.service.FaceLibraryLinkService;
import com.chua.starter.ai.support.service.FaceStores;
import com.chua.starter.ai.support.service.AiFaceService;
import com.chua.starter.ai.support.util.AiUserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * AI 人脸识别服务实现。
 *
 * <p>基于 {@link FaceClientManager} 获取懒加载的人脸模型实例，全部配置实时生效。
 * 1:1 比对使用余弦相似度（与向量库 COSINE 算法一致）。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiFaceServiceImpl implements AiFaceService {

    /**
     * 人脸客户端管理器（懒加载 + 热重建）
     *
     * <p>只用于**模型类**能力（活体/动漫/超分/修复/属性/表情/关键点/质量/换脸）——
     * 这些是全局共享的 ONNX 模型，与账号无关。</p>
     */
    private final FaceClientManager faceClientManager;

    /**
     * 人脸底库门面：**按当前登录账号 + 分组**路由到各自的人脸库（数据隔离 + 快照持久化）。
     */
    private final FaceStores faceStores;

    /**
     * 底库档案（DB）联动层。
     *
     * <p>只用于 {@link #groups()} —— 枚举分组必须看 DB 这个权威口径，
     * 而不是内存快照：快照是懒建的，没被访问过的分组在里面根本不存在，
     * 用它枚举会出现「同一个账号，分组列表时多时少」。</p>
     */
    private final FaceLibraryLinkService library;

    /**
     * 取人脸管线，未启用时给出明确业务异常。
     *
     * <p>人脸检测模型（{@code detector_model}）留空即视为人脸能力未启用 —— ONNX 模型
     * 未配置时模型文件并不存在，此时 {@code FaceStores.currentPipeline()} 返回 null。
     * 这里统一拦截，避免各方法直接对 null 调用产生 NPE，同时把「为什么没生效」讲清楚。</p>
     *
     * @return 可用的人脸管线
     */
    private FacePipeline pipeline() {
        FacePipeline current = faceStores.currentPipeline();
        if (current == null) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "人脸能力未启用：请先在「系统配置 → 人脸识别」中配置人脸检测模型");
        }
        return current;
    }

    /**
     * 辅助能力的模型字段留空 → 统一抛**业务异常**，不走 NPE。
     *
     * <p>{@link FaceClientManager} 的各个 {@code current*()} 对「模型字段留空」一律返回
     * {@code null}（空值即能力未启用，见其 {@code isModelConfigured}），语义与
     * {@link #pipeline()} 的空值拦截一致。此前辅助方法直接链式解引用，
     * 于是「没配模型」这个<b>可预期状态</b>走的是 {@code NullPointerException}
     * （验收报告 §7.5 实测：{@code ERROR [Face] 人脸修复失败 + NullPointerException: ImageEnhancer.enhance}）——
     * 异常类型把业务状态伪装成程序缺陷，控制器只能按未知错误打 {@code ERROR + 全栈}。</p>
     *
     * <p>改抛 {@link ApiException} 后<b>状态码与信封契约不变</b>：二进制端点（restore）仍 400、
     * 可选能力端点（attributes/emotion/landmark/anime-detect/deepfake）仍 200 信封
     * （fg.sh §10.5/§10.6），控制器据此按类型降噪为 WARN 一行。</p>
     *
     * @param capability 能力名称（人类可读）
     * @param field      face 分组下的模型字段名
     * @return 待抛出的业务异常
     */
    private ApiException capabilityDisabled(String capability, String field) {
        return new ApiException(ApiErrorCode.INVALID_PARAM,
                capability + "能力未启用：请先在「系统配置 → 人脸识别」中配置 face." + field);
    }

    /**
     * 人脸检测（单独能力）。
     *
     * @param imageData 图片字节
     * @return 检测命中列表
     */
    @Override
    public List<FaceDetectionHit> detect(byte[] imageData) {
        return pipeline().detect(imageData);
    }

    /**
     * 检测最大人脸（单独能力）。
     *
     * @param imageData 图片字节
     * @return 最大人脸命中，无则 空
     */
    @Override
    public FaceDetectionHit detectLargest(byte[] imageData) {
        return pipeline().detectLargest(imageData);
    }

    /**
     * 人脸检测管线（detect → crop → liveness）。
     *
     * @param imageData 图片字节
     * @return 检测命中列表
     */
    @Override
    public List<FaceDetectionHit> detectPipeline(byte[] imageData) {
        return pipeline().detectPipeline(imageData);
    }

    /**
     * 提取人脸特征向量（单独能力）。
     *
     * @param imageData 图片字节
     * @return 特征向量
     */
    @Override
    public float[] extractFeature(byte[] imageData) {
        FaceFeaturePipelineResult r = pipeline().extractFeatureWithMeta(imageData);
        return r == null || r.feature() == null ? new float[0] : r.feature();
    }

    @Override
    /**
     * extract特征pipeline
    */
    public FaceFeaturePipelineResult extractFeaturePipeline(byte[] imageData) {
        return pipeline().extractFeatureWithMeta(imageData);
    }

    @Override
    /**
     * Align
    */
    public FaceAlignResult align(byte[] imageData) {
        return pipeline().align(imageData);
    }

    /**
     * 1:1 人脸比对（单独能力）。
     *
     * @param imageA 图片 A 字节
     * @param imageB 图片 B 字节
     * @return 相似度（0~1）
     */
    @Override
    public double compare(byte[] imageA, byte[] imageB) {
 // 走完整管线（detect→crop→liveness→align→特征）提取两侧特征后余弦比对
        FaceFeaturePipelineResult a = pipeline().extractFeatureWithMeta(imageA);
        FaceFeaturePipelineResult b = pipeline().extractFeatureWithMeta(imageB);
        if (a == null || b == null || a.feature() == null || b.feature() == null) {
            return 0d;
        }
        return cosineSimilarity(a.feature(), b.feature());
    }

    @Override
    /**
     * 比较withmeta
    */
    public FaceCompareResult compareWithMeta(byte[] imageA, byte[] imageB) {
        return pipeline().compareWithMeta(imageA, imageB);
    }

    /**
     * 1:1 特征向量比对（单独能力）。
     *
     * @param featureA 特征向量 A
     * @param featureB 特征向量 B
     * @return 相似度（0~1）
     */
    @Override
    public double compareFeature(float[] featureA, float[] featureB) {
        return cosineSimilarity(featureA, featureB);
    }

    /**
     * 登记人脸入库（单独能力，取最大人脸）。
     *
     * @param group     分组名（空值按默认组）
     * @param id        人脸 标识
     * @param imageData 图片字节
     * @param metadata  附加元数据
     * @return 是否登记成功
     */
    @Override
    public boolean enroll(String group, String id, byte[] imageData, Map<String, Object> metadata) {
        float[] feature = extractFeature(imageData);
        // 走门面：入库的同时写入本账号**本分组**的向量与快照（否则重启即丢 —— 改造前就是这样）
        return faceStores.enroll(group, id, feature, metadata);
    }

    /**
     * 登记管线（detect → crop → liveness → 特征 → 入库，取最大人脸）。
     *
     * @param group     分组名（空值按默认组）
     * @param id        人脸 标识
     * @param imageData 图片字节
     * @param metadata  附加元数据
     * @return 是否登记成功
     */
    @Override
    public boolean enrollPipeline(String group, String id, byte[] imageData, Map<String, Object> metadata) {
        return faceStores.enrollFromImage(group, id, imageData, metadata);
    }

    @Override
    /**
     * enroll特征
    */
    public boolean enrollFeature(String group, String id, float[] feature, Map<String, Object> metadata) {
        return faceStores.enroll(group, id, feature, metadata);
    }

    /**
     * 1:N 人脸识别（单独能力，取最大人脸）。
     *
     * @param groups    分组名列表（空/未指定只查默认组）
     * @param imageData 图片字节
     * @return 识别命中（TopK）
     */
    @Override
    public FaceIdentifyHit identify(List<String> groups, byte[] imageData) {
        return faceStores.identify(groups, imageData);
    }

    /**
     * 识别管线（detect → crop → liveness → 特征 → 检索，取最大人脸）。
     *
     * @param groups    分组名列表（空/未指定只查默认组）
     * @param imageData 图片字节
     * @return 识别命中（TopK）
     */
    @Override
    public FaceIdentifyHit identifyPipeline(List<String> groups, byte[] imageData) {
        // 与 identify 同一实现：门面内部已按「单组直查、多组归并」分流（见 FaceStores#identify）
        return faceStores.identify(groups, imageData);
    }

    /**
     * 1:N 特征检索（单独能力）。
     *
     * @param groups  分组名列表（空/未指定只查默认组）
     * @param feature 特征向量
     * @param topK    返回条数
     * @return 检索命中列表
     */
    @Override
    public List<FaceSearchHit> search(List<String> groups, float[] feature, int topK) {
        // 门面已做跨分组归并 + 分数提取（各自取 K 条后全局排序再截断），这里不再自行映射
        return faceStores.searchHits(groups, feature, topK);
    }

    /**
     * 枚举当前账号已存在的分组名。
     *
     * <p><b>为什么读 DB 而不是遍历内存快照。</b>快照是懒建的：某个分组这次进程还没被访问过，
     * 内存里就没有它的条目，用它枚举会「有些组时有时无」。DB（{@code sys_ai_face_library}）
     * 是档案权威，不随访问历史变化。</p>
     *
     * <p>不传分组列表 = 不做分组过滤（见 {@link FaceLibraryLinkService#listMine}），
     * 一次拿回该账号全部行；行上的 {@code group} 已是归一化后的字符串，
     * 但历史 {@code NULL} 行仍要兜一层 normalize，故统一过一遍。</p>
     *
     * @return 分组名列表（去重、已排序，至少含默认组）
     */
    @Override
    public List<String> groups() {
        String account = AiUserContext.requireUserId();
        // TreeSet 一举满足「去重 + 排序」；默认组先放进去，保证空库也能选到它
        Set<String> collected = new TreeSet<>();
        collected.add(AiLibraryGroups.DEFAULT);
        for (Map<String, Object> row : library.listMine(account, List.of())) {
            Object group = row.get("group");
            collected.add(AiLibraryGroups.normalize(group == null ? null : String.valueOf(group)));
        }
        return List.copyOf(collected);
    }

    /**
     * 列出当前账号人脸库中已登记的条目。
     *
     * @param group 分组名（空值按默认组）
     * @return 条目列表（id + 元数据）
     */
    @Override
    public List<Map<String, Object>> list(String group) {
        return faceStores.list(group);
    }

    /**
     * 分页 + 关键词 + 状态筛选的条目列表（委托人脸底库门面）。
     *
     * @param group     分组名（空值按默认组）
     * @param keyword   关键词（可空）
     * @param status    状态筛选（{@code thumb} / {@code noThumb} / 空=全部）
     * @param sortField 排序字段（可空）：{@code id} / {@code hasImage} / 或元数据键
     * @param sortOrder 排序方向（{@code asc} / {@code desc} / 空=asc）
     * @param page      页码（从 1 开始）；&le;0 不切片
     * @param size      每页条数；&le;0 不切片
     * @return {@code items} + {@code total} + {@code stats}
     */
    @Override
    public Map<String, Object> listPage(String group, String keyword, String status, String sortField, String sortOrder,
                                        int page, int size) {
        return faceStores.listPage(group, keyword, status, sortField, sortOrder, page, size);
    }

    /**
     * 读一条人脸登记时落存的裁剪图。
     *
     * @param group 分组名（空值按默认组）
     * @param id    人脸标识
     * @return PNG 字节；未落存时返回 空
     */
    @Override
    public byte[] faceImage(String group, String id) {
        return faceStores.faceImage(group, id);
    }

    /**
     * 读一条人脸登记时落存的原图。
     *
     * @param group 分组名（空值按默认组）
     * @param id    人脸标识
     * @return 原图字节；未落存（改造前的老数据）时返回 {@code null}
     */
    @Override
    public byte[] originalImage(String group, String id) {
        return faceStores.originalImage(group, id);
    }

    /**
     * 从当前账号人脸库删除一条。
     *
     * @param group 分组名（空值按默认组）
     * @param id    人脸 标识
     * @return 是否删除成功
     */
    @Override
    public boolean remove(String group, String id) {
        return faceStores.remove(group, id);
    }

    /**
     * 更新一条人脸的元数据（只改档案字段）。
     *
     * @param group    分组名（空值按默认组）
     * @param id       人脸 标识
     * @param metadata 新元数据
     * @return 是否更新成功
     */
    @Override
    public boolean updateMetadata(String group, String id, Map<String, Object> metadata) {
        return faceStores.updateMetadata(group, id, metadata);
    }

    /**
     * 核验管线（detect → 特征 → 与库中检索，取最大人脸，判断**该 id** 是否命中）。
     *
     * <p><b>判据是「命中」而不是「排第一」。</b>同一张脸被重复登记（多个 id）时，
     * 各条目相似度可能都是 1.0，谁排第一取决于向量存储的返回顺序；用
     * 「top-1 必须等于 id」会随数据漂移而误报 false。2026-10-04 实测：
     * 库内存在 2 条同源记录时，对**刚登记**的那条调 {@code /v2/face/verify}
     * 返回 {@code matched=false}，而 {@code /v2/face/identify} 的命中列表里
     * 该 id 的 score 正是 1.0 —— 同一事实两个端点给出相反结论。</p>
     *
     * <p>命中列表已由管线按 {@code face.match_threshold} 过滤
     * （见 {@code FacePipeline.searchFeature}），因此这里无需再判阈值。</p>
     *
     * @param id        人脸 标识
     * @param imageData 图片字节
     * @return 该 id 是否命中
     */
    @Override
    public boolean verify(String id, byte[] imageData) {
        if (id == null) {
            return false;
        }
        FaceIdentifyHit hit = pipeline().identifyPipelineLargest(imageData);
        if (hit == null || hit.hits() == null || hit.hits().isEmpty()) {
            return false;
        }
        return hit.hits().stream().anyMatch(h -> id.equals(h.id()));
    }

    /**
     * 活体检测（辅助能力）。
     *
     * @param imageData 裁剪后人脸图片字节
     * @return 是否活体
     */
    @Override
    public boolean isLive(byte[] imageData) {
        if (faceClientManager.currentLiveness() == null) {
            return true;
        }
        return faceClientManager.currentLiveness().isLive(imageData);
    }

    /**
     * 活体分数（辅助能力）。
     *
     * @param imageData 裁剪后人脸图片字节
     * @return 活体分数
     */
    @Override
    public float liveScore(byte[] imageData) {
        if (faceClientManager.currentLiveness() == null) {
            return 1.0f;
        }
        try {
            return faceClientManager.currentLiveness().liveScore(imageData);
        } catch (Exception e) {
            return 0.0f;
        }
    }

    /**
     * 动漫人脸检测（辅助能力）。
     *
     * @param imageData 图片字节
     * @return 检测信息列表
     */
    @Override
    public List<DetectionInfo> animeDetect(byte[] imageData) {
        FaceDetector current = faceClientManager.currentAnimeDetector();
        if (current == null) {
            throw capabilityDisabled("动漫人脸检测", "anime_detector_model");
        }
        return current.detectInfo(imageData);
    }

    /**
     * 人脸超分（辅助能力）。
     *
     * @param imageData 图片字节
     * @return 超分后图片字节
     */
    @Override
    public byte[] superResolution(byte[] imageData) {
        return faceClientManager.currentSuperResolution().enhance(imageData);
    }

    /**
     * 人脸修复（辅助能力，编码former）。
     *
     * @param imageData 图片字节
     * @return 修复后图片字节
     */
    @Override
    public byte[] restore(byte[] imageData) {
        ImageEnhancer current = faceClientManager.currentRestorer();
        if (current == null) {
            throw capabilityDisabled("人脸修复", "restore_model");
        }
        return current.enhance(imageData);
    }

    /**
     * 人脸属性分析（辅助能力，年龄/性别/种族）。
     *
     * @param imageData 图片字节
     * @return 分类结果标签
     */
    @Override
    public String analyzeAttributes(byte[] imageData) {
        ImageClassifier current = faceClientManager.currentAttributeClassifier();
        if (current == null) {
            throw capabilityDisabled("人脸属性分析", "attribute_model");
        }
        return current.classify(imageData);
    }

    /**
     * 人脸表情识别（辅助能力）。
     *
     * @param imageData 图片字节
     * @return 表情标签
     */
    @Override
    public String analyzeEmotion(byte[] imageData) {
        ImageClassifier current = faceClientManager.currentEmotionClassifier();
        if (current == null) {
            throw capabilityDisabled("人脸表情识别", "emotion_model");
        }
        return current.classify(imageData);
    }

    /**
     * 人脸关键点（辅助能力，106 点）。
     *
     * @param imageData 图片字节
     * @return 关键点坐标
     */
    @Override
    public float[] landmark(byte[] imageData) {
        FeatureExtractor current = faceClientManager.currentLandmarkExtractor();
        if (current == null) {
            throw capabilityDisabled("人脸关键点", "landmark_model");
        }
        return current.extract(imageData);
    }

    /**
     * 人脸关键点管线（坐标为**原图像素**）。
     *
     * <p>走 {@code detect → 取最大脸 → 按框裁剪 → 关键点模型 → 映射回原图}。
     * 与 {@link #landmark(byte[])} 的差异见 {@code FaceLandmarkResult} 的类注释：
     * 后者的归一化坐标画不到原图上，本方法的坐标可以直接叠加。</p>
     *
     * @param imageData 图片字节
     * @return 关键点结果（原图像素）；未配置关键点模型或未检出人脸时返回 空
     */
    @Override
    public FaceLandmarkResult landmarkPipeline(byte[] imageData) {
        if (faceClientManager.currentLandmarkExtractor() == null) {
            throw capabilityDisabled("人脸关键点", "landmark_model");
        }
        return pipeline().landmarkPipelineLargest(imageData);
    }

    /**
     * 人脸质量评估（辅助能力）。
     *
     * @param imageData 图片字节
     * @return 质量信息
     */
    @Override
    public FaceQualityInfo quality(byte[] imageData) {
        if (faceClientManager.currentQualityAssessor() == null) {
            return null;
        }
        return faceClientManager.currentQualityAssessor().assess(imageData);
    }

    /**
     * 换脸检测（辅助能力）。
     *
     * @param imageData 图片字节
     * @return 是否疑似换脸
     */
    @Override
    public boolean isDeepfake(byte[] imageData) {
        ImageClassifier current = faceClientManager.currentDeepfakeClassifier();
        if (current == null) {
            throw capabilityDisabled("换脸检测", "deepfake_model");
        }
        String result = current.classify(imageData);
        if (StringUtils.isBlank(result)) {
            return false;
        }
        String lower = result.toLowerCase();
        return lower.contains("fake") || lower.contains("spoof") || lower.contains("deepfake");
    }

    /**
     * 人脸框坐标检测（返回原始矩形框）。
     *
     * @param imageData 图片字节
     * @return 人脸框列表
     */
    @Override
    public List<PredictRectangle> detectBoxes(byte[] imageData) {
        return pipeline().detectBoxes(imageData);
    }

    /**
     * 枚举可用人脸模型。
     *
     * @return 模型清单（能力分组 → 模型 标识 列表）
     */
    @Override
    public Map<String, List<String>> listModels() {
        return pipeline().listModels();
    }

    /**
     * 余弦相似度计算（0~1）。
     *
     * @param a 向量 A
     * @param b 向量 B
     * @return 相似度
     */
    private static double cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null || a.length == 0 || a.length != b.length) {
            return 0d;
        }
        double dot = 0;
        double normA = 0;
        double normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0 || normB == 0) {
            return 0d;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
