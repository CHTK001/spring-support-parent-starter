package com.chua.starter.pay.support.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.pay.support.entity.PayMerchantConfigWechat;

/**
 * // 接口文档：微信支付商户配置服务
 * @author CH
 * @since 2024/12/30
 */
public interface PayMerchantConfigWechatService extends IService<PayMerchantConfigWechat> {

    /**
     * 根据ID获取微信支付商户配置信息
     *
     * @param payMerchantConfigWechatId 微信支付商户配置的唯一ID
     * @return 返回查询到的微信支付商户配置对象
     */
    PayMerchantConfigWechat getById(Long payMerchantConfigWechatId);

    /**
     * 保存微信支付商户配置信息
     *
     * @param payMerchantConfigWechat 待保存的微信支付商户配置对象
     * @return 返回保存结果，包括操作状态和可能的错误信息
     */
    ReturnResult<PayMerchantConfigWechat> savePayMerchantConfigWechat(PayMerchantConfigWechat payMerchantConfigWechat);

    /**
     * 更新微信支付商户配置信息
     *
     * @param payMerchantConfigWechat 待更新的微信支付商户配置对象
     * @return 返回更新操作的结果，包括操作状态和可能的错误信息
     */
    ReturnResult<Boolean> updatePayMerchantConfigWechat(PayMerchantConfigWechat payMerchantConfigWechat);
}
