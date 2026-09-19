package com.chua.payment.support.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 退款申请 DTO
 * @author CH
 * @since 4.0.0
 */
@Data
public class RefundApplyDTO {

    /**
     * Refundamount
    */
    private BigDecimal refundAmount;

    /**
     * Refundreason
    */
    private String refundReason;

    /**
     * 操作符
    */
    private String operator;
}

