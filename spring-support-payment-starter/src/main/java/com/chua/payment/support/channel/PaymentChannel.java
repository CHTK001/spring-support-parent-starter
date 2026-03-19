package com.chua.payment.support.channel;

/**
 * 支付渠道接口
 *
 * @author CH
 * @since 2026-03-18
 */
public interface PaymentChannel {
    
    /**
     * 发起支付
     */
    PaymentResult pay(PaymentRequest request);
    
    /**
     * 查询支付结果
     */
    PaymentResult query(String orderNo);
    
    /**
     * 申请退款
     */
    RefundResult refund(RefundRequest request);
    
    /**
     * 查询退款结果
     */
    RefundResult queryRefund(String refundNo);
}
