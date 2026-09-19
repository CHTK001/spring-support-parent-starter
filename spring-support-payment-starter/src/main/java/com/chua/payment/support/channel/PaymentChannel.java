package com.chua.payment.support.channel;

import com.chua.payment.support.entity.MerchantChannel;

/**
 * 支付渠道接口
 *
 * @since 2026-03-18
 * @author CH
 */
public interface PaymentChannel {

    /**
     * 支持。
     * @param channelType 通道类型
     * @param channelSubType 通道sub类型
     * @return 支持的结果
     */
    boolean supports(String channelType, String channelSubType);

    /**
     * 创建订单(预下单)
     * @param channel 通道
     * @param request 请求
     * @return 创建订单的结果
     */
    default OrderCreateResult createOrder(MerchantChannel channel, OrderCreateRequest request) {
        OrderCreateResult result = new OrderCreateResult();
        result.setSuccess(true);
        result.setOrderNo(request.getOrderNo());
        return result;
    }

    /**
     * 发起支付
     * @param channel 通道
     * @param request 请求
     * @return 薪酬的结果
     */
    PaymentResult pay(MerchantChannel channel, PaymentRequest request);

    /**
     * 查询支付结果
     * @param channel 通道
     * @param orderNo 订单no
     * @return 查询的结果
     */
    PaymentResult query(MerchantChannel channel, String orderNo);

    /**
     * 关闭订单
     * @param channel 通道
     * @param orderNo 订单no
     * @return 关闭的结果
     */
    default boolean close(MerchantChannel channel, String orderNo) {
        return false;
    }

    /**
     * 申请退款
     * @param channel 通道
     * @param request 请求
     * @return 退税的结果
     */
    RefundResult refund(MerchantChannel channel, RefundRequest request);

    /**
     * 查询退款结果
     * @param channel 通道
     * @param request 请求
     * @return 查询退税的结果
     */
    RefundResult queryRefund(MerchantChannel channel, RefundRequest request);

    /**
     * 充值
     * @param channel 通道
     * @param request 请求
     * @return recharge的结果
     */
    default RechargeResult recharge(MerchantChannel channel, RechargeRequest request) {
        /**
         * unsupportedoperation异常。
         * @param "该渠道不支持充值操作" "该渠道不支持充值操作"
         * @return unsupportedoperation异常的结果
         */
        throw new UnsupportedOperationException("该渠道不支持充值操作");
    }

    /**
     * 转账
     * @param channel 通道
     * @param request 请求
     * @return 调动的结果
     */
    default TransferResult transfer(MerchantChannel channel, TransferRequest request) {
        /**
         * unsupportedoperation异常。
         * @param "该渠道不支持转账操作" "该渠道不支持转账操作"
         * @return unsupportedoperation异常的结果
         */
        throw new UnsupportedOperationException("该渠道不支持转账操作");
    }

    /**
     * 提现
     * @param channel 通道
     * @param request 请求
     * @return withdraw的结果
     */
    default WithdrawResult withdraw(MerchantChannel channel, WithdrawRequest request) {
        /**
         * unsupportedoperation异常。
         * @param "该渠道不支持提现操作" "该渠道不支持提现操作"
         * @return unsupportedoperation异常的结果
         */
        throw new UnsupportedOperationException("该渠道不支持提现操作");
    }
}
