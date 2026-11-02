package com.chua.starter.ai.support.service.impl;

import com.chua.common.support.utils.StringUtils;
import com.chua.common.support.vector.Vector;
import com.chua.common.support.vector.VectorStorage;
import com.chua.deeplearning.support.face.FaceFeaturePipelineResult;
import com.chua.deeplearning.support.face.FacePipeline;
import com.chua.deeplearning.support.face.FaceSearchHit;
import com.chua.deeplearning.support.feature.FeatureExtractor;
import com.chua.deeplearning.support.model.PredictRectangle;
import com.chua.deeplearning.support.reid.FaceReidPipeline;
import com.chua.deeplearning.support.reid.PersonTrack;
import com.chua.deeplearning.support.utils.ImageCropUtils;
import com.chua.starter.ai.support.configuration.client.GlobalSettingReader;
import com.chua.starter.ai.support.configuration.face.FaceSettingsChangedEvent;
import com.chua.starter.ai.support.configuration.reid.ReidClientManager;
import com.chua.starter.ai.support.entity.SysAiFaceLibrary;
import com.chua.starter.ai.support.service.AiReidService;
import com.chua.starter.ai.support.service.FaceLibraryLinkService;
import com.chua.starter.ai.support.service.FaceStores;
import com.chua.starter.ai.support.service.MultiFaceEnrollException;
import com.chua.starter.ai.support.service.ReidLibraryLinkService;
import com.chua.starter.ai.support.service.ReidStores;
import com.chua.starter.ai.support.util.AiLibraryGroups;
import com.chua.starter.ai.support.util.AiUserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI 人脸重识别服务实现。
 *
 * <p><b>两套状态，各司其职：</b></p>
 * <ul>
 *   <li><b>实时归并</b>（{@link PersonTrack}，按账号一份）：判断「这一帧的这张脸
 *       与刚才见过的哪张是同一人」。它要的是低延迟的相似度比较，因此留在内存。</li>
 *   <li><b>持久底库</b>（{@code sys_ai_reid_library} + {@link ReidStores} 的向量库）：
 *       每次观测都落一行并写入向量，轨迹由前者按 {@code track_id} 汇聚而来。
 *       <b>列表/详情/删除都以底库为准</b> —— 这样进程重启后轨迹还在，
 *       而不是"一重启全丢"。</li>
 * </ul>
 *
 * <p><b>为什么轨迹不缓存一份。</b>轨迹是底库的汇聚视图，缓存它就会有两份状态
 * （缓存里有一条已被删的观测、或漏掉新增的观测）。查询成本是一次分组，
 * 与"维护缓存一致性"的复杂度相比不值得省。</p>
 *
 * <p><b>双路分流：清晰人脸走人脸识别，模糊人脸走重识别。</b>两者<b>一起运行</b>，
 * 由检测置信度分界（{@code face.face_threshold}，默认 0.5）：</p>
 * <pre>
 * 场景图 ──低阈值检测(0.3)──► 多张人脸框
 *                              │
 *              ┌───────────────┴───────────────┐
 *              │ 置信度 ≥ 0.5（清晰）            │ 置信度 &lt; 0.5（模糊/远处）
 *              ▼                               ▼
 *        人脸识别（1:N 定库）              重识别（跨摄像头轨迹）
 *        回答「这是张三」                  回答「这个人去过哪」
 * </pre>
 *
 * <p><b>检测阈值故意比人脸识别低</b>：多检出一些模糊脸没关系 ——
 * 分流之后各自走擅长的路。人脸识别那条链路仍然按它自己的高标准阈值独立运行，
 * 两条路<b>互不影响</b>。</p>
 *
 * <p><b>模型共享、状态隔离</b>：检测与特征模型由 {@link ReidClientManager} 单例持有
 * （ONNX 模型不重复加载），轨迹管理器与底库数据按账号隔离。重识别管线的配置（检测/特征模型、
 * 归并阈值与时间窗、向量库与特征维度）与人脸识别共享 {@code face} 分组，
 * 不设第二套配置分组。</p>
 *
 * @author CH
 * @since 4.0.0.43
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiReidServiceImpl implements AiReidService {

    // 重识别的配置与人脸识别共享人脸组（face）：检测/特征模型、归并阈值与时间窗
    // 都在同一组里，这里不再建第二套。
    private static final String GROUP = "face";

    /**
     * 默认 TopK。
     */
    private static final int DEFAULT_TOP_K = 5;

    /**
     * 重识别客户端管理器。
     */
    private final ReidClientManager clientManager;

    /**
     * 全局设置读取器。
     */
    private final GlobalSettingReader settingReader;

    /**
     * 重识别底库联动层（DB 元数据 ↔ 向量，并负责轨迹汇聚）。
     */
    private final ReidLibraryLinkService library;

    /**
     * 重识别向量库门面（按账号隔离的持久向量库）。
     */
    private final ReidStores reidStores;

    /**
     * 人脸向量库门面（用于 1:N 定库检索，回答「这个人是谁」）。
     */
    private final FaceStores faceStores;

    /**
     * 人脸底库联动层（回查姓名等档案字段）。
     */
    private final FaceLibraryLinkService faceLibrary;

    /**
     * 「账号 \\u0000 分组」→ 该账号该分组的实时轨迹管理器
     * （仅用于相似度归并，不作为列表来源）。
     *
     * <p><b>分组必须进键。</b>轨迹是「这个分组里看到过谁」，
     * 让两根分组共用一个管理器会把「访客」组的人并进「员工」组的轨迹 ——
     * 既是数据越权，也产生「认错人」的假阳性。</p>
     */
    private final Map<String, PersonTrack> tracks = new ConcurrentHashMap<>();

    /**
     * 「账号 \\u0000 分组」→ 该账号该分组的重识别管线（检测/特征共用全局模型）。
     */
    private final Map<String, FaceReidPipeline> pipelines = new ConcurrentHashMap<>();

    /**
     * 配置变更后清空各账号的实时归并状态。
     *
     * <p><b>只清内存，不清底库。</b>归并参数变了，内存里按旧参数聚成的轨迹不再可信；
     * 但底库记录的是"当时确实观测到了这张脸"这一事实，它不因配置变化而失效 ——
     * 把它一起删掉等于销毁历史观测。</p>
     *
     * @param event 配置变更事件
     */
    @EventListener
    public void onSettingsChanged(FaceSettingsChangedEvent event) {
        if (event == null || !GROUP.equals(event.group())) {
            return;
        }
        if (tracks.isEmpty() && pipelines.isEmpty()) {
            return;
        }
        int cleared = tracks.size();
        tracks.clear();
        pipelines.clear();
        log.info("[Reid] face 分组配置变更，已重置 {} 个账号的实时归并状态（底库数据保留）", cleared);
    }

    @Override
    public ObserveResult observe(String group, String cameraId, String sourceId, byte[] imageData,
                                 BigDecimal lat, BigDecimal lng) {
        String account = AiUserContext.requireUserId();
        String resolvedGroup = AiLibraryGroups.normalize(group);
        FaceReidPipeline pipeline = pipelineFor(account, resolvedGroup);
        if (pipeline == null) {
            throw new IllegalStateException("人脸重识别能力未启用：请在设置中心配置人脸组的检测模型与特征模型");
        }
        String resolvedCamera = StringUtils.isNotBlank(cameraId)
                ? cameraId
                : clientManager.defaultCameraId();
        String resolvedSource = StringUtils.isNotBlank(sourceId)
                ? sourceId
                : "frame-" + System.currentTimeMillis();

        FaceReidPipeline.ReidResult result = pipeline.observe(resolvedCamera, resolvedSource, imageData);

        int matched = 0;
        int created = 0;
        int faceHits = 0;
        int reidHits = 0;
        List<String> trackIds = new ArrayList<>();
        List<FaceBox> faces = new ArrayList<>();
        for (FaceReidPipeline.FaceObservation observation : result.observations()) {
            if (observation.matched()) {
                matched++;
            } else {
                created++;
            }
            String trackId = observation.trackId();
            // 分流：置信度 ≥ face_threshold 走人脸识别，否则走重识别。
            // 两条路的**检测框是同一批**（一次低阈值检测），只是后续处理不同。
            float confidence = observation.box().confidence();
            boolean clearFace = confidence >= clientManager.faceThreshold();
            // 叠框需要每张脸的框与归属轨迹，逐条带出（前端据此在图上画框并标注轨迹）
            faces.add(new FaceBox(
                    observation.box().x(), observation.box().y(),
                    observation.box().width(), observation.box().height(),
                    trackId, observation.score(), observation.matched(),
                    confidence,
                    clearFace ? MatchType.FACE.name() : MatchType.REID.name(),
                    null));
            if (clearFace) {
                faceHits++;
            } else {
                reidHits++;
            }
            if (trackId == null) {
                continue;
            }
            if (!trackIds.contains(trackId)) {
                trackIds.add(trackId);
            }
            // 每次观测都落库：底库是轨迹的唯一来源，漏写就等于轨迹少一段
            persistObservation(account, resolvedGroup, trackId, resolvedCamera, resolvedSource,
                    imageData, observation, lat, lng);
        }
        log.info("[Reid] 账号 '{}' 分组 '{}' 摄像头 '{}' 观测到 {} 张脸（命中 {} / 新建 {}；清晰走识别 {} / 模糊走重识别 {}）",
                account, resolvedGroup, resolvedCamera, result.observations().size(),
                matched, created, faceHits, reidHits);
        return new ObserveResult(resolvedCamera, resolvedSource,
                result.observations().size(), trackIds, matched, created, faceHits, reidHits, faces);
    }

    /**
     * 把一次人脸观测写入重识别底库。
     *
     * <p>失败只记 WARN 不抛：观测本身已经成功（归并结果已返回给调用方），
     * 落库失败不应让用户看到"观测失败"——但必须留日志，否则轨迹缺段无从排查。</p>
     *
     * <p><b>向量必须落库。</b>此前这里传的是 {@code null, null}，
     * 于是 {@link ReidLibraryLinkService#insert} 直接跳过向量写入 ——
     * 重识别的向量库永远是空的，「以图搜人」只能搜内存里的实时归并，
     * 进程一重启历史观测就再也搜不到了。</p>
     *
     * @param account     账号
     * @param group       分组名（已归一化）
     * @param trackId     轨迹 ID
     * @param cameraId    摄像头
     * @param sourceId    来源帧
     * @param imageData   原始图片字节
     * @param observation 观测
     * @param lat         纬度
     * @param lng         经度
     */
    private void persistObservation(String account, String group, String trackId, String cameraId,
                                    String sourceId, byte[] imageData,
                                    FaceReidPipeline.FaceObservation observation,
                                    BigDecimal lat, BigDecimal lng) {
        try {
            String vectorId = "reid-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            Map<String, Object> meta = new LinkedHashMap<>();
            // 分组随元数据一起落库：联动层据此写 sys_ai_reid_library_group 列。
            // 漏写会让这条观测掉进 NULL（默认组），而它的向量在**本分组**的索引里 ——
            // 检索命中后按分组反查轨迹就查不到，表现为「搜得到、列表里没有」。
            meta.put("group", group);
            meta.put("sourceId", sourceId);
            meta.put("score", observation.score());
            meta.put("matched", observation.matched());
            meta.put("confidence", observation.box().confidence());
            meta.put("box", Map.of(
                    "x", observation.box().x(),
                    "y", observation.box().y(),
                    "width", observation.box().width(),
                    "height", observation.box().height()));
            // 提取特征供向量库写入：DB 先行、向量其次（与 FaceLibraryLinkService 同口径）
            float[] feature = extractFeature(imageData, observation);
            boolean ok = library.insert(account, vectorId, trackId, cameraId,
                    cameraId + "@" + sourceId, meta, null, lat, lng,
                    reidStores.currentVectorStorage(group), feature);
            if (!ok) {
                log.warn("[Reid] 观测落库失败（观测结果仍有效）: user={}, group={}, track={}",
                        account, group, trackId);
                return;
            }
            // 向量已进库 → 记进本分组快照，供进程重启后重建索引。
            // 不在这里重复写向量库：写入与「失败则回滚 DB 行」的原子性由上面的 insert 保证，
            // 这里只补快照这一半（否则重启后快照为空，reload 会把向量索引清空 —— 历史观测全搜不到）。
            reidStores.remember(group, vectorId, feature);
        } catch (Exception ex) {
            log.warn("[Reid] 观测落库异常（观测结果仍有效）: user={}, track={} -> {}",
                    account, trackId, ex.getMessage());
        }
    }

    /**
     * 取观测对应的特征向量。
     *
     * @param imageData   原始图片字节
     * @param observation 观测
     * @return 特征向量；提取失败返回 {@code null}
     */
    private float[] extractFeature(byte[] imageData, FaceReidPipeline.FaceObservation observation) {
        if (imageData == null || imageData.length == 0) {
            return null;
        }
        FeatureExtractor extractor = clientManager.currentFeatureExtractor();
        if (extractor == null) {
            return null;
        }
        try {
            byte[] face = ImageCropUtils.crop(imageData, observation.box());
            if (face == null || face.length == 0) {
                return null;
            }
            return extractor.extract(face);
        } catch (Exception ex) {
            log.debug("[Reid] 提取观测特征失败: {}", ex.getMessage());
            return null;
        }
    }

    /**
     * 登记（加入重识别底库）：检测人脸 → 裁剪 → 提特征 → 写底库。
     *
     * <p>把「观测来的临时人」变成底库里的正式行：登记后它将直接出现在底库列表里，
     * 并能被「以图搜人」按向量搜到。</p>
     *
     * <p><b>只接受单人脸</b>：多于 1 张人脸时拒绝并提示裁剪后重试，避免登记了中间人
     * 而用户毫不知情。</p>
     *
     * <p>特征管线与 {@link FaceStores#enrollFromImage(String, String, byte[], Map)} 同路径
     * （detect → crop → liveness → align → feature），只把落点换到重识别底库连动层
     * （{@code sys_ai_reid_library} + 重识别向量库）—— 检测/特征模型由 {@link ReidClientManager}
     * 共享人脸组（face）加载，模型不重复加载。</p>
     *
     * @param group     分组名（空值按默认组 {@code default}）
     * @param id        人员标识（底库行的向量标识）
     * @param imageData 图片字节
     * @param metadata  元数据（可含 `name` 展示名 / `cameraId` 摄像头 / `lat` `lng` 经纬度）
     * @return 是否登记成功
     */
    @Override
    public boolean enroll(String group, String id, byte[] imageData, Map<String, Object> metadata) {
        String account = AiUserContext.requireUserId();
        String resolvedGroup = AiLibraryGroups.normalize(group);

        FacePipeline detector = clientManager.currentLowThresholdDetector();
        if (detector == null) {
            throw new IllegalStateException("人脸重识别能力未启用：请在设置中心配置人脸组的检测模型与特征模型");
        }
        // 先取特征再写底库：只有先取出特征，才能写底库行与向量（与 FaceStores.enrollFromImage 同路径）
        FaceFeaturePipelineResult result = detector.extractFeatureWithMeta(imageData);
        if (result == null || result.feature() == null || result.feature().length == 0) {
            log.warn("[Reid] 登记失败：未提取到人脸特征: id={}", id);
            return false;
        }
        if (result.faceCount() > 1) {
            throw new MultiFaceEnrollException(result.faceCount());
        }
        float[] feature = result.feature();

        // 元数据归一化（以已解析的分组为准）与展示字段
        Map<String, Object> meta = metadata == null
                ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata);
        meta.put("group", resolvedGroup);
        // 没传展示名就用标识符兜底，否则列表里没有名
        String name = strOf(meta, "name");
        if (name == null) {
            name = id;
        }
        // 没传摄像头就按当前组的配置走默认值
        String cameraId = strOf(meta, "cameraId");
        if (StringUtils.isBlank(cameraId)) {
            cameraId = clientManager.defaultCameraId();
        }
        BigDecimal lat = numberOf(meta.get("lat"));
        BigDecimal lng = numberOf(meta.get("lng"));

        // 先写 DB 行与向量（向量失败则回滚 DB 行，不留下半拉数据）
        boolean ok = library.insert(account, id, null, cameraId, name, meta,
                base64Of(result.faceImage()), lat, lng,
                reidStores.currentVectorStorage(resolvedGroup), feature);
        if (!ok) {
            log.warn("[Reid] 登记落库失败: id={}", id);
            return false;
        }
        // 向量已入库 → 记进本分组快照，供进程启动后重建 jvector 索引
        reidStores.remember(resolvedGroup, id, feature);
        log.info("[Reid] 登记成功: id={} group={} cameraId={}", id, resolvedGroup, cameraId);
        return true;
    }

    @Override
    public List<ReidHit> search(List<String> groups, byte[] imageData, int topK) {
        String account = AiUserContext.requireUserId();
        List<String> wanted = AiLibraryGroups.parse(
                groups == null || groups.isEmpty() ? null : String.join(",", groups));
        // 内存实时归并只在**第一个分组**上跑：轨迹管理器是按 (账号,分组) 隔离的，
        // 多分组检索的语义是「在库里一起找」，实时的那点当次观测对跨组查询没有额外价值，
        // 而逐个分组都跑一遍归并会把延迟乘以组数。
        String primary = wanted.getFirst();
        FaceReidPipeline pipeline = pipelineFor(account, primary);
        if (pipeline == null) {
            throw new IllegalStateException("人脸重识别能力未启用：请在设置中心配置人脸组的检测模型与特征模型");
        }
        int limit = topK <= 0 ? settingReader.getInt(GROUP, "top_k", DEFAULT_TOP_K) : topK;
        double threshold = settingReader.getDouble(GROUP, "match_threshold", 0.5d);

        List<ReidHit> hits = new ArrayList<>();

        // ① 先做人脸识别：拿到「这个人是谁」。
        //
        // <p><b>为什么先做。</b>人脸识别用的是 face 分组自己的高标准检测阈值，
        // 命中就说明这张脸够清晰 —— 那它<b>本来就应该走识别这条路</b>，
        // 而不该被当成「模糊脸」丢给重识别。反过来，重识别那条路用的是低阈值，
        // 会把清晰的脸也检出来，两条路的框是重叠的。</p>
        //
        // <p>因此这里的分界不是「谁先跑」，而是<b>按清晰度决定谁负责回答</b>：
        // 清晰 → 人脸识别给名字；模糊 → 重识别给轨迹。两条路都跑，
        // 但用检测置信度分派，避免同一个人被两个答案重复回答。</p>
        FaceIdentity identity = identifyFace(account, wanted, imageData);
        boolean faceUsable = identity != null && identity.hit();

        if (faceUsable) {
            // 清晰人脸：人脸识别负责回答「这是谁」
            Track detail = identity.trackId() == null ? null : track(primary, identity.trackId());
            Track enriched = detail != null
                    ? new Track(detail.trackId(), detail.cameraIds(), detail.firstSeen(),
                            detail.lastSeen(), detail.observations(), detail.crossCamera(),
                            detail.lat(), detail.lng(), identity.name(), detail.thumbnail())
                    : new Track(identity.faceId(), List.of(), 0L, 0L, 0, false,
                            null, null, identity.name(), null);
            hits.add(new ReidHit(identity.faceId(), identity.score(), enriched,
                    MatchType.FACE.name(), identity.name()));
            return hits;
        }

        // ② 模糊人脸：重识别负责回答「这个人去过哪」。
        //
        // <p>先查<b>持久向量库</b>（跨重启可搜，且按分组隔离/跨组归并），
        // 再查<b>内存实时归并</b>（当次会话内最准）。两者互补：
        // 前者覆盖历史观测，后者覆盖刚发生还没落库的观测。</p>
        for (Vector vector : reidStores.search(wanted, queryFeature(imageData), limit)) {
            double similarity = vectorScore(vector);
            if (similarity < threshold) {
                continue;
            }
            String vectorId = vector.id();
            String trackId = library.trackIdOf(account, vectorId);
            if (trackId == null) {
                continue;
            }
            Track detail = track(primary, trackId);
            Track enriched = detail != null
                    ? new Track(detail.trackId(), detail.cameraIds(), detail.firstSeen(),
                            detail.lastSeen(), detail.observations(), detail.crossCamera(),
                            detail.lat(), detail.lng(), detail.faceName(), detail.thumbnail())
                    : new Track(trackId, List.of(), 0L, 0L, 0, false, null, null, null, null);
            hits.add(new ReidHit(trackId, similarity, enriched, MatchType.REID.name(), null));
        }

        for (PersonTrack.TrackMatch match : pipeline.search(imageData, limit)) {
            if (match.similarity() < threshold) {
                continue;
            }
            // 相似度来自实时归并（内存），轨迹详情来自底库（持久）——
            // 若该轨迹尚未落库（刚落库失败），详情为 null，用 trackId 兜底展示
            Track detail = track(primary, match.summary().trackId());
            Track enriched = detail != null
                    ? new Track(detail.trackId(), detail.cameraIds(), detail.firstSeen(),
                            detail.lastSeen(), detail.observations(), detail.crossCamera(),
                            detail.lat(), detail.lng(), detail.faceName(), detail.thumbnail())
                    : new Track(match.summary().trackId(), match.summary().cameraIds(),
                            match.summary().firstSeen(), match.summary().lastSeen(),
                            match.summary().observations(), match.summary().crossCamera(),
                            null, null, null, null);
            hits.add(new ReidHit(match.summary().trackId(), match.similarity(), enriched,
                    MatchType.REID.name(), null));
        }
        return hits;
    }

    /**
     * 人脸识别：1:N 定库检索，回答「这个人是谁」。
     *
     * @param account   账号
     * @param groups    人脸库分组列表
     * @param imageData 图片字节
     * @return 识别结果；未命中或不可用返回 {@code null}
     */
    private FaceIdentity identifyFace(String account, List<String> groups, byte[] imageData) {
        try {
            FacePipeline pipeline = clientManager.currentLowThresholdDetector();
            if (pipeline == null) {
                return null;
            }
            // 先用低阈值检测出所有脸，取置信度最高的那张（最清晰的那张）
            List<PredictRectangle> boxes = pipeline.detectBoxes(imageData);
            if (boxes == null || boxes.isEmpty()) {
                return null;
            }
            PredictRectangle best = null;
            for (PredictRectangle box : boxes) {
                if (best == null || box.confidence() > best.confidence()) {
                    best = box;
                }
            }
            if (best == null || best.confidence() < clientManager.faceThreshold()) {
                // 最清晰的那张都不过分流线 → 整张图走重识别
                return null;
            }
            byte[] face = ImageCropUtils.crop(imageData, best);
            if (face == null || face.length == 0) {
                return null;
            }
            FeatureExtractor extractor = clientManager.currentFeatureExtractor();
            if (extractor == null) {
                return null;
            }
            float[] feature = extractor.extract(face);
            if (feature == null || feature.length == 0) {
                return null;
            }
            // 1:N 定库检索：命中即「这是谁」。按请求的分组走，不越组去认人。
            List<FaceSearchHit> faceHits = faceStores.searchHits(groups, feature, 1);
            if (faceHits == null || faceHits.isEmpty()) {
                return null;
            }
            FaceSearchHit hit = faceHits.getFirst();
            double score = hit.score();
            double matchThreshold = settingReader.getDouble("face", "match_threshold", 0.5d);
            if (score < matchThreshold) {
                return null;
            }
            String name = resolveFaceName(account, hit);
            return new FaceIdentity(name == null ? hit.id() : name, hit.id(), score, null);
        } catch (Exception ex) {
            log.warn("[Reid] 人脸识别异常: {}", ex.getMessage());
            return null;
        }
    }

    /**
     * 从人脸库档案里取展示名。
     *
     * @param account 账号
     * @param hit     检索命中
     * @return 姓名；取不到返回 {@code null}
     */
    private String resolveFaceName(String account, FaceSearchHit hit) {
        Map<String, Object> metadata = hit.metadata();
        if (metadata != null) {
            Object name = metadata.get("name");
            if (name != null && !String.valueOf(name).isBlank()) {
                return String.valueOf(name);
            }
        }
        // 元数据里没有名字时回查 DB 档案
        try {
            SysAiFaceLibrary row = faceLibrary.find(account, hit.id());
            if (row != null && row.getName() != null && !row.getName().isBlank()) {
                return row.getName();
            }
        } catch (Exception ex) {
            log.debug("[Reid] 回查人脸库档案失败: id={} -> {}", hit.id(), ex.getMessage());
        }
        return null;
    }

    /**
     * 取查询图的特征向量（供向量库检索）。
     *
     * @param imageData 图片字节
     * @return 特征向量；取不到返回 {@code null}
     */
    private float[] queryFeature(byte[] imageData) {
        if (imageData == null || imageData.length == 0) {
            return null;
        }
        try {
            FacePipeline pipeline = clientManager.currentLowThresholdDetector();
            FeatureExtractor extractor = clientManager.currentFeatureExtractor();
            if (pipeline == null || extractor == null) {
                return null;
            }
            List<PredictRectangle> boxes = pipeline.detectBoxes(imageData);
            if (boxes == null || boxes.isEmpty()) {
                return null;
            }
            PredictRectangle best = null;
            for (PredictRectangle box : boxes) {
                if (best == null || box.confidence() > best.confidence()) {
                    best = box;
                }
            }
            byte[] face = best == null ? null : ImageCropUtils.crop(imageData, best);
            if (face == null || face.length == 0) {
                return null;
            }
            return extractor.extract(face);
        } catch (Exception ex) {
            log.debug("[Reid] 提取查询特征失败: {}", ex.getMessage());
            return null;
        }
    }

    /**
     * 取向量检索命中的相似度。
     *
     * @param vector 命中向量
     * @return 相似度
     */
    private static double vectorScore(Vector vector) {
        if (vector == null || vector.metadata() == null) {
            return 0d;
        }
        Object score = vector.metadata().get("score");
        if (score instanceof Number n) {
            return n.doubleValue();
        }
        return 0d;
    }

    /**
     * 取元数据里的字符串字段。
     *
     * @param metadata 元数据；可为空
     * @param key      键
     * @return 字符串值；不存在返回 {@code null}
     */
    private static String strOf(Map<String, Object> metadata, String key) {
        if (metadata == null) {
            return null;
        }
        Object value = metadata.get(key);
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 取元数据里的数值字段（经纬度用）。
     *
     * @param value 原始值（Integer/Long/Double 或字符串）
     * @return 数值；不支持返回 {@code null}
     */
    private static BigDecimal numberOf(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number n) {
            return new BigDecimal(String.valueOf(n));
        }
        String s = String.valueOf(value).trim();
        if (s.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(s);
        } catch (Exception ex) {
            log.debug("[Reid] 数值转换失败: {} -> {}", value, ex.getMessage());
            return null;
        }
    }

    /**
     * 图片字节转 base64（缩略图入库用）。
     *
     * @param data 图片字节；空返回 {@code null}
     * @return base64 字符串
     */
    private static String base64Of(byte[] data) {
        return data == null || data.length == 0 ? null : Base64.getEncoder().encodeToString(data);
    }

    @Override
    public List<Track> tracks(String group, String cameraId, boolean crossOnly, int limit) {
        String account = AiUserContext.requireUserId();
        String resolvedGroup = AiLibraryGroups.normalize(group);
        List<Track> out = new ArrayList<>();
        for (ReidLibraryLinkService.Track track
                : library.aggregateTracks(account, List.of(resolvedGroup), cameraId, crossOnly, limit)) {
            out.add(toTrack(track));
        }
        return out;
    }

    @Override
    public Track track(String group, String trackId) {
        String account = AiUserContext.requireUserId();
        String resolvedGroup = AiLibraryGroups.normalize(group);
        ReidLibraryLinkService.Track track = library.track(account, List.of(resolvedGroup), trackId);
        return track == null ? null : toTrack(track);
    }

    @Override
    public int trackCount(String group) {
        String account = AiUserContext.requireUserId();
        String resolvedGroup = AiLibraryGroups.normalize(group);
        return library.trackCount(account, List.of(resolvedGroup));
    }

    @Override
    public int clear(String group) {
        String account = AiUserContext.requireUserId();
        String resolvedGroup = AiLibraryGroups.normalize(group);
        int count = library.trackCount(account, List.of(resolvedGroup));
        // 底库行与向量一起清：只清一边会留下"认得出但列表里没有"的幽灵条目
        library.clear(account, List.of(resolvedGroup));
        reidStores.clear(resolvedGroup);
        PersonTrack track = tracks.get(trackKey(account, resolvedGroup));
        if (track != null) {
            track.clear();
        }
        log.info("[Reid] 账号 '{}' 分组 '{}' 清空重识别底库：{} 条轨迹", account, resolvedGroup, count);
        return count;
    }

    @Override
    public List<String> groups() {
        String account = AiUserContext.requireUserId();
        // listMine 传空分组列表 = 不按分组过滤，拿到该账号全部观测的分组值
        java.util.Set<String> names = new java.util.TreeSet<>();
        // 即使一条数据都没有，也该有一个可选项，而不是空下拉框
        names.add(AiLibraryGroups.DEFAULT);
        for (Map<String, Object> row : library.listMine(account, List.of())) {
            Object g = row.get("group");
            if (g != null && !String.valueOf(g).isBlank()) {
                names.add(String.valueOf(g));
            }
        }
        return new ArrayList<>(names);
    }

    @Override
    public boolean available() {
        return clientManager.available();
    }

    /**
     * 人脸识别结果。
     *
     * @param name    展示名（人脸库档案里的姓名）
     * @param faceId  人脸库条目 ID
     * @param score   相似度
     * @param trackId 关联的轨迹 ID（可空）
     */
    private record FaceIdentity(String name, String faceId, double score, String trackId) {

        /**
         * 是否命中。
         *
         * @return 命中返回 true
         */
        boolean hit() {
            return name != null;
        }
    }

    /**
     * 底库轨迹视图 → 服务层轨迹视图。
     *
     * @param track 底库汇聚出的轨迹
     * @return 服务层轨迹
     */
    private static Track toTrack(ReidLibraryLinkService.Track track) {
        return new Track(track.trackId(), track.cameraIds(), track.firstSeen(), track.lastSeen(),
                track.observations(), track.crossCamera(), track.lat(), track.lng(),
                track.faceName(), track.thumbnail());
    }

    /**
     * 取（必要时创建）某账号某分组的重识别管线。
     *
     * @param account 账号标识
     * @param group   分组名（已归一化）
     * @return 管线；能力未启用返回 {@code null}
     */
    private FaceReidPipeline pipelineFor(String account, String group) {
        FaceReidPipeline existing = pipelines.get(trackKey(account, group));
        if (existing != null) {
            return existing;
        }
        PersonTrack accountTrack = trackFor(account, group);
        if (accountTrack == null) {
            return null;
        }
        FaceReidPipeline reference = clientManager.currentPipeline();
        if (reference == null) {
            return null;
        }
        // 检测/特征模型是共享单例里的重对象，这里只为每个 (账号,分组) 建一个轻量包装
        FaceReidPipeline built = new FaceReidPipeline(
                reference.detector(), clientManager.currentFeatureExtractor(), accountTrack);
        FaceReidPipeline previous = pipelines.putIfAbsent(trackKey(account, group), built);
        return previous == null ? built : previous;
    }

    /**
     * 取（必要时创建）某账号某分组的实时轨迹管理器。
     *
     * <p><b>每个 (账号,分组) 一份</b>，不是共用管理器那一份 —— 共用会让 A 账号的画面命中
     * B 账号的轨迹（数据越权 + 认错人），也让「访客」组的人并进「员工」组的轨迹里。</p>
     *
     * @param account 账号标识
     * @param group   分组名（已归一化）
     * @return 轨迹管理器；能力未启用返回 {@code null}
     */
    private PersonTrack trackFor(String account, String group) {
        PersonTrack existing = tracks.get(trackKey(account, group));
        if (existing != null) {
            return existing;
        }
        if (!clientManager.available()) {
            return null;
        }
        PersonTrack built = clientManager.newTrack();
        PersonTrack previous = tracks.putIfAbsent(trackKey(account, group), built);
        return previous == null ? built : previous;
    }

    /**
     * 「账号 \\u0000 分组」复合键。
     *
     * <p>分组是隔离的第一层，轨迹管理器与管线都按它隔离 ——
     * 不进键就等于把所有分组并成一个。</p>
     *
     * @param account 账号标识
     * @param group   分组名（已归一化）
     * @return 复合键
     */
    private static String trackKey(String account, String group) {
        return account + "\u0000" + group;
    }
}
