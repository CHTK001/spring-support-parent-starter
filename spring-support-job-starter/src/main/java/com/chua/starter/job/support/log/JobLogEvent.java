package com.chua.starter.job.support.log;

import java.time.Instant;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 任务执行日志事件 (record)。
 *
 * <p>由 EXECUTOR 端的 {@link com.chua.starter.job.support.execute.JobExecutor}
 * 在 handler.invoke 之后构造, 经 {@link com.chua.starter.job.support.sync.ExecutorSyncBinder}
 * 上行到 SCHEDULER, 由 {@link com.chua.starter.job.support.sync.SchedulerSyncBinder}
 * 写入 {@link JobLogSink}。</p>
 *
 * <p>SCHEDULER 自身的本地执行 (SINGLE 模式) 同样适用本事件,
 * 直接在进程内构造后提交给 sink。</p>
 *
 * <h3>Wire 协议</h3>
 * <p>EXECUTOR → SCHEDULER 上行采用紧凑 JSON 序列化, logContent 字段使用 Base64 编码
 * 确保多行/特殊字符不会破坏解析。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public record JobLogEvent(
        String jobName,
        Status status,
        long costMs,
        String exception,
        long executedAtMs,
        String nodeId,
        Instant timestamp,
        String logContent
) {

    /**
     * 状态枚举。
     *
     * @author CH
     * @since 4.0.0.42
     */
    public enum Status {
        SUCCESS, FAILURE, SKIPPED
    }

    /**
     * 构造成功事件 (无日志内容)。
     */
    public static JobLogEvent success(String jobName, long costMs, long executedAtMs, String nodeId) {
        return new JobLogEvent(jobName, Status.SUCCESS, costMs, null,
                executedAtMs, nodeId, Instant.now(), null);
    }

    /**
     * 构造成功事件 (含日志内容)。
     */
    public static JobLogEvent success(String jobName, long costMs, long executedAtMs, String nodeId, String logContent) {
        return new JobLogEvent(jobName, Status.SUCCESS, costMs, null,
                executedAtMs, nodeId, Instant.now(), logContent);
    }

    /**
     * 构造失败事件 (无日志内容)。
     */
    public static JobLogEvent failure(String jobName, long costMs, Throwable throwable,
                                      long executedAtMs, String nodeId) {
        return failure(jobName, costMs, throwable, executedAtMs, nodeId, null);
    }

    /**
     * 构造失败事件 (含日志内容)。
     */
    public static JobLogEvent failure(String jobName, long costMs, Throwable throwable,
                                      long executedAtMs, String nodeId, String logContent) {
        String message = extractMessage(throwable);
        return new JobLogEvent(jobName, Status.FAILURE, costMs, message,
                executedAtMs, nodeId, Instant.now(), logContent);
    }

    private static String extractMessage(Throwable throwable) {
        if (throwable == null) return null;
        Throwable cur = throwable;
        int depth = 0;
        while (cur != null && depth++ < 10) {
            String msg = cur.getMessage();
            if (msg != null && !msg.isEmpty()) return msg;
            cur = cur.getCause();
        }
        return throwable.getClass().getSimpleName();
    }

    /**
     * 构造跳过事件。
     */
    public static JobLogEvent skipped(String jobName, String reason, String nodeId) {
        return new JobLogEvent(jobName, Status.SKIPPED, 0L, reason,
                System.currentTimeMillis(), nodeId, Instant.now(), null);
    }

    /**
     * 序列化为 wire 格式 (Base64 编码 logContent)。
     */
    public String toJson() {
        return "JobLogEvent["
                + "jobName=" + nvl(jobName)
                + ", status=" + status
                + ", costMs=" + costMs
                + ", exception=" + nvl(exception)
                + ", executedAtMs=" + executedAtMs
                + ", nodeId=" + nvl(nodeId)
                + ", timestamp=" + (timestamp == null ? 0 : timestamp.toEpochMilli())
                + ", logContent=" + encodeLogContent(logContent)
                + "]";
    }

    /**
     * 从 wire 格式反序列化。
     */
    public static JobLogEvent fromJson(String text) {
        if (text == null || text.isEmpty()) return null;
        try {
            String jobName = readField(text, "jobName");
            String statusStr = readField(text, "status");
            Status status = statusStr == null ? null : Status.valueOf(statusStr);
            long costMs = readLongField(text, "costMs");
            String exception = readField(text, "exception");
            if ("null".equals(exception)) exception = null;
            long executedAtMs = readLongField(text, "executedAtMs");
            String nodeId = readField(text, "nodeId");
            long ts = readLongField(text, "timestamp");
            String logContent = readField(text, "logContent");
            if ("null".equals(logContent)) {
                logContent = null;
            } else {
                logContent = decodeLogContent(logContent);
            }
            return new JobLogEvent(jobName, status, costMs, exception, executedAtMs, nodeId, Instant.ofEpochMilli(ts), logContent);
        } catch (Exception ex) {
            return null;
        }
    }

    private static final Pattern FIELD_PATTERN = Pattern.compile("\\b([a-zA-Z]+)=([^,\\]]*)");

    private static String readField(String text, String name) {
        Matcher m = FIELD_PATTERN.matcher(text);
        while (m.find()) {
            if (m.group(1).equals(name)) return m.group(2);
        }
        return null;
    }

    private static long readLongField(String text, String name) {
        String v = readField(text, name);
        if (v == null || v.isEmpty() || "null".equals(v)) return 0L;
        try {
            return Long.parseLong(v.trim());
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    private static String nvl(String s) {
        return s == null ? "null" : s;
    }

    private static String encodeLogContent(String content) {
        if (content == null) return "null";
        return Base64.getEncoder().encodeToString(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static String decodeLogContent(String encoded) {
        if (encoded == null || encoded.isEmpty()) return null;
        try {
            byte[] decoded = Base64.getDecoder().decode(encoded);
            return new String(decoded, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception ex) {
            return null;
        }
    }
}