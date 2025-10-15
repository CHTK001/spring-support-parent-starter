package com.chua.starter.pay.support.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.common.support.utils.IdUtils;
import com.chua.common.support.utils.ObjectUtils;
import com.chua.starter.mybatis.entity.Query;
import com.chua.starter.pay.support.pojo.PayMerchantWrapper;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.starter.pay.support.mapper.PayMerchantMapper;
import com.chua.starter.pay.support.entity.PayMerchant;
import com.chua.starter.pay.support.service.PayMerchantService;

import java.util.List;

import static com.chua.starter.common.support.constant.CacheConstant.CACHE_MANAGER_FOR_SYSTEM;
import static com.chua.starter.common.support.constant.CacheConstant.REDIS_CACHE_ALWAYS;

/**
 *
 * @author CH
 * @since 2025/10/14 11:28
 */
@Service
public class PayMerchantServiceImpl extends ServiceImpl<PayMerchantMapper, PayMerchant> implements PayMerchantService{

    @Override
    @Cacheable(cacheManager = CACHE_MANAGER_FOR_SYSTEM, cacheNames = REDIS_CACHE_ALWAYS, key = "'PAY:MERCHANT:' + #payMerchantId" , keyGenerator = "customTenantedKeyGenerator")
    public PayMerchantWrapper getByCodeForPayMerchant(Integer payMerchantId) {
        PayMerchant payMerchant = this.getById(payMerchantId);
        return new PayMerchantWrapper(payMerchant);
    }

    @Override
    @Caching(evict = {
            @CacheEvict(cacheManager = CACHE_MANAGER_FOR_SYSTEM, cacheNames = REDIS_CACHE_ALWAYS, key = "'PAY:MERCHANT:' + #payMerchant.payMerchantId" , keyGenerator = "customTenantedKeyGenerator"),
            @CacheEvict(cacheManager = CACHE_MANAGER_FOR_SYSTEM, cacheNames = REDIS_CACHE_ALWAYS, key = "'PAY:MERCHANT:ALL'" , keyGenerator = "customTenantedKeyGenerator")
    })
    public Boolean updateForPayMerchant(PayMerchant payMerchant) {
        return this.updateById(payMerchant);
    }

    @Override
    @Cacheable(cacheManager = CACHE_MANAGER_FOR_SYSTEM, cacheNames = REDIS_CACHE_ALWAYS, key = "'PAY:MERCHANT:ALL'" , keyGenerator = "customTenantedKeyGenerator")
    public List<PayMerchant> allEffective() {
        return this.list(Wrappers.<PayMerchant>lambdaQuery().eq(PayMerchant::getPayMerchantDelete, 0)
                .eq(PayMerchant::getPayMerchantStatus, 1)
                .eq(PayMerchant::getPayMerchantOpenTimeout, 1)
                .isNotNull(PayMerchant::getPayMerchantOpenTimeoutTime)
        );
    }

    @Override
    @CacheEvict(cacheManager = CACHE_MANAGER_FOR_SYSTEM, cacheNames = REDIS_CACHE_ALWAYS, key = "'PAY:MERCHANT:ALL'" , keyGenerator = "customTenantedKeyGenerator")
    public boolean saveForPayMerchant(PayMerchant payMerchant) {
        payMerchant.setPayMerchantDelete(0);
        payMerchant.setPayMerchantStatus(1);
        payMerchant.setPayMerchantOpenWallet(ObjectUtils.defaultIfNull(payMerchant.getPayMerchantOpenWallet(), 0));
        payMerchant.setPayMerchantCode(IdUtils.createDataFinger());
        return this.save(payMerchant);
    }

    @Override
    public PayMerchant getByCodeForPayMerchantCode(Integer payMerchantCode) {
        return this.getOne(Wrappers.<PayMerchant>lambdaQuery().eq(PayMerchant::getPayMerchantCode, payMerchantCode));
    }

    @Override
    public IPage<PayMerchant> pageForMerchant(Query<PayMerchant> page, PayMerchant payMerchant) {
        return this.page(page.createFullPage(), Wrappers.<PayMerchant>lambdaQuery()
                .eq(PayMerchant::getPayMerchantCode, payMerchant.getPayMerchantCode())
                .likeRight(PayMerchant::getPayMerchantName, payMerchant.getPayMerchantName())
        );
    }


}
