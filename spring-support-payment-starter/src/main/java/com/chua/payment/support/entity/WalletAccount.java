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
 * 钱包账户
 */
@Data
@TableName("pay_wallet_account")
public class WalletAccount {

    @TableId(value = "pay_wallet_account_id", type = IdType.AUTO)
    private Long id;

    @TableField("pay_wallet_account_merchant_id")
    private Long merchantId;

    @TableField("pay_wallet_account_user_id")
    private Long userId;

    @TableField("pay_wallet_account_available_balance")
    private BigDecimal availableBalance;

    @TableField("pay_wallet_account_frozen_balance")
    private BigDecimal frozenBalance;

    @TableField("pay_wallet_account_status")
    private Integer status;

    @TableField(value = "pay_wallet_account_created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "pay_wallet_account_updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
