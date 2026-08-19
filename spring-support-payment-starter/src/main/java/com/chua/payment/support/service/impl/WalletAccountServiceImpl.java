package com.chua.payment.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.payment.support.dto.WalletRechargeDTO;
import com.chua.payment.support.dto.WalletTransferDTO;
import com.chua.payment.support.dto.WalletWithdrawDTO;
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
import com.chua.starter.strategy.template.IdempotentTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 钱包账户服务实现
 * @author CH
 */
@Service
@RequiredArgsConstructor
public class WalletAccountServiceImpl implements WalletAccountService {

    /** Status_enabled */
    private static final int STATUS_ENABLED = 1;

    /** Walletaccountmapper */
    private final WalletAccountMapper walletAccountMapper;
    /** Walletaccount日志mapper */
    private final WalletAccountLogMapper walletAccountLogMapper;
    /** Merchantmapper */
    private final MerchantMapper merchantMapper;
    /** Wallet限制服务 */
    private final WalletLimitService walletLimitService;
    /** Idempotent模板 */
    private final IdempotentTemplate idempotentTemplate;

    @Override
    /** 获取Account */
    public WalletAccountVO getAccount(Long merchantId, Long userId) {
        WalletAccount account = findAccount(merchantId, userId);
        if (account == null) {
            throw new PaymentException("钱包账户不存在");
        }
        return toVO(account);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /** Recharge */
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
    /** Pay */
    public WalletAccountVO pay(Long merchantId, Long userId, BigDecimal amount, String orderNo, String operator, String remark) {
        WalletAccount account = applyChange("PAY", orderNo, merchantId, userId, amount, false, false, operator, remark);
        return toVO(account);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /** Refund */
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
    /**
     * Transfer
     * @param merchantId merchantId
     * @param fromUserId fromUserId
     * @param toUserId toUserId
     * @param amount amount
     * @param transferNo transferNo
     * @param operator operator
     * @param remark remark
     */
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
    /** Withdraw */
    public void withdraw(Long merchantId, Long userId, BigDecimal amount, String withdrawNo, String operator, String remark) {
        walletLimitService.validateWithdrawLimit(merchantId, userId, amount);
        applyChange("WITHDRAW", withdrawNo, merchantId, userId, amount, false, false, operator, remark);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /** FreezeForWithdraw */
    public WalletAccountVO freezeForWithdraw(WalletWithdrawDTO dto) {
        if (dto == null) {
            throw new PaymentException("提现参数不能为空");
        }
        if (dto.getAmount() == null || dto.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new PaymentException("提现金额必须大于0");
        }

        String bizNo = StringUtils.hasText(dto.getWithdrawNo()) ? dto.getWithdrawNo() : generateBizNo("WDW");
        String remark = String.format("提现冻结到%s(%s)%s",
                StringUtils.hasText(dto.getBankName()) ? dto.getBankName() : "银行",
                StringUtils.hasText(dto.getBankAccount()) ? dto.getBankAccount() : "",
                StringUtils.hasText(dto.getRemark()) ? ": " + dto.getRemark() : "");

        requireWalletEnabledMerchant(dto.getMerchantId());

        String idempotentKey = "payment:idempotent:wallet:WITHDRAW_FREEZE:" + dto.getMerchantId() + ":" + dto.getUserId() + ":" + bizNo;
        if (!idempotentTemplate.tryAcquire(idempotentKey, 10, TimeUnit.MINUTES)) {
            throw new PaymentException("请求正在处理中，请勿重复提交");
        }

        try {
            for (int i = 0; i < 5; i++) {
                WalletAccount account = requireAccount(dto.getMerchantId(), dto.getUserId(), false);
                ensureAccountEnabled(account);

                BigDecimal availBefore = scale(account.getAvailableBalance());
                BigDecimal frozenBefore = scale(account.getFrozenBalance());
                BigDecimal delta = scale(dto.getAmount());

                if (availBefore.compareTo(delta) < 0) {
                    throw new PaymentException("钱包余额不足");
                }

                BigDecimal availAfter = availBefore.subtract(delta);
                BigDecimal frozenAfter = frozenBefore.add(delta);

                int updated = walletAccountMapper.update(null, new LambdaUpdateWrapper<WalletAccount>()
                        .eq(WalletAccount::getId, account.getId())
                        .eq(WalletAccount::getAvailableBalance, availBefore)
                        .eq(WalletAccount::getFrozenBalance, frozenBefore)
                        .set(WalletAccount::getAvailableBalance, availAfter)
                        .set(WalletAccount::getFrozenBalance, frozenAfter));
                if (updated <= 0) {
                    continue;
                }

                WalletAccountLog log = new WalletAccountLog();
                log.setMerchantId(dto.getMerchantId());
                log.setUserId(dto.getUserId());
                log.setBizType("WITHDRAW");
                log.setBizNo(bizNo);
                log.setChangeType("OUT");
                log.setChangeAmount(delta);
                log.setBalanceBefore(availBefore);
                log.setBalanceAfter(availAfter);
                log.setOperator(StringUtils.hasText(dto.getOperator()) ? dto.getOperator() : "system");
                log.setRemark(remark);
                walletAccountLogMapper.insert(log);

                WalletAccount refreshed = walletAccountMapper.selectById(account.getId());
                return toVO(refreshed);
            }

            throw new PaymentException("钱包余额更新失败，请稍后重试");
        } finally {
            idempotentTemplate.release(idempotentKey);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /** ConfirmWithdraw */
    public void confirmWithdraw(Long merchantId, Long userId, String bizNo, BigDecimal amount) {
        if (merchantId == null || userId == null || !StringUtils.hasText(bizNo) || amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new PaymentException("参数不完整");
        }

        // 幂等性检查：已确认的提现不再重复处理
        Long existingLog = walletAccountLogMapper.selectCount(new LambdaQueryWrapper<WalletAccountLog>()
                .eq(WalletAccountLog::getMerchantId, merchantId)
                .eq(WalletAccountLog::getUserId, userId)
                .eq(WalletAccountLog::getBizType, "WITHDRAW_CONFIRM")
                .eq(WalletAccountLog::getBizNo, bizNo));
        if (existingLog != null && existingLog > 0) {
            throw new PaymentException("提现已确认到账，请勿重复操作");
        }

        for (int i = 0; i < 5; i++) {
            WalletAccount account = requireAccount(merchantId, userId, false);
            BigDecimal frozenBefore = scale(account.getFrozenBalance());
            BigDecimal delta = scale(amount);

            if (frozenBefore.compareTo(delta) < 0) {
                throw new PaymentException("冻结余额不足");
            }

            BigDecimal frozenAfter = frozenBefore.subtract(delta);

            int updated = walletAccountMapper.update(null, new LambdaUpdateWrapper<WalletAccount>()
                    .eq(WalletAccount::getId, account.getId())
                    .eq(WalletAccount::getFrozenBalance, frozenBefore)
                    .set(WalletAccount::getFrozenBalance, frozenAfter));
            if (updated <= 0) {
                continue;
            }

            WalletAccountLog log = new WalletAccountLog();
            log.setMerchantId(merchantId);
            log.setUserId(userId);
            log.setBizType("WITHDRAW_CONFIRM");
            log.setBizNo(bizNo);
            log.setChangeType("OUT");
            log.setChangeAmount(delta);
            log.setBalanceBefore(frozenBefore);
            log.setBalanceAfter(frozenAfter);
            log.setOperator("system");
            log.setRemark("提现到账确认");
            walletAccountLogMapper.insert(log);
            return;
        }

        throw new PaymentException("钱包余额更新失败，请稍后重试");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /** CancelWithdraw */
    public void cancelWithdraw(Long merchantId, Long userId, String bizNo, BigDecimal amount, String remark) {
        if (merchantId == null || userId == null || !StringUtils.hasText(bizNo) || amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new PaymentException("参数不完整");
        }

        // 幂等性检查：已取消的提现不再重复处理
        Long existingLog = walletAccountLogMapper.selectCount(new LambdaQueryWrapper<WalletAccountLog>()
                .eq(WalletAccountLog::getMerchantId, merchantId)
                .eq(WalletAccountLog::getUserId, userId)
                .eq(WalletAccountLog::getBizType, "WITHDRAW_CANCEL")
                .eq(WalletAccountLog::getBizNo, bizNo));
        if (existingLog != null && existingLog > 0) {
            throw new PaymentException("提现已取消，请勿重复操作");
        }

        for (int i = 0; i < 5; i++) {
            WalletAccount account = requireAccount(merchantId, userId, false);
            BigDecimal availBefore = scale(account.getAvailableBalance());
            BigDecimal frozenBefore = scale(account.getFrozenBalance());
            BigDecimal delta = scale(amount);

            if (frozenBefore.compareTo(delta) < 0) {
                throw new PaymentException("冻结余额不足");
            }

            BigDecimal availAfter = availBefore.add(delta);
            BigDecimal frozenAfter = frozenBefore.subtract(delta);

            int updated = walletAccountMapper.update(null, new LambdaUpdateWrapper<WalletAccount>()
                    .eq(WalletAccount::getId, account.getId())
                    .eq(WalletAccount::getAvailableBalance, availBefore)
                    .eq(WalletAccount::getFrozenBalance, frozenBefore)
                    .set(WalletAccount::getAvailableBalance, availAfter)
                    .set(WalletAccount::getFrozenBalance, frozenAfter));
            if (updated <= 0) {
                continue;
            }

            WalletAccountLog log = new WalletAccountLog();
            log.setMerchantId(merchantId);
            log.setUserId(userId);
            log.setBizType("WITHDRAW_CANCEL");
            log.setBizNo(bizNo);
            log.setChangeType("IN");
            log.setChangeAmount(delta);
            log.setBalanceBefore(availBefore);
            log.setBalanceAfter(availAfter);
            log.setOperator("system");
            log.setRemark(StringUtils.hasText(remark) ? remark : "取消提现解冻");
            walletAccountLogMapper.insert(log);
            return;
        }

        throw new PaymentException("钱包余额更新失败，请稍后重试");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /** EnsureAccount */
    public void ensureAccount(Long merchantId, Long userId) {
        requireAccount(merchantId, userId, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /** Transfer */
    public WalletAccountVO transfer(WalletTransferDTO dto) {
        if (dto == null) {
            throw new PaymentException("转账参数不能为空");
        }
        if (dto.getFromUserId().equals(dto.getToUserId())) {
            throw new PaymentException("不能转账给自己");
        }
        String bizNo = StringUtils.hasText(dto.getTransferNo()) ? dto.getTransferNo() : generateBizNo("TRF");

        applyChange("TRANSFER_OUT", bizNo, dto.getMerchantId(), dto.getFromUserId(), dto.getAmount(), false, false, dto.getOperator(), "转账给用户" + dto.getToUserId() + (StringUtils.hasText(dto.getRemark()) ? ": " + dto.getRemark() : ""));
        WalletAccount toAccount = applyChange("TRANSFER_IN", bizNo, dto.getMerchantId(), dto.getToUserId(), dto.getAmount(), true, true, dto.getOperator(), "来自用户" + dto.getFromUserId() + "的转账" + (StringUtils.hasText(dto.getRemark()) ? ": " + dto.getRemark() : ""));

        return toVO(toAccount);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /** Withdraw */
    public WalletAccountVO withdraw(WalletWithdrawDTO dto) {
        if (dto == null) {
            throw new PaymentException("提现参数不能为空");
        }
        if (!StringUtils.hasText(dto.getBankAccount())) {
            throw new PaymentException("银行账号不能为空");
        }
        String bizNo = StringUtils.hasText(dto.getWithdrawNo()) ? dto.getWithdrawNo() : generateBizNo("WDW");
        String remark = String.format("提现到%s(%s)%s",
                StringUtils.hasText(dto.getBankName()) ? dto.getBankName() : "银行",
                dto.getBankAccount(),
                StringUtils.hasText(dto.getRemark()) ? ": " + dto.getRemark() : "");

        WalletAccount account = applyChange("WITHDRAW", bizNo, dto.getMerchantId(), dto.getUserId(), dto.getAmount(), false, false, dto.getOperator(), remark);
        return toVO(account);
    }

    @Override
    /** PageAccounts */
    public Page<WalletAccount> pageAccounts(int pageNum, int pageSize, Long merchantId) {
        LambdaQueryWrapper<WalletAccount> wrapper = new LambdaQueryWrapper<WalletAccount>()
                .orderByDesc(WalletAccount::getCreatedAt);
        if (merchantId != null) {
            wrapper.eq(WalletAccount::getMerchantId, merchantId);
        }
        return walletAccountMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /** Freeze */
    public void freeze(Long id) {
        WalletAccount account = walletAccountMapper.selectById(id);
        if (account == null) {
            throw new PaymentException("钱包账户不存在");
        }
        if (!Integer.valueOf(STATUS_ENABLED).equals(account.getStatus())) {
            throw new PaymentException("钱包账户已冻结");
        }
        account.setStatus(0);
        walletAccountMapper.updateById(account);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /** Unfreeze */
    public void unfreeze(Long id) {
        WalletAccount account = walletAccountMapper.selectById(id);
        if (account == null) {
            throw new PaymentException("钱包账户不存在");
        }
        if (Integer.valueOf(STATUS_ENABLED).equals(account.getStatus())) {
            throw new PaymentException("钱包账户已启用");
        }
        account.setStatus(STATUS_ENABLED);
        walletAccountMapper.updateById(account);
    }

    @Override
    /** 获取记录日志 */
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
    /** PageLogs */
    public Page<WalletAccountLog> pageLogs(int pageNum, int pageSize, Long merchantId, Long userId, String bizType, String bizNo, LocalDateTime startTime, LocalDateTime endTime) {
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
        if (startTime != null) {
            wrapper.ge(WalletAccountLog::getCreatedAt, startTime);
        }
        if (endTime != null) {
            wrapper.le(WalletAccountLog::getCreatedAt, endTime);
        }
        return walletAccountLogMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    /**
     * 应用Change
     * @param bizType bizType
     * @param bizNo bizNo
     * @param merchantId merchantId
     * @param userId userId
     * @param amount amount
     * @param increase increase
     * @param createIfAbsent createIfAbsent
     * @param operator operator
     * @param remark remark
     */
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

        // Redis 分布式幂等检查
        String idempotentKey = "payment:idempotent:wallet:" + bizType + ":" + merchantId + ":" + userId + ":" + bizNo;
        if (!idempotentTemplate.tryAcquire(idempotentKey, 10, TimeUnit.MINUTES)) {
            WalletAccount account = requireAccount(merchantId, userId, createIfAbsent);
            return account;
        }

        // DB 级幂等兜底（防止 Redis 不可用或键过期导致重复处理）
        WalletAccountLog existing = getLog(bizType, merchantId, userId, bizNo);
        if (existing != null) {
            idempotentTemplate.release(idempotentKey);
            WalletAccount account = requireAccount(merchantId, userId, createIfAbsent);
            return account;
        }

        try {
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

                idempotentTemplate.complete(idempotentKey, true);

                WalletAccount refreshed = walletAccountMapper.selectById(account.getId());
                return refreshed;
            }

            throw new PaymentException("钱包余额更新失败，请稍后重试");
        } catch (RuntimeException e) {
            idempotentTemplate.release(idempotentKey);
            throw e;
        }
    }

    /** RequireAccount */
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

    /** 查找Account */
    private WalletAccount findAccount(Long merchantId, Long userId) {
        if (merchantId == null || userId == null) {
            return null;
        }
        return walletAccountMapper.selectOne(new LambdaQueryWrapper<WalletAccount>()
                .eq(WalletAccount::getMerchantId, merchantId)
                .eq(WalletAccount::getUserId, userId)
                .last("limit 1"));
    }

    /** 校验Args */
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

    /** EnsureAccountEnabled */
    private void ensureAccountEnabled(WalletAccount account) {
        if (account == null || !Integer.valueOf(STATUS_ENABLED).equals(account.getStatus())) {
            throw new PaymentException("钱包账户未启用");
        }
    }

    /** RequireWalletEnabledMerchant */
    private void requireWalletEnabledMerchant(Long merchantId) {
        Merchant merchant = merchantMapper.selectById(merchantId);
        if (merchant == null) {
            throw new PaymentException("商户不存在");
        }
        if (!Integer.valueOf(MerchantStatus.ACTIVE.getCode()).equals(merchant.getStatus())) {
            throw new PaymentException("商户未激活");
        }
        if (!Boolean.TRUE.equals(merchant.getPaymentWalletEnabled())) {
            throw new PaymentException("商户未启用钱包能力");
        }
    }

    /** Scale */
    private BigDecimal scale(BigDecimal value) {
        return value == null ? zeroAmount() : value.setScale(2, RoundingMode.HALF_UP);
    }

    /** ZeroAmount */
    private BigDecimal zeroAmount() {
        return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    /** GenerateBizNo */
    private String generateBizNo(String prefix) {
        return prefix + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }

    /** ToVO */
    private WalletAccountVO toVO(WalletAccount account) {
        WalletAccountVO vo = new WalletAccountVO();
        BeanUtils.copyProperties(account, vo);
        return vo;
    }
}

