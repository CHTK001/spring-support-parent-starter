package com.chua.starter.pay.support.transfer;

import com.chua.common.support.annotations.Spi;
import com.chua.common.support.annotations.SpiDefault;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.objects.annotation.AutoInject;
import com.chua.starter.pay.support.entity.PayUserWallet;
import com.chua.starter.pay.support.service.PayUserWalletService;

import java.math.BigDecimal;

/**
 * 获取当前钱包
 *
 * @author CH
 */
@SpiDefault
public class DefaultWalletCurrentTransfer implements WalletCurrentTransfer{

    @AutoInject
    private PayUserWalletService payUserWalletService;
    @Override
    public BigDecimal getCurrentWallet(String userId) {
        ReturnResult<PayUserWallet> userWallet = payUserWalletService.getUserWallet(userId);
        if(!userWallet.isOk()) {
            return BigDecimal.ZERO;
        }
        PayUserWallet payUserWallet = userWallet.getData();
        if(null == payUserWallet) {
            return BigDecimal.ZERO;
        }
        return payUserWallet.getPayUserWalletMoney();
    }
}
