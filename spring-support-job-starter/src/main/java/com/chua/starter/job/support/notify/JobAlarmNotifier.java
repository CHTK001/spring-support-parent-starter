package com.chua.starter.job.support.notify;

/**
 * 任务失败告警通道（SPI，按类型注册）。
 *
 * <p>任务最终失败且台账配置了告警目标时，执行器按 {@link JobAlarmMessage#alarmType()}
 * 选择同类型的实现投递。job-starter 不绑定具体通道：宿主提供实现 Bean 即接通，
 * 一个宿主可以同时注册多个类型（如邮件 + Webhook）。</p>
 *
 * <p>未注册任何实现时，执行器只在日志里记一条"未接入告警通道"，任务本身的终态不受影响；
 * 注册了实现但没有匹配目标类型的实现时，会记一条带类型的"未接入 X 类型告警通道"，
 * 让配置错误在任务日志里可见而不是静默丢失。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
public interface JobAlarmNotifier {

    /**
     * 本实现支持的告警类型，取值见 {@link JobAlarmMessage#TYPE_EMAIL} / {@link JobAlarmMessage#TYPE_WEBHOOK}。
     *
     * @return 类型标识（大小写不敏感）
     */
    String type();

    /**
     * 是否支持给定告警类型。
     *
     * @param alarmType 台账上的告警类型
     * @return 支持返回 true
     */
    default boolean support(String alarmType) {
        return type() != null && type().equalsIgnoreCase(JobAlarmMessage.normalizeType(alarmType));
    }

    /**
     * 投递失败告警。
     *
     * <p>实现应自行控制耗时并在失败时抛出异常：执行器会捕获异常并把它写进任务日志的告警段，
     * 但不会因此改变任务终态。</p>
     *
     * @param message 告警消息
     * @throws Exception 投递失败
     */
    void notify(JobAlarmMessage message) throws Exception;
}
