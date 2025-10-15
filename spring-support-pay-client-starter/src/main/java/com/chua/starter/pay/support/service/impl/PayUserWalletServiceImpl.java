package com.chua.starter.pay.support.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.starter.pay.support.entity.PayMerchantOrder;
import com.chua.starter.pay.support.enums.PayOrderStatus;
import com.chua.starter.pay.support.enums.PayTradeType;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.starter.pay.support.mapper.PayUserWalletMapper;
import com.chua.starter.pay.support.entity.PayUserWallet;
import com.chua.starter.pay.support.service.PayUserWalletService;

import java.math.BigDecimal;

import static com.chua.starter.common.support.constant.CacheConstant.CACHE_MANAGER_FOR_SYSTEM;
import static com.chua.starter.common.support.constant.CacheConstant.REDIS_CACHE_ALWAYS;

/**
 *
 * @author CH
 * @since 2025/10/14 11:28
 */
@Service
public class PayUserWalletServiceImpl extends ServiceImpl<PayUserWalletMapper, PayUserWallet> implements PayUserWalletService{

    @Override
    @Cacheable(cacheManager = CACHE_MANAGER_FOR_SYSTEM, cacheNames = REDIS_CACHE_ALWAYS, key = "'PAY:WALLET:' + #userId", keyGenerator = "customTenantedKeyGenerator")
    public PayUserWallet getByUser(String userId) {
        PayUserWallet payUserWallet = this.getOne(Wrappers.<PayUserWallet>lambdaQuery()
                .eq(PayUserWallet::getUserId, userId));
        if (null != payUserWallet) {
            return payUserWallet;
        }
        payUserWallet = new PayUserWallet();
        payUserWallet.setUserId(userId);
        payUserWallet.setPayUserWalletAmount(BigDecimal.ZERO);
        this.save(payUserWallet);
        return payUserWallet;
    }

    @Override
    @CacheEvict(cacheManager = CACHE_MANAGER_FOR_SYSTEM, cacheNames = REDIS_CACHE_ALWAYS, key = "'PAY:WALLET:' + #userId", keyGenerator = "customTenantedKeyGenerator")
    public boolean updateWallet(String userId, PayMerchantOrder payMerchantOrder) {
        PayTradeType payMerchantTradeType = payMerchantOrder.getPayMerchantTradeType();
        if(payMerchantTradeType != PayTradeType.PAY_WALLET) {
            return true;
        }
        PayOrderStatus payMerchantOrderStatus = payMerchantOrder.getPayMerchantOrderStatus();
        BigDecimal merchantOrderAmount = payMerchantOrder.getPayMerchantOrderAmount();
        if(payMerchantOrderStatus == PayOrderStatus.PAY_SUCCESS) {
            PayUserWallet payUserWallet = this.getByUser(userId);
            payUserWallet.setPayUserWalletAmount(payUserWallet.getPayUserWalletAmount().subtract(merchantOrderAmount));
            return this.updateById(payUserWallet);
        }

        if(payMerchantOrderStatus == PayOrderStatus.PAY_REFUND_SUCCESS) {
            PayUserWallet payUserWallet = this.getByUser(userId);
            payUserWallet.setPayUserWalletAmount(payUserWallet.getPayUserWalletAmount().add(merchantOrderAmount));
            return this.updateById(payUserWallet);
        }
        return true;
    }

    @Override
    public boolean addOrSubWallet(String userId, BigDecimal amount) {
        PayUserWallet payUserWallet = this.getByUser(userId);
        payUserWallet.setPayUserWalletAmount(payUserWallet.getPayUserWalletAmount().add(amount));
        return this.updateById(payUserWallet);
    }


}
