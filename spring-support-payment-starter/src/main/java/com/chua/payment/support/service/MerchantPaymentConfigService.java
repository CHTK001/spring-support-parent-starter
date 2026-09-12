package com.chua.payment.support.service;

import com.chua.payment.support.entity.MerchantPaymentConfig;

/**
* 商户支付配置服务接口，定义商户维度支付参数与风控检查契约。
*
* @author CH
* @since 4.0.0.50
 */
public interface MerchantPaymentConfigService {

    /**
    * 获取商户支付配置
    * @param merchantId merchantid
    * @return 获取配置的结果
     */
    MerchantPaymentConfig getConfig(Long merchantId);

    /**
    * 保存或更新配置
    * @param config 配置
     */
    void saveOrUpdate(MerchantPaymentConfig config);

    /**
    * 检查是否可以创建新订单
    * @param merchantId merchantid
    * @param userId 用户标识
     */
    void checkCanCreateOrder(Long merchantId, Long userId);

    /**
    * 检查订单是否可以继续支付
    * @param orderId 订单标识
     */
    void checkCanPayOrder(Long orderId);

    /**
    * 获取订单超时时间(分钟)
    * @param merchantId merchantid
    * @return 获取订单超时minutes的结果
     */
    Integer getOrderTimeoutMinutes(Long merchantId);
}

