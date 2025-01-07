package com.chua.starter.pay.support.service;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.pay.support.entity.PayUserWallet;
import com.baomidou.mybatisplus.extension.service.IService;

import java.math.BigDecimal;

public interface PayUserWalletService extends IService<PayUserWallet>{



    /**
     * 获取用户钱包
     * @param payMerchantOrderUserId 用户id
     * @return 用户钱包
     */
    ReturnResult<PayUserWallet> getUserWallet(String payMerchantOrderUserId);
    /**
     * 增加用户钱包
     * @param payMerchantOrderUserId 用户id
     * @param money 金额
     * @return 用户钱包
     */
    ReturnResult<PayUserWallet> incrementUserWallet(String payMerchantOrderUserId, BigDecimal money);
    /**
     * 减少用户钱包
     * @param payMerchantOrderUserId 用户id
     * @param money 金额
     * @return 用户钱包
     */
    ReturnResult<PayUserWallet> decrementUserWallet(String payMerchantOrderUserId, BigDecimal money);
}
