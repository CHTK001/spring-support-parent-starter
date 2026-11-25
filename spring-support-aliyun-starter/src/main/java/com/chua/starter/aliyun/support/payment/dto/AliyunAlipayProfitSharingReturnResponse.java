package com.chua.starter.aliyun.support.payment.dto;

/**
 * 支付宝分账回退响应
 *
 * @param success   是否成功
 * @param outBizNo  商户回退单号
 * @param returnNo  支付宝回退单号
 * @param message   消息
 * @param rawBody   原始响应体
 *
 * @author CH
 * @since 4.0.0
 */
public record AliyunAlipayProfitSharingReturnResponse(
        boolean success,
        String outBizNo,
        String returnNo,
        String message,
        String rawBody
) {
}
