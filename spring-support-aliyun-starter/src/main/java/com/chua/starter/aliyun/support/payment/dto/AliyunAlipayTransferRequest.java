package com.chua.starter.aliyun.support.payment.dto;

import java.math.BigDecimal;

/**
 * 支付宝单笔转账请求。
 *
 * @param outBizNo      商户转账唯一单号
 * @param payeeAccount  收款方支付宝账号（手机号或邮箱）
 * @param payeeName     收款方真实姓名（可选，校验收款方）
 * @param amount        转账金额
 * @param remark        转账备注
 * @author CH
 * @since 4.0.0
 */
public record AliyunAlipayTransferRequest(
        String outBizNo,
        String payeeAccount,
        String payeeName,
        BigDecimal amount,
        String remark) {
}
