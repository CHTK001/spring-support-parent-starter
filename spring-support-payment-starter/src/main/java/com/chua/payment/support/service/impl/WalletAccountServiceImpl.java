package com.chua.payment.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.payment.support.dto.WalletRechargeDTO;
import com.chua.payment.support.entity.Merchant;
import com.chua.payment.support.entity.WalletAccount;
import com.chua.payment.support.entity.WalletAccountLog;
import com.chua.payment.support.enums.MerchantStatus;
import com.chua.payment.support.exception.PaymentException;
import com.chua.payment.support.mapper.MerchantMapper;
import com.chua.payment.support.mapper.WalletAccountLogMapper;
import com.chua.payment.support.mapper.WalletAccountMapper;
import com.chua.payment.support.service.WalletAccountService;
import com.chua.payment.support.service.WalletLimitService;
import com.chua.payment.support.vo.WalletAccountVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * 钱包账户服务实现
 */
@Service
@RequiredArgsConstructor
public class WalletAccountServiceImpl implements WalletAccountService {

    private static final int STATUS_ENABLED = 1;

    private final WalletAccountMapper walletAccountMapper;
    private final WalletAccountLogMapper walletAccountLogMapper;
    private final MerchantMapper merchantMapper;
    private final WalletLimitService walletLimitService;

