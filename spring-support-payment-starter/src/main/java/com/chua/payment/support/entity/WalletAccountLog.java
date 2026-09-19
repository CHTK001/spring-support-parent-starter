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
 * @author CH
 * @since 4.0.0
 */
@Data
@TableName("pay_wallet_account_log")
public class WalletAccountLog {

    @TableId(value = "pay_wallet_account_log_id", type = IdType.AUTO)
    /** 标识 */
    private Long id;

    @TableField("pay_wallet_account_log_merchant_id")
    /** merchantid */
    private Long merchantId;

    @TableField("pay_wallet_account_log_user_id")
    /** 用户标识 */
    private Long userId;

    @TableField("pay_wallet_account_log_biz_type")
    /** BIZ类型 */
    private String bizType;

    @TableField("pay_wallet_account_log_biz_no")
    /** BIZNO */
    private String bizNo;

    @TableField("pay_wallet_account_log_change_type")
    /** 改变类型 */
    private String changeType;

    @TableField("pay_wallet_account_log_change_amount")
    /** Changeamount */
    private BigDecimal changeAmount;

    @TableField("pay_wallet_account_log_balance_before")
    /** Balancebefore */
    private BigDecimal balanceBefore;

    @TableField("pay_wallet_account_log_balance_after")
    /** Balanceafter */
    private BigDecimal balanceAfter;

    @TableField("pay_wallet_account_log_operator")
    /** 操作符 */
    private String operator;

    @TableField("pay_wallet_account_log_remark")
    /** 评论 */
    private String remark;

    @TableField(value = "pay_wallet_account_log_created_at", fill = FieldFill.INSERT)
    /** 创建时间AT */
    private LocalDateTime createdAt;
}

