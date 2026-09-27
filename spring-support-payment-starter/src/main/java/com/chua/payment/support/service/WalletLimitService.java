package com.chua.payment.support.service;

import com.chua.payment.support.entity.MerchantWalletLimit;

import java.math.BigDecimal;

/**
 * 钱包限额服务接口，定义以商户为维度的充值、提现、转账额度校验契约。
 *
 * @author CH
 * @since 4.0.0.50
 */
public interface WalletLimitService {

    /**
     * 获取商户钱包限额配置。
     * @param merchantId 商户标识
     * @return 获取到的限额配置
     */
    MerchantWalletLimit getLimit(Long merchantId);

    /**
     * 保存或更新限额配置。
     * @param limit 限额配置
     */
    void saveOrUpdate(MerchantWalletLimit limit);

    /**
     * 校验充值限额。
     * @param merchantId 商户标识
     * @param userId 用户标识
     * @param amount 金额
     */
    void validateRechargeLimit(Long merchantId, Long userId, BigDecimal amount);

    /**
     * 校验提现限额。
     * @param merchantId 商户标识
     * @param userId 用户标识
     * @param amount 金额
     */
    void validateWithdrawLimit(Long merchantId, Long userId, BigDecimal amount);

    /**
     * 校验转账限额。
     * @param merchantId 商户标识
     * @param userId 用户标识
     * @param amount 金额
     */
    void validateTransferLimit(Long merchantId, Long userId, BigDecimal amount);

    /**
     * 校验余额上限。
     * @param merchantId 商户标识
     * @param userId 用户标识
     * @param currentBalance 当前余额
     * @param addAmount 增加金额
     */
    void validateBalanceLimit(Long merchantId, Long userId, BigDecimal currentBalance, BigDecimal addAmount);
}