    @Override
    public WalletAccountVO getAccount(Long merchantId, Long userId) {
        WalletAccount account = findAccount(merchantId, userId);
        if (account == null) {
            throw new PaymentException("钱包账户不存在");
        }
        return toVO(account);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WalletAccountVO recharge(WalletRechargeDTO dto) {
        if (dto == null) {
            throw new PaymentException("充值参数不能为空");
        }
        walletLimitService.validateRechargeLimit(dto.getMerchantId(), dto.getUserId(), dto.getAmount());
        WalletAccount current = findAccount(dto.getMerchantId(), dto.getUserId());
        walletLimitService.validateBalanceLimit(
                dto.getMerchantId(),
                dto.getUserId(),
                current != null ? scale(current.getAvailableBalance()) : zeroAmount(),
                scale(dto.getAmount()));
        String bizNo = StringUtils.hasText(dto.getRechargeNo()) ? dto.getRechargeNo() : generateBizNo("RCH");
        WalletAccount account = applyChange("RECHARGE", bizNo, dto.getMerchantId(), dto.getUserId(), dto.getAmount(), true, true, dto.getOperator(), dto.getRemark());
        return toVO(account);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WalletAccountVO pay(Long merchantId, Long userId, BigDecimal amount, String orderNo, String operator, String remark) {
        WalletAccount account = applyChange("PAY", orderNo, merchantId, userId, amount, false, false, operator, remark);
        return toVO(account);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WalletAccountVO refund(Long merchantId, Long userId, BigDecimal amount, String refundNo, String operator, String remark) {
        WalletAccount current = findAccount(merchantId, userId);
        walletLimitService.validateBalanceLimit(
                merchantId,
                userId,
                current != null ? scale(current.getAvailableBalance()) : zeroAmount(),
                scale(amount));
        WalletAccount account = applyChange("REFUND", refundNo, merchantId, userId, amount, true, true, operator, remark);
        return toVO(account);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void transfer(Long merchantId,
                         Long fromUserId,
                         Long toUserId,
                         BigDecimal amount,
                         String transferNo,
                         String operator,
                         String remark) {
        if (fromUserId == null || toUserId == null) {
            throw new PaymentException("转账双方用户不能为空");
        }
        if (fromUserId.equals(toUserId)) {
            throw new PaymentException("转出用户和转入用户不能相同");
        }
        validateArgs(merchantId, fromUserId, "TRANSFER_OUT", transferNo, amount);
        requireWalletEnabledMerchant(merchantId);
        walletLimitService.validateTransferLimit(merchantId, fromUserId, amount);
        WalletAccount targetAccount = findAccount(merchantId, toUserId);
        walletLimitService.validateBalanceLimit(
                merchantId,
                toUserId,
                targetAccount != null ? scale(targetAccount.getAvailableBalance()) : zeroAmount(),
                scale(amount));
        applyChange("TRANSFER_OUT", transferNo, merchantId, fromUserId, amount, false, false, operator, remark);
        applyChange("TRANSFER_IN", transferNo, merchantId, toUserId, amount, true, true, operator, remark);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void withdraw(Long merchantId, Long userId, BigDecimal amount, String withdrawNo, String operator, String remark) {
        walletLimitService.validateWithdrawLimit(merchantId, userId, amount);
        applyChange("WITHDRAW", withdrawNo, merchantId, userId, amount, false, false, operator, remark);
    }

    @Override
    public WalletAccountLog getLog(String bizType, Long merchantId, Long userId, String bizNo) {
        LambdaQueryWrapper<WalletAccountLog> wrapper = new LambdaQueryWrapper<WalletAccountLog>()
                .eq(WalletAccountLog::getBizType, bizType)
                .eq(WalletAccountLog::getMerchantId, merchantId)
                .eq(WalletAccountLog::getBizNo, bizNo)
                .last("limit 1");
        if (userId != null) {
            wrapper.eq(WalletAccountLog::getUserId, userId);
        }
        return walletAccountLogMapper.selectOne(wrapper);
    }

    @Override
    public Page<WalletAccountLog> pageLogs(int pageNum, int pageSize, Long merchantId, Long userId, String bizType, String bizNo) {
        LambdaQueryWrapper<WalletAccountLog> wrapper = new LambdaQueryWrapper<WalletAccountLog>()
                .orderByDesc(WalletAccountLog::getCreatedAt);
        if (merchantId != null) {
            wrapper.eq(WalletAccountLog::getMerchantId, merchantId);
        }
        if (userId != null) {
            wrapper.eq(WalletAccountLog::getUserId, userId);
        }
        if (StringUtils.hasText(bizType)) {
            wrapper.eq(WalletAccountLog::getBizType, bizType);
        }
        if (StringUtils.hasText(bizNo)) {
            wrapper.eq(WalletAccountLog::getBizNo, bizNo);
        }
        return walletAccountLogMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    private WalletAccount applyChange(String bizType,
                                      String bizNo,
                                      Long merchantId,
                                      Long userId,
                                      BigDecimal amount,
                                      boolean increase,
                                      boolean createIfAbsent,
                                      String operator,
                                      String remark) {
        validateArgs(merchantId, userId, bizType, bizNo, amount);
        requireWalletEnabledMerchant(merchantId);
        WalletAccountLog existing = getLog(bizType, merchantId, userId, bizNo);
        if (existing != null) {
            WalletAccount account = requireAccount(merchantId, userId, createIfAbsent);
            return account;
        }

        for (int i = 0; i < 5; i++) {
            WalletAccount account = requireAccount(merchantId, userId, createIfAbsent);
            ensureAccountEnabled(account);
            BigDecimal before = scale(account.getAvailableBalance());
            BigDecimal delta = scale(amount);
            BigDecimal after = increase ? before.add(delta) : before.subtract(delta);
            if (!increase && before.compareTo(delta) < 0) {
                throw new PaymentException("钱包余额不足");
            }

            int updated = walletAccountMapper.update(null, new LambdaUpdateWrapper<WalletAccount>()
                    .eq(WalletAccount::getId, account.getId())
                    .eq(WalletAccount::getAvailableBalance, before)
                    .set(WalletAccount::getAvailableBalance, after));
            if (updated <= 0) {
                continue;
            }

            WalletAccountLog log = new WalletAccountLog();
            log.setMerchantId(merchantId);
            log.setUserId(userId);
            log.setBizType(bizType);
            log.setBizNo(bizNo);
            log.setChangeType(increase ? "IN" : "OUT");
            log.setChangeAmount(delta);
            log.setBalanceBefore(before);
            log.setBalanceAfter(after);
            log.setOperator(StringUtils.hasText(operator) ? operator : "system");
            log.setRemark(remark);
            walletAccountLogMapper.insert(log);

            WalletAccount refreshed = walletAccountMapper.selectById(account.getId());
            return refreshed;
        }

        throw new PaymentException("钱包余额更新失败，请稍后重试");
    }

    private WalletAccount requireAccount(Long merchantId, Long userId, boolean createIfAbsent) {
        WalletAccount account = findAccount(merchantId, userId);
        if (account != null) {
            return account;
        }
        if (!createIfAbsent) {
            throw new PaymentException("钱包账户不存在");
        }

        requireWalletEnabledMerchant(merchantId);
        WalletAccount created = new WalletAccount();
        created.setMerchantId(merchantId);
        created.setUserId(userId);
        created.setAvailableBalance(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        created.setFrozenBalance(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        created.setStatus(STATUS_ENABLED);
        walletAccountMapper.insert(created);
        return walletAccountMapper.selectById(created.getId());
    }

    private WalletAccount findAccount(Long merchantId, Long userId) {
        if (merchantId == null || userId == null) {
            return null;
        }
        return walletAccountMapper.selectOne(new LambdaQueryWrapper<WalletAccount>()
                .eq(WalletAccount::getMerchantId, merchantId)
                .eq(WalletAccount::getUserId, userId)
                .last("limit 1"));
    }

    private void validateArgs(Long merchantId, Long userId, String bizType, String bizNo, BigDecimal amount) {
        if (merchantId == null) {
            throw new PaymentException("商户ID不能为空");
        }
        if (userId == null) {
            throw new PaymentException("用户ID不能为空");
        }
        if (!StringUtils.hasText(bizType) || !StringUtils.hasText(bizNo)) {
            throw new PaymentException("钱包业务标识不能为空");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new PaymentException("钱包金额必须大于0");
        }
    }

    private void ensureAccountEnabled(WalletAccount account) {
        if (account == null || !Integer.valueOf(STATUS_ENABLED).equals(account.getStatus())) {
            throw new PaymentException("钱包账户未启用");
        }
    }

    private void requireWalletEnabledMerchant(Long merchantId) {
        Merchant merchant = merchantMapper.selectById(merchantId);
        if (merchant == null) {
            throw new PaymentException("商户不存在");
        }
        if (!Integer.valueOf(MerchantStatus.ACTIVE.getCode()).equals(merchant.getStatus())) {
            throw new PaymentException("商户未激活");
        }
        if (!Boolean.TRUE.equals(merchant.getWalletEnabled())) {
            throw new PaymentException("商户未启用钱包能力");
        }
    }

    private BigDecimal scale(BigDecimal value) {
        return value == null ? zeroAmount() : value.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal zeroAmount() {
        return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    private String generateBizNo(String prefix) {
        return prefix + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }

    private WalletAccountVO toVO(WalletAccount account) {
        WalletAccountVO vo = new WalletAccountVO();
        BeanUtils.copyProperties(account, vo);
        return vo;
    }
}
