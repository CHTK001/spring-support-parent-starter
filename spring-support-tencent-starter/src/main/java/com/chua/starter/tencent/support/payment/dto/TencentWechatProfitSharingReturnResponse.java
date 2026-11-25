package com.chua.starter.tencent.support.payment.dto;

/**
 * 微信分账回退响应
 *
 * @param success     是否成功
 * @param outOrderNo  原商户分账单号
 * @param outReturnNo 商户回退单号
 * @param returnId    微信回退单号
 * @param result      回退结果：PENDING-处理中/SUCCESS-成功/FAILED-失败
 * @param message     消息
 * @param rawBody     原始响应体
 *
 * @author CH
 * @since 4.0.0
 */
public record TencentWechatProfitSharingReturnResponse(
        boolean success,
        String outOrderNo,
        String outReturnNo,
        String returnId,
        String result,
        String message,
        String rawBody
) {
}
