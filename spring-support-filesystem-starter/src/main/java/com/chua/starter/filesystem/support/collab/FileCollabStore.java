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
     * 版本历史存储：id → 历史快照条目（不含当前最新快照，保存时把旧快照压入）。
     *
     * <p>需求：管理员可查看历史记录并「回退」（复制某个历史版本出来成为一个
     * 新文件）。历史上限 {@link #MAX_HISTORY} 条，超过时丢弃最旧条目，防止
     * 快照无限膨胀拖垮内存（协同本身是内存态）。</p>
     */
    private final Map<String, List<SnapshotEntry>> history = new ConcurrentHashMap<>();

    /**
     * 每个文档保留的历史快照条数上限
     */
    private static final int MAX_HISTORY = 50;

    /**
     * 自增操作序号（变更广播用）
     */
    private final AtomicLong seq = new AtomicLong();

    /**
     * 历史快照条目。
     *
     * @param version   该条目对应的版本号
     * @param snapshot  该版本的内容快照
     * @param updatedAt 保存时间戳
     */
    public record SnapshotEntry(long version, String snapshot, long updatedAt) {
    }

    /**
     * 创建协同文档。
     *
     * @param bucket      源文件所在桶（可为空串）
     * @param objectKey   源文件对象键（可为空串，未落桶时）
     * @param fileName    展示文件名（含扩展名，决定文档类型）
     * @param owner       创建者用户标识
     * @param visibility  公开 / 私有
     * @param allowedUserIds 私有时的授权用户集合（公开时忽略）
     * @param path        所在目录（空/“/” = 根）
     * @param type        文档类型：excel / word（空则按扩展名推断）
     * @return 新创建的协同文档
     */
    public FileCollab create(String bucket, String objectKey, String fileName, String owner,
                             Visibility visibility, java.util.Set<String> allowedUserIds,
                             String path, String type) {
        String id = UUID.randomUUID().toString().replace("-", "");
        String dir = normalizePath(path);
        String docType = type == null || type.isBlank() ? inferType(fileName) : type.toLowerCase();
        FileCollab collab = new FileCollab(id, bucket == null ? "" : bucket,
                objectKey == null ? "" : objectKey, fileName == null ? "" : fileName,
                owner, visibility == null ? Visibility.PUBLIC : visibility,
                allowedUserIds == null ? new java.util.LinkedHashSet<>() : new java.util.LinkedHashSet<>(allowedUserIds),
                System.currentTimeMillis(), System.currentTimeMillis(),
                null, 0, dir, docType);
        collabs.put(id, collab);
        return collab;
    }

    /**
     * normalize Path
     * @param path 字符串
     * @return 返回值
    */
    private static String normalizePath(String path) {
        if (path == null) {
            return "";
        }
        String p = path.trim().replace('\\', '/');
        while (p.startsWith("/")) {
            p = p.substring(1);
        }
        while (p.endsWith("/")) {
            p = p.substring(0, p.length() - 1);
        }
        return p;
    }

    /**
     * infer Type
     * @param fileName 字符串
     * @return 返回值
    */
    private static String inferType(String fileName) {
        if (fileName == null) {
            return "excel";
        }
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".doc") || lower.endsWith(".docx")
                || lower.endsWith(".txt") || lower.endsWith(".md")
                || lower.endsWith(".rtf")) {
            return "word";
        }
        return "excel";
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
     * <p>过滤：{@code path} 非空时只列该目录下的文档（精确目录）；{@code path}
     * 为 null/空时列出全部目录（用于跨目录检索）。{@code keyword} 匹配文件名
     * 或完整路径（大小写不敏感）。</p>
     *
     * @param userId   当前用户标识
     * @param path     目录过滤（null/空 = 全部目录）
     * @param keyword  关键字过滤（null/空 = 不过滤）
     * @return 可见文档列表
     */
    public List<FileCollab> list(String userId, String path, String keyword) {
        String dir = (path == null ? "" : path.trim().replace('\\', '/'));
        while (dir.startsWith("/")) {
            dir = dir.substring(1);
        }
        while (dir.endsWith("/")) {
            dir = dir.substring(0, dir.length() - 1);
        }
        String kw = keyword == null || keyword.isBlank() ? null : keyword.trim().toLowerCase();
        List<FileCollab> result = new ArrayList<>();
        for (FileCollab c : collabs.values()) {
            if (c.visibility() != Visibility.PUBLIC
                    && !c.owner().equals(userId)
                    && !c.allowedUserIds().contains(userId)) {
                continue;
            }
            if (!dir.isEmpty() && !dir.equals(c.path())) {
                continue;
            }
            if (kw != null) {
                String full = (c.path() == null || c.path().isEmpty() ? "" : c.path() + "/") + c.fileName();
                if (!full.toLowerCase().contains(kw)) {
                    continue;
                }
            }
            result.add(c);
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
        // 先把「当前内容」压入历史（版本号保持为保存前），再写入新快照
        pushHistory(c);
        FileCollab updated = c.withSnapshot(snapshot, c.version() + 1, System.currentTimeMillis());
        collabs.put(id, updated);
        return updated;
    }

    /**
     * 把文档当前快照压入其历史列表（超上限丢弃最旧），仅当已有内容才记录。
     *
     * @param c 当前文档
     */
    private void pushHistory(FileCollab c) {
        if (c.snapshot() == null || c.snapshot().isEmpty()) {
            return;
        }
        List<SnapshotEntry> entries = history.computeIfAbsent(c.id(), k -> new ArrayList<>());
        entries.add(new SnapshotEntry(c.version(), c.snapshot(), c.updatedAt()));
        if (entries.size() > MAX_HISTORY) {
            entries.subList(0, entries.size() - MAX_HISTORY).clear();
        }
    }

    /**
     * 重命名协同文档。
     *
     * @param id          协同文档号
     * @param newFileName 新展示文件名（含扩展名）
     * @return 更新后的文档；不存在返回 null
     */
    public FileCollab rename(String id, String newFileName) {
        FileCollab c = collabs.get(id);
        if (c == null || newFileName == null || newFileName.isBlank()) {
            return null;
        }
        FileCollab updated = new FileCollab(id, c.bucket(), c.objectKey(), newFileName.trim(),
                c.owner(), c.visibility(), c.allowedUserIds(), c.createdAt(), c.updatedAt(),
                c.snapshot(), c.version(), c.path(), c.type());
        collabs.put(id, updated);
        return updated;
    }

    /**
     * 复制协同文档：生成一个同内容的新文档（新 id，文件名追加「 副本」）。
     *
     * @param id        源文档号
     * @param newOwner  新文档创建者
     * @return 新创建的文档；源不存在返回 null
     */
    public FileCollab duplicate(String id, String newOwner) {
        FileCollab c = collabs.get(id);
        if (c == null) {
            return null;
        }
        String base = c.fileName();
        int dot = base.lastIndexOf('.');
        String stem = dot > 0 ? base.substring(0, dot) : base;
        String ext = dot > 0 ? base.substring(dot) : "";
        String copyName = stem + " 副本" + ext;
        FileCollab copy = new FileCollab(UUID.randomUUID().toString().replace("-", ""),
                c.bucket(), c.objectKey(), copyName, newOwner,
                c.visibility(), c.allowedUserIds(), System.currentTimeMillis(),
                System.currentTimeMillis(), c.snapshot(), 0, c.path(), c.type());
        collabs.put(copy.id(), copy);
        return copy;
    }

    /**
     * 读取文档历史快照条目列表（新→旧）。
     *
     * @param id 协同文档号
     * @return 历史条目；文档不存在返回 null
     */
    public List<SnapshotEntry> history(String id) {
        if (!collabs.containsKey(id)) {
            return null;
        }
        List<SnapshotEntry> entries = history.getOrDefault(id, new ArrayList<>());
        // 新 → 旧
        List<SnapshotEntry> result = new ArrayList<>(entries);
        result.sort(Comparator.comparingLong(SnapshotEntry::version).reversed());
        return result;
    }

    /**
     * 按版本号取历史快照。
     *
     * @param id      协同文档号
     * @param version 版本号
     * @return 历史条目；不存在返回 null
     */
    public SnapshotEntry versionAt(String id, long version) {
        List<SnapshotEntry> entries = history.getOrDefault(id, new ArrayList<>());
        for (SnapshotEntry e : entries) {
            if (e.version() == version) {
                return e;
            }
        }
        return null;
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
     * @param snapshot       当前文档快照（excel = Univer JSON；word = 富文本 HTML；null = 未保存/清空）
     * @param version        快照版本（乐观锁计数）
     * @param path           所在目录（空 = 根）
     * @param type           文档类型：excel / word
     */
    public record FileCollab(String id, String bucket, String objectKey, String fileName,
                             String owner, Visibility visibility, java.util.Set<String> allowedUserIds,
                             long createdAt, long updatedAt, String snapshot, long version,
                             String path, String type) {

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
                    allowedUserIds, createdAt, now, newSnapshot, newVersion, path, type);
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
                    newUsers, createdAt, now, snapshot, version, path, type);
        }
    }
}