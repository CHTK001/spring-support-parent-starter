package com.chua.starter.tencent.support.payment.dto;

/**
 * 微信退款通知解析结果
 *
 * @param orderNo          排序NO
 * @param refundNo         退税no
 * @param refundId         退税标识
 * @param totalAmountFen   总数amountfen
 * @param refundAmountFen  refundamountfen
 * @param refundStatus     退税状态
 * @author CH
 * @since 4.0.0
 */
public record TencentWechatRefundNotifyPayload(
        String orderNo,
        String refundNo,
        String refundId,
        Long totalAmountFen,
        Long refundAmountFen,
        String refundStatus) {
}
