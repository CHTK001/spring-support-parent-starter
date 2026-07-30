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
 */
@Data
@TableName("pay_wallet_withdraw_approval")
public class WalletWithdrawApproval {

    @TableId(value = "pay_wallet_withdraw_approval_id", type = IdType.AUTO)
    private Long id;

    @TableField("pay_wallet_withdraw_approval_merchant_id")
    private Long merchantId;

    @TableField("pay_wallet_withdraw_approval_user_id")
    private Long userId;

    @TableField("pay_wallet_withdraw_approval_amount")
    private BigDecimal amount;

    @TableField("pay_wallet_withdraw_approval_bank_account")
    private String bankAccount;

    @TableField("pay_wallet_withdraw_approval_bank_name")
    private String bankName;

    @TableField("pay_wallet_withdraw_approval_account_name")
    private String accountName;

    @TableField("pay_wallet_withdraw_approval_withdraw_no")
    private String withdrawNo;

    @TableField("pay_wallet_withdraw_approval_status")
    private String status;

    @TableField("pay_wallet_withdraw_approval_operator")
    private String operator;

    @TableField("pay_wallet_withdraw_approval_remark")
    private String remark;

    @TableField("pay_wallet_withdraw_approval_approve_time")
    private LocalDateTime approveTime;

    @TableField("pay_wallet_withdraw_approval_created_at")
    private LocalDateTime createdAt;

    @TableField("pay_wallet_withdraw_approval_updated_at")
    private LocalDateTime updatedAt;
}
