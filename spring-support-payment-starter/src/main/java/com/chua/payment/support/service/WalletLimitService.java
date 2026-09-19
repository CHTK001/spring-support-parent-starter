package com.chua.payment.support.service;

import com.chua.payment.support.entity.MerchantWalletLimit;

import java.math.BigDecimal;

/**
 * 钱包限额服务接口，定义商户钱包充值/提现/转账额度校验契约。
 *
 * @author CH
 * @since 4.0.0.50
 */
public interface WalletLimitService {

    /**
     * 获取商户钱包限额配置
     * @param merchantId merchantid
     * @return 获取限制的结果
     */
    MerchantWalletLimit getLimit(Long merchantId);

    /**
     * 保存或更新限额配置
     * @param limit 限制
     */
    void saveOrUpdate(MerchantWalletLimit limit);

    /**
     * 校验充值限额
     * @param merchantId merchantid
     * @param userId 用户标识
     * @param amount amount
     */
    void validateRechargeLimit(Long merchantId, Long userId, BigDecimal amount);

    /**
     * 校验提现限额
     * @param merchantId merchantid
     * @param userId 用户标识
     * @param amount amount
     */
    void validateWithdrawLimit(Long merchantId, Long userId, BigDecimal amount);

    /**
     * 校验转账限额
     * @param merchantId merchantid
     * @param userId 用户标识
     * @param amount amount
     */
    void validateTransferLimit(Long merchantId, Long userId, BigDecimal amount);

    /**
     * 校验余额上限
     * @param merchantId merchantid
     * @param userId 用户标识
     * @param currentBalance 当前balance
     * @param addAmount 添加amount
     */
    void validateBalanceLimit(Long merchantId, Long userId, BigDecimal currentBalance, BigDecimal addAmount);
}

