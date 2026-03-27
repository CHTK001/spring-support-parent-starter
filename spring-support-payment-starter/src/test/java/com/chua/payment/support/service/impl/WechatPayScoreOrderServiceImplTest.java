package com.chua.payment.support.service.impl;

import com.chua.payment.support.configuration.PaymentCallbackProperties;
import com.chua.payment.support.dto.WechatPayScoreCreateDTO;
import com.chua.payment.support.entity.MerchantChannel;
import com.chua.payment.support.entity.WechatPayScoreOrder;
import com.chua.payment.support.mapper.MerchantChannelMapper;
import com.chua.payment.support.mapper.WechatPayScoreOrderMapper;
import com.chua.payment.support.provider.PaymentProviderGatewayRegistry;
import com.chua.payment.support.service.MerchantChannelService;
import com.chua.payment.support.service.PaymentCallbackUrlResolver;
import com.chua.starter.tencent.support.payment.TencentWechatPayGateway;
import com.chua.starter.tencent.support.payment.dto.TencentWechatPayScoreNotifyPayload;
import com.chua.starter.tencent.support.payment.dto.TencentWechatPayScoreRequest;
import com.chua.starter.tencent.support.payment.dto.TencentWechatPayScoreResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WechatPayScoreOrderServiceImplTest {

    private final MerchantChannelService merchantChannelService = mock(MerchantChannelService.class);
    private final WechatPayScoreOrderMapper wechatPayScoreOrderMapper = mock(WechatPayScoreOrderMapper.class);
    private final MerchantChannelMapper merchantChannelMapper = mock(MerchantChannelMapper.class);
    private final PaymentProviderGatewayRegistry providerGatewayRegistry = mock(PaymentProviderGatewayRegistry.class);
    private final TencentWechatPayGateway tencentWechatPayGateway = mock(TencentWechatPayGateway.class);
    private final PaymentCallbackProperties paymentCallbackProperties = new PaymentCallbackProperties();
    private WechatPayScoreOrderServiceImpl wechatPayScoreOrderService;

    @BeforeEach
    void setUp() {
        paymentCallbackProperties.setBaseUrl("http://127.0.0.1:8080");
        when(merchantChannelService.decryptApiKey(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(providerGatewayRegistry.tencentWechatPayGateway(any())).thenReturn(tencentWechatPayGateway);
        when(providerGatewayRegistry.tencentWechatPayGateway(eq((String) null))).thenReturn(tencentWechatPayGateway);
        wechatPayScoreOrderService = new WechatPayScoreOrderServiceImpl(
                merchantChannelService,
                new ObjectMapper(),
                wechatPayScoreOrderMapper,
                merchantChannelMapper,
                providerGatewayRegistry,
                new PaymentCallbackUrlResolver(paymentCallbackProperties));
    }

    @Test
    void shouldGenerateNotifyUrlAndPersistOrder() {
        MerchantChannel channel = buildChannel();
        when(merchantChannelMapper.selectById(9L)).thenReturn(channel);
        when(wechatPayScoreOrderMapper.selectOne(any())).thenReturn(null);

        TencentWechatPayScoreResponse response = new TencentWechatPayScoreResponse();
        response.setSuccess(true);
        response.setOutOrderNo("PS001");
        response.setServiceOrderNo("WX-PS-001");
        response.setState("CREATED");
        response.setPackageInfo("mock-package");
        response.setRawResponse("{\"state\":\"CREATED\"}");
        when(tencentWechatPayGateway.createPayScoreOrder(any(), any())).thenReturn(response);

        WechatPayScoreCreateDTO request = new WechatPayScoreCreateDTO();
        request.setMerchantId(1L);
        request.setChannelId(9L);
        request.setUserId(2L);
        request.setOutOrderNo("PS001");
        request.setOpenId("openid-001");
        request.setTotalAmount(new BigDecimal("12.34"));
        request.setServiceIntroduction("停车服务");

        WechatPayScoreOrder order = wechatPayScoreOrderService.createOrder(request);

        assertEquals("PS001", order.getOutOrderNo());
        assertEquals("http://127.0.0.1:8080/api/notify/wechat/payscore/9/PS001", order.getNotifyUrl());
        assertEquals("WX-PS-001", order.getServiceOrderNo());

        ArgumentCaptor<TencentWechatPayScoreRequest> requestCaptor = ArgumentCaptor.forClass(TencentWechatPayScoreRequest.class);
        verify(tencentWechatPayGateway).createPayScoreOrder(any(), requestCaptor.capture());
        assertEquals("SVC-001", requestCaptor.getValue().getServiceId());
        assertEquals("http://127.0.0.1:8080/api/notify/wechat/payscore/9/PS001", requestCaptor.getValue().getNotifyUrl());
        verify(wechatPayScoreOrderMapper).insert(any(WechatPayScoreOrder.class));
    }

    @Test
    void shouldApplyNotifyStateToLocalOrder() {
        WechatPayScoreOrder order = new WechatPayScoreOrder();
        order.setId(11L);
        order.setChannelId(9L);
        order.setOutOrderNo("PS002");
        order.setState("CREATED");
        when(wechatPayScoreOrderMapper.selectOne(any())).thenReturn(order);

        TencentWechatPayScoreNotifyPayload payload = new TencentWechatPayScoreNotifyPayload();
        payload.setOutOrderNo("PS002");
        payload.setServiceOrderNo("WX-PS-002");
        payload.setState("COMPLETED");
        payload.setFinishReason("DONE");

        wechatPayScoreOrderService.handleNotify(9L, "PS002", payload, "{\"state\":\"COMPLETED\"}");

        ArgumentCaptor<WechatPayScoreOrder> orderCaptor = ArgumentCaptor.forClass(WechatPayScoreOrder.class);
        verify(wechatPayScoreOrderMapper).updateById(orderCaptor.capture());
        assertEquals("COMPLETED", orderCaptor.getValue().getState());
        assertEquals("WX-PS-002", orderCaptor.getValue().getServiceOrderNo());
        assertEquals("DONE", orderCaptor.getValue().getFinishReason());
        assertNotNull(orderCaptor.getValue().getCompletedAt());
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
        channel.setExtConfig("{\"providerSpi\":\"mock\",\"merchantSerialNumber\":\"SERIAL-001\",\"payScoreServiceId\":\"SVC-001\"}");
        return channel;
    }
}
