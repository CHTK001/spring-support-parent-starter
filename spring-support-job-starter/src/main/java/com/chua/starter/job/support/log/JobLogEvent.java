package com.chua.starter.job.support.log;

import java.time.Instant;
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
 * <p>EXECUTOR → SCHEDULER 上行采用紧凑 JSON 序列化 (避免 record 默认 {@code toString()}
 * 在 wire 上丢失类型信息, 同时修复 {@code SchedulerSyncBinder.logHandler} 的
 * {@code instanceof JobLogEvent} 永远为 false 的问题)。</p>
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
        Instant timestamp
) {

    /**
     * 状态枚举。
     *
     * @author CH
     * @since 4.0.0.42
     */
    public enum Status {
        /**
         * 执行成功
         */
        SUCCESS,
        /**
         * 执行失败
         */
        FAILURE,
        /**
         * 跳过 (handler 不存在等)
         */
        SKIPPED
    }

    /**
     * 构造成功事件。
     *
     * @param jobName     任务名
     * @param costMs      耗时 (毫秒)
     * @param executedAtMs 执行开始时间戳
     * @param nodeId      当前节点
     * @return 事件
     */
    public static JobLogEvent success(String jobName, long costMs, long executedAtMs, String nodeId) {
        return new JobLogEvent(jobName, Status.SUCCESS, costMs, null,
                executedAtMs, nodeId, Instant.now());
    }

    /**
     * 构造失败事件。
     *
     * @param jobName     任务名
     * @param costMs      耗时 (毫秒)
     * @param throwable   异常
     * @param executedAtMs 执行开始时间戳
     * @param nodeId      当前节点
     * @return 事件
     */
    public static JobLogEvent failure(String jobName, long costMs, Throwable throwable,
                                      long executedAtMs, String nodeId) {
        String message = extractMessage(throwable);
        return new JobLogEvent(jobName, Status.FAILURE, costMs, message,
                executedAtMs, nodeId, Instant.now());
    }

    /**
     * 从异常链中提取第一条非 null 的 message (兼容被 wrapper (InvocationTargetException/
     * ExecutionException) 包装后根因 getMessage() 变 null 的情况)。
     *
     * @param throwable 异常
     * @return 异常消息; 全链都 null 时返回类名
     */
    private static String extractMessage(Throwable throwable) {
        if (throwable == null) {
            return null;
        }
        Throwable cur = throwable;
        int depth = 0;
        while (cur != null && depth++ < 10) {
            String msg = cur.getMessage();
            if (msg != null && !msg.isEmpty()) {
                return msg;
            }
            cur = cur.getCause();
        }
        return throwable.getClass().getSimpleName();
    }

    /**
     * 构造跳过事件。
     *
     * @param jobName 任务名
     * @param reason  原因
     * @param nodeId  当前节点
     * @return 事件
     */
    public static JobLogEvent skipped(String jobName, String reason, String nodeId) {
        return new JobLogEvent(jobName, Status.SKIPPED, 0L, reason,
                System.currentTimeMillis(), nodeId, Instant.now());
    }

    /**
     * 序列化为 wire 格式 (复用 record 默认 {@code toString()}, 无依赖)。
     *
     * <p>格式: {@code JobLogEvent[jobName=..., status=..., costMs=..., exception=..., executedAtMs=..., nodeId=..., timestamp=...]}</p>
     *
     * @return wire 字符串
     */
    public String toJson() {
        return toString();
    }

    /**
     * 从 record 默认 {@code toString()} 输出反序列化。
     *
     * @param text record toString 输出
     * @return 事件, 解析失败返回 null
     */
    public static JobLogEvent fromJson(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        try {
            String jobName = readField(text, "jobName");
            String statusStr = readField(text, "status");
            Status status = statusStr == null ? null : Status.valueOf(statusStr);
            long costMs = readLongField(text, "costMs");
            String exception = readField(text, "exception");
            if ("null".equals(exception)) {
                exception = null;
            }
            long executedAtMs = readLongField(text, "executedAtMs");
            String nodeId = readField(text, "nodeId");
            long ts = readLongField(text, "timestamp");
            return new JobLogEvent(jobName, status, costMs, exception, executedAtMs, nodeId, Instant.ofEpochMilli(ts));
        } catch (Exception ex) {
            return null;
        }
    }

    private static final Pattern FIELD_PATTERN = Pattern.compile("\\b([a-zA-Z]+)=([^,\\]]*)");

    /**
     * 从 record toString 中读取一个字符串字段。
     *
     * @param text 源文本
     * @param name 字段名
     * @return 值, 未找到返回 null
     */
    private static String readField(String text, String name) {
        Matcher m = FIELD_PATTERN.matcher(text);
        while (m.find()) {
            if (m.group(1).equals(name)) {
                return m.group(2);
            }
        }
        return null;
    }

    /**
     * 从 record toString 中读取一个长整数字段。
     *
     * @param text 源文本
     * @param name 字段名
     * @return 数值, 缺失或解析失败返回 0
     */
    private static long readLongField(String text, String name) {
        String v = readField(text, name);
        if (v == null || v.isEmpty() || "null".equals(v)) {
            return 0L;
        }
        try {
            return Long.parseLong(v.trim());
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }
}
