package com.chua.starter.pay.support.service;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.pay.support.pojo.PayOrderRequest;
import com.chua.starter.pay.support.pojo.PayRefundRequest;
import com.chua.starter.pay.support.pojo.PaySignCreateRequest;
import com.chua.starter.pay.support.result.PayOrderResponse;
import com.chua.starter.pay.support.result.PayRefundResponse;
import com.chua.starter.pay.support.result.PaySignResponse;

/**
 * 支付服务
 * @author CH
 * @since 2025/1/3
 */
public interface PayService {

    /**
     * 创建订单
     *
     * @param request 用户ID
     * @return 订单
     */
    ReturnResult<PayOrderResponse> createOrder(PayOrderRequest request);


    /**
     * 退款
     *
     * @param refundRequest 退款请求
     * @return 结果
     */
    ReturnResult<PayRefundResponse> refund(PayRefundRequest refundRequest);


    /**
     * 创建签名
     *
     * @param paySignCreateRequest 订单请求
     * @return 结果
     */
    ReturnResult<PaySignResponse> createSign(PaySignCreateRequest paySignCreateRequest);
}
