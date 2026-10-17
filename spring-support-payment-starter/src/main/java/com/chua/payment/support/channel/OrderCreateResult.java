package com.chua.payment.support.channel;

/**
 * 订单创建结果
 *
 * @param success     是否成功
 * @param orderNo     订单号
 * @param message     提示消息
 * @param rawResponse 渠道 RAW 响应
 * @author CH
 * @since 4.0.0
 */
public record OrderCreateResult(
        boolean success,
        String orderNo,
        String message,
        String rawResponse) {
}
