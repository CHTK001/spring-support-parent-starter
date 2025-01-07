package com.chua.starter.pay.support.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.common.support.annotations.ApiCacheKey;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.starter.pay.support.mapper.PayUserWalletMapper;
import com.chua.starter.pay.support.entity.PayUserWallet;
import com.chua.starter.pay.support.service.PayUserWalletService;

import java.math.BigDecimal;

import static com.chua.starter.common.support.constant.CacheConstant.REDIS_CACHE_ALWAYS;

/**
 * pay_user_wallet
 * 用户钱包
 */
@Service
public class PayUserWalletServiceImpl extends ServiceImpl<PayUserWalletMapper, PayUserWallet> implements PayUserWalletService{

    @Override
    @ApiCacheKey("'sys:pay:user:wallet:' + #payMerchantOrderUserId")
    @Cacheable(cacheManager = REDIS_CACHE_ALWAYS, cacheNames = REDIS_CACHE_ALWAYS, keyGenerator = "customTenantedKeyGenerator")
    public ReturnResult<PayUserWallet> getUserWallet(String payMerchantOrderUserId) {
        return ReturnResult.ok(baseMapper.selectOne(Wrappers.<PayUserWallet>lambdaQuery()
                .eq(PayUserWallet::getPayUserWalletUserId, payMerchantOrderUserId)
        ));
    }

    @Override
    @ApiCacheKey("'sys:pay:user:wallet:' + #payMerchantOrderUserId")
    @CacheEvict(cacheManager = REDIS_CACHE_ALWAYS, cacheNames = REDIS_CACHE_ALWAYS, keyGenerator = "customTenantedKeyGenerator")
    public ReturnResult<PayUserWallet> incrementUserWallet(String payMerchantOrderUserId, BigDecimal money) {
        PayUserWallet payUserWallet = baseMapper.selectOne(Wrappers.<PayUserWallet>lambdaQuery()
                .eq(PayUserWallet::getPayUserWalletUserId, payMerchantOrderUserId)
        );

        if(null == payUserWallet) {
            payUserWallet = new PayUserWallet();
            payUserWallet.setPayUserWalletUserId(payMerchantOrderUserId);
            payUserWallet.setPayUserWalletMoney(money);
            baseMapper.insert(payUserWallet);
            return ReturnResult.ok(payUserWallet);
        }
        return incrementUserWallet(payMerchantOrderUserId, money, 1, 3);
    }

    /**
     * 增加
     * @param payMerchantOrderUserId
     * @param money
     * @param index
     * @param max
     * @return
     */
    private ReturnResult<PayUserWallet> incrementUserWallet(String payMerchantOrderUserId, BigDecimal money, int index, int max) {
        if(index > max) {
            return ReturnResult.error("操作失败");
        }
        PayUserWallet payUserWallet = baseMapper.selectOne(Wrappers.<PayUserWallet>lambdaQuery()
                .eq(PayUserWallet::getPayUserWalletUserId, payMerchantOrderUserId)
        );
        try {
            payUserWallet.setPayUserWalletMoney(payUserWallet.getPayUserWalletMoney().add(money));
            baseMapper.updateById(payUserWallet);
        } catch (Exception e) {
            return incrementUserWallet(payMerchantOrderUserId, money, index + 1, max);
        }
        return ReturnResult.ok(payUserWallet);
    }

    @Override
    @ApiCacheKey("'sys:pay:user:wallet:' + #payMerchantOrderUserId")
    @CacheEvict(cacheManager = REDIS_CACHE_ALWAYS, cacheNames = REDIS_CACHE_ALWAYS, keyGenerator = "customTenantedKeyGenerator")
    public ReturnResult<PayUserWallet> decrementUserWallet(String payMerchantOrderUserId, BigDecimal money) {
        PayUserWallet payUserWallet = baseMapper.selectOne(Wrappers.<PayUserWallet>lambdaQuery()
                .eq(PayUserWallet::getPayUserWalletUserId, payMerchantOrderUserId)
        );

        if(null == payUserWallet) {
            payUserWallet = new PayUserWallet();
            payUserWallet.setPayUserWalletUserId(payMerchantOrderUserId);
            payUserWallet.setPayUserWalletMoney(money.multiply(new BigDecimal(-1)));
            baseMapper.insert(payUserWallet);
            return ReturnResult.ok(payUserWallet);
        }
        return decrementUserWallet(payMerchantOrderUserId, money, 1, 3);
    }

    /**
     * 减少
     * @param payMerchantOrderUserId
     * @param money
     * @param index
     * @param max
     * @return
     */
    private ReturnResult<PayUserWallet> decrementUserWallet(String payMerchantOrderUserId, BigDecimal money, int index, int max) {
        if(index > max) {
            return ReturnResult.error("操作失败");
        }
        PayUserWallet payUserWallet = baseMapper.selectOne(Wrappers.<PayUserWallet>lambdaQuery()
                .eq(PayUserWallet::getPayUserWalletUserId, payMerchantOrderUserId)
        );
        try {
            payUserWallet.setPayUserWalletMoney(payUserWallet.getPayUserWalletMoney().subtract(money));
            baseMapper.updateById(payUserWallet);
        } catch (Exception e) {
            return decrementUserWallet(payMerchantOrderUserId, money, index + 1, max);
        }
        return ReturnResult.ok(payUserWallet);
    }
}
