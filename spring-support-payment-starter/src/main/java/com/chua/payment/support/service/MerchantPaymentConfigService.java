package com.chua.payment.support.service;

import com.chua.payment.support.entity.MerchantPaymentConfig;

/**
 * 客户支付配置服务接口，定义以商户为维度的支付参数与风控检查契约。
 *
 * @author CH
 * @since 4.0.0.50
 */
public interface MerchantPaymentConfigService {

    /**
     * 获取客户支付配置。
     * @param merchantId 商户标识
     * @return 获取到的配置
     */
    MerchantPaymentConfig getConfig(Long merchantId);

    /**
     * 保存或更新配置。
     * @param config 配置
     */
    void saveOrUpdate(MerchantPaymentConfig config);

    /**
     * 校验是否可以创建新订单。
     * @param merchantId 商户标识
     * @param userId 用户标识
     */
    void checkCanCreateOrder(Long merchantId, Long userId);

    /**
     * 校验订单是否可以继续支付。
     * @param orderId 订单标识
     */
    void checkCanPayOrder(Long orderId);

    /**
     * 获取订单超时时间（分钟）。
     * @param merchantId 商户标识
     * @return 获取到的订单超时分钟数
     */
    Integer getOrderTimeoutMinutes(Long merchantId);
}
