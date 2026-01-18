package com.chua.starter.pay.support.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.chua.starter.pay.support.entity.PayMerchantConfigUnionPay;
import com.chua.starter.pay.support.pojo.PayMerchantConfigUnionPayWrapper;

/**
 * 云闪付支付商户配置服务接口
 *
 * @author CH
 * @since 2025/10/15 11:28
 */
public interface PayMerchantConfigUnionPayService extends IService<PayMerchantConfigUnionPay> {

    /**
     * 根据商户ID和云闪付类型获取云闪付支付配置信息
     *
     * @param payMerchantId 商户ID
     * @param unionPayType  云闪付类型
     * @return 云闪付支付配置包装对象
     */
    PayMerchantConfigUnionPayWrapper getByCodeForPayMerchantConfigUnionPay(Integer payMerchantId, String unionPayType);

    /**
     * 更新云闪付支付商户配置
     *
     * @param payMerchantConfigUnionPay 云闪付支付商户配置实体
     * @return 更新后的云闪付支付商户配置实体
     */
    PayMerchantConfigUnionPay updateForPayMerchantConfigUnionPay(PayMerchantConfigUnionPay payMerchantConfigUnionPay);

    /**
     * 保存云闪付支付商户配置
     *
     * @param payMerchantConfigUnionPay 云闪付支付商户配置实体
     * @return 是否保存成功
     */
    boolean saveForPayMerchantConfigUnionPay(PayMerchantConfigUnionPay payMerchantConfigUnionPay);

    /**
     * 根据商户ID和配置ID获取云闪付支付配置信息
     *
     * @param payMerchantId           商户ID
     * @param merchantConfigUnionPayId  云闪付支付商户配置ID
     * @return 云闪付支付配置实体
     */
    PayMerchantConfigUnionPay detail(Integer payMerchantId, Integer merchantConfigUnionPayId);
}

