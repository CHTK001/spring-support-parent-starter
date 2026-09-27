package com.chua.starter.job.support.notify;

/**
 * 失败告警消息（专用对象）。
 *
 * <p>执行器在任务最终失败后按台账的告警配置构造本对象，交给对应类型的
 * {@link JobAlarmNotifier} 投递。把"告警"从早期的一根邮箱字符串收敛成一个对象，
 * 是为了让 <b>类型</b>（邮件 / Webhook / 以后的 IM）、<b>目标</b>、失败上下文
 * 有统一的载体：新增通道只需实现 SPI 并按类型注册，执行器与台账结构都不用再动。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
public class JobAlarmMessage {

    /**
     * 告警类型：邮件（目标为收件邮箱，多个逗号分隔）
     */
    public static final String TYPE_EMAIL = "EMAIL";

    /**
     * 告警类型：Webhook（目标为 HTTP 回调地址）
     */
    public static final String TYPE_WEBHOOK = "WEBHOOK";

    /**
     * 告警类型：不告警（台账上的占位值，执行器不会构造消息）
     */
    public static final String TYPE_NONE = "NONE";

    /**
     * 任务键（处理器名 / 脚本任务标识）
     */
    private String jobName;

    /**
     * 告警类型，见 {@link #TYPE_EMAIL} / {@link #TYPE_WEBHOOK}
     */
    private String alarmType;

    /**
     * 告警目标：邮箱（逗号分隔）或 Webhook 地址
     */
    private String target;

    /**
     * 本轮执行耗时毫秒
     */
    private long costMs;

    /**
     * 失败原因（可能多行）
     */
    private String exception;

    /**
     * 执行节点标识
     */
    private String nodeId;

    /**
     * 告警发生时刻（毫秒时间戳）
     */
    private long occurredAtMs;

    /**
     * 归一告警类型：空值回退 EMAIL（兼容只有告警邮箱、没有类型列的历史数据）。
     *
     * @param alarmType 原始类型
     * @return 归一后的类型
     */
    public static String normalizeType(String alarmType) {
        if (alarmType == null || alarmType.isBlank()) {
            return TYPE_EMAIL;
        }
        return alarmType.trim().toUpperCase(java.util.Locale.ROOT);
    }

    /**
     * 获取任务键。
     *
     * @return 任务键
     */
    public String getJobName() {
        return jobName;
    }

    /**
     * 设置任务键。
     *
     * @param jobName 任务键
     */
    public void setJobName(String jobName) {
        this.jobName = jobName;
    }

    /**
     * 获取告警类型。
     *
     * @return 告警类型
     */
    public String getAlarmType() {
        return alarmType;
    }

    /**
     * 设置告警类型。
     *
     * @param alarmType 告警类型
     */
    public void setAlarmType(String alarmType) {
        this.alarmType = alarmType;
    }

    /**
     * 获取告警目标。
     *
     * @return 邮箱或回调地址
     */
    public String getTarget() {
        return target;
    }

    /**
     * 设置告警目标。
     *
     * @param target 邮箱或回调地址
     */
    public void setTarget(String target) {
        this.target = target;
    }

    /**
     * 获取本轮耗时毫秒。
     *
     * @return 耗时毫秒
     */
    public long getCostMs() {
        return costMs;
    }

    /**
     * 设置本轮耗时毫秒。
     *
     * @param costMs 耗时毫秒
     */
    public void setCostMs(long costMs) {
        this.costMs = costMs;
    }

    /**
     * 获取失败原因。
     *
     * @return 失败原因
     */
    public String getException() {
        return exception;
    }

    /**
     * 设置失败原因。
     *
     * @param exception 失败原因
     */
    public void setException(String exception) {
        this.exception = exception;
    }

    /**
     * 获取执行节点标识。
     *
     * @return 节点标识
     */
    public String getNodeId() {
        return nodeId;
    }

    /**
     * 设置执行节点标识。
     *
     * @param nodeId 节点标识
     */
    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    /**
     * 获取告警发生时刻。
     *
     * @return 毫秒时间戳
     */
    public long getOccurredAtMs() {
        return occurredAtMs;
    }

    /**
     * 设置告警发生时刻。
     *
     * @param occurredAtMs 毫秒时间戳
     */
    public void setOccurredAtMs(long occurredAtMs) {
        this.occurredAtMs = occurredAtMs;
    }
}
