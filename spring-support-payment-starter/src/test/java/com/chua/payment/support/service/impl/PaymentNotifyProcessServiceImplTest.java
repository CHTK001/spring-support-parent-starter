package com.chua.payment.support.service.impl;

import com.chua.payment.support.entity.PaymentNotifyError;
import com.chua.payment.support.entity.PaymentNotifyLog;
import com.chua.payment.support.mapper.PaymentNotifyErrorMapper;
import com.chua.payment.support.mapper.PaymentNotifyLogMapper;
import com.chua.payment.support.service.PaymentNotifyService;
import com.chua.payment.support.service.WalletNotifyService;
import com.chua.payment.support.service.WechatPayScoreNotifyService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import org.mockito.ArgumentCaptor;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentNotifyProcessServiceImplTest {

    private final PaymentNotifyLogMapper notifyLogMapper = mock(PaymentNotifyLogMapper.class);
    private final PaymentNotifyErrorMapper notifyErrorMapper = mock(PaymentNotifyErrorMapper.class);
    private final PaymentNotifyService paymentNotifyService = mock(PaymentNotifyService.class);
    private final WechatPayScoreNotifyService wechatPayScoreNotifyService = mock(WechatPayScoreNotifyService.class);
    private final WalletNotifyService walletNotifyService = mock(WalletNotifyService.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);

    private final PaymentNotifyProcessServiceImpl service = new PaymentNotifyProcessServiceImpl(
            notifyLogMapper,
            notifyErrorMapper,
            paymentNotifyService,
            wechatPayScoreNotifyService,
            walletNotifyService,
            new ObjectMapper(),
            eventPublisher);

    @Test
    void shouldReplayWalletNotifyWithoutChannelId() {
        PaymentNotifyError error = new PaymentNotifyError();
        error.setId(1L);
        error.setNotifyLogId(9L);
        error.setStatus("PENDING");
        error.setRetryCount(0);
        error.setMaxRetryCount(5);

        PaymentNotifyLog log = new PaymentNotifyLog();
        log.setId(9L);
        log.setNotifyType("WALLET_TRANSFER");
        log.setChannelType("WALLET");
        log.setChannelSubType("TRANSFER");
        log.setOrderNo("TRF001");
        log.setRequestBody("{\"status\":\"success\",\"thirdPartyOrderNo\":\"TP001\"}");

        when(notifyErrorMapper.selectById(1L)).thenReturn(error);
        when(notifyLogMapper.selectById(9L)).thenReturn(log);

        service.retryFailedNotify(1L);

        verify(walletNotifyService).handleNotify("TRANSFER", "TRF001", "TP001", "success",
                "{\"status\":\"success\",\"thirdPartyOrderNo\":\"TP001\"}", null);

        ArgumentCaptor<PaymentNotifyError> errorCaptor = ArgumentCaptor.forClass(PaymentNotifyError.class);
        verify(notifyErrorMapper, atLeastOnce()).updateById(errorCaptor.capture());
        Assertions.assertTrue(errorCaptor.getAllValues().stream().anyMatch(item -> "RESOLVED".equals(item.getStatus())));

        ArgumentCaptor<PaymentNotifyLog> logCaptor = ArgumentCaptor.forClass(PaymentNotifyLog.class);
        verify(notifyLogMapper, atLeastOnce()).updateById(logCaptor.capture());
        Assertions.assertTrue(logCaptor.getAllValues().stream().anyMatch(item -> "SUCCESS".equals(item.getProcessStatus())));
    }
}
