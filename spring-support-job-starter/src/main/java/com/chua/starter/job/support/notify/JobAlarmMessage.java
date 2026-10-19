package com.chua.starter.job.support.notify;

/**
 * 失败告警消息（专用对象）。
 *
 * <p>执行器在任务最终失败后按台账的告警配置构造本对象，交给对应类型的
 * {@link JobAlarmNotifier} 投递。把"告警"从早期的一根邮箱字符串收敛成一个对象，
 * 是为了让 <b>类型</b>（邮件 / Webhook / 以后的 IM）、<b>目标</b>、失败上下文
 * 有统一的载体：新增通道只需实现 SPI 并按类型注册，执行器与台账结构都不用再动。</p>
 *
 * @param jobName      任务键（处理器名 / 脚本任务标识）
 * @param alarmType    告警类型，见 {@link #TYPE_EMAIL} / {@link #TYPE_WEBHOOK}
 * @param target       告警目标：邮箱（逗号分隔）或 Webhook 地址
 * @param costMs       本轮执行耗时毫秒
 * @param exception    失败原因（可能多行）
 * @param nodeId       执行节点标识
 * @param occurredAtMs 告警发生时刻（毫秒时间戳）
 * @since 4.0.0.42
 * @author CH
 */
public record JobAlarmMessage(
        String jobName,
        String alarmType,
        String target,
        long costMs,
        String exception,
        String nodeId,
        long occurredAtMs) {

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
}
