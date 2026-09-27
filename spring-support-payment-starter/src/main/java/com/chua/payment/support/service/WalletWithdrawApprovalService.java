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

    /**
     * 创建提现审批单。
     * @param dto dto
     * @return 创建出的审批单
     */
    WalletWithdrawApproval createApproval(CreateApprovalDTO dto);

    /**
     * 分页查询审批单。
     * @param pageNum 页码
     * @param pageSize 每页大小
     * @param status 状态
     */
    Page<WalletWithdrawApproval> pageApprovals(int pageNum, int pageSize, String status);

    /**
     * 根据主键获取审批单。
     * @param id 标识
     * @return 获取到的审批单
     */
    WalletWithdrawApproval getById(Long id);

    /**
     * 审批通过。
     * @param id 标识
     * @param operator 操作人
     */
    void approve(Long id, String operator);

    /**
     * 驳回。
     * @param id 标识
     * @param operator 操作人
     * @param reason 驳回原因
     */
    void reject(Long id, String operator, String reason);
}
