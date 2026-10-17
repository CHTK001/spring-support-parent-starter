package com.chua.starter.aliyun.support.payment.dto;

import java.math.BigDecimal;

/**
 * 支付宝单笔转账响应
 *
 * @param success     是否成功
 * @param transferNo  支付宝转账订单号
 * @param outBizNo    商户转账唯一订单号
 * @param amount      转账金额
 * @param status      转账状态：成功/失败/WAIT_薪酬
 * @param payDate     支付时间
 * @param message     错误信息
 * @param rawResponse 原始响应 主体
 * @author CH
 * @since 4.0.0
 */
public record AliyunAlipayTransferResponse(
        boolean success,
        String transferNo,
        String outBizNo,
        BigDecimal amount,
        String status,
        String payDate,
        String message,
        String rawResponse) {
}
