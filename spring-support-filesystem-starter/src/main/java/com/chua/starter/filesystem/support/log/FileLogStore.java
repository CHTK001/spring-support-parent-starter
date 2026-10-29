package com.chua.starter.filesystem.support.log;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 文件操作日志存储器（内存环形缓冲）。
 *
 * <p>记录文件服务器收到的每一次请求与上传任务的每一次状态变迁，
 * 供前端「文件日志」页分页/筛选/清空。数据仅存内存：服务重启即清空，
 * 适合作为运行期审计流水（非持久化存储）。</p>
 *
 * <p>线程安全：{@link ArrayDeque} 加锁保护，容量满时淘汰最旧条目。
 * 记录不抛出业务异常——日志失败不得影响文件操作主链路。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public class FileLogStore {

    /**
     * 默认容量（条数），超出后淘汰最旧
     */
    private static final int DEFAULT_CAPACITY = 2000;

    /**
     * 全局单例（文件服务器过滤器与控制器共用同一份流水）
     */
    private static final FileLogStore INSTANCE = new FileLogStore(DEFAULT_CAPACITY);

    /**
     * 自增序列（日志条目标识，新→旧排序用）
     */
    private final AtomicLong seq = new AtomicLong();

    /**
     * 环形缓冲（写入在锁内，尾部追加）
     */
    private final ArrayDeque<FileLogEntry> ring = new ArrayDeque<>();

    /**
     * 容量上限
     */
    private final int capacity;

    /**
     * 互斥锁
     */
    private final Object lock = new Object();

    /**
     * 创建日志存储器。
     *
     * @param capacity 容量上限
     */
    FileLogStore(int capacity) {
        this.capacity = Math.max(capacity, 1);
    }

    /**
     * 获取全局单例。
     *
     * @return 单例
     */
    public static FileLogStore getInstance() {
        return INSTANCE;
    }

    /**
     * 记录一条文件操作日志。
     *
     * <p>任何异常都不外抛：文件操作主链路不因日志失败而中断。</p>
     *
     * @param operation  操作类型（如 UPLOAD / DOWNLOAD / DELETE / RENAME / TASK_CREATE）
     * @param target     操作目标（文件路径 / 上传任务文件名 / 桶路径）
     * @param status     HTTP 状态码（非 HTTP 场景传 200；未知传 0）
     * @param durationMs 耗时（毫秒）
     * @param detail     补充说明（失败原因等，可为 {@code null}）
     */
    public void record(String operation, String target, int status, long durationMs, String detail) {
        try {
            long id = seq.incrementAndGet();
            FileLogEntry entry = new FileLogEntry(id, System.currentTimeMillis(),
                    operation, target == null ? "" : target, status,
                    Math.max(durationMs, 0), detail);
            synchronized (lock) {
                ring.addLast(entry);
                while (ring.size() > capacity) {
                    ring.removeFirst();
                }
            }
        } catch (Exception ignored) {
            // 日志失败不影响业务
        }
    }

    /**
     * 列出最近的日志（新→旧）。
     *
     * @param operation 操作类型过滤（空表示不过滤）
     * @param keyword   目标关键字过滤（空表示不过滤；大小写不敏感）
     * @param limit     最多返回条数（&le;0 表示不限制）
     * @return 按时间倒序的日志条目（不可变列表）
     */
    public List<FileLogEntry> list(String operation, String keyword, int limit) {
        synchronized (lock) {
            List<FileLogEntry> snapshot = new ArrayList<>(ring);
            if (operation != null && !operation.isBlank()) {
                snapshot.removeIf(e -> !operation.equalsIgnoreCase(e.operation()));
            }
            if (keyword != null && !keyword.isBlank()) {
                String kw = keyword.toLowerCase();
                snapshot.removeIf(e -> !e.target().toLowerCase().contains(kw));
            }
            Collections.reverse(snapshot);
            if (limit > 0 && snapshot.size() > limit) {
                return List.copyOf(snapshot.subList(0, limit));
            }
            return List.copyOf(snapshot);
        }
    }

    /**
     * 清空全部日志。
     *
     * @return 清空前的条数
     */
    public int clear() {
        synchronized (lock) {
            int size = ring.size();
            ring.clear();
            return size;
        }
    }

    /**
     * 删除早于给定截止时间的日志。
     *
     * <p>用于「保留最近 N 天」的定期清理：传入 {@code cutoffMillis}（毫秒时间戳），
     * 所有 {@code time < cutoffMillis} 的条目都会被移除。</p>
     *
     * @param cutoffMillis 截止时间戳（毫秒）；早于它的条目被删除
     * @return 被删除的条数
     */
    public int removeBefore(long cutoffMillis) {
        synchronized (lock) {
            int removed = 0;
            var it = ring.iterator();
            while (it.hasNext()) {
                if (it.next().time() < cutoffMillis) {
                    it.remove();
                    removed++;
                }
            }
            return removed;
        }
    }

    /**
     * 当前日志条数。
     *
     * @return 条数
     */
    public int size() {
        synchronized (lock) {
            return ring.size();
        }
    }

    /**
     * 文件操作日志条目。
     *
     * @param id         自增标识（新→旧排序依据）
     * @param time       记录时间（毫秒时间戳）
     * @param operation  操作类型
     * @param target     操作目标（文件路径 / 任务文件名）
     * @param status     HTTP 状态码
     * @param durationMs 耗时（毫秒）
     * @param detail     补充说明（可为 {@code null}）
     */
    public record FileLogEntry(long id, long time, String operation, String target,
                               int status, long durationMs, String detail) {
    }
}
