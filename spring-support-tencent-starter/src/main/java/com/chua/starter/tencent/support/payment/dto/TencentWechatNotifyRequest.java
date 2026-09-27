package com.chua.starter.tencent.support.payment.dto;

/**
 * 微信支付回调请求
 *
 * @param serialNumber 串行数字
 * @param timestamp 时间戳
 * @param nonce Nonce
 * @param signature 签名
 * @param signType 标志类型
 * @param body 请求体
 *
 * @author CH
 * @since 4.0.0
 */
public record TencentWechatNotifyRequest(
        String serialNumber,
        String timestamp,
        String nonce,
        String signature,
        String signType,
        String body
) {
}

