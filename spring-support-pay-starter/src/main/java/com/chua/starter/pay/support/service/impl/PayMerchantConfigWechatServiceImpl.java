package com.chua.starter.pay.support.service.impl;

import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.common.support.annotations.ApiCacheKey;
import com.chua.starter.pay.support.entity.PayMerchantConfigWechat;
import com.chua.starter.pay.support.mapper.PayMerchantConfigWechatMapper;
import com.chua.starter.pay.support.service.PayMerchantConfigWechatService;
import jakarta.annotation.Resource;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.chua.starter.common.support.constant.CacheConstant.REDIS_CACHE_ALWAYS;

/**
 *
 * @since 2024/12/30
 * @author CH    
 */
@Service
public class PayMerchantConfigWechatServiceImpl extends ServiceImpl<PayMerchantConfigWechatMapper, PayMerchantConfigWechat> implements PayMerchantConfigWechatService{
    @Resource(name = REDIS_CACHE_ALWAYS)
    private CacheManager cacheManager;

    @Override
    @ApiCacheKey("'sys:pay:merchant:wechat' + #payMerchantConfigWechatId")
    @Cacheable(cacheManager = REDIS_CACHE_ALWAYS, cacheNames = REDIS_CACHE_ALWAYS, keyGenerator = "customTenantedKeyGenerator")
    public PayMerchantConfigWechat getById(Long payMerchantConfigWechatId) {
        return baseMapper.selectById(payMerchantConfigWechatId);
    }

    @Override
    public ReturnResult<PayMerchantConfigWechat> savePayMerchantConfigWechat(PayMerchantConfigWechat payMerchantConfigWechat) {
        PayMerchantConfigWechat payMerchantConfigWechat1 = baseMapper.selectOne(Wrappers.<PayMerchantConfigWechat>lambdaQuery()
                .eq(PayMerchantConfigWechat::getPayMerchantId, payMerchantConfigWechat.getPayMerchantId())
                .eq(PayMerchantConfigWechat::getPayMerchantConfigWechatTradeType, payMerchantConfigWechat.getPayMerchantConfigWechatTradeType())
        );
        if(null != payMerchantConfigWechat1) {
            return ReturnResult.fail("微信支付配置已存在");
        }
        baseMapper.insert(payMerchantConfigWechat);
        return ReturnResult.ok(payMerchantConfigWechat);
    }

    @Override
    public ReturnResult<Boolean> updatePayMerchantConfigWechat(PayMerchantConfigWechat payMerchantConfigWechat) {
        PayMerchantConfigWechat payMerchantConfigWechat2 = getById(payMerchantConfigWechat);
        String payMerchantConfigWechatTradeType = payMerchantConfigWechat.getPayMerchantConfigWechatTradeType();
        if(StringUtils.isNotBlank(payMerchantConfigWechatTradeType)) {
            PayMerchantConfigWechat payMerchantConfigWechat1 = baseMapper.selectOne(Wrappers.<PayMerchantConfigWechat>lambdaQuery()
                    .eq(PayMerchantConfigWechat::getPayMerchantId, payMerchantConfigWechat.getPayMerchantId())
                    .eq(PayMerchantConfigWechat::getPayMerchantConfigWechatTradeType, payMerchantConfigWechatTradeType)
                    .ne(PayMerchantConfigWechat::getPayMerchantConfigWechatId, payMerchantConfigWechat.getPayMerchantConfigWechatId())
            );
            if(null != payMerchantConfigWechat1) {
                return ReturnResult.fail("微信支付配置已存在");
            }
        }
        clearCache(payMerchantConfigWechat2);
        return ReturnResult.ok(baseMapper.updateById(payMerchantConfigWechat )> 0);
    }

    @Override
    public List<PayMerchantConfigWechat> getByMerchant(Integer payMerchantId) {
        return baseMapper.selectList(Wrappers.<PayMerchantConfigWechat>lambdaQuery().eq(PayMerchantConfigWechat::getPayMerchantId, payMerchantId));
    }

    /**
     * 清除缓存
     * @param payMerchantConfigWechat payMerchantConfigWechat
     */
    private void clearCache(PayMerchantConfigWechat payMerchantConfigWechat) {
        cacheManager.getCache(REDIS_CACHE_ALWAYS).evictIfPresent(
                "sys:pay:merchant:wechat" + payMerchantConfigWechat.getPayMerchantId());

    }
}
