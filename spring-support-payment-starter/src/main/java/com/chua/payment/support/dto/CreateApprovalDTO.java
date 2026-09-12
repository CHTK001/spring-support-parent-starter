package com.chua.payment.support.dto;

import java.math.BigDecimal;

/**
* 创建提现审批记录请求参数对象。
*
* @param merchantId  商户标识
* @param userId      用户标识
* @param amount      提现金额
* @param bankAccount 银行卡号
* @param bankName    银行名称
* @param accountName 账户名
* @param withdrawNo  提现单号
* @param remark      备注
* @author CH
* @since 4.0.0.50
 */
public record CreateApprovalDTO(
        Long merchantId,
        Long userId,
        BigDecimal amount,
        String bankAccount,
        String bankName,
        String accountName,
        String withdrawNo,
        String remark
) {
}
