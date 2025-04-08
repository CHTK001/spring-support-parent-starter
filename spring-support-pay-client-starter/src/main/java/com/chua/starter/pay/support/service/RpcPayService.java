package com.chua.starter.pay.support.service;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.pay.support.pojo.PayOrderRequest;
import com.chua.starter.pay.support.pojo.PayReOrderRequest;
import com.chua.starter.pay.support.result.PayOrderResponse;

/**
 * 支付服务
 * @author CH
 * @since 2025/1/3
 */
public interface RpcPayService {

    /**
     * 创建订单
     *
     * @param request 用户ID
     * @return 订单
     */
    ReturnResult<PayOrderResponse> createOrder(PayOrderRequest request);

    /**
     * 重新支付订单
     *
     * @param request 用户ID
     * @return 订单
     */
    ReturnResult<PayOrderResponse> recreateOrder(PayReOrderRequest request);

}
