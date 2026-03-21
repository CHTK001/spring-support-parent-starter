package com.chua.payment.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商户钱包限额配置
 */
@Data
@TableName("merchant_wallet_limit")
public class MerchantWalletLimit {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private Long merchantId;
    private BigDecimal singleRechargeLimit;
    private BigDecimal dailyRechargeLimit;
    private BigDecimal singleWithdrawLimit;
    private BigDecimal dailyWithdrawLimit;
    private BigDecimal singleTransferLimit;
    private BigDecimal dailyTransferLimit;
    private BigDecimal balanceLimit;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private String createBy;
    private String updateBy;
    private String createName;
    private String updateName;
}
