package com.chua.payment.support.service;

import com.chua.payment.support.entity.Merchant;
import com.chua.payment.support.entity.MerchantChannel;
import com.chua.payment.support.entity.PaymentGlobalConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PaymentCallbackUrlResolverTest {

    @Test
    void shouldBuildOrderAndRefundScopedNotifyUrls() {
        PaymentGlobalConfigService paymentGlobalConfigService = mock(PaymentGlobalConfigService.class);
        PaymentGlobalConfig globalConfig = new PaymentGlobalConfig();
        globalConfig.setPaymentNotifyBaseUrl("http://127.0.0.1:8080/");
        when(paymentGlobalConfigService.getConfigEntity()).thenReturn(globalConfig);
        PaymentCallbackUrlResolver resolver = new PaymentCallbackUrlResolver(paymentGlobalConfigService);

        Merchant merchant = new Merchant();
        merchant.setId(10001L);
        MerchantChannel channel = new MerchantChannel();
        channel.setChannelType("WECHAT");

        assertEquals(
                "http://127.0.0.1:8080/api/notify/wechat/pay/ORD001/10001",
                resolver.resolvePayNotifyUrl(null, channel, merchant, "ORD001"));
        assertEquals(
                "http://127.0.0.1:8080/api/notify/wechat/refund/REF001/10001",
                resolver.resolveRefundNotifyUrl(channel, 10001L, "REF001", null));
        assertEquals(
                "http://127.0.0.1:8080/api/notify/wechat/payscore/12/PS001",
                resolver.defaultWechatPayScoreNotifyUrl(12L, "PS001"));
    }

    @Test
    void shouldPreserveExplicitConfiguredNotifyUrl() {
        PaymentGlobalConfigService paymentGlobalConfigService = mock(PaymentGlobalConfigService.class);
        PaymentGlobalConfig globalConfig = new PaymentGlobalConfig();
        globalConfig.setPaymentNotifyBaseUrl("http://127.0.0.1:8080");
        when(paymentGlobalConfigService.getConfigEntity()).thenReturn(globalConfig);
        PaymentCallbackUrlResolver resolver = new PaymentCallbackUrlResolver(paymentGlobalConfigService);

        Merchant merchant = new Merchant();
        merchant.setId(10002L);
        merchant.setDefaultNotifyUrl("https://merchant.example.com/pay/notify");
        MerchantChannel channel = new MerchantChannel();
        channel.setChannelType("ALIPAY");

        assertEquals(
                "https://merchant.example.com/pay/notify",
                resolver.resolvePayNotifyUrl(null, channel, merchant, "ORD002"));
    }
}
