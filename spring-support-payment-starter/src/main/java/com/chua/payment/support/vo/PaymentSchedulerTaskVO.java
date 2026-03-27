package com.chua.payment.support.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 支付调度任务视图
 */
@Data
public class PaymentSchedulerTaskVO {
    private String taskKey;
    private String taskName;
    private String cronExpression;
    private Boolean enabled;
    private String description;
    private Boolean scheduled;
    private LocalDateTime nextExecutionTime;
    private LocalDateTime lastStartedAt;
    private LocalDateTime lastFinishedAt;
    private String lastRunStatus;
    private String lastRunMessage;
}
