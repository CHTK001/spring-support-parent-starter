package com.chua.starter.pay.support.service;

import com.chua.common.support.lang.code.ReturnPageResult;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.mybatis.entity.Query;
import com.chua.starter.pay.support.entity.PayMerchantOrder;
import com.chua.starter.pay.support.pojo.PayOrderRequest;
import com.chua.starter.pay.support.pojo.PayOrderV1Request;
import com.chua.starter.pay.support.result.PayOrderResponse;

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
     * 创建订单
     *
     * @param request 用户ID
     * @return 订单
     */
    ReturnPageResult<PayMerchantOrder> order(Query<PayMerchantOrder> query, PayOrderV1Request request, Integer sysUserId);

}
