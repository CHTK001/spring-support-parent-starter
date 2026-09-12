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
* @author CH
* @since 4.0.0
 */
@Data
@TableName("pay_merchant_wallet_limit")
public class MerchantWalletLimit {
    @TableId(value = "pay_merchant_wallet_limit_id", type = IdType.AUTO)
    /** 标识 */
    private Long id;

    @TableField("pay_merchant_wallet_limit_tenant_id")
    /** tenantid */
    private Long tenantId;

    @TableField("pay_merchant_wallet_limit_merchant_id")
    /** merchantid */
    private Long merchantId;

    @TableField("pay_merchant_wallet_limit_single_recharge_limit")
    /** Singlerecharge限制 */
    private BigDecimal singleRechargeLimit;

    @TableField("pay_merchant_wallet_limit_daily_recharge_limit")
    /** Dailyrecharge限制 */
    private BigDecimal dailyRechargeLimit;

    @TableField("pay_merchant_wallet_limit_single_withdraw_limit")
    /** Singlewithdraw限制 */
    private BigDecimal singleWithdrawLimit;

    @TableField("pay_merchant_wallet_limit_daily_withdraw_limit")
    /** Dailywithdraw限制 */
    private BigDecimal dailyWithdrawLimit;

    @TableField("pay_merchant_wallet_limit_single_transfer_limit")
    /** Singletransfer限制 */
    private BigDecimal singleTransferLimit;

    @TableField("pay_merchant_wallet_limit_daily_transfer_limit")
    /** Dailytransfer限制 */
    private BigDecimal dailyTransferLimit;

    @TableField("pay_merchant_wallet_limit_balance_limit")
    /** Balance限制 */
    private BigDecimal balanceLimit;

    @TableField("pay_merchant_wallet_limit_create_time")
    /** 创建时间 */
    private LocalDateTime createTime;

    @TableField("pay_merchant_wallet_limit_update_time")
    /** 更新时间 */
    private LocalDateTime updateTime;

    @TableField("pay_merchant_wallet_limit_create_by")
    /** 创建by */
    private String createBy;

    @TableField("pay_merchant_wallet_limit_update_by")
    /** 更新by */
    private String updateBy;

    @TableField("pay_merchant_wallet_limit_create_name")
    /** 创建名称 */
    private String createName;

    @TableField("pay_merchant_wallet_limit_update_name")
    /** 更新名称 */
    private String updateName;
}

