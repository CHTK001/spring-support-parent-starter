package com.chua.starter.pay.support.service;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.validator.group.AddGroup;
import com.chua.starter.pay.support.entity.PayMerchantOrder;
import com.baomidou.mybatisplus.extension.service.IService;
import com.chua.starter.pay.support.pojo.CreateOrderV2Request;
import com.chua.starter.pay.support.pojo.CreateOrderV2Response;
import com.chua.starter.pay.support.pojo.PaySignResponse;
import jakarta.validation.constraints.NotEmpty;

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
}
