package com.chua.payment.support.service.impl;

import com.chua.payment.support.entity.MerchantPaymentConfig;
import com.chua.payment.support.entity.PaymentOrder;
import com.chua.payment.support.exception.PaymentException;
import com.chua.payment.support.mapper.MerchantPaymentConfigMapper;
import com.chua.payment.support.mapper.PaymentOrderMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MerchantPaymentConfigServiceImplTest {

    private final MerchantPaymentConfigMapper configMapper = mock(MerchantPaymentConfigMapper.class);
    private final PaymentOrderMapper orderMapper = mock(PaymentOrderMapper.class);
    private final MerchantPaymentConfigServiceImpl service = new MerchantPaymentConfigServiceImpl(configMapper, orderMapper);

    @Test
    void shouldReturnDefaultConfigWhenMerchantSpecificConfigMissing() {
        when(configMapper.selectOne(any())).thenReturn(null);

        MerchantPaymentConfig config = service.getConfig(1L);

        assertTrue(Boolean.TRUE.equals(config.getOrderReusable()));
        assertNull(config.getOrderTimeoutMinutes());
        assertNull(config.getPendingOrderLimit());
        assertTrue(Boolean.TRUE.equals(config.getAutoCancelTimeoutOrder()));
    }

    @Test
    void shouldRejectCreateOrderWhenPendingCountReachedLimit() {
        MerchantPaymentConfig config = new MerchantPaymentConfig();
        config.setMerchantId(1L);
        config.setPendingOrderLimit(1);
        when(configMapper.selectOne(any())).thenReturn(config);
        when(orderMapper.selectCount(any())).thenReturn(1L);

        assertThrows(PaymentException.class, () -> service.checkCanCreateOrder(1L, 2L));
    }

    @Test
    void shouldRejectRepeatPayForNonReusableOrder() {
        MerchantPaymentConfig config = new MerchantPaymentConfig();
        config.setMerchantId(1L);
        config.setOrderReusable(false);
        when(configMapper.selectOne(any())).thenReturn(config);

        PaymentOrder order = new PaymentOrder();
        order.setId(100L);
        order.setMerchantId(1L);
        order.setStatus("PAID");
        when(orderMapper.selectById(100L)).thenReturn(order);

        assertThrows(PaymentException.class, () -> service.checkCanPayOrder(100L));
    }

    @Test
    void shouldRejectExpiredOrderPayment() {
        MerchantPaymentConfig config = new MerchantPaymentConfig();
        config.setMerchantId(1L);
        config.setOrderReusable(true);
        config.setOrderTimeoutMinutes(5);
        when(configMapper.selectOne(any())).thenReturn(config);

        PaymentOrder order = new PaymentOrder();
        order.setId(101L);
        order.setMerchantId(1L);
        order.setStatus("PENDING");
        order.setCreatedAt(LocalDateTime.now().minusMinutes(10));
        when(orderMapper.selectById(101L)).thenReturn(order);

        assertThrows(PaymentException.class, () -> service.checkCanPayOrder(101L));
    }
}
