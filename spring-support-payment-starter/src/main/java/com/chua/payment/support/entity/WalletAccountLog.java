package com.chua.payment.support.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 钱包账户流水
 */
@Data
@TableName("pay_wallet_account_log")
public class WalletAccountLog {

    @TableId(value = "pay_wallet_account_log_id", type = IdType.AUTO)
    private Long id;

    @TableField("pay_wallet_account_log_merchant_id")
    private Long merchantId;

    @TableField("pay_wallet_account_log_user_id")
    private Long userId;

    @TableField("pay_wallet_account_log_biz_type")
    private String bizType;

    @TableField("pay_wallet_account_log_biz_no")
    private String bizNo;

    @TableField("pay_wallet_account_log_change_type")
    private String changeType;

    @TableField("pay_wallet_account_log_change_amount")
    private BigDecimal changeAmount;

    @TableField("pay_wallet_account_log_balance_before")
    private BigDecimal balanceBefore;

    @TableField("pay_wallet_account_log_balance_after")
    private BigDecimal balanceAfter;

    @TableField("pay_wallet_account_log_operator")
    private String operator;

    @TableField("pay_wallet_account_log_remark")
    private String remark;

    @TableField(value = "pay_wallet_account_log_created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
