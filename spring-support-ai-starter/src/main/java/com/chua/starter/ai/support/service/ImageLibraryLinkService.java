package com.chua.starter.ai.support.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.starter.ai.support.entity.SysAiImageLibrary;
import com.chua.starter.ai.support.mapper.SysAiImageLibraryMapper;
import com.chua.starter.ai.support.util.AiLibraryGroups;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 图像底库的「DB 元数据 ↔ 向量索引」联动层。
 *
 * <p>与 {@link FaceLibraryLinkService} 同构，两处刻意保持一致：
 * 底库的写入顺序、回滚口径、错误语义都应当相同，否则日后排查
 * 「人脸能删干净、图片删不干净」这类问题时无从对照。</p>
 *
 * <p><b>向量不在本表。</b>以图搜图的特征存在 jvector 里，本表靠
 * {@code vector_id} 关联同名的向量条目。</p>
 *
 * <p><b>分组（group）是字段的必须参数，与 {@code user_id} 一起构成
 * 约束表的唯一键：</b>{@code (user_id, vector_id, group)}。
 * 同一账号下不同分组的底库，向量标识前缀虽然都含分组信息，但
 * {@code vector_id} 里已有分组，因此联合（账号, 向量标识, 分组）是严格唯一
 * 的。</p>
 *
 * @since 4.0.0.45
 * @author CH
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImageLibraryLinkService {

    /**
     * 图像底库映射器。
     */
    private final SysAiImageLibraryMapper mapper;

    /**
     * JSON 序列化器：特征模型名 / 原图字节数等落 meta 列。
     */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 新增一条图像底库记录。
     *
     * <p>用 upsert 而非 insert：以图搜图的 {@code vectorId} 由自增 id 派生，
     * 撞键只可能是「同一条记录被重复写」，语义上就是更新。</p>
     *
     * <p><b>分组归一化</b>：交给本类处理，写入方只用传原始分组名。
     * 同 {@link FaceLibraryLinkService#insert}，MyBatis 会把 null 显式写进列，
     * 覆盖掉 DDL 上的 {@code DEFAULT 'default'}，这条记录就变成 NULL 分组，
     * 查默认组时靠 {@code AiLibraryGroups.matches} 才兜底捞得回来。</p>
     *
     * @param userId   归属账号
     * @param group    分组名
     * @param vectorId 向量标识
     * @param name     展示名
     * @param thumbnail 缩略图 base64
     * @param imageFile 原图文件名（相对路径，勿存绝对路径）
     * @param tags     标签
     * @param width    宽
     * @param height   高
     * @param meta     其余元数据（特征模型名、原图字节数等）
     * @return 是否成功
     */
    public boolean insert(String userId, String group, String vectorId, String name, String thumbnail,
                          String imageFile, String tags, Integer width, Integer height,
                          java.util.Map<String, Object> meta) {
        SysAiImageLibrary row = new SysAiImageLibrary();
        row.setUserId(userId);
        row.setGroupName(AiLibraryGroups.normalize(group));
        row.setVectorId(vectorId);
        row.setName(name);
        row.setThumbnail(thumbnail);
        row.setUrl(imageFile);
        row.setTags(tags);
        row.setWidth(width);
        row.setHeight(height);
        row.setMeta(writeMeta(meta));
        row.setStatus(1);
        try {
            SysAiImageLibrary exist = find(userId, group, vectorId);
            if (exist == null) {
                mapper.insert(row);
            } else {
                row.setId(exist.getId());
                mapper.updateById(row);
            }
        } catch (Exception ex) {
            log.error("[ImageSearch] 底库元数据写入失败: user={}, group={}, id={} -> {}",
                    userId, group, vectorId, ex.getMessage(), ex);
            return false;
        }
        return true;
    }

    /**
     * 删除一行元数据。
     *
     * <p>「行不存在」算成功：DB 化之前入库的图片没有 DB 行，
     * 若把「没删到」当失败，那批图片将永远删不掉。</p>
     *
     * @param userId   归属账号
     * @param group    分组名
     * @param vectorId 向量标识
     * @return DB 是否处于「已无此行»的状态；出错返回 false
     */
    public boolean deleteMeta(String userId, String group, String vectorId) {
        try {
            mapper.delete(byKey(userId, group, vectorId));
            return true;
        } catch (Exception ex) {
            log.error("[ImageSearch] 底库元数据删除失败: user={}, group={}, id={} -> {}",
                    userId, group, vectorId, ex.getMessage(), ex);
            return false;
        }
    }

    /**
     * 查一行图像底库记录。
     *
     * @param userId   归属账号
     * @param group    分组名
     * @param vectorId 向量标识
     * @return 记录；不存在返回 {@code null}
     */
    public SysAiImageLibrary find(String userId, String group, String vectorId) {
        return mapper.selectOne(byKey(userId, group, vectorId));
    }

    /**
     * 查本账号本分组 DB 里已有的向量标识集合（存量迁移用）。
     *
     * <p>一次查全量而非逐条 SELECT：底库每次加载都会跑，逐条查等于每次都打 N 次库。</p>
     *
     * @param userId 归属账号
     * @param group  分组名
     * @return 已存在的 vector_id 集合；账号无数据时返回空集合
     */
    public java.util.Set<String> listExistingVectorIds(String userId, String group) {
        if (userId == null || userId.isBlank()) {
            return java.util.Set.of();
        }
        try {
            java.util.List<SysAiImageLibrary> rows = mapper.selectList(
                    new LambdaQueryWrapper<SysAiImageLibrary>()
                            .select(SysAiImageLibrary::getVectorId)
                            .eq(SysAiImageLibrary::getUserId, userId)
                            .eq(SysAiImageLibrary::getGroupName, group));
            java.util.Set<String> ids = new java.util.LinkedHashSet<>(rows.size());
            for (SysAiImageLibrary r : rows) {
                if (r.getVectorId() != null) {
                    ids.add(r.getVectorId());
                }
            }
            return ids;
        } catch (Exception ex) {
            log.error("[ImageSearch] 读取底库已有条目失败（存量补建将退化为逐条判断）: user={}, group={} -> {}",
                    userId, group, ex.getMessage());
            return java.util.Set.of();
        }
    }

    /**
     * 本账号底库分组的列表（DB 权威）。
     *
     * <p>从 {@code sys_ai_image_library} 里按账号捞所有不重复的分组；
     * 历史行（group 列为 NULL）与清理后的「无分组（''）」都归一到 {@code default}。</p>
     *
     * <p>返回的是排序后的集合，交给前端下拉窗的「加入分组」单选/多选用；
     * 不存在新建分组的权限（分组由管理端维护，见 {@code AiFaceServiceImpl#groups()}）。</p>
     *
     * @param userId 归属账号
     * @return 分组名集合
     */
    public java.util.Set<String> listGroups(String userId) {
        if (userId == null || userId.isBlank()) {
            return java.util.Set.of();
        }
        try {
            java.util.List<SysAiImageLibrary> rows = mapper.selectList(
                    new LambdaQueryWrapper<SysAiImageLibrary>()
                            .select(SysAiImageLibrary::getGroupName)
                            .eq(SysAiImageLibrary::getUserId, userId));
            return rows.stream()
                    .map(SysAiImageLibrary::getGroupName)
                    .map(AiLibraryGroups::normalize)
                    .collect(Collectors.toSet());
        } catch (Exception ex) {
            log.error("[ImageSearch] 读取底库分组失败: user={} -> {}", userId, ex.getMessage());
            return java.util.Set.of();
        }
    }

    /**
     * 按（归属, 分组, 向量标识）构造查询条件。
     *
     * <p>与 {@code listMine} 共用同一套 <b>分组的 NULL 语义</b>：分组列建成之前
     * 写入的行是 {@code NULL}，语义上属于默认组；查默认组时必须把 {@code NULL}
     * 也捞进来，查非默认组时不能带 {@code NULL}。</p>
     *
     * @param userId   归属账号
     * @param group    分组名
     * @param vectorId 向量标识
     * @return 查询包装
     */
    private LambdaQueryWrapper<SysAiImageLibrary> byKey(String userId, String group, String vectorId) {
        LambdaQueryWrapper<SysAiImageLibrary> w = new LambdaQueryWrapper<SysAiImageLibrary>()
                .eq(SysAiImageLibrary::getVectorId, vectorId);
        if (userId != null && !userId.isBlank()) {
            w.eq(SysAiImageLibrary::getUserId, userId);
        }
        if (group != null && !group.isBlank()) {
            w.and(q -> {
                q.eq(SysAiImageLibrary::getGroupName, group);
                // 默认组额外带上 NULL：历史行没有分组值，语义上属于默认组
                if (AiLibraryGroups.DEFAULT.equals(group)) {
                    q.or().isNull(SysAiImageLibrary::getGroupName);
                }
            });
        }
        return w;
    }

    /**
     * 其余元数据序列化成 meta 列。
     *
     * @param meta 元数据
     * @return JSON 字符串；入参为空时返回 {@code null}
     */
    private String writeMeta(java.util.Map<String, Object> meta) {
        if (meta == null || meta.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(meta);
        } catch (Exception ex) {
            log.warn("[ImageSearch] 元数据序列化失败（该部分将丢失）: {}", ex.getMessage());
            return null;
        }
    }
}
