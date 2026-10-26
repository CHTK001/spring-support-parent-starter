package com.chua.starter.ai.support.service.impl;

import com.chua.common.support.utils.StringUtils;
import com.chua.deeplearning.support.reid.FaceReidPipeline;
import com.chua.deeplearning.support.reid.PersonTrack;
import com.chua.starter.ai.support.configuration.client.GlobalSettingReader;
import com.chua.starter.ai.support.configuration.face.FaceSettingsChangedEvent;
import com.chua.starter.ai.support.configuration.reid.ReidClientManager;
import com.chua.starter.ai.support.service.AiReidService;
import com.chua.starter.ai.support.service.ReidLibraryLinkService;
import com.chua.starter.ai.support.util.AiUserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI 人脸重识别服务实现。
 *
 * <p><b>两套状态，各司其职：</b></p>
 * <ul>
 *   <li><b>实时归并</b>（{@link PersonTrack}，按账号一份）：判断「这一帧的这张脸
 *       与刚才见过的哪张是同一人」。它要的是低延迟的相似度比较，因此留在内存。</li>
 *   <li><b>持久底库</b>（{@code sys_ai_reid_library}）：每次观测都落一行，
 *       轨迹由它按 {@code track_id} 汇聚而来。<b>列表/详情/删除都以底库为准</b> ——
 *       这样进程重启后轨迹还在，而不是"一重启全丢"。</li>
 * </ul>
 *
 * <p><b>为什么轨迹不缓存一份。</b>轨迹是底库的汇聚视图，缓存它就会有两份状态
 * （缓存里有一条已被删的观测、或漏掉新增的观测）。查询成本是一次分组，
 * 与"维护缓存一致性"的复杂度相比不值得省。</p>
 *
 * <p><b>模型共享、状态隔离</b>：检测与特征模型由 {@link ReidClientManager} 单例持有
 * （ONNX 模型不重复加载），轨迹管理器与底库数据按账号隔离。</p>
 *
 * @author CH
 * @since 4.0.0.43
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiReidServiceImpl implements AiReidService {

    /**
     * 配置分组。
     */
    private static final String GROUP = "reid";

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
     * 账号 → 该账号的实时轨迹管理器（仅用于相似度归并，不作为列表来源）。
     */
    private final Map<String, PersonTrack> tracks = new ConcurrentHashMap<>();

    /**
     * 账号 → 该账号的重识别管线（检测/特征共用全局模型）。
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
        log.info("[Reid] reid 分组配置变更，已重置 {} 个账号的实时归并状态（底库数据保留）", cleared);
    }

    @Override
    public ObserveResult observe(String cameraId, String sourceId, byte[] imageData,
                                 BigDecimal lat, BigDecimal lng) {
        String account = AiUserContext.requireUserId();
        FaceReidPipeline pipeline = pipelineFor(account);
        if (pipeline == null) {
            throw new IllegalStateException("人脸重识别能力未启用：请在设置中心配置 reid 分组的检测模型与特征模型");
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
        List<String> trackIds = new ArrayList<>();
        List<FaceBox> faces = new ArrayList<>();
        for (FaceReidPipeline.FaceObservation observation : result.observations()) {
            if (observation.matched()) {
                matched++;
            } else {
                created++;
            }
            String trackId = observation.trackId();
            // 叠框需要每张脸的框与归属轨迹，逐条带出（前端据此在图上画框并标注轨迹）
            faces.add(new FaceBox(
                    observation.box().x(), observation.box().y(),
                    observation.box().width(), observation.box().height(),
                    trackId, observation.score(), observation.matched()));
            if (trackId == null) {
                continue;
            }
            if (!trackIds.contains(trackId)) {
                trackIds.add(trackId);
            }
            // 每次观测都落库：底库是轨迹的唯一来源，漏写就等于轨迹少一段
            persistObservation(account, trackId, resolvedCamera, resolvedSource, observation, lat, lng);
        }
        log.info("[Reid] 账号 '{}' 摄像头 '{}' 观测到 {} 张脸（命中 {} / 新建 {}）",
                account, resolvedCamera, result.observations().size(), matched, created);
        return new ObserveResult(resolvedCamera, resolvedSource,
                result.observations().size(), trackIds, matched, created, faces);
    }

    /**
     * 把一次人脸观测写入重识别底库。
     *
     * <p>失败只记 WARN 不抛：观测本身已经成功（归并结果已返回给调用方），
     * 落库失败不应让用户看到"观测失败"——但必须留日志，否则轨迹缺段无从排查。</p>
     *
     * @param account     账号
     * @param trackId     轨迹 ID
     * @param cameraId    摄像头
     * @param sourceId    来源帧
     * @param observation 观测
     * @param lat         纬度
     * @param lng         经度
     */
    private void persistObservation(String account, String trackId, String cameraId, String sourceId,
                                    FaceReidPipeline.FaceObservation observation,
                                    BigDecimal lat, BigDecimal lng) {
        try {
            String vectorId = "reid-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("sourceId", sourceId);
            meta.put("score", observation.score());
            meta.put("matched", observation.matched());
            meta.put("box", Map.of(
                    "x", observation.box().x(),
                    "y", observation.box().y(),
                    "width", observation.box().width(),
                    "height", observation.box().height()));
            boolean ok = library.insert(account, vectorId, trackId, cameraId,
                    cameraId + "@" + sourceId, meta, null, lat, lng,
                    null, null);
            if (!ok) {
                log.warn("[Reid] 观测落库失败（观测结果仍有效）: user={}, track={}", account, trackId);
            }
        } catch (Exception ex) {
            log.warn("[Reid] 观测落库异常（观测结果仍有效）: user={}, track={} -> {}",
                    account, trackId, ex.getMessage());
        }
    }

    @Override
    public List<ReidHit> search(byte[] imageData, int topK) {
        String account = AiUserContext.requireUserId();
        FaceReidPipeline pipeline = pipelineFor(account);
        if (pipeline == null) {
            throw new IllegalStateException("人脸重识别能力未启用：请在设置中心配置 reid 分组的检测模型与特征模型");
        }
        int limit = topK <= 0 ? settingReader.getInt(GROUP, "top_k", DEFAULT_TOP_K) : topK;
        double threshold = settingReader.getDouble(GROUP, "match_threshold", 0.5d);

        List<ReidHit> hits = new ArrayList<>();
        for (PersonTrack.TrackMatch match : pipeline.search(imageData, limit)) {
            if (match.similarity() < threshold) {
                continue;
            }
            // 相似度来自实时归并（内存），轨迹详情来自底库（持久）——
            // 若该轨迹尚未落库（刚落库失败），详情为 null，用 trackId 兜底展示
            Track detail = track(match.summary().trackId());
            hits.add(new ReidHit(match.summary().trackId(), match.similarity(),
                    detail != null ? detail : new Track(match.summary().trackId(),
                            match.summary().cameraIds(), match.summary().firstSeen(),
                            match.summary().lastSeen(), match.summary().observations(),
                            match.summary().crossCamera(), null, null, null, null)));
        }
        return hits;
    }

    @Override
    public List<Track> tracks(String cameraId, boolean crossOnly, int limit) {
        String account = AiUserContext.requireUserId();
        List<Track> out = new ArrayList<>();
        for (ReidLibraryLinkService.Track track
                : library.aggregateTracks(account, cameraId, crossOnly, limit)) {
            out.add(toTrack(track));
        }
        return out;
    }

    @Override
    public Track track(String trackId) {
        String account = AiUserContext.requireUserId();
        ReidLibraryLinkService.Track track = library.track(account, trackId);
        return track == null ? null : toTrack(track);
    }

    @Override
    public int trackCount() {
        String account = AiUserContext.requireUserId();
        return library.trackCount(account);
    }

    @Override
    public int clear() {
        String account = AiUserContext.requireUserId();
        int count = library.trackCount(account);
        // 底库行与向量一起清：只清一边会留下"认得出但列表里没有"的幽灵条目
        library.clear(account);
        PersonTrack track = tracks.get(account);
        if (track != null) {
            track.clear();
        }
        log.info("[Reid] 账号 '{}' 清空重识别底库：{} 条轨迹", account, count);
        return count;
    }

    @Override
    public boolean available() {
        return clientManager.available();
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
     * 取（必要时创建）某账号的重识别管线。
     *
     * @param account 账号标识
     * @return 管线；能力未启用返回 {@code null}
     */
    private FaceReidPipeline pipelineFor(String account) {
        FaceReidPipeline existing = pipelines.get(account);
        if (existing != null) {
            return existing;
        }
        PersonTrack accountTrack = trackFor(account);
        if (accountTrack == null) {
            return null;
        }
        FaceReidPipeline reference = clientManager.currentPipeline();
        if (reference == null) {
            return null;
        }
        // 检测/特征模型是共享单例里的重对象，这里只为每个账号建一个轻量包装
        FaceReidPipeline built = new FaceReidPipeline(
                reference.detector(), clientManager.currentFeatureExtractor(), accountTrack);
        FaceReidPipeline previous = pipelines.putIfAbsent(account, built);
        return previous == null ? built : previous;
    }

    /**
     * 取（必要时创建）某账号的实时轨迹管理器。
     *
     * <p><b>每个账号一份</b>，不是共用管理器那一份 —— 共用会让 A 账号的画面命中
     * B 账号的轨迹（数据越权 + 认错人）。</p>
     *
     * @param account 账号标识
     * @return 轨迹管理器；能力未启用返回 {@code null}
     */
    private PersonTrack trackFor(String account) {
        PersonTrack existing = tracks.get(account);
        if (existing != null) {
            return existing;
        }
        if (!clientManager.available()) {
            return null;
        }
        PersonTrack built = clientManager.newTrack();
        PersonTrack previous = tracks.putIfAbsent(account, built);
        return previous == null ? built : previous;
    }
}
