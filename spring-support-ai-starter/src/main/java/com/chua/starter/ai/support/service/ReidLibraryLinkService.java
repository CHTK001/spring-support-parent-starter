package com.chua.starter.ai.support.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.common.support.vector.VectorStorage;
import com.chua.starter.ai.support.entity.SysAiReidLibrary;
import com.chua.starter.ai.support.mapper.SysAiReidLibraryMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 人脸重识别底库的「DB 元数据 ↔ 向量索引」联动层，并负责把观测<b>汇聚成跨帧轨迹</b>。
 *
 * <p><b>为什么要这一层。</b>与 {@link FaceLibraryLinkService} 同构：特征存 jvector、
 * 元数据存 MySQL，两者没有事务。区别在于重识别的<b>单位是「观测」而非「人」</b> ——
 * 同一个人会被拍很多次，每次一条记录，靠 {@code track_id} 串起来。</p>
 *
 * <p><b>轨迹为什么不另建表。</b>轨迹 = 按 {@code track_id} 分组、按时间排序的观测集合。
 * 若单独存一张轨迹表，那么「删一条观测要同步改轨迹」「轨迹删除要级联删观测」
 * 都要人工保证，两处状态迟早分叉。这里用一次分组查询得到轨迹，
 * 只有一份真相：<b>删掉底库行，就等于把这次观测从轨迹里移除</b>。</p>
 *
 * <p><b>经纬度。</b>非必填，随档案一起落库（DECIMAL 列），
 * <b>不参与向量、不改变特征存储</b>。它只描述「这次观测发生在哪」。</p>
 *
 * @since 4.0.0.43
 * @author CH
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReidLibraryLinkService {

    /**
     * 重识别底库映射器。
     */
    private final SysAiReidLibraryMapper mapper;

    /**
     * JSON 序列化器：元数据落 {@code meta} 列，读回时还原成 Map。
     */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 一条跨帧轨迹（由底库观测汇聚而成）。
     *
     * @param trackId      轨迹 ID
     * @param cameraIds    出现过的摄像头（去重，按首次出现顺序）
     * @param firstSeen    首次观测时间（毫秒）
     * @param lastSeen     最近观测时间（毫秒）
     * @param observations 观测次数
     * @param crossCamera  是否跨摄像头
     * @param lat          最近一次观测的纬度（可空）
     * @param lng          最近一次观测的经度（可空）
     * @param faceId       关联的人脸底库行 ID（可空）
     * @param faceName     关联的人脸姓名（可空）
     * @param thumbnail    最近一次观测的缩略图（可空）
     */
    public record Track(String trackId,
                        List<String> cameraIds,
                        long firstSeen,
                        long lastSeen,
                        int observations,
                        boolean crossCamera,
                        BigDecimal lat,
                        BigDecimal lng,
                        Integer faceId,
                        String faceName,
                        String thumbnail) {

        /**
         * 规范构造器：集合不可变。
         *
         * @param cameraIds 摄像头集合
         */
        public Track {
            cameraIds = cameraIds == null ? List.of() : List.copyOf(cameraIds);
        }
    }

    /**
     * 新增/更新一条观测：先写 DB，再写向量；向量失败则删掉刚写的 DB 行。
     *
     * <p>顺序与 {@link FaceLibraryLinkService#insert} 一致（DB 先行）：向量库没有事务，
     * 让它当先手失败时残局更难收拾。</p>
     *
     * @param userId    归属账号
     * @param vectorId  向量标识（与 jvector 中的 key 同名）
     * @param trackId   所属跨帧轨迹 ID；为空表示新建一条轨迹
     * @param cameraId  摄像头标识
     * @param name      展示名
     * @param metadata  其余档案（落 meta 列）
     * @param thumbnail 缩略图 base64（可空）
     * @param lat       纬度（可空）
     * @param lng       经度（可空）
     * @param storage   向量存储；{@code null} 表示向量能力未就绪
     * @param feature   特征向量；{@code null} 表示向量能力未就绪
     * @return 是否成功
     */
    public boolean insert(String userId, String vectorId, String trackId, String cameraId,
                          String name, Map<String, Object> metadata, String thumbnail,
                          BigDecimal lat, BigDecimal lng,
                          VectorStorage storage, float[] feature) {
        SysAiReidLibrary row = new SysAiReidLibrary();
        row.setUserId(userId);
        row.setVectorId(vectorId);
        row.setTrackId(trackId);
        row.setCameraId(cameraId);
        row.setName(name);
        row.setThumbnail(thumbnail);
        row.setMeta(writeMeta(metadata));
        row.setGroupName(strOf(metadata, "group"));
        row.setLat(lat);
        row.setLng(lng);
        row.setStatus(1);
        // create_time / update_time 显式赋值，**不依赖 {@code @TableField(fill = INSERT)}**。
        // 实测：该实体没有对应的 MetaObjectHandler 填充时，MyBatis 会把 NULL 显式写进去，
        // 于是数据库列上的 DEFAULT CURRENT_TIMESTAMP 也被覆盖成 NULL ——
        // 结果是轨迹汇聚时 firstSeen/lastSeen 全为 0（前端显示「-」、按时间排序失效）。
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        row.setCreateTime(now);
        row.setUpdateTime(now);
        Integer faceId = intOf(metadata, "faceId");
        row.setFaceId(faceId);
        row.setFaceName(strOf(metadata, "faceName"));
        try {
            SysAiReidLibrary exist = find(userId, vectorId);
            if (exist == null) {
                mapper.insert(row);
            } else {
                row.setId(exist.getId());
                mapper.updateById(row);
            }
        } catch (Exception ex) {
            log.error("[Reid] 底库元数据写入失败（向量不动）: user={}, id={} -> {}",
                    userId, vectorId, ex.getMessage(), ex);
            return false;
        }

        if (storage == null || feature == null || feature.length == 0) {
            // 向量能力未就绪：只落元数据，不算失败
            return true;
        }
        try {
            storage.remove(vectorId);
            if (!storage.add(vectorId, feature)) {
                throw new IllegalStateException("向量库拒绝写入");
            }
        } catch (Exception ex) {
            log.error("[Reid] 向量写入失败，回滚底库元数据: user={}, id={} -> {}",
                    userId, vectorId, ex.getMessage(), ex);
            deleteRow(userId, vectorId);
            return false;
        }
        return true;
    }

    /**
     * 更新档案字段（含经纬度），不换向量。
     *
     * @param userId   归属账号
     * @param vectorId 向量标识
     * @param name     展示名
     * @param metadata 新档案
     * @param lat      纬度（可空；{@code null} 表示清空）
     * @param lng      经度（可空）
     * @return 是否成功（行不存在返回 false）
     */
    public boolean updateMeta(String userId, String vectorId, String name,
                              Map<String, Object> metadata, BigDecimal lat, BigDecimal lng) {
        SysAiReidLibrary exist = find(userId, vectorId);
        if (exist == null) {
            return false;
        }
        exist.setName(name);
        exist.setMeta(writeMeta(metadata));
        exist.setGroupName(strOf(metadata, "group"));
        exist.setLat(lat);
        exist.setLng(lng);
        Integer faceId = intOf(metadata, "faceId");
        if (faceId != null) {
            exist.setFaceId(faceId);
        }
        String faceName = strOf(metadata, "faceName");
        if (faceName != null) {
            exist.setFaceName(faceName);
        }
        // 更新时间显式写（理由同 insert：无填充器时 NULL 会覆盖列默认值）
        exist.setUpdateTime(java.time.LocalDateTime.now());
        try {
            mapper.updateById(exist);
            return true;
        } catch (Exception ex) {
            log.error("[Reid] 底库档案更新失败: user={}, id={} -> {}", userId, vectorId, ex.getMessage(), ex);
            return false;
        }
    }

    /**
     * 删除一条观测的元数据行（向量由调用方同步删除）。
     *
     * @param userId   归属账号
     * @param vectorId 向量标识
     * @return 是否删除成功（行不存在也算成功，便于幂等调用）
     */
    public boolean deleteMeta(String userId, String vectorId) {
        try {
            SysAiReidLibrary exist = find(userId, vectorId);
            if (exist == null) {
                return true;
            }
            mapper.deleteById(exist.getId());
            return true;
        } catch (Exception ex) {
            log.error("[Reid] 底库元数据删除失败: user={}, id={} -> {}", userId, vectorId, ex.getMessage(), ex);
            return false;
        }
    }

    /**
     * 当前账号的全部观测行（按时间倒序）。
     *
     * @param userId 归属账号
     * @return 观测列表
     */
    public List<Map<String, Object>> listMine(String userId) {
        List<SysAiReidLibrary> rows;
        try {
            rows = mapper.selectList(new LambdaQueryWrapper<SysAiReidLibrary>()
                    .eq(SysAiReidLibrary::getUserId, userId)
                    .eq(SysAiReidLibrary::getStatus, 1)
                    .orderByDesc(SysAiReidLibrary::getCreateTime));
        } catch (Exception ex) {
            log.warn("[Reid] 读取重识别底库失败: user={} -> {}", userId, ex.getMessage());
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>(rows.size());
        for (SysAiReidLibrary row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", row.getVectorId());
            item.put("trackId", row.getTrackId());
            item.put("cameraId", row.getCameraId());
            item.put("name", row.getName());
            item.put("faceId", row.getFaceId());
            item.put("faceName", row.getFaceName());
            item.put("lat", row.getLat());
            item.put("lng", row.getLng());
            item.put("thumbnail", row.getThumbnail());
            item.put("metadata", readMeta(row.getMeta()));
            item.put("createTime", row.getCreateTime() == null ? null : row.getCreateTime().toString());
            out.add(item);
        }
        return out;
    }

    /**
     * 当前账号已有的向量标识集合（用于向量重建时判断是否已入库）。
     *
     * @param userId 归属账号
     * @return 向量标识集合
     */
    public Set<String> listExistingVectorIds(String userId) {
        Set<String> ids = new LinkedHashSet<>();
        try {
            List<SysAiReidLibrary> rows = mapper.selectList(new LambdaQueryWrapper<SysAiReidLibrary>()
                    .select(SysAiReidLibrary::getVectorId)
                    .eq(SysAiReidLibrary::getUserId, userId));
            for (SysAiReidLibrary row : rows) {
                if (row.getVectorId() != null) {
                    ids.add(row.getVectorId());
                }
            }
        } catch (Exception ex) {
            log.warn("[Reid] 读取向量标识集合失败: user={} -> {}", userId, ex.getMessage());
        }
        return ids;
    }

    /**
     * 把底库观测<b>汇聚成跨帧轨迹</b>。
     *
     * <p>这就是「跨帧轨迹从底库汇聚」的落地：一次分组查询得到轨迹，
     * 不依赖任何内存状态 —— 进程重启后轨迹依然在，因为它就是底库数据本身。</p>
     *
     * @param userId   归属账号
     * @param cameraId 只看某个摄像头的轨迹；为空表示全部
     * @param crossOnly 只看跨摄像头轨迹
     * @param limit    返回上限；≤0 表示不限
     * @return 轨迹列表（按最近观测时间倒序）
     */
    public List<Track> aggregateTracks(String userId, String cameraId, boolean crossOnly, int limit) {
        List<SysAiReidLibrary> rows;
        try {
            LambdaQueryWrapper<SysAiReidLibrary> query = new LambdaQueryWrapper<SysAiReidLibrary>()
                    .eq(SysAiReidLibrary::getUserId, userId)
                    .eq(SysAiReidLibrary::getStatus, 1)
                    .isNotNull(SysAiReidLibrary::getTrackId)
                    .orderByAsc(SysAiReidLibrary::getCreateTime);
            rows = mapper.selectList(query);
        } catch (Exception ex) {
            log.warn("[Reid] 汇聚轨迹失败: user={} -> {}", userId, ex.getMessage());
            return List.of();
        }

        Map<String, List<SysAiReidLibrary>> byTrack = new LinkedHashMap<>();
        for (SysAiReidLibrary row : rows) {
            byTrack.computeIfAbsent(row.getTrackId(), k -> new ArrayList<>()).add(row);
        }

        List<Track> tracks = new ArrayList<>(byTrack.size());
        for (Map.Entry<String, List<SysAiReidLibrary>> entry : byTrack.entrySet()) {
            List<SysAiReidLibrary> observations = entry.getValue();
            // 同一轨迹按时间排序（查询已升序，这里再保证一次，避免依赖 SQL 稳定性）
            observations.sort(Comparator.comparing(SysAiReidLibrary::getCreateTime,
                    Comparator.nullsLast(Comparator.naturalOrder())));

            Set<String> cameras = new LinkedHashSet<>();
            for (SysAiReidLibrary observation : observations) {
                if (observation.getCameraId() != null && !observation.getCameraId().isBlank()) {
                    cameras.add(observation.getCameraId());
                }
            }
            // 按摄像头过滤：该轨迹必须包含指定摄像头
            if (cameraId != null && !cameraId.isBlank() && !cameras.contains(cameraId)) {
                continue;
            }
            boolean cross = cameras.size() > 1;
            if (crossOnly && !cross) {
                continue;
            }
            SysAiReidLibrary first = observations.get(0);
            SysAiReidLibrary last = observations.get(observations.size() - 1);
            tracks.add(new Track(
                    entry.getKey(),
                    new ArrayList<>(cameras),
                    millis(first.getCreateTime()),
                    millis(last.getCreateTime()),
                    observations.size(),
                    cross,
                    last.getLat(),
                    last.getLng(),
                    last.getFaceId(),
                    last.getFaceName(),
                    last.getThumbnail()));
        }
        tracks.sort(Comparator.comparingLong(Track::lastSeen).reversed());
        return limit > 0 && tracks.size() > limit ? new ArrayList<>(tracks.subList(0, limit)) : tracks;
    }

    /**
     * 按轨迹 ID 取一条轨迹（同样从底库汇聚）。
     *
     * @param userId  归属账号
     * @param trackId 轨迹 ID
     * @return 轨迹；不存在返回 {@code null}
     */
    public Track track(String userId, String trackId) {
        if (trackId == null || trackId.isBlank()) {
            return null;
        }
        for (Track track : aggregateTracks(userId, null, false, 0)) {
            if (trackId.equals(track.trackId())) {
                return track;
            }
        }
        return null;
    }

    /**
     * 该账号下轨迹总数。
     *
     * @param userId 归属账号
     * @return 轨迹数
     */
    public int trackCount(String userId) {
        return aggregateTracks(userId, null, false, 0).size();
    }

    /**
     * 清空该账号的全部观测（元数据行；向量由调用方清理）。
     *
     * @param userId 归属账号
     * @return 删除的行数
     */
    public int clear(String userId) {
        try {
            return mapper.delete(new LambdaQueryWrapper<SysAiReidLibrary>()
                    .eq(SysAiReidLibrary::getUserId, userId));
        } catch (Exception ex) {
            log.warn("[Reid] 清空重识别底库失败: user={} -> {}", userId, ex.getMessage());
            return 0;
        }
    }

    // ==================== 内部工具 ====================

    /**
     * 按 (账号, 向量标识) 查一行。
     *
     * @param userId   归属账号
     * @param vectorId 向量标识
     * @return 行；不存在返回 {@code null}
     */
    private SysAiReidLibrary find(String userId, String vectorId) {
        return mapper.selectOne(new LambdaQueryWrapper<SysAiReidLibrary>()
                .eq(SysAiReidLibrary::getUserId, userId)
                .eq(SysAiReidLibrary::getVectorId, vectorId)
                .last("LIMIT 1"));
    }

    /**
     * 删除 DB 行（向量写入失败时回滚用）。
     *
     * @param userId   归属账号
     * @param vectorId 向量标识
     */
    private void deleteRow(String userId, String vectorId) {
        try {
            SysAiReidLibrary exist = find(userId, vectorId);
            if (exist != null) {
                mapper.deleteById(exist.getId());
            }
        } catch (Exception ex) {
            log.warn("[Reid] 回滚底库行失败: user={}, id={} -> {}", userId, vectorId, ex.getMessage());
        }
    }

    /**
     * 时间戳转毫秒。
     *
     * @param time 本地时间
     * @return 毫秒；入参为空返回 0
     */
    private static long millis(LocalDateTime time) {
        return time == null ? 0L : time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    /**
     * 档案序列化为 JSON。
     *
     * @param metadata 档案
     * @return JSON 字符串；入参为空返回 {@code null}
     */
    private String writeMeta(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (Exception ex) {
            log.warn("[Reid] 档案序列化失败: {}", ex.getMessage());
            return null;
        }
    }

    /**
     * JSON 反序列化为档案。
     *
     * @param meta JSON 字符串
     * @return 档案；解析失败返回空 map
     */
    private Map<String, Object> readMeta(String meta) {
        if (meta == null || meta.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(meta, new TypeReference<Map<String, Object>>() { });
        } catch (Exception ex) {
            log.debug("[Reid] 档案反序列化失败: {}", ex.getMessage());
            return Map.of();
        }
    }

    /**
     * 取档案里的字符串字段。
     *
     * @param metadata 档案
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
     * 取档案里的整数字段。
     *
     * @param metadata 档案
     * @param key      键
     * @return 整数；不存在或无法解析返回 {@code null}
     */
    private static Integer intOf(Map<String, Object> metadata, String key) {
        if (metadata == null) {
            return null;
        }
        Object value = metadata.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
