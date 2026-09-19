package com.chua.payment.support.dto;

import java.math.BigDecimal;

/**
 * 钱包提现请求参数对象。
 *
 * @param merchantId 商户标识
 * @param userId     用户标识
 * @param amount     提现金额
 * @param withdrawNo 提现单号
 * @param operator   操作人
 * @param remark     备注
 * @author CH
 * @since 4.0.0.50
 */
public record WalletWithdrawRequest(
        Long merchantId,
        Long userId,
        BigDecimal amount,
        String withdrawNo,
        String operator,
        String remark
) {
}
