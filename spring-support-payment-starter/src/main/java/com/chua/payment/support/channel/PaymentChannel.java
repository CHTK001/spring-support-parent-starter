package com.chua.payment.support.channel;

import com.chua.payment.support.entity.MerchantChannel;

/**
 * 支付通道接口
 *
 * @since 2026-03-18
 * @author CH
 */
public interface PaymentChannel {

    /**
     * 是否支持该通道
     * @param channelType 通道类型
     * @param channelSubType 通道子类型
     * @return 支持则返回 true
     */
    boolean supports(String channelType, String channelSubType);

    /**
     * 创建渠道订单(预下单)
     * @param channel 通道配置
     * @param request 请求参数
     * @return 创建订单的结果
     */
    default OrderCreateResult createOrder(MerchantChannel channel, OrderCreateRequest request) {
        return new OrderCreateResult(true, request.orderNo(), null, null);
    }

    /**
     * 发起支付
     * @param channel 通道配置
     * @param request 请求参数
     * @return 支付结果
     */
    PaymentResult pay(MerchantChannel channel, PaymentRequest request);

    /**
     * 查询支付结果
     * @param channel 通道配置
     * @param orderNo 订单号
     * @return 查询结果
     */
    PaymentResult query(MerchantChannel channel, String orderNo);

    /**
     * 关闭订单
     * @param channel 通道配置
     * @param orderNo 订单号
     * @return 关闭结果
     */
    default boolean close(MerchantChannel channel, String orderNo) {
        return false;
    }

    /**
     * 申请退款
     * @param channel 通道配置
     * @param request 请求参数
     * @return 退款结果
     */
    RefundResult refund(MerchantChannel channel, RefundRequest request);

    /**
     * 查询退款结果
     * @param channel 通道配置
     * @param request 请求参数
     * @return 查询退款的结果
     */
    RefundResult queryRefund(MerchantChannel channel, RefundRequest request);

    /**
     * 通道充值
     * @param channel 通道配置
     * @param request 请求参数
     */
    default RechargeResult recharge(MerchantChannel channel, RechargeRequest request) {
        /**
         * 默认抛出不支持操作异常。
         * @return 不支持操作异常的结果
         */
        throw new UnsupportedOperationException("该通道不支持充值操作");
    }

    /**
     * 通道转账
     * @param channel 通道配置
     * @param request 请求参数
     * @return 转账结果
     */
    default TransferResult transfer(MerchantChannel channel, TransferRequest request) {
        /**
         * 默认抛出不支持操作异常。
         * @return 不支持操作异常的结果
         */
        throw new UnsupportedOperationException("该通道不支持转账操作");
    }

    /**
     * 通道提现
     * @param channel 通道配置
     * @param request 请求参数
     */
    default WithdrawResult withdraw(MerchantChannel channel, WithdrawRequest request) {
        /**
         * 默认抛出不支持操作异常。
         * @return 不支持操作异常的结果
         */
        throw new UnsupportedOperationException("该通道不支持提现操作");
    }
}
