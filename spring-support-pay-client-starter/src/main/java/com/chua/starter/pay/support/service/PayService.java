package com.chua.starter.pay.support.service;

import com.chua.common.support.lang.code.ReturnPageResult;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.mybatis.entity.Query;
import com.chua.starter.pay.support.entity.PayMerchantOrder;
import com.chua.starter.pay.support.entity.PayMerchantOrderWater;
import com.chua.starter.pay.support.pojo.*;
import com.chua.starter.pay.support.result.PayOrderResponse;
import com.chua.starter.pay.support.result.PayRefundResponse;

import java.util.List;
import java.util.Set;

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
    ReturnPageResult<PayMerchantOrder> order(Query<PayMerchantOrder> query, PayOrderV1Request request, Set<String> sysUserIds);

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


    /**
     * 订单流水
     *
     * @param request 订单编号
     * @return 订单
     */
    ReturnPageResult<PayMerchantOrderWater> water(WaterQueryV1Request request);


    /**
     * 退款
     *
     * @param request 订单编号
     * @return 订单
     */
    ReturnResult<PayRefundResponse> refund(PayRefundRequest request);
    /**
     * 退款到钱包
     *
     * @param request 订单编号
     * @return 订单
     */
    ReturnResult<PayRefundResponse> refundToWallet(PayRefundRequest request);
    /**
     * 取消到钱包
     *
     * @param request 订单编号
     * @return 订单
     */
    ReturnResult<PayRefundResponse> cancelToWallet(PayRefundRequest request);
}
