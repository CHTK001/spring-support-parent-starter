package com.chua.starter.pay.support.rules;

import com.chua.common.support.core.annotation.SpiDefault;
import com.chua.starter.pay.support.entity.PayMerchantConfigWechat;
import com.chua.starter.pay.support.pojo.PaymentPointRequest;

/**
 * @author CH
 * @since 2025/10/15 11:27
 */
@SpiDefault
public class DefaultPayPaymentPointsRule implements PayPaymentPointsRule {

    private final PayMerchantConfigWechat payMerchantConfigWechat;

    public DefaultPayPaymentPointsRule(PayMerchantConfigWechat payMerchantConfigWechat) {
        this.payMerchantConfigWechat = payMerchantConfigWechat;
    }

    @Override
    public String getDescription(PaymentPointRequest.PostPaymentsDTO postPayments) {
        return postPayments.getDescription();
    }
}
