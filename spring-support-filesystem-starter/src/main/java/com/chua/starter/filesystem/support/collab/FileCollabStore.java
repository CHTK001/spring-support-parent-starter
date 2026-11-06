package com.chua.starter.filesystem.support.collab;

import com.chua.starter.common.support.logger.ModuleLog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 协同文档存储器（内存态读写 + 定时落盘）。
 *
 * <p>每个协同文档是一条 {@link FileCollab}：记录原始文件（桶 + 对象键）、
 * 可见性（公开/私有）、被授权的用户集合、当前快照（Univer JSON / 富文本 HTML / 纯文本）
 * 与版本号。快照采用「版本乐观锁」：写入时携带期望版本，版本不匹配拒绝写入，
 * 客户端据此拉最新快照再应用，避免并发覆盖。</p>
 *
 * <p><b>读写都在内存里</b>（编辑零阻塞），落盘交给
 * {@link FileCollabPersistence} 按间隔整库刷盘：所以「重启即清空」不再是必然，
 * 代价是最多丢一个落盘间隔的改动。</p>
 *
 * <p><b>并发约定</b>：所有「读-改-写」都走 {@code ConcurrentHashMap.compute}，
 * 对同一个文档 id 持锁执行 —— 版本比对与写入因此是原子的（否则两个并发保存带同一个
 * expectedVersion 会双双通过、互相覆盖），改权限/重命名也不会把并发保存上去的快照退回上一版。
 * 历史快照列表用 {@link CopyOnWriteArrayList}，读侧在并发保存时不会读到半个条目。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public class FileCollabStore {

    /**
     * 日志记录器
     */
    private static final ModuleLog MODULE_LOG = ModuleLog.of("FileSystem", FileCollabStore.class);

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
     * 是否有未落盘的改动
     *
     * <p>由 {@link FileCollabPersistence} 的定时任务消费：没有改动就一个字节都不写，
     * 空闲时落盘成本为 0。所有写方法尾部都会 {@link #markDirty()}，
     * 漏标 = 这次改动永远不会落盘，所以改动写方法时务必一起补上。</p>
     */
    private final java.util.concurrent.atomic.AtomicBoolean dirty =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    /**
     * 标记「有未落盘改动」。
     */
    public void markDirty() {
        dirty.set(true);
    }

    /**
     * 取走「有未落盘改动」标记（取走即清零）。
     *
     * <p>落盘方拿它当门禁：返回 false 代表自上次落盘以来没有任何写入，直接跳过。
     * 落盘失败时应重新 {@link #markDirty()}，否则这次改动要等到下一次写入才有机会落盘。</p>
     *
     * @return 有改动为 true（并把标记清零）
     */
    public boolean consumeDirty() {
        return dirty.getAndSet(false);
    }

    /**
     * 变更监听器（每个 SSE 连接一个）
     */
    private final List<java.util.function.Consumer<CollabChange>> listeners =
            new java.util.concurrent.CopyOnWriteArrayList<>();

    /**
     * 注册变更监听器。
     *
     * <p>与上传任务的 {@code publish} 同一套模式：写操作成功后回调一次，
     * SSE 连接把 {@code changed} 事件推给客户端。</p>
     *
     * @param listener 监听器
     */
    public void addListener(java.util.function.Consumer<CollabChange> listener) {
        listeners.add(listener);
    }

    /**
     * 移除变更监听器（连接关闭时调用）。
     *
     * @param listener 监听器
     */
    public void removeListener(java.util.function.Consumer<CollabChange> listener) {
        listeners.remove(listener);
    }

    /**
     * 广播一次文档变更（单连接异常不影响其余连接）。
     *
     * @param change 变更内容
     */
    private void publish(CollabChange change) {
        for (java.util.function.Consumer<CollabChange> listener : listeners) {
            try {
                listener.accept(change);
            } catch (Exception e) {
                MODULE_LOG.warn("协同变更推送失败（忽略单连接异常）: {}", e.getMessage());
            }
        }
    }

    /**
     * 文档变更通知。
     *
     * @param id        协同文档号
     * @param version   变更后的版本号
     * @param updatedAt 变更时间戳
     * @param kind      变更类型：snapshot（保存内容）/ access（改权限）/ rename / create / delete
     */
    public record CollabChange(String id, long version, long updatedAt, String kind) {
    }

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
        markDirty();
        publish(new CollabChange(id, collab.version(), collab.updatedAt(), "create"));
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
     * 当前账号对某份协同文档是否有可写权限（可看即可编辑）。
     *
     * <p>与终端共享 {@code TerminalShareStore.canWrite} <b>同一口径</b>：
     * 公开 = 全员可写；私有 = 仅创建者与被授权用户可写。前端据 {@code canWrite}
     * 决定「权限 / 重命名 / 删除」等入口是否可用，后端在每个写接口再校验一次。</p>
     *
     * @param collab 协同文档（null 视为无权限）
     * @param userId 当前用户标识
     * @return true = 可写
     */
    public static boolean canWrite(FileCollab collab, String userId) {
        if (collab == null) {
            return false;
        }
        if (collab.visibility() == Visibility.PUBLIC) {
            return true;
        }
        if (userId == null || userId.isBlank()) {
            return false;
        }
        return collab.owner().equals(userId) || collab.allowedUserIds().contains(userId);
    }

    /**
     * 当前账号是否是某份协同文档的创建者（权限变更 / 删除的归属判定）。
     *
     * <p>归属与可写是两件事：被授权用户能编辑内容，但不能反向改授权名单、
     * 也不能删掉别人的文档 —— 与终端共享「仅发起者可改权限 / 取消共享」一致。</p>
     *
     * @param collab 协同文档（null 视为非创建者）
     * @param userId 当前用户标识
     * @return true = 创建者
     */
    public static boolean isOwner(FileCollab collab, String userId) {
        return collab != null && userId != null && !userId.isBlank() && collab.owner().equals(userId);
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
            if (!canWrite(c, userId)) {
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
     * <p><b>为什么必须走 {@code compute}。</b>「读 version → 比对 → 写回」如果分成三步，
     * 两个并发保存带同一个 {@code expectedVersion} 时会双双通过校验：两个人都收到
     * 「保存成功、版本 +1」，可其中一份内容被后写的那次覆盖掉了 —— 乐观锁形同虚设。
     * {@code ConcurrentHashMap.compute} 对同一个 key 持锁执行，比对与写入因此是原子的，
     * 顺带让 {@link #pushHistory} 也变成同 id 串行，历史列表不会被并发插坏。</p>
     *
     * @param id           协同文档号
     * @param snapshot     Univer 工作簿快照 JSON（可为 null 表示清空）
     * @param expectedVersion 期望的当前版本；不匹配时拒绝写入
     * @return 更新后的文档；文档不存在或版本不匹配返回 null
     */
    public FileCollab saveSnapshot(String id, String snapshot, long expectedVersion) {
        long now = System.currentTimeMillis();
        // compute 的 lambda 里只能通过副作用把结果带出来（返回值是「Map 里该放什么」）
        FileCollab[] written = new FileCollab[1];
        collabs.compute(id, (key, current) -> {
            if (current == null || current.version() != expectedVersion) {
                // 文档不存在或版本过期：保持原值（null 表示不创建）
                return current;
            }
            // 先把「当前内容」压入历史（版本号保持为保存前），再写入新快照
            pushHistory(current);
            FileCollab next = current.withSnapshot(snapshot, current.version() + 1, now);
            written[0] = next;
            return next;
        });
        if (written[0] == null) {
            return null;
        }
        markDirty();
        publish(new CollabChange(id, written[0].version(), written[0].updatedAt(), "snapshot"));
        return written[0];
    }

    /**
     * 把文档当前快照压入其历史列表（超上限丢弃最旧），仅当已有内容才记录。
     *
     * <p>只允许在 {@link #saveSnapshot} 的 {@code compute} 回调里调用：那里对同一个 id
     * 持锁，因此 {@code computeIfAbsent + add + 裁剪} 天然串行。列表本身是
     * {@link CopyOnWriteArrayList}，读侧（{@link #history} / {@link #versionAt}）在并发
     * 保存时也不会读到半个条目。</p>
     *
     * @param c 当前文档
     */
    private void pushHistory(FileCollab c) {
        if (c.snapshot() == null || c.snapshot().isEmpty()) {
            return;
        }
        List<SnapshotEntry> entries = history.computeIfAbsent(c.id(), k -> new CopyOnWriteArrayList<>());
        entries.add(new SnapshotEntry(c.version(), c.snapshot(), c.updatedAt()));
        if (entries.size() > MAX_HISTORY) {
            entries.subList(0, entries.size() - MAX_HISTORY).clear();
        }
    }

    /**
     * 重命名协同文档。
     *
     * <p>同样走 {@code compute}：如果先 {@code get} 再 {@code put} 一个新对象，
     * 中间夹进来的一次保存会被这个「旧快照的新副本」覆盖掉 —— 用户看到的是
     * 「改了个名字，内容退回到上一版」。在锁内基于当前值重建就不会。</p>
     *
     * @param id          协同文档号
     * @param newFileName 新展示文件名（含扩展名）
     * @return 更新后的文档；不存在返回 null
     */
    public FileCollab rename(String id, String newFileName) {
        if (newFileName == null || newFileName.isBlank()) {
            return null;
        }
        FileCollab[] updated = new FileCollab[1];
        collabs.compute(id, (key, c) -> {
            if (c == null) {
                return null;
            }
            FileCollab next = new FileCollab(id, c.bucket(), c.objectKey(), newFileName.trim(),
                    c.owner(), c.visibility(), c.allowedUserIds(), c.createdAt(), c.updatedAt(),
                    c.snapshot(), c.version(), c.path(), c.type());
            updated[0] = next;
            return next;
        });
        if (updated[0] == null) {
            return null;
        }
        markDirty();
        publish(new CollabChange(id, updated[0].version(), updated[0].updatedAt(), "rename"));
        return updated[0];
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
        markDirty();
        publish(new CollabChange(copy.id(), copy.version(), copy.updatedAt(), "create"));
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
     * <p>走 {@code compute}：改权限不该顺手把并发保存上去的快照回退掉
     * （{@code withAccess} 会带上当时的 snapshot/version，先 get 再 put 就是典型的
     * 「读-改-写」竞态）。</p>
     *
     * @param id             协同文档号
     * @param visibility     新的可见性（null 表示不变）
     * @param allowedUserIds 新的授权用户集合（null 表示不变）
     * @return 更新后的文档；不存在返回 null
     */
    public FileCollab updateAccess(String id, Visibility visibility,
                                   java.util.Set<String> allowedUserIds) {
        FileCollab[] updated = new FileCollab[1];
        collabs.compute(id, (key, c) -> {
            if (c == null) {
                return null;
            }
            Visibility v = visibility == null ? c.visibility() : visibility;
            java.util.Set<String> users = allowedUserIds == null
                    ? c.allowedUserIds() : new java.util.LinkedHashSet<>(allowedUserIds);
            FileCollab next = c.withAccess(v, users, System.currentTimeMillis());
            updated[0] = next;
            return next;
        });
        if (updated[0] == null) {
            return null;
        }
        markDirty();
        publish(new CollabChange(id, updated[0].version(), updated[0].updatedAt(), "access"));
        return updated[0];
    }

    /**
     * 删除协同文档。
     *
     * @param id 协同文档号
     * @return 删除前是否存在
     */
    public boolean delete(String id) {
        boolean removed = collabs.remove(id) != null;
        history.remove(id);
        if (removed) {
            markDirty();
            publish(new CollabChange(id, -1, System.currentTimeMillis(), "delete"));
        }
        return removed;
    }

    /**
     * 当前协同文档总数。
     *
     * @return 总数
     */
    public int size() {
        return collabs.size();
    }

    /* ==================== 落盘用：整库导出 / 导入 ==================== */

    /**
     * 导出全部协同文档（落盘用）。
     *
     * <p>返回的是当前快照引用列表：{@link FileCollab} 本身是不可变 record，
     * 并发写入只会替换 Map 里的引用，不会改到已经交出去的这份。</p>
     *
     * @return 全部文档（顺序不保证）
     */
    public List<FileCollab> exportCollabs() {
        return new ArrayList<>(collabs.values());
    }

    /**
     * 导出全部历史快照（落盘用，列表已做浅拷贝）。
     *
     * @return id → 历史条目
     */
    public Map<String, List<SnapshotEntry>> exportHistory() {
        Map<String, List<SnapshotEntry>> copy = new java.util.LinkedHashMap<>();
        history.forEach((id, entries) -> copy.put(id, new ArrayList<>(entries)));
        return copy;
    }

    /**
     * 用落盘内容整体替换当前状态（启动装载用）。
     *
     * <p>只应在启动阶段调用：它会清空现有内存态。装载完成后<b>不会</b>标脏
     * ——刚读进来的内容和磁盘一致，不需要再写回去。</p>
     *
     * @param collabs 文档列表（null 视为空）
     * @param history 历史快照（null 视为空）
     */
    public void replaceAll(List<FileCollab> collabs, Map<String, List<SnapshotEntry>> history) {
        Map<String, FileCollab> nextCollabs = new ConcurrentHashMap<>();
        if (collabs != null) {
            for (FileCollab c : collabs) {
                if (c != null && c.id() != null && !c.id().isBlank()) {
                    nextCollabs.put(c.id(), c);
                }
            }
        }
        this.collabs.clear();
        this.collabs.putAll(nextCollabs);
        this.history.clear();
        if (history != null) {
            history.forEach((id, entries) -> {
                if (id != null && entries != null && !entries.isEmpty()) {
                    // 装载进来的列表也要是并发安全容器：读侧可能在请求线程上遍历它
                    this.history.put(id, new CopyOnWriteArrayList<>(entries));
                }
            });
        }
        // 装载后与磁盘一致：显式清掉脏标记
        dirty.set(false);
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