package com.chua.payment.support.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 退款申请执行DTO
 * @author CH
 * @since 4.0.0
 */
@Data
public class RefundApplyDTO {

    /**
     * 退款金额
     */
    private BigDecimal refundAmount;

    /**
     * 退款原因
     */
    private String refundReason;

    /**
     * 操作人
     */
    private String operator;
}
