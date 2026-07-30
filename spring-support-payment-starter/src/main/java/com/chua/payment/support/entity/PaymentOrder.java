package com.chua.payment.support.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付订单实体类
 *
 * @author CH
 * @since 2026-03-18
 */
@Data
@TableName("pay_order")
public class PaymentOrder {
    @TableId(value = "pay_order_id", type = IdType.AUTO)
    private Long id;

    @TableField("pay_order_order_no")
    private String orderNo;

    @TableField("pay_order_business_order_no")
    private String businessOrderNo;

    @TableField("pay_order_merchant_id")
    private Long merchantId;

    @TableField("pay_order_channel_id")
    private Long channelId;

    @TableField("pay_order_user_id")
    private Long userId;

    @TableField("pay_order_channel_type")
    private String channelType;

    @TableField("pay_order_channel_sub_type")
    private String channelSubType;

    @TableField("pay_order_amount")
    private BigDecimal orderAmount;

    @TableField("pay_order_paid_amount")
    private BigDecimal paidAmount;

    @TableField("pay_order_refund_amount")
    private BigDecimal refundAmount;

    @TableField("pay_order_discount_amount")
    private BigDecimal discountAmount;

    @TableField("pay_order_currency")
    private String currency;

    @TableField("pay_order_status")
    private String status;

    @TableField("pay_order_subject")
    private String subject;

    @TableField("pay_order_body")
    private String body;

    @TableField("pay_order_notify_url")
    private String notifyUrl;

    @TableField("pay_order_return_url")
    private String returnUrl;

    @TableField("pay_order_expire_time")
    private LocalDateTime expireTime;

    @TableField("pay_order_pay_time")
    private LocalDateTime payTime;

    @TableField("pay_order_complete_time")
    private LocalDateTime completeTime;

    @TableField("pay_order_refund_time")
    private LocalDateTime refundTime;

    @TableField("pay_order_third_party_order_no")
    private String thirdPartyOrderNo;

    @TableField("pay_order_remark")
    private String remark;

    @TableField("pay_order_deleted")
    private Integer deleted;

    @TableField(value = "pay_order_created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "pay_order_updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
