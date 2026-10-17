package com.chua.payment.support.dto;

import java.math.BigDecimal;

/**
 * 退款申请执行DTO
 *
 * @param refundAmount 退款金额
 * @param refundReason 退款原因
 * @param operator     操作人
 * @author CH
 * @since 4.0.0
 */
public record RefundApplyDTO(
        BigDecimal refundAmount,
        String refundReason,
        String operator) {
}
