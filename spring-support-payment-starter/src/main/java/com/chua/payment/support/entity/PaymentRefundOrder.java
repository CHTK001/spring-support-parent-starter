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
* @author CH
* @since 4.0.0
 */
@Data
@TableName("pay_refund_order")
public class PaymentRefundOrder {

    @TableId(value = "pay_refund_order_id", type = IdType.AUTO)
    /** 标识 */
    private Long id;

    @TableField("pay_refund_order_refund_no")
    /** 退税no */
    private String refundNo;

    @TableField("pay_refund_order_order_id")
    /** 排序标识 */
    private Long orderId;

    @TableField("pay_refund_order_order_no")
    /** 排序NO */
    private String orderNo;

    @TableField("pay_refund_order_merchant_id")
    /** merchantid */
    private Long merchantId;

    @TableField("pay_refund_order_channel_id")
    /** 通道标识 */
    private Long channelId;

    @TableField("pay_refund_order_source_order_status")
    /** 来源排序状态 */
    private String sourceOrderStatus;

    @TableField("pay_refund_order_third_party_refund_no")
    /** thirdpartyrefundno */
    private String thirdPartyRefundNo;

    @TableField("pay_refund_order_refund_amount")
    /** Refundamount */
    private BigDecimal refundAmount;

    @TableField("pay_refund_order_status")
    /** 状态 */
    private String status;

    @TableField("pay_refund_order_reason")
    /** ReasonMLmlml */
    private String reason;

    @TableField("pay_refund_order_notify_status")
    /** 通知状态 */
    private Integer notifyStatus;

    @TableField("pay_refund_order_request_payload")
    /** 请求payload */
    private String requestPayload;

    @TableField("pay_refund_order_response_payload")
    /** 响应payload */
    private String responsePayload;

    @TableField("pay_refund_order_operator")
    /** 操作符 */
    private String operator;

    @TableField("pay_refund_order_remark")
    /** 评论 */
    private String remark;

    @TableField(value = "pay_refund_order_created_at", fill = FieldFill.INSERT)
    /** 创建时间AT */
    private LocalDateTime createdAt;

    @TableField(value = "pay_refund_order_updated_at", fill = FieldFill.INSERT_UPDATE)
    /** 更新时间AT */
    private LocalDateTime updatedAt;
}

