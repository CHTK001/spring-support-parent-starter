package com.chua.starter.tencent.support.payment.dto;

/**
 * 微信下单请求
 *
 * @param orderNo 排序NO
 * @param amountFen amountfen
 * @param currency 货币
 * @param description 描述
 * @param notifyUrl 通知url
 * @param expireTime Expire时间
 * @param payerOpenId payeropenid
 * @param clientIp 客户端IP
 * @param deviceId deviceid
 * @param attach Attach
 * @param h5Type H5类型
 * @param appName APP名称
 * @param appUrl APPURL
 *
 * @author CH
 * @since 4.0.0
 */
public record TencentWechatPayRequest(
        String orderNo,
        Long amountFen,
        String currency,
        String description,
        String notifyUrl,
        String expireTime,
        String payerOpenId,
        String clientIp,
        String deviceId,
        String attach,
        String h5Type,
        String appName,
        String appUrl
) {
}

