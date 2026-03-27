package com.chua.payment.support.channel;

import com.chua.payment.support.channel.impl.AlipayAppPaymentChannel;
import com.chua.payment.support.channel.impl.AlipayWapPaymentChannel;
import com.chua.payment.support.channel.impl.AlipayWebPaymentChannel;
import com.chua.payment.support.channel.impl.WalletBalancePaymentChannel;
import com.chua.payment.support.channel.impl.WechatAppPaymentChannel;
import com.chua.payment.support.channel.impl.WechatH5PaymentChannel;
import com.chua.payment.support.channel.impl.WechatJsapiPaymentChannel;
import com.chua.payment.support.channel.impl.WechatMiniProgramPaymentChannel;
import com.chua.payment.support.channel.impl.WechatNativePaymentChannel;
import com.chua.payment.support.exception.PaymentException;
import com.chua.payment.support.provider.PaymentProviderGatewayRegistry;
import com.chua.payment.support.service.MerchantChannelService;
import com.chua.payment.support.service.WalletAccountService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class PaymentChannelRegistryTest {

    private final MerchantChannelService merchantChannelService = mock(MerchantChannelService.class);
    private final PaymentProviderGatewayRegistry providerGatewayRegistry = mock(PaymentProviderGatewayRegistry.class);
    private final WalletAccountService walletAccountService = mock(WalletAccountService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldResolveAllExecutableChannelImplementations() {
        PaymentChannel jsapi = new WechatJsapiPaymentChannel(merchantChannelService, objectMapper, providerGatewayRegistry);
        PaymentChannel h5 = new WechatH5PaymentChannel(merchantChannelService, objectMapper, providerGatewayRegistry);
        PaymentChannel app = new WechatAppPaymentChannel(merchantChannelService, objectMapper, providerGatewayRegistry);
        PaymentChannel mini = new WechatMiniProgramPaymentChannel(merchantChannelService, objectMapper, providerGatewayRegistry);
        PaymentChannel nativePay = new WechatNativePaymentChannel(merchantChannelService, objectMapper, providerGatewayRegistry);
        PaymentChannel alipayWeb = new AlipayWebPaymentChannel(merchantChannelService, objectMapper, providerGatewayRegistry);
        PaymentChannel alipayWap = new AlipayWapPaymentChannel(merchantChannelService, objectMapper, providerGatewayRegistry);
        PaymentChannel alipayApp = new AlipayAppPaymentChannel(merchantChannelService, objectMapper, providerGatewayRegistry);
        PaymentChannel wallet = new WalletBalancePaymentChannel(merchantChannelService, objectMapper, walletAccountService);

        PaymentChannelRegistry registry = new PaymentChannelRegistry(provider(
                jsapi,
                h5,
                app,
                mini,
                nativePay,
                alipayWeb,
                alipayWap,
                alipayApp,
                wallet));

        assertResolved(registry, "WECHAT", "JSAPI", jsapi);
        assertResolved(registry, "WECHAT", "H5", h5);
        assertResolved(registry, "WECHAT", "APP", app);
        assertResolved(registry, "WECHAT", "MINI_PROGRAM", mini);
        assertResolved(registry, "WECHAT", "MINIPROGRAM", mini);
        assertResolved(registry, "WECHAT", "NATIVE", nativePay);
        assertResolved(registry, "ALIPAY", "WEB", alipayWeb);
        assertResolved(registry, "ALIPAY", "WAP", alipayWap);
        assertResolved(registry, "ALIPAY", "APP", alipayApp);
        assertResolved(registry, "WALLET", "BALANCE", wallet);
    }

    @Test
    void shouldRejectUnsupportedChannelMatrix() {
        PaymentChannelRegistry registry = new PaymentChannelRegistry(provider(
                new WechatJsapiPaymentChannel(merchantChannelService, objectMapper, providerGatewayRegistry)));

        assertThrows(PaymentException.class, () -> registry.getChannel("ALIPAY", "APP"));
    }

    private ObjectProvider<PaymentChannel> provider(PaymentChannel... channels) {
        Map<String, Object> beans = new LinkedHashMap<>();
        for (int i = 0; i < channels.length; i++) {
            beans.put("channel" + i, channels[i]);
        }
        return new StaticListableBeanFactory(beans).getBeanProvider(PaymentChannel.class);
    }

    private void assertResolved(PaymentChannelRegistry registry,
                                String channelType,
                                String channelSubType,
                                PaymentChannel expected) {
        assertTrue(registry.supports(channelType, channelSubType));
        assertSame(expected, registry.getChannel(channelType, channelSubType));
    }
}
