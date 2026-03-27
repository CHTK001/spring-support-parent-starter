package com.chua.payment.support.service;

import com.chua.payment.support.configuration.PaymentCallbackProperties;
import com.chua.payment.support.entity.Merchant;
import com.chua.payment.support.entity.MerchantChannel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PaymentCallbackUrlResolverTest {

    @Test
    void shouldBuildOrderAndRefundScopedNotifyUrls() {
        PaymentCallbackProperties properties = new PaymentCallbackProperties();
        properties.setBaseUrl("http://127.0.0.1:8080/");
        PaymentCallbackUrlResolver resolver = new PaymentCallbackUrlResolver(properties);

        Merchant merchant = new Merchant();
        MerchantChannel channel = new MerchantChannel();
        channel.setId(12L);
        channel.setChannelType("WECHAT");

        assertEquals(
                "http://127.0.0.1:8080/api/notify/wechat/pay/12/ORD001",
                resolver.resolvePayNotifyUrl(null, channel, merchant, "ORD001"));
        assertEquals(
                "http://127.0.0.1:8080/api/notify/wechat/refund/12/REF001",
                resolver.resolveRefundNotifyUrl(channel, "REF001", null));
        assertEquals(
                "http://127.0.0.1:8080/api/notify/wechat/payscore/12/PS001",
                resolver.defaultWechatPayScoreNotifyUrl(12L, "PS001"));
    }

    @Test
    void shouldPreserveExplicitConfiguredNotifyUrl() {
        PaymentCallbackProperties properties = new PaymentCallbackProperties();
        properties.setBaseUrl("http://127.0.0.1:8080");
        PaymentCallbackUrlResolver resolver = new PaymentCallbackUrlResolver(properties);

        Merchant merchant = new Merchant();
        merchant.setDefaultNotifyUrl("https://merchant.example.com/pay/notify");
        MerchantChannel channel = new MerchantChannel();
        channel.setId(18L);
        channel.setChannelType("ALIPAY");

        assertEquals(
                "https://merchant.example.com/pay/notify",
                resolver.resolvePayNotifyUrl(null, channel, merchant, "ORD002"));
    }
}
