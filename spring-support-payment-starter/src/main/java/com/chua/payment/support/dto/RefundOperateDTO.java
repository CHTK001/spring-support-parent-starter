package com.chua.payment.support.dto;

import java.math.BigDecimal;

/**
 * 退款操作 DTO
 *
 * @param operator           操作人
 * @param remark             备注
 * @param refundAmount       退款金额
 * @param thirdPartyRefundNo 第三方退款单号
 * @author CH
 * @since 4.0.0
 */
public record RefundOperateDTO(
        String operator,
        String remark,
        BigDecimal refundAmount,
        String thirdPartyRefundNo) {
}
