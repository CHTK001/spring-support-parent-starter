package com.chua.payment.support.service.impl;

import com.chua.payment.support.entity.MerchantWalletLimit;
import com.chua.payment.support.entity.WalletAccountLog;
import com.chua.payment.support.exception.PaymentException;
import com.chua.payment.support.mapper.MerchantWalletLimitMapper;
import com.chua.payment.support.mapper.WalletAccountLogMapper;
import com.chua.payment.support.mapper.WalletAccountMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WalletLimitServiceImplTest {

    private final MerchantWalletLimitMapper limitMapper = mock(MerchantWalletLimitMapper.class);
    private final WalletAccountMapper accountMapper = mock(WalletAccountMapper.class);
    private final WalletAccountLogMapper logMapper = mock(WalletAccountLogMapper.class);
    private final WalletLimitServiceImpl service = new WalletLimitServiceImpl(limitMapper, accountMapper, logMapper);

    @Test
    void shouldRejectRechargeAboveSingleLimit() {
        MerchantWalletLimit limit = new MerchantWalletLimit();
        limit.setMerchantId(1L);
        limit.setSingleRechargeLimit(new BigDecimal("50.00"));
        when(limitMapper.selectOne(any())).thenReturn(limit);

        assertThrows(PaymentException.class,
                () -> service.validateRechargeLimit(1L, 2L, new BigDecimal("60.00")));
    }

    @Test
    void shouldRejectTransferAboveDailyLimit() {
        MerchantWalletLimit limit = new MerchantWalletLimit();
        limit.setMerchantId(1L);
        limit.setDailyTransferLimit(new BigDecimal("100.00"));
        when(limitMapper.selectOne(any())).thenReturn(limit);

        WalletAccountLog existing = new WalletAccountLog();
        existing.setChangeAmount(new BigDecimal("60.00"));
        when(logMapper.selectList(any())).thenReturn(List.of(existing));

        assertThrows(PaymentException.class,
                () -> service.validateTransferLimit(1L, 2L, new BigDecimal("50.00")));
    }

    @Test
    void shouldRejectBalanceOverflow() {
        MerchantWalletLimit limit = new MerchantWalletLimit();
        limit.setMerchantId(1L);
        limit.setBalanceLimit(new BigDecimal("100.00"));
        when(limitMapper.selectOne(any())).thenReturn(limit);

        assertThrows(PaymentException.class,
                () -> service.validateBalanceLimit(1L, 2L, new BigDecimal("80.00"), new BigDecimal("30.00")));
    }
}
