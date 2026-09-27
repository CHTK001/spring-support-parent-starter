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
 *
 * @author CH
 * @since 4.0.0
 */
@Data
@TableName("pay_wallet_order")
public class WalletOrder {
    @TableId(value = "pay_wallet_order_id", type = IdType.AUTO)
    /**
     * 标识
     */
    private Long id;

    @TableField("pay_wallet_order_order_no")
    /**
     * 订单号
     */
    private String orderNo;

    @TableField("pay_wallet_order_order_type")
    /**
     * 订单类型
     */
    private String orderType;

    @TableField("pay_wallet_order_tenant_id")
    /**
     * 租户标识
     */
    private Long tenantId;

    @TableField("pay_wallet_order_merchant_id")
    /**
     * 商户标识
     */
    private Long merchantId;

    @TableField("pay_wallet_order_user_id")
    /**
     * 用户标识
     */
    private Long userId;

    @TableField("pay_wallet_order_related_user_id")
    /**
     * 关联用户标识
     */
    private Long relatedUserId;

    @TableField("pay_wallet_order_amount")
    /**
     * 金额
     */
    private BigDecimal amount;

    @TableField("pay_wallet_order_status")
    /**
     * 订单状态
     */
    private String status;

    @TableField("pay_wallet_order_third_party_order_no")
    /**
     * 第三方订单号
     */
    private String thirdPartyOrderNo;

    @TableField("pay_wallet_order_bank_account")
    /**
     * 银行账号
     */
    private String bankAccount;

    @TableField("pay_wallet_order_bank_name")
    /**
     * 开户银行名称
     */
    private String bankName;

    @TableField("pay_wallet_order_account_name")
    /**
     * 账户名称
     */
    private String accountName;

    @TableField("pay_wallet_order_request_payload")
    /**
     * 请求报文
     */
    private String requestPayload;

    @TableField("pay_wallet_order_response_payload")
    /**
     * 响应报文
     */
    private String responsePayload;

    @TableField("pay_wallet_order_operator")
    /**
     * 操作者
     */
    private String operator;

    @TableField("pay_wallet_order_remark")
    /**
     * 备注
     */
    private String remark;

    @TableField("pay_wallet_order_completed_at")
    /**
     * 完成时间
     */
    private LocalDateTime completedAt;

    @TableField("pay_wallet_order_create_time")
    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    @TableField("pay_wallet_order_update_time")
    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    @TableField("pay_wallet_order_create_by")
    /**
     * 创建人
     */
    private String createBy;

    @TableField("pay_wallet_order_update_by")
    /**
     * 更新人
     */
    private String updateBy;

    @TableField("pay_wallet_order_create_name")
    /**
     * 创建人名称
     */
    private String createName;

    @TableField("pay_wallet_order_update_name")
    /**
     * 更新人名称
     */
    private String updateName;
}
