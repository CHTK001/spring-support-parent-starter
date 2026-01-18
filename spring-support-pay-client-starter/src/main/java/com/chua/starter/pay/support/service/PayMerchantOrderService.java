package com.chua.starter.pay.support.service;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.base.validator.group.AddGroup;
import com.chua.common.support.base.validator.group.UpdateGroup;
import com.chua.starter.pay.support.entity.PayMerchantOrder;
import com.baomidou.mybatisplus.extension.service.IService;
import com.chua.starter.pay.support.enums.PayOrderStatus;
import com.chua.starter.pay.support.pojo.*;
import jakarta.validation.constraints.NotBlank;

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
     * 创建微信信用分订单
     *
     * @param request 创建微信信用分订单请求参数
     * @return 创建微信信用分订单结果
     */
    ReturnResult<CreateOrderV2Response> createOrder(CreatePaymentPointsOrderV2Request request);
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
     * 更新微信订单信息(状态由调用方设置)
     *
     * @param merchantOrder 订单信息
     * @return 更新结果 true-更新成功 false-更新失败
     */
    boolean finishWechatOrder(PayMerchantOrder merchantOrder);
    /**
     * 更新订单信息(退款包含部分退款, 状态由调用方设置)
     *
     * @param merchantOrder 订单信息
     * @return 更新结果 true-更新成功 false-更新失败
     */
    boolean refundOrder(PayMerchantOrder merchantOrder);

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

    /**
     * 订单超时
     *
     * @param payMerchantId 商户ID
     * @param payMerchantOpenTimeoutTime 商户订单超时时间
     * @return 订单超时数量
     */
    int timeout(Integer payMerchantId, Integer payMerchantOpenTimeoutTime);

    /**
     * 关闭订单
     *
     * @param payMerchantOrderCode 订单编号
     * @return 关闭结果
     */
    ReturnResult<Boolean> closeOrder(String payMerchantOrderCode);

    /**
     * 获取订单状态
     *
     * @param payMerchantOrderCode 订单编号
     * @return 订单状态
     */
    PayOrderStatus getOrderStatus(String payMerchantOrderCode);

    /**
     * 分页查询订单（含关联字段）
     * @param page 分页
     * @param entity 条件
     * @param cond 额外条件
     * @return 分页结果
     */
    com.baomidou.mybatisplus.core.metadata.IPage<com.chua.starter.pay.support.pojo.PayMerchantOrderVO> pageForPayMerchantOrder(
            com.chua.starter.mybatis.entity.Query<PayMerchantOrder> page,
            PayMerchantOrder entity,
            com.chua.starter.pay.support.pojo.PayMerchantOrderPageRequest cond);
}
