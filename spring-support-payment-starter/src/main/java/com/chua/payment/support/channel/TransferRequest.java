package com.chua.payment.support.channel;

import java.math.BigDecimal;

/**
 * 转账请求
 *
 * @param transferNo 转账单号
 * @param notifyUrl  通知回调 URL
 * @param merchantId 商户 ID
 * @param fromUserId 转出用户标识
 * @param toUserId   转入用户标识
 * @param amount     转账金额
 * @param operator   操作人
 * @param remark     备注
 * @author CH
 * @since 4.0.0
 */
public record TransferRequest(
        String transferNo,
        String notifyUrl,
        Long merchantId,
        Long fromUserId,
        Long toUserId,
        BigDecimal amount,
        String operator,
        String remark) {
}
