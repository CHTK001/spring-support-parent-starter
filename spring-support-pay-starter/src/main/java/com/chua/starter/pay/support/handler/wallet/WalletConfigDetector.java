package com.chua.starter.pay.support.handler.wallet;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.common.support.annotations.Spi;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.objects.annotation.AutoInject;
import com.chua.common.support.utils.ObjectUtils;
import com.chua.starter.pay.support.emuns.TradeType;
import com.chua.starter.pay.support.entity.PayMerchant;
import com.chua.starter.pay.support.entity.PayMerchantConfigWechat;
import com.chua.starter.pay.support.handler.PayConfigDetector;
import com.chua.starter.pay.support.mapper.PayMerchantConfigWechatMapper;

/**
 * 支付配置处理器
 * @author CH
 * @since 2024/12/30
 */
@Spi("wechat_h5")
public final class WalletConfigDetector implements PayConfigDetector<PayMerchant> {


    @AutoInject
    private PayMerchantConfigWechatMapper payMerchantConfigWechatMapper;

    @Override
    public ReturnResult<PayMerchant> check(PayMerchant payMerchant, TradeType tradeType) {
        return ObjectUtils.equals(payMerchant.getPayMerchantOpenWallet(), 1) ?  ReturnResult.ok(payMerchant) : ReturnResult.illegal("未开通钱包");
    }
}
