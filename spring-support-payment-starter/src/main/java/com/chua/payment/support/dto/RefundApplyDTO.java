package com.chua.payment.support.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 退款申请 DTO
 */
@Data
public class RefundApplyDTO {

    private BigDecimal refundAmount;

    private String refundReason;

    private String operator;
}
