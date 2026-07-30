package com.chua.payment.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.payment.support.entity.WalletWithdrawApproval;
import com.chua.payment.support.exception.PaymentException;
import com.chua.payment.support.mapper.WalletWithdrawApprovalMapper;
import com.chua.payment.support.service.WalletWithdrawApprovalService;
import com.chua.starter.strategy.template.IdempotentTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * 钱包提现审批服务实现
 */
@Service
@RequiredArgsConstructor
public class WalletWithdrawApprovalServiceImpl implements WalletWithdrawApprovalService {

    private final WalletWithdrawApprovalMapper approvalMapper;
    private final IdempotentTemplate idempotentTemplate;

    private static final String LOCK_PREFIX = "payment:lock:withdraw-approval:";

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WalletWithdrawApproval createApproval(Long merchantId, Long userId, BigDecimal amount,
                                                  String bankAccount, String bankName, String accountName,
                                                  String withdrawNo, String remark) {
        WalletWithdrawApproval approval = new WalletWithdrawApproval();
        approval.setMerchantId(merchantId);
        approval.setUserId(userId);
        approval.setAmount(amount);
        approval.setBankAccount(bankAccount);
        approval.setBankName(bankName);
        approval.setAccountName(accountName);
        approval.setWithdrawNo(withdrawNo);
        approval.setStatus("PENDING");
        approval.setRemark(remark);
        approvalMapper.insert(approval);
        return approvalMapper.selectById(approval.getId());
    }

    @Override
    public Page<WalletWithdrawApproval> pageApprovals(int pageNum, int pageSize, String status) {
        LambdaQueryWrapper<WalletWithdrawApproval> wrapper = new LambdaQueryWrapper<WalletWithdrawApproval>()
                .orderByDesc(WalletWithdrawApproval::getCreatedAt);
        if (StringUtils.hasText(status)) {
            wrapper.eq(WalletWithdrawApproval::getStatus, status);
        }
        return approvalMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    @Override
    public WalletWithdrawApproval getById(Long id) {
        WalletWithdrawApproval approval = approvalMapper.selectById(id);
        if (approval == null) {
            throw new PaymentException("提现审批记录不存在");
        }
        return approval;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long id, String operator) {
        String lockKey = LOCK_PREFIX + id;
        if (!idempotentTemplate.tryAcquire(lockKey, 30, TimeUnit.SECONDS)) {
            throw new PaymentException("操作过于频繁，请稍后重试");
        }
        try {
            WalletWithdrawApproval approval = getById(id);
            if (!"PENDING".equals(approval.getStatus())) {
                throw new PaymentException("仅待审批的记录可以审批通过");
            }
            approval.setStatus("APPROVED");
            approval.setOperator(operator);
            approval.setApproveTime(LocalDateTime.now());
            approvalMapper.updateById(approval);
        } finally {
            idempotentTemplate.release(lockKey);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long id, String operator, String reason) {
        String lockKey = LOCK_PREFIX + id;
        if (!idempotentTemplate.tryAcquire(lockKey, 30, TimeUnit.SECONDS)) {
            throw new PaymentException("操作过于频繁，请稍后重试");
        }
        try {
            WalletWithdrawApproval approval = getById(id);
            if (!"PENDING".equals(approval.getStatus())) {
                throw new PaymentException("仅待审批的记录可以拒绝");
            }
            approval.setStatus("REJECTED");
            approval.setOperator(operator);
            approval.setRemark(reason);
            approval.setApproveTime(LocalDateTime.now());
            approvalMapper.updateById(approval);
        } finally {
            idempotentTemplate.release(lockKey);
        }
    }
}
