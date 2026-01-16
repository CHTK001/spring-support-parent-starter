package com.chua.starter.pay.support.rules;

import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.pay.support.entity.PayMerchantConfigWechat;
import com.chua.starter.pay.support.pojo.PaymentPointRequest;

/**
 * 支付分规则
 *
 * @author CH
 * @since 2025/10/15 11:26
 */
public interface PayPaymentPointsRule {

    /**
     * 创建支付分规则实例
     *
     * @param payMerchantConfigWechat 微信商户配置信息
     * @param postPaymentsDTO         支付后付款信息
     * @return 支付分规则实例
     */
    static PayPaymentPointsRule create(PayMerchantConfigWechat payMerchantConfigWechat, PaymentPointRequest.PostPaymentsDTO postPaymentsDTO) {
        String name = postPaymentsDTO.getName();
        return ServiceProvider.of(PayPaymentPointsRule.class).getNewExtension(name, payMerchantConfigWechat);
    }

    /**
     * 获取描述信息
     * @param postPayments   支付后付款信息
     * @return 描述信息
     */
    String getDescription(PaymentPointRequest.PostPaymentsDTO postPayments );
}
