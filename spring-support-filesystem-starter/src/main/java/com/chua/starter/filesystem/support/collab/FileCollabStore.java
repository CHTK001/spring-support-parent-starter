package com.chua.starter.filesystem.support.collab;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 协同文档存储器（内存态，与上传任务同生命周期）。
 *
 * <p>每个协同文档是一条 {@link FileCollab}：记录原始文件（桶 + 对象键）、
 * 可见性（公开/私有）、被授权的用户集合、当前表格快照（Univer JSON）与
 * 版本号。快照采用「版本乐观锁」：写入时携带期望版本，版本不匹配拒绝写入，
 * 客户端据此拉最新快照再应用，避免并发覆盖。</p>
 *
 * <p>数据仅存内存：服务重启即清空。生产可替换为 DB 实现（本类接口按最小
 * 表面积设计，便于后续扩展）。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public class FileCollabStore {

    /**
     * 协同文档存储：id → 文档
     */
    private final Map<String, FileCollab> collabs = new ConcurrentHashMap<>();

    /**
     * 自增操作序号（变更广播用）
     */
    private final AtomicLong seq = new AtomicLong();

    /**
     * 创建协同文档。
     *
     * @param bucket      源文件所在桶（可为空串）
     * @param objectKey   源文件对象键（可为空串，未落桶时）
     * @param fileName    展示文件名（含扩展名，决定表格类型）
     * @param owner       创建者用户标识
     * @param visibility  公开 / 私有
     * @param allowedUserIds 私有时的授权用户集合（公开时忽略）
     * @return 新创建的协同文档
     */
    public FileCollab create(String bucket, String objectKey, String fileName, String owner,
                             Visibility visibility, java.util.Set<String> allowedUserIds) {
        String id = UUID.randomUUID().toString().replace("-", "");
        FileCollab collab = new FileCollab(id, bucket == null ? "" : bucket,
                objectKey == null ? "" : objectKey, fileName == null ? "" : fileName,
                owner, visibility == null ? Visibility.PUBLIC : visibility,
                allowedUserIds == null ? new java.util.LinkedHashSet<>() : new java.util.LinkedHashSet<>(allowedUserIds),
                System.currentTimeMillis(), System.currentTimeMillis(),
                null, 0);
        collabs.put(id, collab);
        return collab;
    }

    /**
     * 按 id 取协同文档。
     *
     * @param id 协同文档号
     * @return 文档或 null
     */
    public FileCollab get(String id) {
        return collabs.get(id);
    }

    /**
     * 列出可见的协同文档（新→旧）。
     *
     * <p>可见规则：公开文档全员可见；私有文档仅创建者与被授权用户可见。</p>
     *
     * @param userId 当前用户标识
     * @return 可见文档列表
     */
    public List<FileCollab> list(String userId) {
        List<FileCollab> result = new ArrayList<>();
        for (FileCollab c : collabs.values()) {
            if (c.visibility() == Visibility.PUBLIC
                    || c.owner().equals(userId)
                    || c.allowedUserIds().contains(userId)) {
                result.add(c);
            }
        }
        result.sort(Comparator.comparingLong(FileCollab::updatedAt).reversed());
        return Collections.unmodifiableList(result);
    }

    /**
     * 保存表格快照（版本乐观锁）。
     *
     * @param id           协同文档号
     * @param snapshot     Univer 工作簿快照 JSON（可为 null 表示清空）
     * @param expectedVersion 期望的当前版本；不匹配时拒绝写入
     * @return 更新后的文档；文档不存在或版本不匹配返回 null
     */
    public FileCollab saveSnapshot(String id, String snapshot, long expectedVersion) {
        FileCollab c = collabs.get(id);
        if (c == null) {
            return null;
        }
        if (c.version() != expectedVersion) {
            return null;
        }
        FileCollab updated = c.withSnapshot(snapshot, c.version() + 1, System.currentTimeMillis());
        collabs.put(id, updated);
        return updated;
    }

    /**
     * 更新可见性与授权用户。
     *
     * @param id             协同文档号
     * @param visibility     新的可见性（null 表示不变）
     * @param allowedUserIds 新的授权用户集合（null 表示不变）
     * @return 更新后的文档；不存在返回 null
     */
    public FileCollab updateAccess(String id, Visibility visibility,
                                   java.util.Set<String> allowedUserIds) {
        FileCollab c = collabs.get(id);
        if (c == null) {
            return null;
        }
        Visibility v = visibility == null ? c.visibility() : visibility;
        java.util.Set<String> users = allowedUserIds == null
                ? c.allowedUserIds() : new java.util.LinkedHashSet<>(allowedUserIds);
        FileCollab updated = c.withAccess(v, users, System.currentTimeMillis());
        collabs.put(id, updated);
        return updated;
    }

    /**
     * 删除协同文档。
     *
     * @param id 协同文档号
     * @return 删除前是否存在
     */
    public boolean delete(String id) {
        return collabs.remove(id) != null;
    }

    /**
     * 当前协同文档总数。
     *
     * @return 总数
     */
    public int size() {
        return collabs.size();
    }

    /**
     * 下一变更序号（SSE 广播携带，客户端去重）。
     *
     * @return 自增序号
     */
    public long nextSeq() {
        return seq.incrementAndGet();
    }

    /**
     * 可见性：公开（全员可看可编辑）/ 私有（仅创建者与授权用户）。
     */
    public enum Visibility {
        /**
         * 公开
         */
        PUBLIC,
        /**
         * 私有
         */
        PRIVATE
    }

    /**
     * 协同文档。
     *
     * @param id             协同文档号
     * @param bucket         源文件桶
     * @param objectKey      源文件对象键
     * @param fileName       展示文件名
     * @param owner          创建者用户标识
     * @param visibility     可见性
     * @param allowedUserIds 私有授权用户集合
     * @param createdAt      创建时间戳
     * @param updatedAt      最后更新时间戳
     * @param snapshot       当前表格快照（Univer JSON；null = 未保存/清空）
     * @param version        快照版本（乐观锁计数）
     */
    public record FileCollab(String id, String bucket, String objectKey, String fileName,
                             String owner, Visibility visibility, java.util.Set<String> allowedUserIds,
                             long createdAt, long updatedAt, String snapshot, long version) {

        /**
         * 附带新快照的副本（版本 +1，时间刷新）。
         *
         * @param newSnapshot 新快照
         * @param newVersion  新版本号
         * @param now         更新时间戳
         * @return 新副本
         */
        FileCollab withSnapshot(String newSnapshot, long newVersion, long now) {
            return new FileCollab(id, bucket, objectKey, fileName, owner, visibility,
                    allowedUserIds, createdAt, now, newSnapshot, newVersion);
        }

        /**
         * 附带新访问控制的副本（时间刷新，版本不变）。
         *
         * @param newVisibility 新可见性
         * @param newUsers      新授权用户
         * @param now           更新时间戳
         * @return 新副本
         */
        FileCollab withAccess(Visibility newVisibility, java.util.Set<String> newUsers, long now) {
            return new FileCollab(id, bucket, objectKey, fileName, owner, newVisibility,
                    newUsers, createdAt, now, snapshot, version);
        }
    }
}