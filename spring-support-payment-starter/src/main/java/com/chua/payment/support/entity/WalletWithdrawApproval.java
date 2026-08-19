package com.chua.payment.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 钱包提现审批
 * @author CH
 */
@Data
@TableName("pay_wallet_withdraw_approval")
public class WalletWithdrawApproval {

    @TableId(value = "pay_wallet_withdraw_approval_id", type = IdType.AUTO)
    /** ID */
    private Long id;

    @TableField("pay_wallet_withdraw_approval_merchant_id")
    /** MerchantID */
    private Long merchantId;

    @TableField("pay_wallet_withdraw_approval_user_id")
    /** 用户ID */
    private Long userId;

    @TableField("pay_wallet_withdraw_approval_amount")
    /** Amount */
    private BigDecimal amount;

    @TableField("pay_wallet_withdraw_approval_bank_account")
    /** Bankaccount */
    private String bankAccount;

    @TableField("pay_wallet_withdraw_approval_bank_name")
    /** Bank名称 */
    private String bankName;

    @TableField("pay_wallet_withdraw_approval_account_name")
    /** Account名称 */
    private String accountName;

    @TableField("pay_wallet_withdraw_approval_withdraw_no")
    /** WithdrawNO */
    private String withdrawNo;

    @TableField("pay_wallet_withdraw_approval_status")
    /** 状态 */
    private String status;

    @TableField("pay_wallet_withdraw_approval_operator")
    /** Operator */
    private String operator;

    @TableField("pay_wallet_withdraw_approval_remark")
    /** Remark */
    private String remark;

    @TableField("pay_wallet_withdraw_approval_approve_time")
    /** Approve时间 */
    private LocalDateTime approveTime;

    @TableField("pay_wallet_withdraw_approval_created_at")
    /** 创建时间AT */
    private LocalDateTime createdAt;

    @TableField("pay_wallet_withdraw_approval_updated_at")
    /** 更新时间AT */
    private LocalDateTime updatedAt;
}

