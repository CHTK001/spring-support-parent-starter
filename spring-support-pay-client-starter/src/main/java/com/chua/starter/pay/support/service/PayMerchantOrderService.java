package com.chua.starter.pay.support.service;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.pay.support.entity.PayMerchantOrder;
import com.baomidou.mybatisplus.extension.service.IService;
import com.chua.starter.pay.support.pojo.*;

/**
 * 商户订单服务接口
 *
 * @author CH
 * @since 2025-10-14
 */
public interface PayMerchantOrderService extends IService<PayMerchantOrder> {

    /**
     * 创建订单
     *
     * @param request 创建订单请求参数
     * @return 返回创建订单结果
     */
    ReturnResult<CreateOrderV2Response> createOrder(CreateOrderV2Request request);

    /**
     * 保存订单
     *
     * @param payMerchantOrder 订单信息
     * @return 保存结果 true-保存成功 false-保存失败
     */
    boolean saveOrder(PayMerchantOrder payMerchantOrder);

    /**
     * 创建签名
     *
     * @param request 创建签名请求参数
     * @return 创建签名结果
     */
    ReturnResult<PaySignResponse> createSign(CreateOrderV2Response request);

    /**
     * 根据订单编号查询订单信息
     *
     * @param payMerchantOrderCode 订单编号
     * @return 订单信息
     */
    PayMerchantOrder getByCode(String payMerchantOrderCode);

    /**
     * 更新微信订单信息
     *
     * @param merchantOrder 订单信息
     * @return 更新结果 true-更新成功 false-更新失败
     */
    boolean updateWechatOrder(PayMerchantOrder merchantOrder);
    /**
     * 更新订单信息(退款包含部分退款, 状态由调用方设置)
     *
     * @param merchantOrder 订单信息
     * @return 更新结果 true-更新成功 false-更新失败
     */
    boolean updateRefundOrder(PayMerchantOrder merchantOrder);

    /**
     * 退款订单
     *
     * @param payMerchantOrderCode 订单编号
     * @param request 退款请求参数
     * @return 退款结果
     */
    ReturnResult<RefundOrderV2Response> refundOrder(String payMerchantOrderCode, RefundOrderV2Request request);

    /**
     * 退款订单到钱包
     *
     * @param payMerchantOrderCode 订单编号
     * @param request 退款请求参数
     * @return 退款结果
     */
    ReturnResult<RefundOrderV2Response> refundOrderToWallet(String payMerchantOrderCode, RefundOrderV2Request request);
}
