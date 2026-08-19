package com.chua.payment.support.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付订单实体类
 *
 * @since 2026-03-18
 * @author CH
 */
@Data
@TableName("pay_order")
public class PaymentOrder {
    @TableId(value = "pay_order_id", type = IdType.AUTO)
    /** ID */
    private Long id;

    @TableField("pay_order_order_no")
    /** 排序NO */
    private String orderNo;

    @TableField("pay_order_business_order_no")
    /** Business排序NO */
    private String businessOrderNo;

    @TableField("pay_order_merchant_id")
    /** MerchantID */
    private Long merchantId;

    @TableField("pay_order_channel_id")
    /** 通道ID */
    private Long channelId;

    @TableField("pay_order_user_id")
    /** 用户ID */
    private Long userId;

    @TableField("pay_order_channel_type")
    /** 通道类型 */
    private String channelType;

    @TableField("pay_order_channel_sub_type")
    /** 通道SUB类型 */
    private String channelSubType;

    @TableField("pay_order_amount")
    /** 排序amount */
    private BigDecimal orderAmount;

    @TableField("pay_order_paid_amount")
    /** Paidamount */
    private BigDecimal paidAmount;

    @TableField("pay_order_refund_amount")
    /** Refundamount */
    private BigDecimal refundAmount;

    @TableField("pay_order_discount_amount")
    /** Discountamount */
    private BigDecimal discountAmount;

    @TableField("pay_order_currency")
    /** Currency */
    private String currency;

    @TableField("pay_order_status")
    /** 状态 */
    private String status;

    @TableField("pay_order_subject")
    /** Subject */
    private String subject;

    @TableField("pay_order_body")
    /** 请求体 */
    private String body;

    @TableField("pay_order_notify_url")
    /** NotifyURL */
    private String notifyUrl;

    @TableField("pay_order_return_url")
    /** 返回值URL */
    private String returnUrl;

    @TableField("pay_order_expire_time")
    /** Expire时间 */
    private LocalDateTime expireTime;

    @TableField("pay_order_pay_time")
    /** PAY时间 */
    private LocalDateTime payTime;

    @TableField("pay_order_complete_time")
    /** Complete时间 */
    private LocalDateTime completeTime;

    @TableField("pay_order_refund_time")
    /** Refund时间 */
    private LocalDateTime refundTime;

    @TableField("pay_order_third_party_order_no")
    /** Thirdparty排序NO */
    private String thirdPartyOrderNo;

    @TableField("pay_order_remark")
    /** Remark */
    private String remark;

    @TableField("pay_order_deleted")
    /** 删除标记 */
    private Integer deleted;

    @TableField(value = "pay_order_created_at", fill = FieldFill.INSERT)
    /** 创建时间AT */
    private LocalDateTime createdAt;

    @TableField(value = "pay_order_updated_at", fill = FieldFill.INSERT_UPDATE)
    /** 更新时间AT */
    private LocalDateTime updatedAt;
}
