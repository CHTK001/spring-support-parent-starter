package com.chua.payment.support.service.impl;

import com.chua.payment.support.channel.PaymentChannelRegistry;
import com.chua.payment.support.configuration.PaymentCipherService;
import com.chua.payment.support.configuration.PaymentProviderProperties;
import com.chua.payment.support.entity.MerchantChannel;
import com.chua.payment.support.enums.ChannelStatus;
import com.chua.payment.support.exception.PaymentException;
import com.chua.payment.support.mapper.MerchantChannelMapper;
import com.chua.payment.support.mapper.MerchantMapper;
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
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PaymentProviderProperties paymentProviderProperties = new PaymentProviderProperties();
    private final PaymentCipherService paymentCipherService = mock(PaymentCipherService.class);
    private final PaymentChannelRegistry paymentChannelRegistry = mock(PaymentChannelRegistry.class);

    private MerchantChannelServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new MerchantChannelServiceImpl(
                channelMapper,
                merchantMapper,
                objectMapper,
                paymentProviderProperties,
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
}
