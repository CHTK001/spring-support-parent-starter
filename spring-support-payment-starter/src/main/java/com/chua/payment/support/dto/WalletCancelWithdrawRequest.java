package com.chua.payment.support.dto;

import java.math.BigDecimal;

/**
 * 钱包提现取消请求参数对象。
 *
 * @param merchantId 商户ID
 * @param userId     用户ID
 * @param bizNo      业务单号
 * @param amount     金额
 * @param remark     备注
 * @author CH
 * @since 4.0.0.50
 */
public record WalletCancelWithdrawRequest(
        Long merchantId,
        Long userId,
        String bizNo,
        BigDecimal amount,
        String remark
) {
}
