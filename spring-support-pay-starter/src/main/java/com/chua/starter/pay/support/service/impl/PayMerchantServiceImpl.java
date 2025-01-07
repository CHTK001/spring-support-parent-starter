package com.chua.starter.pay.support.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.rpc.RpcService;
import com.chua.common.support.value.Value;
import com.chua.starter.common.support.annotations.ApiCacheKey;
import com.chua.starter.mybatis.entity.Query;
import com.chua.starter.pay.support.entity.PayMerchant;
import com.chua.starter.pay.support.mapper.PayMerchantMapper;
import com.chua.starter.pay.support.service.PayMerchantService;
import jakarta.annotation.Resource;
import org.springframework.cache.CacheManager;
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

    @Resource(name = REDIS_CACHE_ALWAYS)
    private CacheManager cacheManager;

    @Override
    @ApiCacheKey("'sys:pay:merchant' + #merchantCode")
    @Cacheable(cacheManager = REDIS_CACHE_ALWAYS, cacheNames = REDIS_CACHE_ALWAYS, keyGenerator = "customTenantedKeyGenerator", unless = "#result == null")
    public ReturnResult<PayMerchant> getOneByCode(String merchantCode) {
        PayMerchant payMerchant = baseMapper.selectOne(Wrappers.<PayMerchant>lambdaQuery().eq(PayMerchant::getPayMerchantCode, merchantCode));
        return null == payMerchant ? ReturnResult.illegal() : ReturnResult.ok(payMerchant);
    }


    @Override
    public PayMerchant savePayMerchant(PayMerchant payMerchant) {
        baseMapper.insert(payMerchant);
        return payMerchant;
    }

    @Override
    public ReturnResult<Boolean> updatePayMerchant(PayMerchant payMerchant) {
        PayMerchant payMerchant1 = baseMapper.selectById(payMerchant.getPayMerchantId());
        payMerchant.setPayMerchantCode(null);
        int updateById = baseMapper.updateById(payMerchant);
        clearCache(payMerchant1);
        return ReturnResult.of(updateById > 0);
    }

    @Override
    public ReturnResult<Boolean> deletePayMerchant(Integer payMerchantId) {
        PayMerchant payMerchant1 = baseMapper.selectById(payMerchantId);
        int updateById = baseMapper.deleteById(payMerchantId);
        clearCache(payMerchant1);
        return ReturnResult.of(updateById > 0);

    }

    @Override
    public IPage<PayMerchant> pageForMerchant(Query<PayMerchant> query) {
        return baseMapper.selectPage(query.createPage(), query.mpjLambda());
    }

    /**
     * 清除缓存
     * @param payMerchant payMerchant
     */
    private void clearCache(PayMerchant payMerchant) {
        cacheManager.getCache(REDIS_CACHE_ALWAYS).evictIfPresent("sys:pay:merchant" + payMerchant.getPayMerchantCode());

    }
}
