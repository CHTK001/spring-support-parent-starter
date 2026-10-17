package com.chua.payment.support.dto;

import java.math.BigDecimal;

/**
 * 钱包充值DTO
 *
 * @param merchantId 商户ID
 * @param userId     用户标识
 * @param rechargeNo 充值单号
 * @param notifyUrl  通知URL
 * @param amount     金额
 * @param operator   操作人
 * @param remark     备注
 * @author CH
 * @since 4.0.0
 */
public record WalletRechargeDTO(
        Long merchantId,
        Long userId,
        String rechargeNo,
        String notifyUrl,
        BigDecimal amount,
        String operator,
        String remark) {
}
