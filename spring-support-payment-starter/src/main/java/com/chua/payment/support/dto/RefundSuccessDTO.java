package com.chua.payment.support.dto;

import java.math.BigDecimal;

/**
 * 按退款单号标记退款成功请求参数对象。
 *
 * @param refundNo           退款单号
 * @param refundAmount       退款金额
 * @param thirdPartyRefundNo 第三方退款单号
 * @param operator           操作人
 * @param responsePayload    响应报文
 * @param remark             备注
 * @author CH
 * @since 4.0.0.50
 */
public record RefundSuccessDTO(
        String refundNo,
        BigDecimal refundAmount,
        String thirdPartyRefundNo,
        String operator,
        String responsePayload,
        String remark
) {
}
