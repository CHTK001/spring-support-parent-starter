package com.chua.payment.support.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 支付调度任务视图
 * @author CH
 */
@Data
public class PaymentSchedulerTaskVO {
    /** 任务密钥 */
    private String taskKey;
    /** 任务名称 */
    private String taskName;
    /** Cron表达式 */
    private String cronExpression;
    /** 是否启用 */
    private Boolean enabled;
    /** 描述 */
    private String description;
    /** Scheduled */
    private Boolean scheduled;
    /** 下一个execution时间 */
    private LocalDateTime nextExecutionTime;
    /** 最后startedAT */
    private LocalDateTime lastStartedAt;
    /** 最后finishedAT */
    private LocalDateTime lastFinishedAt;
    /** 最后RUN状态 */
    private String lastRunStatus;
    /** 最后RUN消息 */
    private String lastRunMessage;
}

