package com.chua.starter.pay.support.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.starter.common.support.annotations.ApiCacheKey;
import com.chua.starter.pay.support.entity.PayMerchant;
import com.chua.starter.pay.support.mapper.PayMerchantMapper;
import com.chua.starter.pay.support.service.PayMerchantService;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import static com.chua.starter.common.support.constant.CacheConstant.REDIS_CACHE_ALWAYS;

/**
 *
 * @since 2024/12/30
 * @author CH    
 */
@Service
public class PayMerchantServiceImpl extends ServiceImpl<PayMerchantMapper, PayMerchant> implements PayMerchantService{

    @Override
    @ApiCacheKey("'sys:pay:merchant' + #merchantCode")
    @Cacheable(cacheManager = REDIS_CACHE_ALWAYS, cacheNames = REDIS_CACHE_ALWAYS, keyGenerator = "customTenantedKeyGenerator")
    public PayMerchant getOneByCode(String merchantCode) {
        return baseMapper.selectOne(Wrappers.<PayMerchant>lambdaQuery().eq(PayMerchant::getPayMerchantCode, merchantCode));
    }

    @Override
    @ApiCacheKey("'sys:pay:merchant' + #{#merchantCode} + #{#force}")
    @Cacheable(cacheManager = REDIS_CACHE_ALWAYS, cacheNames = REDIS_CACHE_ALWAYS, keyGenerator = "customTenantedKeyGenerator")
    public PayMerchant getOneByCode(String merchantCode, boolean force) {
        return baseMapper.selectOne(Wrappers.<PayMerchant>lambdaQuery().eq(PayMerchant::getPayMerchantCode, merchantCode)
                .in(force, PayMerchant::getPayMerchantDelete, 0, 1)
                .in(force, PayMerchant::getPayMerchantStatus, 0, 1)
        );
    }
}
