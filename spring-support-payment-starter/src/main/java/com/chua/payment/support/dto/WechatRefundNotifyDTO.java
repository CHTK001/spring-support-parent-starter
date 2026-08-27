package com.chua.payment.support.dto;

/**
 * 微信支付退款回调通知参数对象。
 *
 * @param channelId      渠道ID
 * @param refundNo       退款单号
 * @param serialNumber   证书序列号
 * @param timestamp      时间戳
 * @param nonce          随机字符串
 * @param signature      签名
 * @param signType       签名类型
 * @param body           请求体
 * @author CH
 * @since 4.0.0.50
 */
public record WechatRefundNotifyDTO(
        Long channelId,
        String refundNo,
        String serialNumber,
        String timestamp,
        String nonce,
        String signature,
        String signType,
        String body
) {
}
