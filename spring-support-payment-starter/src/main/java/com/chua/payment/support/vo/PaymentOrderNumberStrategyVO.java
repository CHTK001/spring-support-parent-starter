package com.chua.payment.support.vo;

/**
 * 订单号策略视图
 *
 * @param businessType        业务类型
 * @param fieldName           字段名称
 * @param generationRule      生成规则
 * @param callerOverrideField 调用方覆盖字段
 * @param idempotentRule      幂等规则
 * @param notes               备注
 * @author CH
 * @since 4.0.0
 */
public record PaymentOrderNumberStrategyVO(
        String businessType,
        String fieldName,
        String generationRule,
        String callerOverrideField,
        String idempotentRule,
        String notes) {
}
