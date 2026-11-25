package com.chua.starter.tencent.support.payment.dto;

import java.util.List;
import java.util.Map;

/**
 * 微信分账创建/查询响应
 *
 * @param success    是否成功
 * @param outOrderNo 商户分账单号
 * @param orderId    微信分账单号
 * @param state      分账状态：PROCESSING-处理中/SUCCESS-成功/FAILED-失败
 * @param receivers  分账接收方明细(原始)
 * @param message    消息
 * @param rawBody    原始响应体
 *
 * @author CH
 * @since 4.0.0
 */
public record TencentWechatProfitSharingResponse(
        boolean success,
        String outOrderNo,
        String orderId,
        String state,
        List<Map<String, Object>> receivers,
        String message,
        String rawBody
) {
}
