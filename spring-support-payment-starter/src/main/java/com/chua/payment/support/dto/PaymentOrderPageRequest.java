package com.chua.payment.support.dto;

/**
 * 支付订单分页查询请求参数对象。
 *
 * @param page      页码
 * @param size      每页大小
 * @param merchantId 商户标识（可选）
 * @param orderNo   订单号（可选）
 * @param status    状态（可选）
 * @author CH
 * @since 4.0.0.50
 */
public record PaymentOrderPageRequest(
        int page,
        int size,
        Long merchantId,
        String orderNo,
        String status
) {
}
