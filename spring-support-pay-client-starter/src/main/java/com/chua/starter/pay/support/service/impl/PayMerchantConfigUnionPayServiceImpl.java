package com.chua.starter.pay.support.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.starter.pay.support.entity.PayMerchantConfigUnionPay;
import com.chua.starter.pay.support.mapper.PayMerchantConfigUnionPayMapper;
import com.chua.starter.pay.support.pojo.PayMerchantConfigUnionPayWrapper;
import com.chua.starter.pay.support.service.PayMerchantConfigUnionPayService;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import static com.chua.starter.common.support.constant.CacheConstant.CACHE_MANAGER_FOR_SYSTEM;
import static com.chua.starter.common.support.constant.CacheConstant.REDIS_CACHE_ALWAYS;

/**
 * 云闪付支付商户配置服务实现类
 *
 * @author CH
 * @since 2025/10/15 11:28
 */
@Service
public class PayMerchantConfigUnionPayServiceImpl extends ServiceImpl<PayMerchantConfigUnionPayMapper, PayMerchantConfigUnionPay> implements PayMerchantConfigUnionPayService {

    @Override
    @Cacheable(cacheManager = CACHE_MANAGER_FOR_SYSTEM, cacheNames = REDIS_CACHE_ALWAYS, key = "'PAY:UNIONPAY:' + #unionPayType + '-' + #payMerchantId", keyGenerator = "customTenantedKeyGenerator")
    public PayMerchantConfigUnionPayWrapper getByCodeForPayMerchantConfigUnionPay(Integer payMerchantId, String unionPayType) {
        return new PayMerchantConfigUnionPayWrapper(
                this.getOne(Wrappers.<PayMerchantConfigUnionPay>lambdaQuery()
                        .eq(PayMerchantConfigUnionPay::getPayMerchantId, payMerchantId)
                        .eq(PayMerchantConfigUnionPay::getPayMerchantConfigUnionPayTradeType, unionPayType), false)
        );
    }

    @Override
    @Cacheable(cacheManager = CACHE_MANAGER_FOR_SYSTEM, cacheNames = REDIS_CACHE_ALWAYS, key = "'PAY:UNIONPAY:' + #payMerchantConfigUnionPay.payMerchantConfigUnionPayTradeType + '-' + #payMerchantConfigUnionPay.payMerchantId", keyGenerator = "customTenantedKeyGenerator")
    public PayMerchantConfigUnionPay updateForPayMerchantConfigUnionPay(PayMerchantConfigUnionPay payMerchantConfigUnionPay) {
        updateById(payMerchantConfigUnionPay);
        return payMerchantConfigUnionPay;
    }

    @Override
    public boolean saveForPayMerchantConfigUnionPay(PayMerchantConfigUnionPay payMerchantConfigUnionPay) {
        return this.save(payMerchantConfigUnionPay);
    }

    @Override
    public PayMerchantConfigUnionPay detail(Integer payMerchantId, Integer merchantConfigUnionPayId) {
        PayMerchantConfigUnionPay configUnionPay = getById(merchantConfigUnionPayId);
        if (null != configUnionPay) {
            return configUnionPay;
        }
        configUnionPay = new PayMerchantConfigUnionPay();
        configUnionPay.setPayMerchantId(payMerchantId);
        saveForPayMerchantConfigUnionPay(configUnionPay);
        return configUnionPay;
    }
}

