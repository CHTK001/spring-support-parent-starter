package com.chua.payment.support.vo;

/**
 * 回调策略审计视图
 *
 * @param callbackType       回调类型
 * @param callbackName       回调名称
 * @param recommendedPattern 推荐模式
 * @param scopedIdentifier   作用域标识符
 * @param strictScoped       严格作用域
 * @param effectivePriority  生效优先级
 * @param notes              备注
 * @author CH
 * @since 4.0.0
 */
public record PaymentCallbackAuditVO(
        String callbackType,
        String callbackName,
        String recommendedPattern,
        String scopedIdentifier,
        Boolean strictScoped,
        String effectivePriority,
        String notes) {
}
