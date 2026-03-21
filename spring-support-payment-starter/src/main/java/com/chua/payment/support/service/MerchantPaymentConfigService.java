package com.chua.payment.support.service;

import com.chua.payment.support.entity.MerchantPaymentConfig;

/**
 * 商户支付配置服务
 */
public interface MerchantPaymentConfigService {

    /**
     * 获取商户支付配置
     */
    MerchantPaymentConfig getConfig(Long merchantId);

    /**
     * 保存或更新配置
     */
    void saveOrUpdate(MerchantPaymentConfig config);

    /**
     * 检查是否可以创建新订单
     */
    void checkCanCreateOrder(Long merchantId, Long userId);

    /**
     * 检查订单是否可以继续支付
     */
    void checkCanPayOrder(Long orderId);

    /**
     * 获取订单超时时间(分钟)
     */
    Integer getOrderTimeoutMinutes(Long merchantId);
}
