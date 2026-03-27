package com.chua.payment.support.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.chua.payment.support.dto.WalletRechargeDTO;
import com.chua.payment.support.entity.Merchant;
import com.chua.payment.support.entity.WalletAccount;
import com.chua.payment.support.entity.WalletAccountLog;
import com.chua.payment.support.enums.MerchantStatus;
import com.chua.payment.support.exception.PaymentException;
import com.chua.payment.support.mapper.MerchantMapper;
import com.chua.payment.support.mapper.WalletAccountLogMapper;
import com.chua.payment.support.mapper.WalletAccountMapper;
import com.chua.payment.support.service.WalletLimitService;
import com.chua.payment.support.vo.WalletAccountVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.stubbing.Answer;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WalletAccountServiceImplTest {

    private final WalletAccountMapper walletAccountMapper = mock(WalletAccountMapper.class);
    private final WalletAccountLogMapper walletAccountLogMapper = mock(WalletAccountLogMapper.class);
    private final MerchantMapper merchantMapper = mock(MerchantMapper.class);
    private final WalletLimitService walletLimitService = mock(WalletLimitService.class);
    private final WalletAccountServiceImpl service = new WalletAccountServiceImpl(
            walletAccountMapper,
            walletAccountLogMapper,
            merchantMapper,
            walletLimitService);

    @BeforeAll
    static void initTableInfo() {
        if (TableInfoHelper.getTableInfo(WalletAccount.class) == null) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), WalletAccount.class);
        }
    }

    @Test
    void shouldReturnExistingAccountWhenRechargeAlreadyProcessed() {
        Merchant merchant = activeWalletMerchant();
        WalletAccount account = enabledAccount(1L, 2L, "100.00");
        when(merchantMapper.selectById(1L)).thenReturn(merchant);
        when(walletAccountMapper.selectOne(any())).thenReturn(account);
        when(walletAccountLogMapper.selectOne(any())).thenReturn(new WalletAccountLog());

        WalletRechargeDTO dto = new WalletRechargeDTO();
        dto.setMerchantId(1L);
        dto.setUserId(2L);
        dto.setAmount(new BigDecimal("10.00"));
        dto.setRechargeNo("RCH001");

        WalletAccountVO result = service.recharge(dto);

        assertEquals(new BigDecimal("100.00"), result.getAvailableBalance());
        verify(walletLimitService).validateRechargeLimit(1L, 2L, new BigDecimal("10.00"));
        verify(walletLimitService).validateBalanceLimit(1L, 2L, new BigDecimal("100.00"), new BigDecimal("10.00"));
        verify(walletAccountMapper, never()).update(any(), any());
        verify(walletAccountLogMapper, never()).insert(org.mockito.ArgumentMatchers.<com.chua.payment.support.entity.WalletAccountLog>any());
    }

    @Test
    void shouldSkipTransferWhenBothSidesAlreadyProcessed() {
        Merchant merchant = activeWalletMerchant();
        WalletAccount toAccount = enabledAccount(1L, 3L, "50.00");
        WalletAccount fromAccount = enabledAccount(1L, 2L, "100.00");
        when(merchantMapper.selectById(1L)).thenReturn(merchant);
        when(walletAccountMapper.selectOne(any())).thenReturn(toAccount, fromAccount, toAccount);
        when(walletAccountLogMapper.selectOne(any())).thenReturn(new WalletAccountLog(), new WalletAccountLog());

        service.transfer(1L, 2L, 3L, new BigDecimal("10.00"), "TRF001", "tester", "transfer");

        verify(walletLimitService).validateTransferLimit(1L, 2L, new BigDecimal("10.00"));
        verify(walletLimitService).validateBalanceLimit(1L, 3L, new BigDecimal("50.00"), new BigDecimal("10.00"));
        verify(walletAccountMapper, never()).update(any(), any());
        verify(walletAccountLogMapper, never()).insert(org.mockito.ArgumentMatchers.<com.chua.payment.support.entity.WalletAccountLog>any());
    }

    @Test
    void shouldSkipWithdrawWhenAlreadyProcessed() {
        Merchant merchant = activeWalletMerchant();
        WalletAccount account = enabledAccount(1L, 2L, "80.00");
        when(merchantMapper.selectById(1L)).thenReturn(merchant);
        when(walletAccountMapper.selectOne(any())).thenReturn(account);
        when(walletAccountLogMapper.selectOne(any())).thenReturn(new WalletAccountLog());

        service.withdraw(1L, 2L, new BigDecimal("8.00"), "WDW001", "tester", "withdraw");

        verify(walletLimitService).validateWithdrawLimit(1L, 2L, new BigDecimal("8.00"));
        verify(walletAccountMapper, never()).update(any(), any());
        verify(walletAccountLogMapper, never()).insert(org.mockito.ArgumentMatchers.<com.chua.payment.support.entity.WalletAccountLog>any());
    }

    @Test
    void shouldRejectRechargeWhenMerchantIsNotActive() {
        Merchant merchant = new Merchant();
        merchant.setId(1L);
        merchant.setStatus(MerchantStatus.PENDING.getCode());
        merchant.setWalletEnabled(true);
        when(merchantMapper.selectById(1L)).thenReturn(merchant);
        when(walletAccountMapper.selectOne(any())).thenReturn(null);
        when(walletAccountLogMapper.selectOne(any())).thenReturn(null);

        WalletRechargeDTO dto = new WalletRechargeDTO();
        dto.setMerchantId(1L);
        dto.setUserId(2L);
        dto.setAmount(new BigDecimal("10.00"));
        dto.setRechargeNo("RCH002");

        assertThrows(PaymentException.class, () -> service.recharge(dto));
        verify(walletAccountMapper, never()).update(any(), any());
        verify(walletAccountLogMapper, never()).insert(org.mockito.ArgumentMatchers.<com.chua.payment.support.entity.WalletAccountLog>any());
    }

    @Test
    void shouldCreateWalletAccountAndWriteLogWhenRechargeAppliedFirstTime() {
        Merchant merchant = activeWalletMerchant();
        AtomicLong idGenerator = new AtomicLong(100L);
        WalletAccount created = enabledAccount(1L, 2L, "0.00");
        created.setId(101L);
        WalletAccount refreshed = enabledAccount(1L, 2L, "10.00");
        refreshed.setId(101L);

        when(merchantMapper.selectById(1L)).thenReturn(merchant);
        when(walletAccountMapper.selectOne(any())).thenReturn(null).thenReturn(null);
        when(walletAccountLogMapper.selectOne(any())).thenReturn(null);
        when(walletAccountMapper.update(any(), any())).thenReturn(1);
        when(walletAccountMapper.selectById(101L)).thenReturn(created).thenReturn(refreshed);
        doAnswer((Answer<Integer>) invocation -> {
            WalletAccount account = invocation.getArgument(0);
            account.setId(idGenerator.incrementAndGet());
            return 1;
        }).when(walletAccountMapper).insert(any(WalletAccount.class));

        WalletRechargeDTO dto = new WalletRechargeDTO();
        dto.setMerchantId(1L);
        dto.setUserId(2L);
        dto.setAmount(new BigDecimal("10.00"));
        dto.setRechargeNo("RCH1001");
        dto.setOperator("tester");

        WalletAccountVO result = service.recharge(dto);

        assertEquals(new BigDecimal("10.00"), result.getAvailableBalance());
        ArgumentCaptor<WalletAccountLog> logCaptor = ArgumentCaptor.forClass(WalletAccountLog.class);
        verify(walletAccountLogMapper).insert(logCaptor.capture());
        WalletAccountLog log = logCaptor.getValue();
        assertEquals("RECHARGE", log.getBizType());
        assertEquals("RCH1001", log.getBizNo());
        assertEquals(new BigDecimal("10.00"), log.getChangeAmount());
        assertEquals(new BigDecimal("0.00"), log.getBalanceBefore());
        assertEquals(new BigDecimal("10.00"), log.getBalanceAfter());
    }

    @Test
    void shouldRejectPayWhenBalanceIsInsufficient() {
        Merchant merchant = activeWalletMerchant();
        WalletAccount account = enabledAccount(1L, 2L, "5.00");
        when(merchantMapper.selectById(1L)).thenReturn(merchant);
        when(walletAccountMapper.selectOne(any())).thenReturn(account);
        when(walletAccountLogMapper.selectOne(any())).thenReturn(null);

        PaymentException exception = assertThrows(PaymentException.class,
                () -> service.pay(1L, 2L, new BigDecimal("10.00"), "ORD1001", "tester", "pay"));

        assertEquals("钱包余额不足", exception.getMessage());
        verify(walletAccountMapper, never()).update(any(), any());
        verify(walletAccountLogMapper, never()).insert(org.mockito.ArgumentMatchers.<WalletAccountLog>any());
    }

    @Test
    void shouldTransferBetweenAccountsAndWriteDoubleLogs() {
        Merchant merchant = activeWalletMerchant();
        WalletAccount targetBefore = enabledAccount(1L, 3L, "50.00");
        targetBefore.setId(301L);
        WalletAccount fromBefore = enabledAccount(1L, 2L, "100.00");
        fromBefore.setId(201L);
        WalletAccount toBefore = enabledAccount(1L, 3L, "50.00");
        toBefore.setId(301L);
        WalletAccount fromAfter = enabledAccount(1L, 2L, "90.00");
        fromAfter.setId(201L);
        WalletAccount toAfter = enabledAccount(1L, 3L, "60.00");
        toAfter.setId(301L);

        when(merchantMapper.selectById(1L)).thenReturn(merchant);
        when(walletAccountMapper.selectOne(any())).thenReturn(targetBefore, fromBefore, toBefore);
        when(walletAccountLogMapper.selectOne(any())).thenReturn(null).thenReturn(null);
        when(walletAccountMapper.update(any(), any())).thenReturn(1, 1);
        when(walletAccountMapper.selectById(201L)).thenReturn(fromAfter);
        when(walletAccountMapper.selectById(301L)).thenReturn(toAfter);

        service.transfer(1L, 2L, 3L, new BigDecimal("10.00"), "TRF1001", "tester", "transfer");

        verify(walletAccountMapper, times(2)).update(any(), any());
        ArgumentCaptor<WalletAccountLog> logCaptor = ArgumentCaptor.forClass(WalletAccountLog.class);
        verify(walletAccountLogMapper, times(2)).insert(logCaptor.capture());
        List<WalletAccountLog> logs = logCaptor.getAllValues();
        assertEquals(2, logs.size());
        assertEquals(List.of("TRANSFER_OUT", "TRANSFER_IN"), logs.stream().map(WalletAccountLog::getBizType).toList());
        assertEquals(List.of("TRF1001", "TRF1001"), logs.stream().map(WalletAccountLog::getBizNo).toList());
        assertEquals(List.of(new BigDecimal("10.00"), new BigDecimal("10.00")),
                logs.stream().map(WalletAccountLog::getChangeAmount).toList());
    }

    private Merchant activeWalletMerchant() {
        Merchant merchant = new Merchant();
        merchant.setId(1L);
        merchant.setStatus(MerchantStatus.ACTIVE.getCode());
        merchant.setWalletEnabled(true);
        return merchant;
    }

    private WalletAccount enabledAccount(Long merchantId, Long userId, String balance) {
        WalletAccount account = new WalletAccount();
        account.setId(System.nanoTime());
        account.setMerchantId(merchantId);
        account.setUserId(userId);
        account.setAvailableBalance(new BigDecimal(balance));
        account.setStatus(1);
        return account;
    }
}
