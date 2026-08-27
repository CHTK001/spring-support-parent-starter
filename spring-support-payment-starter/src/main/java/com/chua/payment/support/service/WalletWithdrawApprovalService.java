package com.chua.payment.support.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.payment.support.dto.CreateApprovalDTO;
import com.chua.payment.support.entity.WalletWithdrawApproval;

/**
 * 钱包提现审批服务接口，定义提现申请的创建、审批与驳回契约。
 *
 * @author CH
 * @since 4.0.0.50
 */
public interface WalletWithdrawApprovalService {

    WalletWithdrawApproval createApproval(CreateApprovalDTO dto);

    Page<WalletWithdrawApproval> pageApprovals(int pageNum, int pageSize, String status);

    WalletWithdrawApproval getById(Long id);

    void approve(Long id, String operator);

    void reject(Long id, String operator, String reason);
}

