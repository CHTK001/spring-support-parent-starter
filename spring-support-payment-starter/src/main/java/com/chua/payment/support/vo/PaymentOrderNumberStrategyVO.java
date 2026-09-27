package com.chua.payment.support.vo;

import lombok.Data;

/**
 * 订单号策略视图
 * @author CH
 * @since 4.0.0
 */
@Data
public class PaymentOrderNumberStrategyVO {
    /**
     * 业务类型
     */
    private String businessType;
    /**
     * 字段名称
     */
    private String fieldName;
    /**
     * 生成规则
     */
    private String generationRule;
    /**
     * 调用方覆盖字段
     */
    private String callerOverrideField;
    /**
     * 幂等规则
     */
    private String idempotentRule;
    /**
     * 备注
     */
    private String notes;
}
