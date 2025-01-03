package com.chua.starter.pay.support.handler;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.pay.support.emuns.TradeType;
import com.chua.starter.pay.support.entity.PayMerchant;

/**
 * 支付配置处理器
 * @author CH
 * @since 2024/12/30
 */
public interface PayConfigDetector<R> {

    /**
     * 检测支付配置
     * @param payMerchant 支付商户
     * @param tradeType 交易类型
     * @return 支付配置
     */
    ReturnResult<R> check(PayMerchant payMerchant, TradeType tradeType);
}
