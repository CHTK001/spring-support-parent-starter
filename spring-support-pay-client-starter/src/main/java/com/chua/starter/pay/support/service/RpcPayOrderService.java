package com.chua.starter.pay.support.service;

import com.chua.common.support.lang.code.ReturnPageResult;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.mybatis.entity.Query;
import com.chua.starter.pay.support.entity.PayMerchantOrder;
import com.chua.starter.pay.support.pojo.PayOrderRequest;
import com.chua.starter.pay.support.pojo.PayOrderV1Request;
import com.chua.starter.pay.support.pojo.PayReOrderQueryV1Request;
import com.chua.starter.pay.support.pojo.PayRefundRequest;
import com.chua.starter.pay.support.result.PayRefundResponse;

import java.util.Set;

/**
 * 支付服务
 *
 * @author CH
 * @since 2025/1/3
 */
public interface RpcPayOrderService {


    /**
     * 是否有未支付的订单
     *
     * @param request 请求
     * @return 订单
     */
    boolean hasNoPayOrder(PayOrderRequest request);

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
