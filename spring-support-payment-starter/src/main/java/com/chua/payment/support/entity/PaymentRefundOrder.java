package com.chua.payment.support.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 退款单实体
 */
@Data
@TableName("pay_refund_order")
public class PaymentRefundOrder {

    @TableId(value = "pay_refund_order_id", type = IdType.AUTO)
    private Long id;

    @TableField("pay_refund_order_refund_no")
    private String refundNo;

    @TableField("pay_refund_order_order_id")
    private Long orderId;

    @TableField("pay_refund_order_order_no")
    private String orderNo;

    @TableField("pay_refund_order_merchant_id")
    private Long merchantId;

    @TableField("pay_refund_order_channel_id")
    private Long channelId;

    @TableField("pay_refund_order_source_order_status")
    private String sourceOrderStatus;

    @TableField("pay_refund_order_third_party_refund_no")
    private String thirdPartyRefundNo;

    @TableField("pay_refund_order_refund_amount")
    private BigDecimal refundAmount;

    @TableField("pay_refund_order_status")
    private String status;

    @TableField("pay_refund_order_reason")
    private String reason;

    @TableField("pay_refund_order_notify_status")
    private Integer notifyStatus;

    @TableField("pay_refund_order_request_payload")
    private String requestPayload;

    @TableField("pay_refund_order_response_payload")
    private String responsePayload;

    @TableField("pay_refund_order_operator")
    private String operator;

    @TableField("pay_refund_order_remark")
    private String remark;

    @TableField(value = "pay_refund_order_created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "pay_refund_order_updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
