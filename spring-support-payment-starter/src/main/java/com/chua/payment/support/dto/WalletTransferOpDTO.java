package com.chua.payment.support.dto;

import java.math.BigDecimal;

/**
 * 钱包转账操作请求参数对象。
 *
 * @param merchantId  商户ID
 * @param fromUserId  转出用户ID
 * @param toUserId    转入用户ID
 * @param amount      转账金额
 * @param transferNo  转账单号
 * @param operator    操作人
 * @param remark      备注
 * @author CH
 * @since 4.0.0.50
 */
public record WalletTransferOpDTO(
        Long merchantId,
        Long fromUserId,
        Long toUserId,
        BigDecimal amount,
        String transferNo,
        String operator,
        String remark
) {
}
