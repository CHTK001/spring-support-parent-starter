package com.chua.starter.pay.support.handler.wechat.natives;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.common.support.annotations.Spi;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.objects.annotation.AutoInject;
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
@Spi("wechat_native")
public final class WechatNativePayConfigDetector implements PayConfigDetector<PayMerchantConfigWechat> {


    @AutoInject
    private PayMerchantConfigWechatMapper payMerchantConfigWechatMapper;

    @Override
    public ReturnResult<PayMerchantConfigWechat> check(PayMerchant payMerchant, TradeType tradeType) {
        PayMerchantConfigWechat payMerchantConfigWechat = payMerchantConfigWechatMapper.selectOne(Wrappers.<PayMerchantConfigWechat>lambdaQuery()
                .eq(PayMerchantConfigWechat::getPayMerchantConfigWechatTradeType,"native")
                .eq(PayMerchantConfigWechat::getPayMerchantId, payMerchant.getPayMerchantId())
                .eq(PayMerchantConfigWechat::getPayMerchantConfigStatus, 1)
        );

        if(null == payMerchantConfigWechat) {
            return ReturnResult.error("当前系统不支持native支付");
        }
        return ReturnResult.ok(payMerchantConfigWechat);
    }
}
