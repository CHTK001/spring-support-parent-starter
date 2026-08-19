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
 * @author CH
 */
@Data
@TableName("pay_wallet_order")
public class WalletOrder {
    @TableId(value = "pay_wallet_order_id", type = IdType.AUTO)
    /** ID */
    private Long id;

    @TableField("pay_wallet_order_order_no")
    /** 排序NO */
    private String orderNo;

    @TableField("pay_wallet_order_order_type")
    /** 排序类型 */
    private String orderType;

    @TableField("pay_wallet_order_tenant_id")
    /** TenantID */
    private Long tenantId;

    @TableField("pay_wallet_order_merchant_id")
    /** MerchantID */
    private Long merchantId;

    @TableField("pay_wallet_order_user_id")
    /** 用户ID */
    private Long userId;

    @TableField("pay_wallet_order_related_user_id")
    /** Related用户ID */
    private Long relatedUserId;

    @TableField("pay_wallet_order_amount")
    /** Amount */
    private BigDecimal amount;

    @TableField("pay_wallet_order_status")
    /** 状态 */
    private String status;

    @TableField("pay_wallet_order_third_party_order_no")
    /** Thirdparty排序NO */
    private String thirdPartyOrderNo;

    @TableField("pay_wallet_order_bank_account")
    /** Bankaccount */
    private String bankAccount;

    @TableField("pay_wallet_order_bank_name")
    /** Bank名称 */
    private String bankName;

    @TableField("pay_wallet_order_account_name")
    /** Account名称 */
    private String accountName;

    @TableField("pay_wallet_order_request_payload")
    /** 请求payload */
    private String requestPayload;

    @TableField("pay_wallet_order_response_payload")
    /** 响应payload */
    private String responsePayload;

    @TableField("pay_wallet_order_operator")
    /** Operator */
    private String operator;

    @TableField("pay_wallet_order_remark")
    /** Remark */
    private String remark;

    @TableField("pay_wallet_order_completed_at")
    /** CompletedAT */
    private LocalDateTime completedAt;

    @TableField("pay_wallet_order_create_time")
    /** Create时间 */
    private LocalDateTime createTime;

    @TableField("pay_wallet_order_update_time")
    /** Update时间 */
    private LocalDateTime updateTime;

    @TableField("pay_wallet_order_create_by")
    /** CreateBY */
    private String createBy;

    @TableField("pay_wallet_order_update_by")
    /** UpdateBY */
    private String updateBy;

    @TableField("pay_wallet_order_create_name")
    /** Create名称 */
    private String createName;

    @TableField("pay_wallet_order_update_name")
    /** Update名称 */
    private String updateName;
}

