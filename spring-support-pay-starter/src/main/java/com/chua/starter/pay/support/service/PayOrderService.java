package com.chua.starter.pay.support.service;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.pay.support.pojo.PayOrderRequest;
import com.chua.starter.pay.support.pojo.PayRefundRequest;
import com.chua.starter.pay.support.pojo.WechatOrderCallbackRequest;
import com.chua.starter.pay.support.pojo.WechatOrderCallbackResponse;
import com.chua.starter.pay.support.result.PayOrderResponse;
import com.chua.starter.pay.support.result.PayRefundResponse;

/**
 * 支付订单
 * @author CH
 * @since 2024/12/30
 */
public interface PayOrderService {

    /**
     * 创建订单
     *
     * @param request 用户ID
     * @return 订单
     */
    ReturnResult<PayOrderResponse> createOrder(PayOrderRequest request);

    /**
     * 支付订单微信回调
     *
     * @param wechatOrderCallbackRequest 回调请求
     * @param tradeType 交易类型
     * @return 结果
     */
    WechatOrderCallbackResponse notifyOrder(WechatOrderCallbackRequest wechatOrderCallbackRequest, String tradeType);

    /**
     * 退款
     *
     * @param refundRequest 退款请求
     * @return 结果
     */
    ReturnResult<PayRefundResponse> refund(PayRefundRequest refundRequest);

    /**
     * 退款订单微信回调
     *
     * @param wechatOrderCallbackRequest 回调请求
     * @param s 交易类型
     * @return 结果
     */
    WechatOrderCallbackResponse refundOrder(WechatOrderCallbackRequest wechatOrderCallbackRequest, String s);
}
