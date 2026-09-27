package com.chua.payment.support.dto;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 支付回调日志记录请求参数对象。
 *
 * @param notifyType     通知类型，如 WECHAT_公众号、ALIPAY_公众号、WECHAT_商户等
 * @param merchantId     客户标识
 * @param channelId      渠道标识
 * @param channelType    渠道类型
 * @param channelSubType 渠道子类型
 * @param orderNo        支付订单号
 * @param refundNo       退款单号
 * @param request        HTTP请求对象（用于提取请求头与参数）
 * @param body           请求体内容
 * @author CH
 * @since 4.0.0.50
 */
public record LogNotifyDTO(
        String notifyType,
        Long merchantId,
        Long channelId,
        String channelType,
        String channelSubType,
        String orderNo,
        String refundNo,
        HttpServletRequest request,
        String body
) {
}
