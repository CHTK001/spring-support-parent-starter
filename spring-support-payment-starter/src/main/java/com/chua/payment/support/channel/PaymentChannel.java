package com.chua.payment.support.channel;

import com.chua.payment.support.entity.MerchantChannel;

/**
 * 支付渠道接口
 *
 * @author CH
 * @since 2026-03-18
 */
public interface PaymentChannel {

    boolean supports(String channelType, String channelSubType);

    /**
     * 发起支付
     */
    PaymentResult pay(MerchantChannel channel, PaymentRequest request);

    /**
     * 查询支付结果
     */
    PaymentResult query(MerchantChannel channel, String orderNo);

    /**
     * 关闭订单
     */
    default boolean close(MerchantChannel channel, String orderNo) {
        return false;
    }

    /**
     * 申请退款
     */
    RefundResult refund(MerchantChannel channel, RefundRequest request);

    /**
     * 查询退款结果
     */
    RefundResult queryRefund(MerchantChannel channel, RefundRequest request);
}
