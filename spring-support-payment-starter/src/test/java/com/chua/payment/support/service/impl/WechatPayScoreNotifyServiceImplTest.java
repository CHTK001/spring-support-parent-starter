package com.chua.payment.support.service.impl;

import com.chua.payment.support.entity.MerchantChannel;
import com.chua.payment.support.exception.PaymentException;
import com.chua.payment.support.mapper.MerchantChannelMapper;
import com.chua.payment.support.provider.PaymentProviderGatewayRegistry;
import com.chua.payment.support.service.MerchantChannelService;
import com.chua.payment.support.service.WechatPayScoreOrderService;
import com.chua.starter.tencent.support.payment.TencentWechatPayGateway;
import com.chua.starter.tencent.support.payment.dto.TencentWechatPayScoreNotifyPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WechatPayScoreNotifyServiceImplTest {

    private final MerchantChannelService merchantChannelService = mock(MerchantChannelService.class);
    private final MerchantChannelMapper merchantChannelMapper = mock(MerchantChannelMapper.class);
    private final PaymentProviderGatewayRegistry providerGatewayRegistry = mock(PaymentProviderGatewayRegistry.class);
    private final TencentWechatPayGateway tencentWechatPayGateway = mock(TencentWechatPayGateway.class);
    private final WechatPayScoreOrderService wechatPayScoreOrderService = mock(WechatPayScoreOrderService.class);
    private final WechatPayScoreNotifyServiceImpl wechatPayScoreNotifyService = new WechatPayScoreNotifyServiceImpl(
            merchantChannelService,
            new ObjectMapper(),
            merchantChannelMapper,
            providerGatewayRegistry,
            wechatPayScoreOrderService);

    @Test
    void shouldRejectMismatchedOutOrderNo() {
        MerchantChannel channel = buildChannel();
        when(merchantChannelMapper.selectById(9L)).thenReturn(channel);
        when(merchantChannelService.decryptApiKey(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(providerGatewayRegistry.tencentWechatPayGateway(any())).thenReturn(tencentWechatPayGateway);
        when(providerGatewayRegistry.tencentWechatPayGateway(eq((String) null))).thenReturn(tencentWechatPayGateway);

        TencentWechatPayScoreNotifyPayload payload = new TencentWechatPayScoreNotifyPayload();
        payload.setOutOrderNo("PS999");
        when(tencentWechatPayGateway.parsePayScoreNotify(any(), any())).thenReturn(payload);

        assertThrows(PaymentException.class, () -> wechatPayScoreNotifyService.handleNotify(
                9L, "PS001", "serial", "ts", "nonce", "sig", "RSA", "{\"out_order_no\":\"PS999\"}"));
    }

    @Test
    void shouldForwardNotifyToOrderService() {
        MerchantChannel channel = buildChannel();
        when(merchantChannelMapper.selectById(9L)).thenReturn(channel);
        when(merchantChannelService.decryptApiKey(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(providerGatewayRegistry.tencentWechatPayGateway(any())).thenReturn(tencentWechatPayGateway);
        when(providerGatewayRegistry.tencentWechatPayGateway(eq((String) null))).thenReturn(tencentWechatPayGateway);

        TencentWechatPayScoreNotifyPayload payload = new TencentWechatPayScoreNotifyPayload();
        payload.setOutOrderNo("PS001");
        payload.setState("DOING");
        when(tencentWechatPayGateway.parsePayScoreNotify(any(), any())).thenReturn(payload);

        wechatPayScoreNotifyService.handleNotify(
                9L, "PS001", "serial", "ts", "nonce", "sig", "RSA", "{\"out_order_no\":\"PS001\"}");

        verify(wechatPayScoreOrderService).handleNotify(9L, "PS001", payload, "{\"out_order_no\":\"PS001\"}");
    }

    private MerchantChannel buildChannel() {
        MerchantChannel channel = new MerchantChannel();
        channel.setId(9L);
        channel.setMerchantId(1L);
        channel.setChannelType("WECHAT");
        channel.setStatus(1);
        channel.setAppId("wx-app-001");
        channel.setMerchantNo("mch-001");
        channel.setPrivateKey("private-key");
        channel.setApiKey("api-v3-key");
        channel.setExtConfig("{\"providerSpi\":\"mock\",\"merchantSerialNumber\":\"SERIAL-001\"}");
        return channel;
    }
}
