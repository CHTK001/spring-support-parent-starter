package com.chua.starter.pay.support.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.starter.pay.support.pojo.PayMerchantConfigWechatWrapper;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.starter.pay.support.entity.PayMerchantConfigWechat;
import com.chua.starter.pay.support.mapper.PayMerchantConfigWechatMapper;
import com.chua.starter.pay.support.service.PayMerchantConfigWechatService;

import static com.chua.starter.common.support.constant.CacheConstant.SYSTEM;

/**
 *
 * @author CH
 * @since 2025/10/14 11:28
 */
@Service
public class PayMerchantConfigWechatServiceImpl extends ServiceImpl<PayMerchantConfigWechatMapper, PayMerchantConfigWechat> implements PayMerchantConfigWechatService{

    @Override
    @Cacheable(cacheManager = SYSTEM, cacheNames = SYSTEM, key = "'PAY:WECHAT:' + #wechatType + '-' + #payMerchantId")
    public PayMerchantConfigWechatWrapper getByCodeForPayMerchantConfigWechat(Integer payMerchantId, String wechatType) {
        return new PayMerchantConfigWechatWrapper(
                this.getOne(Wrappers.<PayMerchantConfigWechat>lambdaQuery().eq(PayMerchantConfigWechat::getPayMerchantId, payMerchantId).eq(PayMerchantConfigWechat::getPayMerchantConfigWechatTradeType, wechatType), false)
        );
    }

    @Override
    @Cacheable(cacheManager = SYSTEM, cacheNames = SYSTEM, key = "'PAY:WECHAT:' + #payMerchantConfigWechat.payMerchantConfigWechatTradeType + '-' + #payMerchantConfigWechat.payMerchantId")
    public PayMerchantConfigWechat updateForPayMerchantConfigWechat(PayMerchantConfigWechat payMerchantConfigWechat) {
        updateById(payMerchantConfigWechat);
        return payMerchantConfigWechat;
    }

    @Override
    public boolean saveForPayMerchantConfigWechat(PayMerchantConfigWechat payMerchantConfigWechat) {
        return this.save(payMerchantConfigWechat);
    }

    @Override
    public PayMerchantConfigWechat detail(Integer payMerchantId, Integer merchantConfigWechatId) {
        PayMerchantConfigWechat configWechat = getById(payMerchantId);
        if (null != configWechat) {
            return configWechat;
        }
        configWechat = new PayMerchantConfigWechat();
        configWechat.setPayMerchantId(payMerchantId);
        saveForPayMerchantConfigWechat(configWechat);
        return configWechat;
    }

}
