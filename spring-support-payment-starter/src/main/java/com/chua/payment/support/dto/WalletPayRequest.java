package com.chua.payment.support.dto;

import java.math.BigDecimal;

/**
 * 钱包支付请求参数对象。
 *
 * @param merchantId 商户ID
 * @param userId     用户ID
 * @param amount     支付金额
 * @param orderNo    订单号
 * @param operator   操作人
 * @param remark     备注
 * @author CH
 * @since 4.0.0.50
 */
public record WalletPayRequest(
        Long merchantId,
        Long userId,
        BigDecimal amount,
        String orderNo,
        String operator,
        String remark
) {
}
