package com.chua.starter.ai.support.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.common.support.vector.VectorStorage;
import com.chua.starter.ai.support.entity.SysAiFaceLibrary;
import com.chua.starter.ai.support.mapper.SysAiFaceLibraryMapper;
import com.chua.starter.ai.support.util.AiUserContext;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 人脸底库的「DB 元数据 ↔ 向量索引」联动层。
 *
 * <p><b>为什么需要这一层。</b>人脸特征存在 jvector（ANN 检索要求全量放内存，
 * DB 做 ANN 不现实），元数据存在 MySQL，两者之间<b>没有事务</b>。一旦某次操作只
 * 改了其中一边，就会出现两种很难排查的状态：</p>
 * <ul>
 *   <li><b>幽灵向量</b>：向量在、DB 行没了 → 1:N 识别能命中一个查不到档案的人；</li>
 *   <li><b>孤儿向量缺失</b>：DB 行在、向量没了 → 底库列表里有人，但永远认不出。</li>
 * </ul>
 *
 * <p>本类把「同一条人脸」的三处写（DB 行、向量、登记图）收进一个方法，
 * 并采取 <b>DB 先行</b>的顺序：DB 失败则向量不动（干净回滚）；向量失败则回滚 DB 行。
 * 之所以 DB 先行而不是反过来 —— 向量库没有事务、删除/写入都是即时生效的，
 * 让它当「先手」失败时留下的残局更难收拾。</p>
 *
 * <p><b>关于顺序与可恢复性：</b>登记图仍由 {@code FaceStores} 负责落盘，
 * 因为它和向量共用同一套「本账号目录」约定；本类只保证 DB 与向量两者一致。</p>
 *
 * @since 4.0.0.44
 * @author CH
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FaceLibraryLinkService {

    /**
     * 人脸底库映射器。
     */
    private final SysAiFaceLibraryMapper mapper;

    /**
     * JSON 序列化器：元数据落 {@code meta} 列、读回时还原成 Map。
     */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 新增一条人脸：先写 DB，再写向量；向量失败则删掉刚写的 DB 行。
     *
     * <p>之所以 DB 用 upsert 而不是 insert：向量库按 id 覆盖写，重复登记同一人
     * 本就是「更新」语义。若 DB 侧撞了唯一键就转成更新，否则第二次登记会直接报错，
     * 而此时向量已经被覆盖 —— 结果是向量是新数据、DB 是旧档案。</p>
     *
     * @param userId   归属账号
     * @param vectorId 向量标识（与人脸 id 同值）
     * @param name     展示名
     * @param metadata 档案（落 meta 列）
     * @param thumbnail 缩略图 base64（可空）
     * @param storage  向量存储；为 {@code null} 表示向量能力未就绪
     * @param feature  特征向量；为 {@code null} 表示向量能力未就绪
     * @return 是否成功
     */
    public boolean insert(String userId, String vectorId, String name,
                          Map<String, Object> metadata, String thumbnail,
                          VectorStorage storage, float[] feature) {
        SysAiFaceLibrary row = new SysAiFaceLibrary();
        row.setUserId(userId);
        row.setVectorId(vectorId);
        row.setName(name);
        row.setThumbnail(thumbnail);
        row.setMeta(writeMeta(metadata));
        row.setGroup(strOf(metadata, "group"));
        row.setStatus(1);
        try {
            SysAiFaceLibrary exist = find(userId, vectorId);
            if (exist == null) {
                mapper.insert(row);
            } else {
                row.setId(exist.getId());
                mapper.updateById(row);
            }
        } catch (Exception ex) {
            log.error("[Face] 底库元数据写入失败（向量不动）: user={}, id={} -> {}",
                    userId, vectorId, ex.getMessage(), ex);
            return false;
        }

        if (storage == null || feature == null || feature.length == 0) {
            // 向量能力未就绪：只落元数据，不算失败（与既有行为一致，
            // 人脸模型未配置时底库本就只做档案管理）
            return true;
        }
        try {
            storage.remove(vectorId);
            if (!storage.add(vectorId, feature)) {
                throw new IllegalStateException("向量库拒绝写入");
            }
        } catch (Exception ex) {
            log.error("[Face] 向量写入失败，回滚底库元数据: user={}, id={} -> {}",
                    userId, vectorId, ex.getMessage(), ex);
            deleteRow(userId, vectorId);
            return false;
        }
        return true;
    }

    /**
     * 更新档案（不换向量）。行不存在时<b>补建</b>而非失败。
     *
     * <p>这里必须是 upsert：DB 化之前登记的存量人脸只有 index.json、没有 DB 行，
     * 若「查不到就返回 false」，那批人脸将永远改不了档案 —— 用户改姓名却毫无反应，
     * 且没有任何报错。存量迁移到 DB 之前，这个分支必须能自愈。</p>
     *
     * <p>换特征模型时向量必须重算，那是「重新登记」而不是「改档案」，
     * 走 {@link #insert} —— 否则会出现新模型特征配旧档案的错配。</p>
     *
     * @param userId    归属账号
     * @param vectorId  向量标识
     * @param name      展示名
     * @param metadata  新档案
     * @param thumbnail 新缩略图（为空表示保持原值）
     * @return 是否成功
     */
    public boolean updateMeta(String userId, String vectorId, String name,
                              Map<String, Object> metadata, String thumbnail) {
        SysAiFaceLibrary row = find(userId, vectorId);
        if (row == null) {
            return insert(userId, vectorId, name, metadata, thumbnail, null, null);
        }
        if (name != null) {
            row.setName(name);
        }
        row.setMeta(writeMeta(metadata));
        row.setGroup(strOf(metadata, "group"));
        if (thumbnail != null && !thumbnail.isBlank()) {
            row.setThumbnail(thumbnail);
        }
        try {
            mapper.updateById(row);
        } catch (Exception ex) {
            log.error("[Face] 底库元数据更新失败: user={}, id={} -> {}", userId, vectorId, ex.getMessage(), ex);
            return false;
        }
        return true;
    }

    /**
     * 删除一条人脸：先删 DB 行，再删向量；向量删除失败只告警。
     *
     * <p>顺序与 {@link #insert} 相反且是刻意的：删除时 DB 行已经没了，
     * 此时若向量删不掉，结果是「幽灵向量」（能识别、查不到档案），
     * 比「孤儿向量缺失」危害小 —— 前者下次重建索引会自动清掉，
     * 后者只能让用户重新登记。</p>
     *
     * @param userId   归属账号
     * @param vectorId 向量标识
     * @param storage  向量存储（可空）
     * @return 是否删掉了 DB 行
     */
    public boolean delete(String userId, String vectorId, VectorStorage storage) {
        boolean removed = deleteRow(userId, vectorId);
        if (storage != null) {
            try {
                if (!storage.remove(vectorId)) {
                    log.debug("[Face] 向量索引中无此条目（可安全忽略）: id={}", vectorId);
                }
            } catch (Exception ex) {
                log.warn("[Face] 向量删除失败，可能残留幽灵向量（下次重建索引会自动清理）: id={} -> {}",
                        vectorId, ex.getMessage());
            }
        }
        return removed;
    }

    /**
     * 查本账号 DB 里已有的向量标识集合（存量迁移用）。
     *
     * <p>一次查全量，而不是逐条 SELECT：底库加载会在每次设置变更后重跑，
     * 逐条查等于每次重载都打 N 次库，人脸上百条时开销明显。</p>
     *
     * @param userId 归属账号
     * @return 已存在的 vector_id 集合；账号无数据时返回空集合
     */
    public java.util.Set<String> listExistingVectorIds(String userId) {
        if (userId == null || userId.isBlank()) {
            return java.util.Set.of();
        }
        try {
            List<SysAiFaceLibrary> rows = mapper.selectList(new LambdaQueryWrapper<SysAiFaceLibrary>()
                    .select(SysAiFaceLibrary::getVectorId)
                    .eq(SysAiFaceLibrary::getUserId, userId));
            java.util.Set<String> ids = new java.util.LinkedHashSet<>(rows.size());
            for (SysAiFaceLibrary r : rows) {
                if (r.getVectorId() != null) {
                    ids.add(r.getVectorId());
                }
            }
            return ids;
        } catch (Exception ex) {
            log.error("[Face] 读取底库已有条目失败（存量补建将退化为逐条判断）: user={} -> {}", userId, ex.getMessage());
            return java.util.Set.of();
        }
    }

    /**
     * 查一行人脸底库记录。
     *
     * @param userId   归属账号
     * @param vectorId 向量标识
     * @return 记录；不存在返回 {@code null}
     */
    public SysAiFaceLibrary find(String userId, String vectorId) {
        LambdaQueryWrapper<SysAiFaceLibrary> w = new LambdaQueryWrapper<SysAiFaceLibrary>()
                .eq(SysAiFaceLibrary::getVectorId, vectorId);
        if (userId != null && !userId.isBlank()) {
            w.eq(SysAiFaceLibrary::getUserId, userId);
        }
        return mapper.selectOne(w);
    }

    /**
     * 当前账号的人脸底库行数（统计卡用）。
     *
     * @return 行数
     */
    public long countMine() {
        String userId = AiUserContext.currentUserId();
        if (userId == null || userId.isBlank()) {
            return 0L;
        }
        return mapper.selectCount(new LambdaQueryWrapper<SysAiFaceLibrary>()
                .eq(SysAiFaceLibrary::getUserId, userId));
    }

    /**
     * 删除一行元数据（不含向量）。
     *
     * <p><b>「行不存在」算成功，只有 DB 出错才算失败。</b>存量 index.json 里的人脸
     * 可能压根没有对应的 DB 行（DB 化之前登记的），此时若把「没删到」当成失败，
     * 这些历史人脸就永远删不掉 —— 用户点了删除却被拒绝，且提示语还很有误导性。
     * 从一致性角度看，「本来就没有」与「已删除」是同一种终态。</p>
     *
     * @param userId   归属账号
     * @param vectorId 向量标识
     * @return DB 是否处于「已无此行」的状态；出错返回 false
     */
    public boolean deleteMeta(String userId, String vectorId) {
        LambdaQueryWrapper<SysAiFaceLibrary> w = new LambdaQueryWrapper<SysAiFaceLibrary>()
                .eq(SysAiFaceLibrary::getVectorId, vectorId);
        if (userId != null && !userId.isBlank()) {
            w.eq(SysAiFaceLibrary::getUserId, userId);
        }
        try {
            mapper.delete(w);
            return true;
        } catch (Exception ex) {
            log.error("[Face] 底库元数据删除失败: user={}, id={} -> {}", userId, vectorId, ex.getMessage(), ex);
            return false;
        }
    }

    /**
     * 删除一行（不含向量），用于向量写入失败时的内部回滚。
     *
     * @param userId   归属账号
     * @param vectorId 向量标识
     * @return 是否删到了行
     */
    private boolean deleteRow(String userId, String vectorId) {
        LambdaQueryWrapper<SysAiFaceLibrary> w = new LambdaQueryWrapper<SysAiFaceLibrary>()
                .eq(SysAiFaceLibrary::getVectorId, vectorId);
        if (userId != null && !userId.isBlank()) {
            w.eq(SysAiFaceLibrary::getUserId, userId);
        }
        try {
            return mapper.delete(w) > 0;
        } catch (Exception ex) {
            log.error("[Face] 底库元数据删除失败: user={}, id={} -> {}", userId, vectorId, ex.getMessage(), ex);
            return false;
        }
    }

    /**
     * 档案序列化成 meta 列。
     *
     * @param metadata 档案
     * @return JSON 字符串；入参为空时返回 {@code null}
     */
    private String writeMeta(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (Exception ex) {
            log.warn("[Face] 档案序列化失败（元数据将丢失）: {}", ex.getMessage());
            return null;
        }
    }

    /**
     * meta 列还原成档案。
     *
     * @param json meta 列
     * @return 档案；为空或解析失败时返回空 Map
     */
    public Map<String, Object> readMeta(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, Object> m = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
            return m == null ? new LinkedHashMap<>() : new LinkedHashMap<>(m);
        } catch (Exception ex) {
            log.warn("[Face] 档案反序列化失败，按空档案处理: {}", ex.getMessage());
            return new LinkedHashMap<>();
        }
    }

    /**
     * 取档案里的字符串字段。
     *
     * @param metadata 档案（可为空）
     * @param key      键
     * @return 字符串值；不存在返回 {@code null}
     */
    private static String strOf(Map<String, Object> metadata, String key) {
        if (metadata == null) {
            return null;
        }
        Object v = metadata.get(key);
        return v == null ? null : String.valueOf(v);
    }
}
