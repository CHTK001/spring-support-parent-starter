package com.chua.payment.support.dto;

/**
 * 支付调度任务更新请求
 *
 * @param cronExpression Cron表达式
 * @param enabled        是否启用
 * @author CH
 * @since 4.0.0
 */
public record PaymentSchedulerTaskUpdateDTO(
        String cronExpression,
        Boolean enabled) {
}
