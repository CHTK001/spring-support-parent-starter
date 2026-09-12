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
* @author CH
* @since 4.0.0
 */
@Data
@TableName("pay_wallet_account")
public class WalletAccount {

    @TableId(value = "pay_wallet_account_id", type = IdType.AUTO)
    /** 标识 */
    private Long id;

    @TableField("pay_wallet_account_merchant_id")
    /** merchantid */
    private Long merchantId;

    @TableField("pay_wallet_account_user_id")
    /** 用户标识 */
    private Long userId;

    @TableField("pay_wallet_account_available_balance")
    /** 可用balance */
    private BigDecimal availableBalance;

    @TableField("pay_wallet_account_frozen_balance")
    /** Frozenbalance */
    private BigDecimal frozenBalance;

    @TableField("pay_wallet_account_status")
    /** 状态 */
    private Integer status;

    @TableField(value = "pay_wallet_account_created_at", fill = FieldFill.INSERT)
    /** 创建时间AT */
    private LocalDateTime createdAt;

    @TableField(value = "pay_wallet_account_updated_at", fill = FieldFill.INSERT_UPDATE)
    /** 更新时间AT */
    private LocalDateTime updatedAt;
}

