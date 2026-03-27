package com.chua.payment.support.service.impl;

import com.chua.payment.support.entity.WalletOrder;
import com.chua.payment.support.exception.PaymentException;
import com.chua.payment.support.service.WalletAccountService;
import com.chua.payment.support.service.WalletOrderService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WalletNotifyServiceImplTest {

    private final WalletOrderService walletOrderService = mock(WalletOrderService.class);
    private final WalletAccountService walletAccountService = mock(WalletAccountService.class);
    private final WalletNotifyServiceImpl walletNotifyService = new WalletNotifyServiceImpl(walletOrderService, walletAccountService);

    @Test
    void shouldHandleSuccessNotify() {
        WalletOrder order = new WalletOrder();
        order.setOrderNo("TRF001");
        order.setOrderType("TRANSFER");
        order.setMerchantId(1L);
        order.setUserId(2L);
        order.setRelatedUserId(3L);
        order.setAmount(java.math.BigDecimal.TEN);
        when(walletOrderService.getByOrderNo("TRF001")).thenReturn(order);

        walletNotifyService.handleNotify("transfer", "TRF001", "TP123", "success", "{\"status\":\"success\"}", null);

        verify(walletAccountService).transfer(1L, 2L, 3L, java.math.BigDecimal.TEN, "TRF001", "wallet-notify", "TRF001");
        verify(walletOrderService).markSuccess("TRF001", "TP123", "{\"status\":\"success\"}");
    }

    @Test
    void shouldHandleProcessingNotify() {
        WalletOrder order = new WalletOrder();
        order.setOrderNo("WDW001");
        order.setOrderType("WITHDRAW");
        when(walletOrderService.getByOrderNo("WDW001")).thenReturn(order);

        walletNotifyService.handleNotify("withdraw", "WDW001", "TP456", "processing", "{\"status\":\"processing\"}", null);

        verify(walletOrderService).markProcessing("WDW001", "TP456", "{\"status\":\"processing\"}");
    }

    @Test
    void shouldSkipAccountReplayWhenOrderAlreadySuccess() {
        WalletOrder order = new WalletOrder();
        order.setOrderNo("RCH001");
        order.setOrderType("RECHARGE");
        order.setStatus("SUCCESS");
        when(walletOrderService.getByOrderNo("RCH001")).thenReturn(order);

        walletNotifyService.handleNotify("recharge", "RCH001", "TP789", "success", "{\"status\":\"success\"}", null);

        verify(walletAccountService, never()).recharge(any());
        verify(walletOrderService).markSuccess("RCH001", "TP789", "{\"status\":\"success\"}");
    }

    @Test
    void shouldRejectMismatchedOrderType() {
        WalletOrder order = new WalletOrder();
        order.setOrderNo("RCH001");
        order.setOrderType("RECHARGE");
        when(walletOrderService.getByOrderNo("RCH001")).thenReturn(order);

        assertThrows(PaymentException.class, () ->
                walletNotifyService.handleNotify("transfer", "RCH001", null, "success", "{}", null));
    }
}
