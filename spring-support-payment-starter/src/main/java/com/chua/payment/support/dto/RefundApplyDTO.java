package com.chua.payment.support.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 退款申请 DTO
 * @author CH
 */
@Data
public class RefundApplyDTO {

    /** Refundamount */
    private BigDecimal refundAmount;

    /** Refundreason */
    private String refundReason;

    /** Operator */
    private String operator;
}

