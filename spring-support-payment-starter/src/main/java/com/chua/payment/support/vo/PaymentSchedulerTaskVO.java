package com.chua.payment.support.vo;

import java.time.LocalDateTime;

/**
 * 支付调度任务视图
 *
 * @param taskKey           任务键
 * @param taskName          任务名称
 * @param cronExpression    Cron表达式
 * @param enabled           是否启用
 * @param description       描述
 * @param scheduled         是否已调度
 * @param nextExecutionTime 下一次执行时间
 * @param lastStartedAt     最后启动时间
 * @param lastFinishedAt    最后完成时间
 * @param lastRunStatus     最后运行状态
 * @param lastRunMessage    最后运行消息
 * @author CH
 * @since 4.0.0
 */
public record PaymentSchedulerTaskVO(
        String taskKey,
        String taskName,
        String cronExpression,
        Boolean enabled,
        String description,
        Boolean scheduled,
        LocalDateTime nextExecutionTime,
        LocalDateTime lastStartedAt,
        LocalDateTime lastFinishedAt,
        String lastRunStatus,
        String lastRunMessage) {
}
