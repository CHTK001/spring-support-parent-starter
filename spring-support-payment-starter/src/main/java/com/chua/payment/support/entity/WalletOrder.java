package com.chua.payment.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 钱包订单
 */
@Data
@TableName("pay_wallet_order")
public class WalletOrder {
    @TableId(value = "pay_wallet_order_id", type = IdType.AUTO)
    private Long id;

    @TableField("pay_wallet_order_order_no")
    private String orderNo;

    @TableField("pay_wallet_order_order_type")
    private String orderType;

    @TableField("pay_wallet_order_tenant_id")
    private Long tenantId;

    @TableField("pay_wallet_order_merchant_id")
    private Long merchantId;

    @TableField("pay_wallet_order_user_id")
    private Long userId;

    @TableField("pay_wallet_order_related_user_id")
    private Long relatedUserId;

    @TableField("pay_wallet_order_amount")
    private BigDecimal amount;

    @TableField("pay_wallet_order_status")
    private String status;

    @TableField("pay_wallet_order_third_party_order_no")
    private String thirdPartyOrderNo;

    @TableField("pay_wallet_order_bank_account")
    private String bankAccount;

    @TableField("pay_wallet_order_bank_name")
    private String bankName;

    @TableField("pay_wallet_order_account_name")
    private String accountName;

    @TableField("pay_wallet_order_request_payload")
    private String requestPayload;

    @TableField("pay_wallet_order_response_payload")
    private String responsePayload;

    @TableField("pay_wallet_order_operator")
    private String operator;

    @TableField("pay_wallet_order_remark")
    private String remark;

    @TableField("pay_wallet_order_completed_at")
    private LocalDateTime completedAt;

    @TableField("pay_wallet_order_create_time")
    private LocalDateTime createTime;

    @TableField("pay_wallet_order_update_time")
    private LocalDateTime updateTime;

    @TableField("pay_wallet_order_create_by")
    private String createBy;

    @TableField("pay_wallet_order_update_by")
    private String updateBy;

    @TableField("pay_wallet_order_create_name")
    private String createName;

    @TableField("pay_wallet_order_update_name")
    private String updateName;
}
