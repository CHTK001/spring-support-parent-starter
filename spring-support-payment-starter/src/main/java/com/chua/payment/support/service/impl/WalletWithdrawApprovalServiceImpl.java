package com.chua.payment.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.payment.support.dto.CreateApprovalDTO;
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
* 钱包提现审批服务实现，处理商户钱包提现申请的创建、审批与状态流转。
*
* @author CH
* @since 4.0.0.50
 */
@Service
@RequiredArgsConstructor
public class WalletWithdrawApprovalServiceImpl implements WalletWithdrawApprovalService {

    /** Approvalmapper */
    private final WalletWithdrawApprovalMapper approvalMapper;
    /** 幂等模板 */
    private final IdempotentTemplate idempotentTemplate;

    /** 锁_前缀 */
    private static final String LOCK_PREFIX = "payment:lock:withdraw-approval:";

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
    * 创建Approval
    * @param merchantId merchantid
    * @param userId 用户标识
    * @param amount amount
    * @param bankAccount bank账户
    * @param bankName bank名称
    * @param accountName 账户名称
    * @param withdrawNo withdrawno
    * @param remark 评论
    * @param dto dto
    * @return 创建approval的结果
     */
    public WalletWithdrawApproval createApproval(CreateApprovalDTO dto) {
        WalletWithdrawApproval approval = new WalletWithdrawApproval();
        approval.setMerchantId(dto.merchantId());
        approval.setUserId(dto.userId());
        approval.setAmount(dto.amount());
        approval.setBankAccount(dto.bankAccount());
        approval.setBankName(dto.bankName());
        approval.setAccountName(dto.accountName());
        approval.setWithdrawNo(dto.withdrawNo());
        approval.setStatus("PENDING");
        approval.setRemark(dto.remark());
        approvalMapper.insert(approval);
        return approvalMapper.selectById(approval.getId());
    }

    @Override
    /** pageapprovals */
    public Page<WalletWithdrawApproval> pageApprovals(int pageNum, int pageSize, String status) {
        LambdaQueryWrapper<WalletWithdrawApproval> wrapper = new LambdaQueryWrapper<WalletWithdrawApproval>()
                .orderByDesc(WalletWithdrawApproval::getCreatedAt);
        if (StringUtils.hasText(status)) {
            wrapper.eq(WalletWithdrawApproval::getStatus, status);
        }
        return approvalMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    @Override
    /** 获取byid */
    public WalletWithdrawApproval getById(Long id) {
        WalletWithdrawApproval approval = approvalMapper.selectById(id);
        if (approval == null) {
            /**
            * 薪酬异常。
            * @param "提现审批记录不存在" "提现审批记录不存在"
            * @return 薪酬异常的结果
             */
            throw new PaymentException("提现审批记录不存在");
        }
        return approval;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
    * Approve
    *
    * @param id 标识
    * @param operator 操作符
     */
    public void approve(Long id, String operator) {
        String lockKey = LOCK_PREFIX + id;
        if (!idempotentTemplate.tryAcquire(lockKey, 30, TimeUnit.SECONDS)) {
            /**
            * 薪酬异常。
            * @param "操作过于频繁，请稍后重试" "操作过于频繁，请稍后重试"
            * @return 薪酬异常的结果
             */
            throw new PaymentException("操作过于频繁，请稍后重试");
        }
        try {
            WalletWithdrawApproval approval = getById(id);
            if (!"PENDING".equals(approval.getStatus())) {
                /**
                * 薪酬异常。
                * @param "仅待审批的记录可以审批通过" "仅待审批的记录可以审批通过"
                * @return 薪酬异常的结果
                 */
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
    /**
    * Reject
    *
    * @param id 标识
    * @param operator 操作符
    * @param reason ReasonMLmlml
     */
    public void reject(Long id, String operator, String reason) {
        String lockKey = LOCK_PREFIX + id;
        if (!idempotentTemplate.tryAcquire(lockKey, 30, TimeUnit.SECONDS)) {
            /**
            * 薪酬异常。
            * @param "操作过于频繁，请稍后重试" "操作过于频繁，请稍后重试"
            * @return 薪酬异常的结果
             */
            throw new PaymentException("操作过于频繁，请稍后重试");
        }
        try {
            WalletWithdrawApproval approval = getById(id);
            if (!"PENDING".equals(approval.getStatus())) {
                /**
                * 薪酬异常。
                * @param "仅待审批的记录可以拒绝" "仅待审批的记录可以拒绝"
                * @return 薪酬异常的结果
                 */
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

