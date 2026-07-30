package com.chua.payment.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商户钱包限额配置
 */
@Data
@TableName("pay_merchant_wallet_limit")
public class MerchantWalletLimit {
    @TableId(value = "pay_merchant_wallet_limit_id", type = IdType.AUTO)
    private Long id;

    @TableField("pay_merchant_wallet_limit_tenant_id")
    private Long tenantId;

    @TableField("pay_merchant_wallet_limit_merchant_id")
    private Long merchantId;

    @TableField("pay_merchant_wallet_limit_single_recharge_limit")
    private BigDecimal singleRechargeLimit;

    @TableField("pay_merchant_wallet_limit_daily_recharge_limit")
    private BigDecimal dailyRechargeLimit;

    @TableField("pay_merchant_wallet_limit_single_withdraw_limit")
    private BigDecimal singleWithdrawLimit;

    @TableField("pay_merchant_wallet_limit_daily_withdraw_limit")
    private BigDecimal dailyWithdrawLimit;

    @TableField("pay_merchant_wallet_limit_single_transfer_limit")
    private BigDecimal singleTransferLimit;

    @TableField("pay_merchant_wallet_limit_daily_transfer_limit")
    private BigDecimal dailyTransferLimit;

    @TableField("pay_merchant_wallet_limit_balance_limit")
    private BigDecimal balanceLimit;

    @TableField("pay_merchant_wallet_limit_create_time")
    private LocalDateTime createTime;

    @TableField("pay_merchant_wallet_limit_update_time")
    private LocalDateTime updateTime;

    @TableField("pay_merchant_wallet_limit_create_by")
    private String createBy;

    @TableField("pay_merchant_wallet_limit_update_by")
    private String updateBy;

    @TableField("pay_merchant_wallet_limit_create_name")
    private String createName;

    @TableField("pay_merchant_wallet_limit_update_name")
    private String updateName;
}
