package com.chua.starter.pay.support.transfer;

import java.math.BigDecimal;

/**
 * 获取当前钱包
 *
 * @author CH
 */
public interface WalletCurrentTransfer {

    /**
     * 获取当前钱包
     *
     * @param userId 用户ID
     * @return 当前钱包
     */
    BigDecimal getCurrentWallet(String userId);
}
