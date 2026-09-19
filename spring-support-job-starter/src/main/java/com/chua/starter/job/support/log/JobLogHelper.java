package com.chua.starter.job.support.log;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 任务日志收集器 (执行器 端)。
 *
 * <p>在 handler 执行期间通过 {@link #log(String)} 收集日志文本,
 * 执行完成后由 {@link JobExecutor} 提取并随 {@link JobLogEvent} 上行到 调度器。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
public final class JobLogHelper {

    /** 时间_fmt */
    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
                    .withZone(ZoneId.systemDefault());

    /** 日志_缓冲 */
    private static final ThreadLocal<StringBuilder> LOG_BUFFER = new ThreadLocal<>();

    /** 创建 作业日志助手 实例 */
    private JobLogHelper() {
    }

    /**
     * 写入一行日志文本。
     *
     * @param msg 日志消息
     */
    public static void log(String msg) {
        StringBuilder sb = LOG_BUFFER.get();
        if (sb == null) {
            sb = new StringBuilder(256);
            LOG_BUFFER.set(sb);
        }
        sb.append(TIME_FMT.format(Instant.now())).append(" - ").append(msg).append('\n');
    }

    /**
     * 获取并清空当前线程的日志缓冲区。
     *
     * @return 日志文本; 无日志时返回 空
     */
    public static String getAndClear() {
        StringBuilder sb = LOG_BUFFER.get();
        if (sb == null || sb.isEmpty()) {
            LOG_BUFFER.remove();
            return null;
        }
        LOG_BUFFER.remove();
        return sb.toString();
    }

    /**
     * 清空当前线程的日志缓冲区（异常回滚时调用）。
     */
    public static void clear() {
        LOG_BUFFER.remove();
    }
}
