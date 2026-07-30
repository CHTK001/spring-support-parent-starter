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
     * 创建订单(预下单)
     */
    default OrderCreateResult createOrder(MerchantChannel channel, OrderCreateRequest request) {
        OrderCreateResult result = new OrderCreateResult();
        result.setSuccess(true);
        result.setOrderNo(request.getOrderNo());
        return result;
    }

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

    /**
     * 充值
     */
    default RechargeResult recharge(MerchantChannel channel, RechargeRequest request) {
        throw new UnsupportedOperationException("该渠道不支持充值操作");
    }

    /**
     * 转账
     */
    default TransferResult transfer(MerchantChannel channel, TransferRequest request) {
        throw new UnsupportedOperationException("该渠道不支持转账操作");
    }

    /**
     * 提现
     */
    default WithdrawResult withdraw(MerchantChannel channel, WithdrawRequest request) {
        throw new UnsupportedOperationException("该渠道不支持提现操作");
    }
}
