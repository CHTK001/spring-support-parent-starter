package com.chua.payment.support.vo;

import lombok.Data;

/**
 * 订单号策略视图
 * @author CH
 */
@Data
public class PaymentOrderNumberStrategyVO {
    /** Business类型 */
    private String businessType;
    /** Field名称 */
    private String fieldName;
    /** Generationrule */
    private String generationRule;
    /** Calleroverridefield */
    private String callerOverrideField;
    /** Idempotentrule */
    private String idempotentRule;
    /** Notes */
    private String notes;
}

