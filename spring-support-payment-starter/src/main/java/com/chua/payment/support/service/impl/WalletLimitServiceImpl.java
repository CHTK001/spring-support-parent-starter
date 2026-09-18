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
* 钱包限额服务实现，校验充值/转账/提现等操作是否超出商户额度限制。
*
* @author CH
* @since 4.0.0.50
 */
@Service
@RequiredArgsConstructor
public class WalletLimitServiceImpl implements WalletLimitService {

    /** 限制映射器 */
    private final MerchantWalletLimitMapper limitMapper;
    /** Accountmapper */
    private final WalletAccountMapper accountMapper;
    /** 日志映射器 */
    private final WalletAccountLogMapper logMapper;

    @Override
    /** 获取限制 */
    public MerchantWalletLimit getLimit(Long merchantId) {
        if (merchantId == null) {
            return null;
        }
        return limitMapper.selectOne(new LambdaQueryWrapper<MerchantWalletLimit>()
                .eq(MerchantWalletLimit::getMerchantId, merchantId)
                .last("limit 1"));
    }

    @Override
    /** 保存或更新 */
    public void saveOrUpdate(MerchantWalletLimit limit) {
        if (limit == null || limit.getMerchantId() == null) {
            /**
    * 薪酬异常。
    * @param "限额配置不能为空" "限额配置不能为空"
    * @return 薪酬异常的结果
    */
            throw new PaymentException("限额配置不能为空");
        }
        MerchantWalletLimit existing = getLimit(limit.getMerchantId());
        if (existing != null) {
            existing.setSingleRechargeLimit(limit.getSingleRechargeLimit());
            existing.setDailyRechargeLimit(limit.getDailyRechargeLimit());
            existing.setSingleWithdrawLimit(limit.getSingleWithdrawLimit());
            existing.setDailyWithdrawLimit(limit.getDailyWithdrawLimit());
            existing.setSingleTransferLimit(limit.getSingleTransferLimit());
            existing.setDailyTransferLimit(limit.getDailyTransferLimit());
            existing.setBalanceLimit(limit.getBalanceLimit());
            limitMapper.updateById(existing);
        } else {
            limitMapper.insert(limit);
        }
    }

    @Override
    /** 校验recharge限制 */
    public void validateRechargeLimit(Long merchantId, Long userId, BigDecimal amount) {
        MerchantWalletLimit limit = getLimit(merchantId);
        if (limit == null) {
            return;
        }

        if (limit.getSingleRechargeLimit() != null && amount.compareTo(limit.getSingleRechargeLimit()) > 0) {
            /**
    * 薪酬异常。
    * @param limit.getSingleRechargeLimit( 限制.获取单个rechargelimit(
    * @return 薪酬异常的结果
    */
            throw new PaymentException("充值金额超过单笔限额: " + limit.getSingleRechargeLimit());
        }

        if (limit.getDailyRechargeLimit() != null) {
            BigDecimal todayTotal = getTodayAmount(merchantId, userId, "RECHARGE");
            if (todayTotal.add(amount).compareTo(limit.getDailyRechargeLimit()) > 0) {
                /**
                * 薪酬异常。
                * @param limit.getDailyRechargeLimit( 限制.获取dailyrecharge限制(
                * @return 薪酬异常的结果
                */
                throw new PaymentException("充值金额超过日累计限额: " + limit.getDailyRechargeLimit());
            }
        }
    }

    @Override
    /** 校验withdraw限制 */
    public void validateWithdrawLimit(Long merchantId, Long userId, BigDecimal amount) {
        MerchantWalletLimit limit = getLimit(merchantId);
        if (limit == null) {
            return;
        }

        if (limit.getSingleWithdrawLimit() != null && amount.compareTo(limit.getSingleWithdrawLimit()) > 0) {
            /**
    * 薪酬异常。
    * @param limit.getSingleWithdrawLimit( 限制.获取单个withdrawlimit(
    * @return 薪酬异常的结果
    */
            throw new PaymentException("提现金额超过单笔限额: " + limit.getSingleWithdrawLimit());
        }

        if (limit.getDailyWithdrawLimit() != null) {
            BigDecimal todayTotal = getTodayAmount(merchantId, userId, "WITHDRAW");
            if (todayTotal.add(amount).compareTo(limit.getDailyWithdrawLimit()) > 0) {
                /**
                * 薪酬异常。
                * @param limit.getDailyWithdrawLimit( 限制.获取dailywithdraw限制(
                * @return 薪酬异常的结果
                */
                throw new PaymentException("提现金额超过日累计限额: " + limit.getDailyWithdrawLimit());
            }
        }
    }

    @Override
    /** 校验调动限制 */
    public void validateTransferLimit(Long merchantId, Long userId, BigDecimal amount) {
        MerchantWalletLimit limit = getLimit(merchantId);
        if (limit == null) {
            return;
        }

        if (limit.getSingleTransferLimit() != null && amount.compareTo(limit.getSingleTransferLimit()) > 0) {
            /**
    * 薪酬异常。
    * @param limit.getSingleTransferLimit( 限制.获取单个调动限制(
    * @return 薪酬异常的结果
    */
            throw new PaymentException("转账金额超过单笔限额: " + limit.getSingleTransferLimit());
        }

        if (limit.getDailyTransferLimit() != null) {
            BigDecimal todayTotal = getTodayAmount(merchantId, userId, "TRANSFER_OUT");
            if (todayTotal.add(amount).compareTo(limit.getDailyTransferLimit()) > 0) {
                /**
                * 薪酬异常。
                * @param limit.getDailyTransferLimit( 限制.getdaily调动限制(
                * @return 薪酬异常的结果
                */
                throw new PaymentException("转账金额超过日累计限额: " + limit.getDailyTransferLimit());
            }
        }
    }

    @Override
    /** 校验balance限制 */
    public void validateBalanceLimit(Long merchantId, Long userId, BigDecimal currentBalance, BigDecimal addAmount) {
        MerchantWalletLimit limit = getLimit(merchantId);
        if (limit == null || limit.getBalanceLimit() == null) {
            return;
        }

        BigDecimal afterBalance = currentBalance.add(addAmount);
        if (afterBalance.compareTo(limit.getBalanceLimit()) > 0) {
            /**
    * 薪酬异常。
    * @param limit.getBalanceLimit( 限制.获取balance限制(
    * @return 薪酬异常的结果
    */
            throw new PaymentException("余额将超过上限: " + limit.getBalanceLimit());
        }
    }

    /**
    * 获取todayamount
    *
    * @param merchantId merchantid
    * @param userId 用户标识
    * @param bizType biz类型
    * @return 获取todayamount的结果
    */
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

