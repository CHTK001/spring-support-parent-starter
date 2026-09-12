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
    * 创建approval。
    * @param dto dto
    * @return 创建approval的结果
     */
    WalletWithdrawApproval createApproval(CreateApprovalDTO dto);

    /**
    * pageapprovals。
    * @param pageNum pagenum
    * @param pageSize page大小
    * @param status 状态
    * @return pageApprovals的结果
     */
    Page<WalletWithdrawApproval> pageApprovals(int pageNum, int pageSize, String status);

    /**
    * 获取byid。
    * @param id 标识
    * @return 获取byid的结果
     */
    WalletWithdrawApproval getById(Long id);

    /**
    * approve。
    * @param id 标识
    * @param operator 操作符
     */
    void approve(Long id, String operator);

    /**
    * reject。
    * @param id 标识
    * @param operator 操作符
    * @param reason ReasonMLmlml
     */
    void reject(Long id, String operator, String reason);
}

