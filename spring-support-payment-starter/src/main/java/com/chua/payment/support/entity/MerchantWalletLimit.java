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
 */
@Data
@TableName("pay_merchant_wallet_limit")
public class MerchantWalletLimit {
    @TableId(value = "pay_merchant_wallet_limit_id", type = IdType.AUTO)
    /** ID */
    private Long id;

    @TableField("pay_merchant_wallet_limit_tenant_id")
    /** TenantID */
    private Long tenantId;

    @TableField("pay_merchant_wallet_limit_merchant_id")
    /** MerchantID */
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
    /** Create时间 */
    private LocalDateTime createTime;

    @TableField("pay_merchant_wallet_limit_update_time")
    /** Update时间 */
    private LocalDateTime updateTime;

    @TableField("pay_merchant_wallet_limit_create_by")
    /** CreateBY */
    private String createBy;

    @TableField("pay_merchant_wallet_limit_update_by")
    /** UpdateBY */
    private String updateBy;

    @TableField("pay_merchant_wallet_limit_create_name")
    /** Create名称 */
    private String createName;

    @TableField("pay_merchant_wallet_limit_update_name")
    /** Update名称 */
    private String updateName;
}

