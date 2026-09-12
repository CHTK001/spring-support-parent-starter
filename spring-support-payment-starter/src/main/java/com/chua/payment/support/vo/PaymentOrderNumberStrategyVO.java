package com.chua.payment.support.vo;

import lombok.Data;

/**
* 订单号策略视图
* @author CH
* @since 4.0.0
 */
@Data
public class PaymentOrderNumberStrategyVO {
    /** 业务类型 */
    private String businessType;
    /** 字段名称 */
    private String fieldName;
    /** Generationrule */
    private String generationRule;
    /** Calleroverridefield */
    private String callerOverrideField;
    /** Idempotentrule */
    private String idempotentRule;
    /** 笔记 */
    private String notes;
}

