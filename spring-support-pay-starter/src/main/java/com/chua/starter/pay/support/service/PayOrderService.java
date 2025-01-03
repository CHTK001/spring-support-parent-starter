package com.chua.starter.pay.support.service;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.pay.support.handler.CallbackNotificationParser;
import com.chua.starter.pay.support.pojo.PayOrderRequest;
import com.chua.starter.pay.support.pojo.PayRefundRequest;
import com.chua.starter.pay.support.pojo.PaySignCreateRequest;
import com.chua.starter.pay.support.pojo.WechatOrderCallbackResponse;
import com.chua.starter.pay.support.result.PayOrderResponse;
import com.chua.starter.pay.support.result.PayRefundResponse;
import com.chua.starter.pay.support.result.PaySignResponse;

/**
 * 支付订单
 * @author CH
 * @since 2024/12/30
 */
public interface PayOrderService extends PayService{


    /**
     * 支付订单微信回调
     *
     * @param parser 解析器
     * @return 结果
     */
    WechatOrderCallbackResponse notifyOrder(CallbackNotificationParser parser);


    /**
     * 退款订单微信回调
     *
     * @param wechatOrderCallbackRequest 回调请求
     * @param s 交易类型
     * @return 结果
     */
    WechatOrderCallbackResponse refundOrder(CallbackNotificationParser wechatOrderCallbackRequest);

}
