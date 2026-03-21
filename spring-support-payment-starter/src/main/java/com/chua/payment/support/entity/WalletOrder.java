package com.chua.payment.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 钱包订单
 */
@Data
@TableName("wallet_order")
public class WalletOrder {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String orderNo;
    private String orderType;
    private Long tenantId;
    private Long merchantId;
    private Long userId;
    private Long relatedUserId;
    private BigDecimal amount;
    private String status;
    private String thirdPartyOrderNo;
    private String bankAccount;
    private String bankName;
    private String accountName;
    private String requestPayload;
    private String responsePayload;
    private String operator;
    private String remark;
    private LocalDateTime completedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private String createBy;
    private String updateBy;
    private String createName;
    private String updateName;
}
