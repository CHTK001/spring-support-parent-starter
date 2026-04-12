package com.chua.payment.support.service.impl;

import com.chua.payment.support.channel.PaymentChannelRegistry;
import com.chua.payment.support.configuration.PaymentCipherService;
import com.chua.payment.support.entity.PaymentOrder;
import com.chua.payment.support.entity.MerchantChannel;
import com.chua.payment.support.enums.ChannelStatus;
import com.chua.payment.support.exception.PaymentException;
import com.chua.payment.support.mapper.MerchantChannelMapper;
import com.chua.payment.support.mapper.MerchantMapper;
import com.chua.payment.support.mapper.PaymentOrderMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MerchantChannelServiceImplTest {

    private final MerchantChannelMapper channelMapper = mock(MerchantChannelMapper.class);
    private final MerchantMapper merchantMapper = mock(MerchantMapper.class);
    private final PaymentOrderMapper paymentOrderMapper = mock(PaymentOrderMapper.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PaymentCipherService paymentCipherService = mock(PaymentCipherService.class);
    private final PaymentChannelRegistry paymentChannelRegistry = mock(PaymentChannelRegistry.class);

    private MerchantChannelServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new MerchantChannelServiceImpl(
                channelMapper,
                merchantMapper,
                paymentOrderMapper,
                objectMapper,
                paymentCipherService,
                paymentChannelRegistry);
    }

    @Test
    void shouldRejectEnableWhenChannelImplementationIsMissing() {
        MerchantChannel channel = new MerchantChannel();
        channel.setId(99L);
        channel.setChannelType("COMPOSITE");
        channel.setChannelSubType("AGGREGATE_ROUTE");
        channel.setStatus(ChannelStatus.DISABLED.getCode());
        when(channelMapper.selectById(99L)).thenReturn(channel);
        when(paymentChannelRegistry.supports("COMPOSITE", "AGGREGATE_ROUTE")).thenReturn(false);

        assertThrows(PaymentException.class, () -> service.enableChannel(99L));

        verify(channelMapper, never()).updateById(channel);
    }

    @Test
    void shouldRejectDeleteWhenChannelIsEnabled() {
        MerchantChannel channel = new MerchantChannel();
        channel.setId(100L);
        channel.setStatus(ChannelStatus.ENABLED.getCode());
        when(channelMapper.selectById(100L)).thenReturn(channel);

        assertThrows(PaymentException.class, () -> service.deleteChannel(100L));

        verify(channelMapper, never()).deleteById(100L);
    }

    @Test
    void shouldRejectDeleteWhenOrdersStillReferenceChannel() {
        MerchantChannel channel = new MerchantChannel();
        channel.setId(101L);
        channel.setStatus(ChannelStatus.DISABLED.getCode());
        when(channelMapper.selectById(101L)).thenReturn(channel);
        when(paymentOrderMapper.selectCount(org.mockito.ArgumentMatchers.any())).thenReturn(1L);

        assertThrows(PaymentException.class, () -> service.deleteChannel(101L));

        verify(channelMapper, never()).deleteById(101L);
    }
}
