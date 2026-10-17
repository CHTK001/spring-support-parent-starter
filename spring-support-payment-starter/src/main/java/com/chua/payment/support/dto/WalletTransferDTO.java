package com.chua.payment.support.dto;

import java.math.BigDecimal;

/**
 * 钱包转账DTO
 *
 * @param merchantId 商户ID
 * @param fromUserId 转出用户标识
 * @param toUserId   转入用户标识
 * @param transferNo 调拨单号
 * @param notifyUrl  通知URL
 * @param amount     金额
 * @param operator   操作人
 * @param remark     备注
 * @author CH
 * @since 4.0.0
 */
public record WalletTransferDTO(
        Long merchantId,
        Long fromUserId,
        Long toUserId,
        String transferNo,
        String notifyUrl,
        BigDecimal amount,
        String operator,
        String remark) {
}
