package com.chua.payment.support.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.payment.support.entity.WalletWithdrawApproval;

import java.math.BigDecimal;

/**
 * 钱包提现审批服务
 * @author CH
 */
public interface WalletWithdrawApprovalService {

    WalletWithdrawApproval createApproval(Long merchantId, Long userId, BigDecimal amount,
                                          String bankAccount, String bankName, String accountName,
                                          String withdrawNo, String remark);

    Page<WalletWithdrawApproval> pageApprovals(int pageNum, int pageSize, String status);

    WalletWithdrawApproval getById(Long id);

    void approve(Long id, String operator);

    void reject(Long id, String operator, String reason);
}

