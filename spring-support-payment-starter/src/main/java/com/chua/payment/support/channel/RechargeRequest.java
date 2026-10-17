package com.chua.payment.support.channel;

import java.math.BigDecimal;

/**
 * 充值请求
 *
 * @param rechargeNo 充值单号
 * @param notifyUrl  通知回调 URL
 * @param merchantId 商户 ID
 * @param userId     用户标识
 * @param amount     充值金额
 * @param operator   操作人
 * @param remark     备注
 * @author CH
 * @since 4.0.0
 */
public record RechargeRequest(
        String rechargeNo,
        String notifyUrl,
        Long merchantId,
        Long userId,
        BigDecimal amount,
        String operator,
        String remark) {
}
