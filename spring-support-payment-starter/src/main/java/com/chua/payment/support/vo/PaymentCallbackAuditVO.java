package com.chua.payment.support.vo;

import lombok.Data;

/**
* 回调策略审计视图
* @author CH
* @since 4.0.0
 */
@Data
public class PaymentCallbackAuditVO {
    /** Callback类型 */
    private String callbackType;
    /** Callback名称 */
    private String callbackName;
    /** Recommended模式 */
    private String recommendedPattern;
    /** Scoped标识符 */
    private String scopedIdentifier;
    /** Strictscoped */
    private Boolean strictScoped;
    /** Effective优先级 */
    private String effectivePriority;
    /** 笔记 */
    private String notes;
}

