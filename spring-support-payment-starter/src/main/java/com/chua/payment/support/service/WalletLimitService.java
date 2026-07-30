package com.chua.payment.support.service;

import com.chua.payment.support.entity.MerchantWalletLimit;

import java.math.BigDecimal;

/**
 * 钱包限额服务
 */
public interface WalletLimitService {

    /**
     * 获取商户钱包限额配置
     */
    MerchantWalletLimit getLimit(Long merchantId);

    /**
     * 保存或更新限额配置
     */
    void saveOrUpdate(MerchantWalletLimit limit);

    /**
     * 校验充值限额
     */
    void validateRechargeLimit(Long merchantId, Long userId, BigDecimal amount);

    /**
     * 校验提现限额
     */
    void validateWithdrawLimit(Long merchantId, Long userId, BigDecimal amount);

    /**
     * 校验转账限额
     */
    void validateTransferLimit(Long merchantId, Long userId, BigDecimal amount);

    /**
     * 校验余额上限
     */
    void validateBalanceLimit(Long merchantId, Long userId, BigDecimal currentBalance, BigDecimal addAmount);
}
