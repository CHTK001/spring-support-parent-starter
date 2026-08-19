package com.chua.payment.support.dto;

import lombok.Data;

/**
 * 支付调度任务更新请求
 * @author CH
 */
@Data
public class PaymentSchedulerTaskUpdateDTO {
    /** Cron表达式 */
    private String cronExpression;
    /** 是否启用 */
    private Boolean enabled;
}

