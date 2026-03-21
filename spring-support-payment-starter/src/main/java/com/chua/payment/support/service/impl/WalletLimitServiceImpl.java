package com.chua.payment.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.payment.support.entity.MerchantWalletLimit;
import com.chua.payment.support.entity.WalletAccount;
import com.chua.payment.support.entity.WalletAccountLog;
import com.chua.payment.support.exception.PaymentException;
import com.chua.payment.support.mapper.MerchantWalletLimitMapper;
import com.chua.payment.support.mapper.WalletAccountLogMapper;
import com.chua.payment.support.mapper.WalletAccountMapper;
import com.chua.payment.support.service.WalletLimitService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * 钱包限额服务实现
 */
@Service
@RequiredArgsConstructor
public class WalletLimitServiceImpl implements WalletLimitService {

    private final MerchantWalletLimitMapper limitMapper;
    private final WalletAccountMapper accountMapper;
    private final WalletAccountLogMapper logMapper;

    @Override
    public MerchantWalletLimit getLimit(Long merchantId) {
        if (merchantId == null) {
            return null;
        }
        return limitMapper.selectOne(new LambdaQueryWrapper<MerchantWalletLimit>()
                .eq(MerchantWalletLimit::getMerchantId, merchantId)
                .last("limit 1"));
    }

    @Override
    public void saveOrUpdate(MerchantWalletLimit limit) {
        if (limit == null || limit.getMerchantId() == null) {
            throw new PaymentException("限额配置不能为空");
        }
        MerchantWalletLimit existing = getLimit(limit.getMerchantId());
        if (existing != null) {
            limit.setId(existing.getId());
            limitMapper.updateById(limit);
        } else {
            limitMapper.insert(limit);
        }
    }

    @Override
    public void validateRechargeLimit(Long merchantId, Long userId, BigDecimal amount) {
        MerchantWalletLimit limit = getLimit(merchantId);
        if (limit == null) {
            return;
        }

        if (limit.getSingleRechargeLimit() != null && amount.compareTo(limit.getSingleRechargeLimit()) > 0) {
            throw new PaymentException("充值金额超过单笔限额: " + limit.getSingleRechargeLimit());
        }

        if (limit.getDailyRechargeLimit() != null) {
            BigDecimal todayTotal = getTodayAmount(merchantId, userId, "RECHARGE");
            if (todayTotal.add(amount).compareTo(limit.getDailyRechargeLimit()) > 0) {
                throw new PaymentException("充值金额超过日累计限额: " + limit.getDailyRechargeLimit());
            }
        }
    }

    @Override
    public void validateWithdrawLimit(Long merchantId, Long userId, BigDecimal amount) {
        MerchantWalletLimit limit = getLimit(merchantId);
        if (limit == null) {
            return;
        }

        if (limit.getSingleWithdrawLimit() != null && amount.compareTo(limit.getSingleWithdrawLimit()) > 0) {
            throw new PaymentException("提现金额超过单笔限额: " + limit.getSingleWithdrawLimit());
        }

        if (limit.getDailyWithdrawLimit() != null) {
            BigDecimal todayTotal = getTodayAmount(merchantId, userId, "WITHDRAW");
            if (todayTotal.add(amount).compareTo(limit.getDailyWithdrawLimit()) > 0) {
                throw new PaymentException("提现金额超过日累计限额: " + limit.getDailyWithdrawLimit());
            }
        }
    }

    @Override
    public void validateTransferLimit(Long merchantId, Long userId, BigDecimal amount) {
        MerchantWalletLimit limit = getLimit(merchantId);
        if (limit == null) {
            return;
        }

        if (limit.getSingleTransferLimit() != null && amount.compareTo(limit.getSingleTransferLimit()) > 0) {
            throw new PaymentException("转账金额超过单笔限额: " + limit.getSingleTransferLimit());
        }

        if (limit.getDailyTransferLimit() != null) {
            BigDecimal todayTotal = getTodayAmount(merchantId, userId, "TRANSFER_OUT");
            if (todayTotal.add(amount).compareTo(limit.getDailyTransferLimit()) > 0) {
                throw new PaymentException("转账金额超过日累计限额: " + limit.getDailyTransferLimit());
            }
        }
    }

    @Override
    public void validateBalanceLimit(Long merchantId, Long userId, BigDecimal currentBalance, BigDecimal addAmount) {
        MerchantWalletLimit limit = getLimit(merchantId);
        if (limit == null || limit.getBalanceLimit() == null) {
            return;
        }

        BigDecimal afterBalance = currentBalance.add(addAmount);
        if (afterBalance.compareTo(limit.getBalanceLimit()) > 0) {
            throw new PaymentException("余额将超过上限: " + limit.getBalanceLimit());
        }
    }

    private BigDecimal getTodayAmount(Long merchantId, Long userId, String bizType) {
        LocalDateTime startOfDay = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
        LocalDateTime endOfDay = LocalDateTime.of(LocalDate.now(), LocalTime.MAX);

        List<WalletAccountLog> logs = logMapper.selectList(new LambdaQueryWrapper<WalletAccountLog>()
                .eq(WalletAccountLog::getMerchantId, merchantId)
                .eq(WalletAccountLog::getUserId, userId)
                .eq(WalletAccountLog::getBizType, bizType)
                .between(WalletAccountLog::getCreatedAt, startOfDay, endOfDay));

        return logs.stream()
                .map(WalletAccountLog::getChangeAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
