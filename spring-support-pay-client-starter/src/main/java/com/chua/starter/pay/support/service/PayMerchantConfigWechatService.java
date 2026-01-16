package com.chua.starter.pay.support.service;

import com.chua.starter.pay.support.entity.PayMerchantConfigWechat;
import com.baomidou.mybatisplus.extension.service.IService;
import com.chua.starter.pay.support.pojo.PayMerchantConfigWechatWrapper;

/**
 * 微信支付商户配置服务接口
 *
 * @author CH
 * @since 2025/10/14 11:28
 */
public interface PayMerchantConfigWechatService extends IService<PayMerchantConfigWechat> {

    /**
     * 根据商户ID和微信类型获取微信支付配置信息
     *
     * @param payMerchantId 商户ID
     * @param wechatType    微信类型
     * @return 微信支付配置包装对象
     */
    PayMerchantConfigWechatWrapper getByCodeForPayMerchantConfigWechat(Integer payMerchantId, String wechatType);

    /**
     * 更新微信支付商户配置
     *
     * @param payMerchantConfigWechat 微信支付商户配置实体
     * @return 更新后的微信支付商户配置实体
     */
    PayMerchantConfigWechat updateForPayMerchantConfigWechat(PayMerchantConfigWechat payMerchantConfigWechat);

    /**
     * 保存微信支付商户配置
     *
     * @param payMerchantConfigWechat 微信支付商户配置实体
     * @return 是否保存成功
     */
    boolean saveForPayMerchantConfigWechat(PayMerchantConfigWechat payMerchantConfigWechat);

    /**
     * 根据商户ID和微信类型获取微信支付配置信息
     *
     * @param merchantConfigWechatId 商户ID
     * @param merchantConfigWechatId 微信支付商户配置ID
     * @return 微信支付配置包装对象
     */
    PayMerchantConfigWechat detail(Integer payMerchantId, Integer merchantConfigWechatId);

}
