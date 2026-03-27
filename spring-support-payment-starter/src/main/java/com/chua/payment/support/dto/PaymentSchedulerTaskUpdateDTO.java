package com.chua.payment.support.dto;

import lombok.Data;

/**
 * 支付调度任务更新请求
 */
@Data
public class PaymentSchedulerTaskUpdateDTO {
    private String cronExpression;
    private Boolean enabled;
}
