package com.chua.payment.support.vo;

import lombok.Data;

/**
 * 回调策略审计视图
 * @author CH
 * @since 4.0.0
 */
@Data
public class PaymentCallbackAuditVO {
    /**
     * 回调类型
     */
    private String callbackType;
    /**
     * 回调名称
     */
    private String callbackName;
    /**
     * 推荐模式
     */
    private String recommendedPattern;
    /**
     * 作用域标识符
     */
    private String scopedIdentifier;
    /**
     * 严格作用域
     */
    private Boolean strictScoped;
    /**
     * 生效优先级
     */
    private String effectivePriority;
    /**
     * 备注
     */
    private String notes;
}
