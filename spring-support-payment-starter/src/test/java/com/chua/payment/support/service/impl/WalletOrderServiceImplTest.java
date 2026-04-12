package com.chua.payment.support.service.impl;

import com.chua.payment.support.channel.RechargeRequest;
import com.chua.payment.support.channel.TransferRequest;
import com.chua.payment.support.entity.PaymentGlobalConfig;
import com.chua.payment.support.entity.WalletOrder;
import com.chua.payment.support.mapper.WalletOrderMapper;
import com.chua.payment.support.service.PaymentCallbackUrlResolver;
import com.chua.payment.support.service.PaymentGlobalConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WalletOrderServiceImplTest {

    private final WalletOrderMapper walletOrderMapper = mock(WalletOrderMapper.class);
    private final PaymentGlobalConfigService paymentGlobalConfigService = mock(PaymentGlobalConfigService.class);
    private final PaymentCallbackUrlResolver paymentCallbackUrlResolver;
    private final WalletOrderServiceImpl walletOrderService;

    WalletOrderServiceImplTest() {
        PaymentGlobalConfig globalConfig = new PaymentGlobalConfig();
        globalConfig.setPaymentNotifyBaseUrl("http://127.0.0.1:8080");
        when(paymentGlobalConfigService.getConfigEntity()).thenReturn(globalConfig);
        paymentCallbackUrlResolver = new PaymentCallbackUrlResolver(paymentGlobalConfigService);
        walletOrderService = new WalletOrderServiceImpl(walletOrderMapper, paymentCallbackUrlResolver, new ObjectMapper());
    }

    @Test
    void shouldGenerateRechargeNotifyUrlAndPersistRequestPayload() {
        RechargeRequest request = new RechargeRequest();
        request.setRechargeNo("RCH001");
        request.setMerchantId(1L);
        request.setUserId(2L);
        request.setAmount(BigDecimal.TEN);

        when(walletOrderMapper.selectOne(any())).thenReturn(null);

        WalletOrder order = walletOrderService.createRechargeOrder(request);

        assertEquals("RCH001", order.getOrderNo());
        assertEquals("http://127.0.0.1:8080/api/notify/wallet/recharge/RCH001", request.getNotifyUrl());
        verify(walletOrderMapper).insert(any(WalletOrder.class));
    }

    @Test
    void shouldReturnExistingTransferOrderWhenOrderNoAlreadyExists() {
        WalletOrder existing = new WalletOrder();
        existing.setId(10L);
        existing.setOrderNo("TRF001");
        existing.setOrderType("TRANSFER");

        TransferRequest request = new TransferRequest();
        request.setTransferNo("TRF001");
        request.setMerchantId(1L);
        request.setFromUserId(2L);
        request.setToUserId(3L);
        request.setAmount(new BigDecimal("12.34"));

        when(walletOrderMapper.selectOne(any())).thenReturn(existing);

        WalletOrder result = walletOrderService.createTransferOrder(request);

        assertSame(existing, result);
        verify(walletOrderMapper, never()).insert(any(WalletOrder.class));
    }

    @Test
    void shouldMarkWalletOrderSuccessWithCompletionTime() {
        WalletOrder order = new WalletOrder();
        order.setId(20L);
        order.setOrderNo("RCH002");
        when(walletOrderMapper.selectOne(any())).thenReturn(order);

        walletOrderService.markSuccess("RCH002", "TP2001", "{\"status\":\"success\"}");

        ArgumentCaptor<WalletOrder> captor = ArgumentCaptor.forClass(WalletOrder.class);
        verify(walletOrderMapper).updateById(captor.capture());
        WalletOrder updated = captor.getValue();
        assertEquals("SUCCESS", updated.getStatus());
        assertEquals("TP2001", updated.getThirdPartyOrderNo());
        assertEquals("{\"status\":\"success\"}", updated.getResponsePayload());
        assertNotNull(updated.getCompletedAt());
    }

    @Test
    void shouldMarkWalletOrderFailedWithReason() {
        WalletOrder order = new WalletOrder();
        order.setId(21L);
        order.setOrderNo("RCH003");
        when(walletOrderMapper.selectOne(any())).thenReturn(order);

        walletOrderService.markFailed("RCH003", "{\"status\":\"failed\"}", "bank rejected");

        ArgumentCaptor<WalletOrder> captor = ArgumentCaptor.forClass(WalletOrder.class);
        verify(walletOrderMapper).updateById(captor.capture());
        WalletOrder updated = captor.getValue();
        assertEquals("FAILED", updated.getStatus());
        assertEquals("{\"status\":\"failed\"}", updated.getResponsePayload());
        assertEquals("bank rejected", updated.getRemark());
    }
}
