// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.job.support.log;

import java.time.Instant;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 任务执行日志事件 (record)。
 *
 * <p>由 EXECUTOR 端的 {@link com.chua.starter.job.support.execute.JobExecutor}
 * 在 处理器.invoke 之后构造, 经 {@link com.chua.starter.job.support.sync.ExecutorSyncBinder}
 * 上行到 调度器, 由 {@link com.chua.starter.job.support.sync.SchedulerSyncBinder}
 * 写入 {@link JobLogSink}。</p>
 *
 * <p>SCHEDULER 自身的本地执行 (SINGLE 模式) 同样适用本事件,
 * 直接在进程内构造后提交给 sink。</p>
 *
 * <h3>Wire 协议</h3>
 * <p>EXECUTOR → SCHEDULER 上行采用紧凑 JSON 序列化, logContent 字段使用 Base64 编码
 * 确保多行/特殊字符不会破坏解析。</p>
 *
 * <p>本 record 没有紧凑构造器, 因此九个组件都不做校验: 除 {@code status} 之外全部
 * 允许为 {@code null}（{@code costMs} / {@code executedAtMs} 为基本类型, 反序列化缺失时
 * 补 0）。取值来源与约束见各组件说明。</p>
 *
 * @param jobName 作业名称, 即被触发的 JobHandler 标识, 落库时写入
 *                {@code sys_job_log_trigger_bean}, 同时用于反查任务台账补齐触发方式。
 *                取值来源为 EXECUTOR 收到的派发命令; wire 上允许为 {@code null}
 * @param status  本轮执行的终态, 取值仅 {@link Status#SUCCESS} / {@link Status#FAILURE} /
 *                {@link Status#SKIPPED}, 落库时同时写入 {@code sys_job_log_trigger_code} 与
 *                {@code sys_job_log_execute_code}。允许为 {@code null}（仅 wire 状态字段缺失时出现）
 * @param costMs  本轮执行耗时, 单位毫秒, 取值 {@code 结束时刻 - 开始时刻}, 含全部重试与重试间隔,
 *                不是单次尝试耗时; {@link Status#SKIPPED} 固定记 0。落库写入 {@code sys_job_log_cost}。
 *                不允许为负; 反序列化缺失时补 0
 * @param exception 失败原因文本, 仅在 {@link Status#FAILURE} 时由 {@code extractMessage} 从抛出对象
 *                  逐层上溯（最多 10 层）取首个非空 message, 全为空时退化为异常类简名;
 *                  {@link Status#SKIPPED} 时存放跳过原因（如 handler 未注册）,
 *                  {@link Status#SUCCESS} 时为 {@code null}。允许为 {@code null};
 *                  wire 上经 Base64 编码以防多行栈信息破坏行协议
 * @param executedAtMs 本轮执行的开始时刻, 为 {@code System.currentTimeMillis()} 得到的 epoch 毫秒
 *                    时间戳（不是结束时刻, 也不是 {@code timestamp}）, 落库时同时写入
 *                    {@code sys_job_log_trigger_time} 与 {@code sys_job_log_trigger_date}。
 *                    必须为正数; 反序列化缺失或非法时补 0
 * @param nodeId  执行节点标识, 用于区分同一任务由哪个实例跑完。取值来源为
 *               {@code JobProperties#nodeId}, 未显式配置时由 {@code jobNodeId} Bean 按
 *               JVM 运行名 + UUID 哈希派生, 落库时写入 {@code sys_job_log_node_id}。
 *               允许为 {@code null}
 * @param timestamp 事件构造时刻, 取 {@code Instant.now()}, 表示这条日志被组装的时间,
 *                  与 {@code executedAtMs} 的执行开始时刻语义不同, 二者可能相差一次执行时长。
 *                  wire 上以 {@code toEpochMilli()} 平铺传输, 空值序列化为 0;
 *                  {@code fromJson} 以 {@code Instant.ofEpochMilli} 反解。允许为 {@code null}
 * @param logContent 执行日志正文, 为多行文本（重试轨迹、回调执行、脚本输出等）,
 *                  由 EXECUTOR 侧 {@code JobLogHelper} 收集拼接; 无正文时为 {@code null}。
 *                  允许为 {@code null}; wire 上经 UTF-8 + Base64 编码保证单行安全,
 *                  落库时与 {@code exception} 合并成结果说明（超 4000 列截断）
 * @param triggerType 本轮触发方式, 用于区分调度中心手动触发与定时触发。
 *                  已知取值 {@link #TRIGGER_MANUAL}（{@code MANUAL}）表示调度中心点的
 *                  立即运行/立即触发; 定时触发时传 {@code null}。落库时非空照记,
 *                  为空则由 {@code MybatisJobLogSink} 回退到任务台账的调度类型（缺省 {@code CRON}）
 * @since 4.0.0.42
 * @author CH
 */
public record JobLogEvent(
        String jobName,
        Status status,
        long costMs,
        String exception,
        long executedAtMs,
        String nodeId,
        Instant timestamp,
        String logContent,
        String triggerType
) {

    /**
     * 触发方式: 有人在调度中心点的立即运行/立即触发
     */
    public static final String TRIGGER_MANUAL = "MANUAL";

    /**
     * 状态枚举。
     *
     * @since 4.0.0.42
     * @author CH
     */
    public enum Status {
        SUCCESS, FAILURE, SKIPPED
    }

    /**
     * 构造成功事件 (无日志内容)。
     * @param jobName 作业名称
     * @param costMs costms
     * @param executedAtMs 执行atms
     * @param nodeId 节点标识
     * @return 成功的结果
     */
    public static JobLogEvent success(String jobName, long costMs, long executedAtMs, String nodeId) {
        return new JobLogEvent(jobName, Status.SUCCESS, costMs, null,
                executedAtMs, nodeId, Instant.now(), null);
    }

    /**
     * 构造成功事件 (含日志内容)。
     * @param jobName 作业名称
     * @param costMs costms
     * @param executedAtMs 执行atms
     * @param nodeId 节点标识
     * @param logContent 日志内容
     * @return 成功的结果
     */
    public static JobLogEvent success(String jobName, long costMs, long executedAtMs, String nodeId, String logContent) {
        return new JobLogEvent(jobName, Status.SUCCESS, costMs, null,
                executedAtMs, nodeId, Instant.now(), logContent);
    }

    /**
     * 构造成功事件 (含日志内容与本轮触发方式)。
     *
     * @param triggerType 本轮触发方式, 为空时由落库侧回退台账的调度类型
     * @return 成功的结果
     */
    public static JobLogEvent success(String jobName, long costMs, long executedAtMs, String nodeId,
                                      String logContent, String triggerType) {
        return withTrigger(new JobLogEvent(jobName, Status.SUCCESS, costMs, null,
                executedAtMs, nodeId, Instant.now(), logContent), triggerType);
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
     * @param throwable 抛出
     * @return extract消息的结果
     */
    public static JobLogEvent failure(String jobName, long costMs, Throwable throwable,
                                      long executedAtMs, String nodeId, String logContent) {
        String message = extractMessage(throwable);
        return new JobLogEvent(jobName, Status.FAILURE, costMs, message,
                executedAtMs, nodeId, Instant.now(), logContent);
    }

    /**
     * 构造失败事件 (含日志内容与本轮触发方式)。
     *
     * @param triggerType 本轮触发方式, 为空时由落库侧回退台账的调度类型
     * @return 失败的结果
     */
    public static JobLogEvent failure(String jobName, long costMs, Throwable throwable,
                                      long executedAtMs, String nodeId, String logContent, String triggerType) {
        String message = extractMessage(throwable);
        return withTrigger(new JobLogEvent(jobName, Status.FAILURE, costMs, message,
                executedAtMs, nodeId, Instant.now(), logContent), triggerType);
    }

    /**
     * 兼容不关心触发方式的构造 (8 参)。
     */
    public JobLogEvent(String jobName, Status status, long costMs, String exception, long executedAtMs,
                       String nodeId, Instant timestamp, String logContent) {
        this(jobName, status, costMs, exception, executedAtMs, nodeId, timestamp, logContent, null);
    }

    /**
     * 给事件补上本轮触发方式。
     *
     * @param event       原事件
     * @param triggerType 触发方式, 为空保持原样
     * @return 带触发方式的事件
     */
    private static JobLogEvent withTrigger(JobLogEvent event, String triggerType) {
        return triggerType == null ? event
                : new JobLogEvent(event.jobName(), event.status(), event.costMs(), event.exception(),
                event.executedAtMs(), event.nodeId(), event.timestamp(), event.logContent(), triggerType);
    }

    /**
     * extract消息。
     *
     * @param throwable 方法入参 throwable
     * @return 结果字符串
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
     * @param jobName 作业名称
     * @param reason ReasonMLML
     * @param nodeId 节点标识
     * @return skipped的结果
     */
    public static JobLogEvent skipped(String jobName, String reason, String nodeId) {
        return new JobLogEvent(jobName, Status.SKIPPED, 0L, reason,
                System.currentTimeMillis(), nodeId, Instant.now(), null);
    }

    /**
     * 序列化为 wire 格式 (基础64 编码 日志内容)。
     * @return 转为json的结果
     */
    public String toJson() {
        return "JobLogEvent["
                + "jobName=" + nvl(jobName)
                + ", status=" + status
                + ", costMs=" + costMs
                + ", exception=" + encodeField(exception)
                + ", executedAtMs=" + executedAtMs
                + ", nodeId=" + nvl(nodeId)
                + ", timestamp=" + (timestamp == null ? 0 : timestamp.toEpochMilli())
                + ", logContent=" + encodeLogContent(logContent)
                + ", triggerType=" + nvl(triggerType)
                + "]";
    }

    /**
     * 从 wire 格式反序列化。
     * @param text 文本
     * @return 从json的结果
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
            String exception = decodeField(readField(text, "exception"));
            long executedAtMs = readLongField(text, "executedAtMs");
            String nodeId = readField(text, "nodeId");
            long ts = readLongField(text, "timestamp");
            String logContent = readField(text, "logContent");
            if ("null".equals(logContent)) {
                logContent = null;
            } else {
                logContent = decodeLogContent(logContent);
            }
            String triggerType = readField(text, "triggerType");
            if ("null".equals(triggerType)) {
                triggerType = null;
            }
            return new JobLogEvent(jobName, status, costMs, exception, executedAtMs, nodeId,
                    Instant.ofEpochMilli(ts), logContent, triggerType);
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * 字段模式。
     */
    private static final Pattern FIELD_PATTERN = Pattern.compile("\\b([a-zA-Z]+)=([^,\\]]*)");

    /**
     * 读取字段。
     * @param text 文本
     * @param name 名称
     * @return 读取字段的结果
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
     * 读取long字段。
     * @param text 文本
     * @param name 名称
     * @return 读取long字段的结果
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

    /**
     * nvl。
     * @param s s
     * @return nvl的结果
     */
    private static String nvl(String s) {
        return s == null ? "null" : s;
    }

    /**
     * 编码Log内容。
     *
     * @param content 内容，不允许为 null
     * @return 结果字符串
     */
    private static String encodeLogContent(String content) {
        if (content == null) {
            return "null";
        }
        return Base64.getEncoder().encodeToString(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /**
     * 编码可能含换行/逗号的字段（异常消息）。
     *
     * <p>wire 是行协议（{@code topic:payload} 按换行切分），异常的多行栈信息若原样写入，
     * 会把一行拆成多行，接收端只能解析到残行——表现就是日志 executedAtMs=0、nodeId 丢失、
     * message 截断。这里与 logContent 一样做 Base64，保证单行安全。</p>
     *
     * @param content 字段内容，可为 null
     * @return "null" 或 Base64 文本
     */
    private static String encodeField(String content) {
        if (content == null) {
            return "null";
        }
        return Base64.getEncoder().encodeToString(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /**
     * 解码字段：Base64 失败时回退原文（兼容旧版未编码的 wire 报文）。
     *
     * @param encoded 编码文本或旧版原文
     * @return 原文，null 表示无
     */
    private static String decodeField(String encoded) {
        if (encoded == null || encoded.isEmpty() || "null".equals(encoded)) {
            return null;
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(encoded);
            return new String(decoded, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception ex) {
            return encoded;
        }
    }

    /**
     * 解码Log内容。
     *
     * @param encoded 方法入参 encoded
     * @return 结果字符串
     */
    private static String decodeLogContent(String encoded) {
        if (encoded == null || encoded.isEmpty()) {
            return null;
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(encoded);
            return new String(decoded, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception ex) {
            return null;
        }
    }
}
