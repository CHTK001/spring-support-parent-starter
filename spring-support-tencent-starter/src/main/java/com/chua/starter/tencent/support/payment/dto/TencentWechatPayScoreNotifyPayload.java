package com.chua.starter.tencent.support.payment.dto;

import java.util.Map;

/**
 * 微信支付分服务订单回调解析结果
 *
 * @param outOrderNo     出排序NO
 * @param serviceOrderNo 服务排序NO
 * @param appId          APPID
 * @param serviceId      服务标识
 * @param openId         打开标识
 * @param state          状态
 * @param finishReason   Finishreason
 * @param rawData        raw数据
 * @author CH
 * @since 4.0.0
 */
public record TencentWechatPayScoreNotifyPayload(
        String outOrderNo,
        String serviceOrderNo,
        String appId,
        String serviceId,
        String openId,
        String state,
        String finishReason,
        Map<String, Object> rawData) {
}
