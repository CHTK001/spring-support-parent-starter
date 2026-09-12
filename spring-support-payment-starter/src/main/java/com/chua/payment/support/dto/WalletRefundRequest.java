package com.chua.payment.support.dto;

import java.math.BigDecimal;

/**
* 钱包退款请求参数对象。
*
* @param merchantId 商户标识
* @param userId     用户标识
* @param amount     退款金额
* @param refundNo   退款单号
* @param operator   操作人
* @param remark     备注
* @author CH
* @since 4.0.0.50
 */
public record WalletRefundRequest(
        Long merchantId,
        Long userId,
        BigDecimal amount,
        String refundNo,
        String operator,
        String remark
) {
}
