package com.chua.starter.pay.support.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.starter.pay.support.entity.PayMerchantConfigAlipay;
import com.chua.starter.pay.support.mapper.PayMerchantConfigAlipayMapper;
import com.chua.starter.pay.support.pojo.PayMerchantConfigAlipayWrapper;
import com.chua.starter.pay.support.service.PayMerchantConfigAlipayService;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import static com.chua.starter.common.support.constant.CacheConstant.CACHE_MANAGER_FOR_SYSTEM;
import static com.chua.starter.common.support.constant.CacheConstant.REDIS_CACHE_ALWAYS;

/**
 * 支付宝支付商户配置服务实现类
 *
 * @author CH
 * @since 2025/10/15 11:28
 */
@Service
public class PayMerchantConfigAlipayServiceImpl extends ServiceImpl<PayMerchantConfigAlipayMapper, PayMerchantConfigAlipay> implements PayMerchantConfigAlipayService {

    @Override
    @Cacheable(cacheManager = CACHE_MANAGER_FOR_SYSTEM, cacheNames = REDIS_CACHE_ALWAYS, key = "'PAY:ALIPAY:' + #alipayType + '-' + #payMerchantId", keyGenerator = "customTenantedKeyGenerator")
    public PayMerchantConfigAlipayWrapper getByCodeForPayMerchantConfigAlipay(Integer payMerchantId, String alipayType) {
        return new PayMerchantConfigAlipayWrapper(
                this.getOne(Wrappers.<PayMerchantConfigAlipay>lambdaQuery()
                        .eq(PayMerchantConfigAlipay::getPayMerchantId, payMerchantId)
                        .eq(PayMerchantConfigAlipay::getPayMerchantConfigAlipayTradeType, alipayType), false)
        );
    }

    @Override
    @Cacheable(cacheManager = CACHE_MANAGER_FOR_SYSTEM, cacheNames = REDIS_CACHE_ALWAYS, key = "'PAY:ALIPAY:' + #payMerchantConfigAlipay.payMerchantConfigAlipayTradeType + '-' + #payMerchantConfigAlipay.payMerchantId", keyGenerator = "customTenantedKeyGenerator")
    public PayMerchantConfigAlipay updateForPayMerchantConfigAlipay(PayMerchantConfigAlipay payMerchantConfigAlipay) {
        updateById(payMerchantConfigAlipay);
        return payMerchantConfigAlipay;
    }

    @Override
    public boolean saveForPayMerchantConfigAlipay(PayMerchantConfigAlipay payMerchantConfigAlipay) {
        return this.save(payMerchantConfigAlipay);
    }

    @Override
    public PayMerchantConfigAlipay detail(Integer payMerchantId, Integer merchantConfigAlipayId) {
        PayMerchantConfigAlipay configAlipay = getById(merchantConfigAlipayId);
        if (null != configAlipay) {
            return configAlipay;
        }
        configAlipay = new PayMerchantConfigAlipay();
        configAlipay.setPayMerchantId(payMerchantId);
        saveForPayMerchantConfigAlipay(configAlipay);
        return configAlipay;
    }
}

