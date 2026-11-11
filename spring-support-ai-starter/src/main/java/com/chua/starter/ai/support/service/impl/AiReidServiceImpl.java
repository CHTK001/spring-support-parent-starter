// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
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
        int invalidFaces = 0;
        List<String> trackIds = new ArrayList<>();
        List<FaceBox> faces = new ArrayList<>();
        for (FaceReidPipeline.FaceObservation observation : result.observations()) {
            String trackId = observation.trackId();
            // 三档分流（判据是检测置信度/质量，不是 1:N 相似度）：
            //   ≥ face_threshold                → 人脸识别（1:N 认人）
            //   ≥ reid_threshold 且 < face_threshold → 重识别（跨帧轨迹）
            //   < reid_threshold                → 不合规：明确提示，不落库、不计轨迹
            float confidence = observation.box().confidence();
            String decision = decide(confidence);
            boolean invalidFace = MatchType.INVALID.name().equals(decision);
            String reason = invalidFace ? REASON_INVALID : null;
            // FACE 档要回答「这是谁」：把这张脸送一次 1:N，命中的姓名一并带出。
            // REID 档只回答「去过哪」，不查人脸库；INVALID 档连结果行都不产生。
            String faceName = null;
            if (MatchType.FACE.name().equals(decision)) {
                FaceIdentity identity = identifyFace(account, List.of(resolvedGroup),
                        imageData, observation.box());
                if (identity != null && identity.hit()) {
                    faceName = identity.name();
                }
            }
            // 叠框需要每张脸的框与归属轨迹，逐条带出（前端据此在图上画框并标注轨迹）
            faces.add(new FaceBox(
                    observation.box().x(), observation.box().y(),
                    observation.box().width(), observation.box().height(),
                    trackId, observation.score(), observation.matched(),
                    confidence, decision, faceName, reason));
            if (invalidFace) {
                // 不合规不是「结果行」：不落库、不计入轨迹，也不参与命中/新建统计
                invalidFaces++;
                continue;
            }
            if (observation.matched()) {
                matched++;
            } else {
                created++;
            }
            if (MatchType.FACE.name().equals(decision)) {
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
                    imageData, observation, decision, lat, lng);
        }
        log.info("[Reid] 账号 '{}' 分组 '{}' 摄像头 '{}' 观测到 {} 张脸（命中 {} / 新建 {}；清晰走识别 {} / 模糊走重识别 {} / 不合规 {}）",
                account, resolvedGroup, resolvedCamera, result.observations().size(),
                matched, created, faceHits, reidHits, invalidFaces);
        return new ObserveResult(resolvedCamera, resolvedSource,
                result.observations().size(), trackIds, matched, created, faceHits, reidHits,
                invalidFaces, faces);
    }

    /**
     * 把一次人脸观测写入重识别底库。
     *
     * <p>失败只记 WARN 不抛：观测本身已经成功（归并结果已返回给调用方），
     * 落库失败不应让用户看到"观测失败"——但必须留日志，否则轨迹缺段无从排查。</p>
     *
     * <p><b>一条记录两个特征值。</b>观测和登记入库是同一个底库：同一次观测同时写入
     * 人脸特征（{@code reid-face} 索引，回答「这是谁」）与 ReID 特征
     * （{@code reid} 索引，回答「去过哪」），两套共用同一向量标识。
     * 只写 ReID 特征时 FACE 档 1:N 永远查不到这条观测。</p>
     *
     * @param account     账号
     * @param group       分组名（已归一化）
     * @param trackId     轨迹 ID
     * @param cameraId    摄像头
     * @param sourceId    来源帧
     * @param imageData   原始图片字节
     * @param observation 观测
     * @param matchType   本次分流结论（{@code FACE} / {@code REID}）
     * @param lat         纬度
     * @param lng         经度
     */
    private void persistObservation(String account, String group, String trackId, String cameraId,
                                    String sourceId, byte[] imageData,
                                    FaceReidPipeline.FaceObservation observation, String matchType,
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
            // 分流结论随行持久化：底库一行两个特征，检索时据此决定主用哪个
            meta.put("matchType", matchType);
            // 一条记录**两个特征值**（人脸特征 + ReID 特征），与登记入库同口径。
            // 只写 ReID 特征会让这条观测在 reid-face 索引里缺席 ——
            // FACE 档 1:N（reidStores.searchFace）永远查不到它，表现为 hits=0。
            byte[] face = ImageCropUtils.crop(imageData, observation.box());
            float[] reidFeature = extractWith(clientManager.currentFeatureExtractor(), face);
            float[] faceFeature = extractWith(clientManager.currentFaceFeatureExtractor(), face);
            boolean ok = library.insertDual(account, vectorId, vectorId, trackId, cameraId,
                    cameraId + "@" + sourceId, meta, base64Of(face), lat, lng,
                    reidStores.currentVectorStorage(group), reidFeature,
                    reidStores.currentFaceVectorStorage(group), faceFeature, matchType);
            if (!ok) {
                log.warn("[Reid] 观测落库失败（观测结果仍有效）: user={}, group={}, track={}",
                        account, group, trackId);
                return;
            }
            // 向量已进库 → 两套快照都记上，供进程重启后重建索引。
            // 不在这里重复写向量库：写入与「失败则回滚 DB 行」的原子性由上面的 insertDual 保证，
            // 这里只补快照这一半（否则重启后快照为空，reload 会把向量索引清空 —— 历史观测全搜不到）。
            reidStores.remember(group, vectorId, reidFeature);
            reidStores.rememberFace(group, vectorId, faceFeature);
        } catch (Exception ex) {
            log.warn("[Reid] 观测落库异常（观测结果仍有效）: user={}, track={} -> {}",
                    account, trackId, ex.getMessage());
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
        return enrollDetailed(group, id, imageData, metadata).success();
    }

    /**
     * 登记（底库入库），返回三档分流结论。
     *
     * <p><b>只接受单人图。</b>多脸直接抛 {@link MultiFaceEnrollException}，
     * 由调用方提示「请裁剪后重试」—— 底库一行对应一个人，
     * 悄悄挑一张脸入库会让用户以为登记的是另一个人。</p>
     *
     * <p><b>不合规不入库。</b>唯一那张脸的检测置信度低于 ReID 阈值时，
     * 这条图片不足以支撑任何检索，回 {@link AiReidService#REASON_INVALID} 且不落行。</p>
     *
     * <p><b>两个特征一次算完、一次写入。</b>达标后同时取人脸特征（{@code feature_model}）
     * 与 ReID 特征（{@code reid_feature_model}），分别写进两个索引；
     * 任一失败即整条回滚，不留「认得出、搜不到」的半拉数据。</p>
     *
     * @param group     分组名（空值按默认组 {@code default}）
     * @param id        人员标识（底库行的向量标识，两套特征共用同一标识）
     * @param imageData 图片字节
     * @param metadata  元数据（可含 `name` 展示名 / `cameraId` 摄像头 / `lat` `lng` 经纬度）
     * @return 登记结果
     */
    @Override
    public EnrollResult enrollDetailed(String group, String id, byte[] imageData,
                                       Map<String, Object> metadata) {
        String account = AiUserContext.requireUserId();
        String resolvedGroup = AiLibraryGroups.normalize(group);

        FacePipeline detector = clientManager.currentLowThresholdDetector();
        if (detector == null) {
            throw new IllegalStateException("人脸重识别能力未启用：请在设置中心配置人脸组的检测模型与特征模型");
        }
        // 低阈值检测：只有先看到框，才能区分「不合规」与「多脸」两种情况
        FaceFeaturePipelineResult result = detector.extractFeatureWithMeta(imageData);
        if (result == null || result.box() == null) {
            log.warn("[Reid] 登记失败：未检测到人脸: id={}", id);
            return new EnrollResult(false, null, "未检测到人脸", 0, id, false);
        }
        if (result.faceCount() > 1) {
            throw new MultiFaceEnrollException(result.faceCount());
        }
        float confidence = result.box().confidence();
        String decision = decide(confidence);
        if (MatchType.INVALID.name().equals(decision)) {
            log.info("[Reid] 登记被拒（图片不合规）: id={} confidence={}", id, confidence);
            return new EnrollResult(false, decision, REASON_INVALID, result.faceCount(), id, false);
        }

        // 一次裁图、两套模型各算一遍：人脸特征回答「这是谁」，ReID 特征回答「去过哪」
        byte[] face = result.faceImage();
        if (face == null || face.length == 0) {
            byte[] cropped = ImageCropUtils.crop(imageData, result.box());
            face = cropped;
        }
        if (face == null || face.length == 0) {
            log.warn("[Reid] 登记失败：人脸裁剪为空: id={}", id);
            return new EnrollResult(false, decision, "人脸裁剪失败", result.faceCount(), id, false);
        }
        float[] faceFeature = extractWith(clientManager.currentFaceFeatureExtractor(), face);
        float[] reidFeature = extractWith(clientManager.currentFeatureExtractor(), face);
        if (faceFeature == null || faceFeature.length == 0
                || reidFeature == null || reidFeature.length == 0) {
            log.warn("[Reid] 登记失败：特征提取为空: id={} face={} reid={}", id,
                    faceFeature == null ? 0 : faceFeature.length,
                    reidFeature == null ? 0 : reidFeature.length);
            return new EnrollResult(false, decision, "特征提取失败", result.faceCount(), id, false);
        }

        // 元数据归一化（以已解析的分组为准）与展示字段
        Map<String, Object> meta = metadata == null
                ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata);
        meta.put("group", resolvedGroup);
        // 分流结论随行持久化：检索时据此决定这行主用哪个特征
        meta.put("matchType", decision);
        meta.put("faceVectorId", id);
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

        // 先写 DB 行，再写两套向量（任一失败则删行并回滚另一套）
        boolean ok = library.insertDual(account, id, id, null, cameraId, name, meta,
                base64Of(result.faceImage()), lat, lng,
                reidStores.currentVectorStorage(resolvedGroup), reidFeature,
                reidStores.currentFaceVectorStorage(resolvedGroup), faceFeature, decision);
        if (!ok) {
            log.warn("[Reid] 登记落库失败: id={}", id);
            return new EnrollResult(false, decision, "底库写入失败", result.faceCount(), id, false);
        }
        // 向量已入库 → 两套快照都记上，供进程启动后重建 jvector 索引
        reidStores.remember(resolvedGroup, id, reidFeature);
        reidStores.rememberFace(resolvedGroup, id, faceFeature);
        // 登记是用户手动触发的一次性操作：立刻落盘，别让它落在 5 秒节流窗口里
        // （否则「入库成功」之后 index.json 还是旧的，进程重启就丢这一条向量）
        try {
            reidStores.flush(resolvedGroup);
            reidStores.flushFace(resolvedGroup);
        } catch (Exception ex) {
            log.warn("[Reid] 登记后快照落盘失败（内存中仍可检索）: id={} -> {}", id, ex.getMessage());
        }
        log.info("[Reid] 登记成功: id={} group={} cameraId={} decision={} confidence={}",
                id, resolvedGroup, cameraId, decision, confidence);
        return new EnrollResult(true, decision, null, result.faceCount(), id, true);
    }

    /**
     * 用指定提取器取特征（失败只记日志、返回 {@code null}）。
     *
     * @param extractor 特征提取器（可空）
     * @param face      单脸图片
     * @return 特征；提取失败返回 {@code null}
     */
    private static float[] extractWith(FeatureExtractor extractor, byte[] face) {
        if (extractor == null || face == null || face.length == 0) {
            return null;
        }
        try {
            return extractor.extract(face);
        } catch (Exception ex) {
            log.warn("[Reid] 特征提取失败: {}", ex.getMessage());
            return null;
        }
    }

    /**
     * 逐脸检索：一张脸一行结果，多脸就是多行，不合并。
     *
     * <p><b>为什么逐脸。</b>一张合影里的脸清晰度可能天差地别：一张够清晰可以认人，
     * 另一张糊到不合规。按整图检索只能给出一个答案，用户没法知道
     * 「这条结果对应图里哪张脸」「为什么那张脸没有结果」。逐脸之后，
     * 每张脸按自己的检测置信度走三档，结论互不干扰。</p>
     *
     * <p><b>不降级。</b>FACE 档在人脸库里没认出人，仍是 FACE 档的结果（命中为空），
     * 不会退回去用 ReID 凑一个答案 —— 两条管线回答的是不同问题，
     * 「库里没这个人」与「这个人去过哪」不能互相冒充。</p>
     *
     * @param groups    分组名列表
     * @param imageData 图片字节
     * @param topK      每张脸返回条数
     * @return 逐脸结果（按检测框在图中出现顺序，从 1 开始编号）
     */
    @Override
    public List<FaceSearchResult> searchFaces(List<String> groups, byte[] imageData, int topK) {
        if (imageData == null || imageData.length == 0) {
            return List.of();
        }
        String account = AiUserContext.requireUserId();
        List<String> wanted = AiLibraryGroups.parse(
                groups == null || groups.isEmpty() ? null : String.join(",", groups));
        String primary = wanted.getFirst();
        FaceReidPipeline pipeline = pipelineFor(account, primary);
        FacePipeline detector = clientManager.currentLowThresholdDetector();
        if (pipeline == null || detector == null) {
            throw new IllegalStateException("人脸重识别能力未启用：请在设置中心配置人脸组的检测模型与特征模型");
        }
        // 检测一律走低阈值管线：太早丢框就判不出「不合规」这一档，也看不见模糊人脸
        List<PredictRectangle> boxes = detector.detectBoxes(imageData);
        if (boxes == null || boxes.isEmpty()) {
            return List.of();
        }
        int limit = topK <= 0 ? settingReader.getInt(GROUP, "top_k", DEFAULT_TOP_K) : topK;
        double faceMatchThreshold = settingReader.getDouble(GROUP, "match_threshold", 0.5d);
        // 重识别 1:N 检索用自己的阈值（reid_match_threshold），留空回落到人脸检索阈值
        double reidMatchThreshold = clientManager.reidMatchThreshold();

        List<FaceSearchResult> out = new ArrayList<>();
        int index = 0;
        for (PredictRectangle box : boxes) {
            index++;
            float confidence = box.confidence();
            String decision = decide(confidence);
            String reason = MatchType.INVALID.name().equals(decision) ? REASON_INVALID : null;
            List<ReidHit> hits = List.of();
            if (reason == null) {
                byte[] face = ImageCropUtils.crop(imageData, box);
                if (face != null && face.length > 0) {
                    hits = MatchType.FACE.name().equals(decision)
                            ? faceIdentityHits(account, wanted, primary, face, faceMatchThreshold, limit)
                            : reidIdentityHits(account, wanted, primary, pipeline, face,
                                    reidMatchThreshold, limit);
                }
            }
            FaceBox faceBox = new FaceBox(box.x(), box.y(), box.width(), box.height(),
                    null, 0d, false, confidence, decision, null, reason);
            out.add(new FaceSearchResult(index, faceBox, confidence, decision, reason, hits));
        }
        return out;
    }

    /**
     * 按检测置信度落档：这是三档分流唯一的判据。
     *
     * <p>用「门槛」而不是「两条都跑完再合并」：同一张脸只由达标的那条管线回答，
     * 否则同一个人的识别结果与轨迹命中会重复出现两份。</p>
     *
     * @param confidence 检测置信度
     * @return 分流类型名（{@code FACE} / {@code REID} / {@code INVALID}）
     */
    private String decide(float confidence) {
        if (confidence >= clientManager.faceThreshold()) {
            return MatchType.FACE.name();
        }
        if (confidence >= clientManager.reidThreshold()) {
            return MatchType.REID.name();
        }
        return MatchType.INVALID.name();
    }

    /**
     * FACE 档单脸检索：人脸特征 1:N 定库，回答「这是谁」。
     *
     * <p>用<b>人脸特征提取器</b>（{@code feature_model}），不是 ReID 提取器 ——
     * 底库的人脸特征列由前者产出，混用会让「只换了 ReID 模型」变成整库认不出人。</p>
     *
     * @param account   账号
     * @param groups    人脸库分组列表
     * @param primary   主分组（轨迹详情按它取）
     * @param faceImage 裁剪后的单脸图
     * @param threshold 人脸 1:N 检索阈值
     * @param limit     返回条数
     * @return 命中列表；未命中返回空列表（仍属 FACE 档结果）
     */
    private List<ReidHit> faceIdentityHits(String account, List<String> groups, String primary,
                                           byte[] faceImage, double threshold, int limit) {
        FeatureExtractor extractor = clientManager.currentFaceFeatureExtractor();
        if (extractor == null) {
            return List.of();
        }
        float[] feature;
        try {
            feature = extractor.extract(faceImage);
        } catch (Exception ex) {
            log.warn("[Reid] 人脸识别提特征失败: {}", ex.getMessage());
            return List.of();
        }
        if (feature == null || feature.length == 0) {
            return List.of();
        }
        // 查**重识别底库的人脸特征索引**（reid-face），不是独立的人脸库：
        // 登记时两个特征是一起写的，认人这一半必须落在同一条底库记录上。
        List<Vector> faceHits = reidStores.searchFace(groups, feature, limit);
        if (faceHits == null || faceHits.isEmpty()) {
            return List.of();
        }
        List<ReidHit> hits = new ArrayList<>();
        for (Vector hit : faceHits) {
            double similarity = vectorScore(hit);
            if (similarity < threshold) {
                continue;
            }
            String name = library.nameOf(account, hit.id());
            String trackId = library.trackIdOf(account, hit.id());
            Track detail = trackId == null ? null : track(primary, trackId);
            Track enriched = detail != null
                    ? new Track(detail.trackId(), detail.cameraIds(), detail.firstSeen(),
                            detail.lastSeen(), detail.observations(), detail.crossCamera(),
                            detail.lat(), detail.lng(), name, detail.thumbnail())
                    : new Track(trackId == null ? hit.id() : trackId, List.of(), 0L, 0L, 0, false,
                            null, null, name, null);
            hits.add(new ReidHit(hit.id(), similarity, enriched, MatchType.FACE.name(), name));
        }
        return hits;
    }

    /**
     * REID 档单脸检索：重识别特征查轨迹，回答「这个人去过哪」。
     *
     * <p>先查<b>持久向量库</b>（跨重启可搜、按分组隔离），再查<b>内存实时归并</b>
     * （当次会话内最准）。两者互补：前者覆盖历史观测，后者覆盖刚发生还没落库的观测。</p>
     *
     * @param account   账号
     * @param groups    分组名列表（向量库检索范围）
     * @param primary   主分组（轨迹详情按它取）
     * @param pipeline  该账号该分组的实时管线
     * @param faceImage 裁剪后的单脸图
     * @param threshold 重识别 1:N 检索阈值
     * @param limit     返回条数
     * @return 命中列表
     */
    private List<ReidHit> reidIdentityHits(String account, List<String> groups, String primary,
                                           FaceReidPipeline pipeline, byte[] faceImage,
                                           double threshold, int limit) {
        FeatureExtractor extractor = clientManager.currentFeatureExtractor();
        if (extractor == null) {
            return List.of();
        }
        float[] feature;
        try {
            feature = extractor.extract(faceImage);
        } catch (Exception ex) {
            log.warn("[Reid] 重识别提特征失败: {}", ex.getMessage());
            return List.of();
        }
        if (feature == null || feature.length == 0) {
            return List.of();
        }
        List<ReidHit> hits = new ArrayList<>();
        for (Vector vector : reidStores.search(groups, feature, limit)) {
            double similarity = vectorScore(vector);
            if (similarity < threshold) {
                continue;
            }
            String trackId = library.trackIdOf(account, vector.id());
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
        for (PersonTrack.TrackMatch match : pipeline.search(feature, limit)) {
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
     * 人脸识别：对指定的单个检测框做 1:N 定库检索，回答「这个人是谁」。
     *
     * <p><b>逐脸调用。</b>框由检测阶段传进来，不在这里重新检一遍 ——
     * 重检会用另一个阈值选出另一张脸，导致叠框与识别结果对不上。
     * 用<b>人脸特征提取器</b>（{@code feature_model}），不是 ReID 提取器。</p>
     *
     * @param account   账号
     * @param groups    人脸库分组列表
     * @param imageData 原图
     * @param box       该脸的检测框
     * @return 识别结果；未命中或不可用返回 {@code null}
     */
    private FaceIdentity identifyFace(String account, List<String> groups,
                                      byte[] imageData, PredictRectangle box) {
        try {
            if (box == null) {
                return null;
            }
            byte[] face = ImageCropUtils.crop(imageData, box);
            if (face == null || face.length == 0) {
                return null;
            }
            FeatureExtractor extractor = clientManager.currentFaceFeatureExtractor();
            if (extractor == null) {
                return null;
            }
            float[] feature = extractor.extract(face);
            if (feature == null || feature.length == 0) {
                return null;
            }
            // 1:N 定库检索：命中即「这是谁」。按请求的分组走，不越组去认人。
            // 查重识别底库的人脸特征索引 —— 与登记写入的是同一套，换 ReID 模型不影响认人。
            List<Vector> faceHits = reidStores.searchFace(groups, feature, 1);
            if (faceHits == null || faceHits.isEmpty()) {
                return null;
            }
            Vector hit = faceHits.getFirst();
            double score = vectorScore(hit);
            double matchThreshold = settingReader.getDouble("face", "match_threshold", 0.5d);
            if (score < matchThreshold) {
                return null;
            }
            String name = library.nameOf(account, hit.id());
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
    public boolean delete(String group, String id) {
        if (id == null || id.isBlank()) {
            return false;
        }
        String account = AiUserContext.requireUserId();
        String resolvedGroup = AiLibraryGroups.normalize(group);
        boolean removed = library.deleteMeta(account, id);
        // 元数据与两套向量一起删：任何一边残留都会让列表与检索对不上
        reidStores.remove(resolvedGroup, id);
        reidStores.removeFace(resolvedGroup, id);
        log.info("[Reid] 删除底库记录: user={} group={} id={} removed={}",
                account, resolvedGroup, id, removed);
        return removed;
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