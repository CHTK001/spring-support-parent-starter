package com.chua.starter.pay.support.service;

import com.chua.common.support.lang.code.ReturnPageResult;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.mybatis.entity.Query;
import com.chua.starter.pay.support.entity.PayMerchantOrder;
import com.chua.starter.pay.support.entity.PayMerchantOrderWater;
import com.chua.starter.pay.support.pojo.PayOrderRequest;
import com.chua.starter.pay.support.pojo.PayOrderV1Request;
import com.chua.starter.pay.support.pojo.PayReOrderQueryV1Request;
import com.chua.starter.pay.support.pojo.PayReOrderRequest;
import com.chua.starter.pay.support.result.PayOrderResponse;

import java.util.List;

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
     * 重新支付订单
     *
     * @param request 用户ID
     * @return 订单
     */
    ReturnResult<PayOrderResponse> recreateOrder(PayReOrderRequest request);


    /**
     * 查询待支付订单
     *
     * @param request 用户ID
     * @return 订单
     */
    ReturnPageResult<PayMerchantOrder> order(PayReOrderQueryV1Request request);
    /**
     * 查询订单
     *
     * @param request 用户ID
     * @return 订单
     */
    ReturnPageResult<PayMerchantOrder> order(Query<PayMerchantOrder> query, PayOrderV1Request request, Integer sysUserId);

    /**
     * 订单详情
     *
     * @param payMerchantOrderCode 订单编号
     * @return 订单
     */
    ReturnResult<PayMerchantOrder> detail(String payMerchantOrderCode);

    /**
     * 订单流水
     *
     * @param payMerchantOrderCode 订单编号
     * @return 订单
     */
    ReturnResult<List<PayMerchantOrderWater>> water(String payMerchantOrderCode);
}
