package com.chua.starter.pay.support.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.chua.starter.pay.support.entity.PayMerchantConfigAlipay;
import com.chua.starter.pay.support.pojo.PayMerchantConfigAlipayWrapper;

/**
 * 支付宝支付商户配置服务接口
 *
 * @author CH
 * @since 2025/10/15 11:28
 */
public interface PayMerchantConfigAlipayService extends IService<PayMerchantConfigAlipay> {

    /**
     * 根据商户ID和支付宝类型获取支付宝支付配置信息
     *
     * @param payMerchantId 商户ID
     * @param alipayType    支付宝类型
     * @return 支付宝支付配置包装对象
     */
    PayMerchantConfigAlipayWrapper getByCodeForPayMerchantConfigAlipay(Integer payMerchantId, String alipayType);

    /**
     * 更新支付宝支付商户配置
     *
     * @param payMerchantConfigAlipay 支付宝支付商户配置实体
     * @return 更新后的支付宝支付商户配置实体
     */
    PayMerchantConfigAlipay updateForPayMerchantConfigAlipay(PayMerchantConfigAlipay payMerchantConfigAlipay);

    /**
     * 保存支付宝支付商户配置
     *
     * @param payMerchantConfigAlipay 支付宝支付商户配置实体
     * @return 是否保存成功
     */
    boolean saveForPayMerchantConfigAlipay(PayMerchantConfigAlipay payMerchantConfigAlipay);

    /**
     * 根据商户ID和配置ID获取支付宝支付配置信息
     *
     * @param payMerchantId           商户ID
     * @param merchantConfigAlipayId  支付宝支付商户配置ID
     * @return 支付宝支付配置实体
     */
    PayMerchantConfigAlipay detail(Integer payMerchantId, Integer merchantConfigAlipayId);
}

