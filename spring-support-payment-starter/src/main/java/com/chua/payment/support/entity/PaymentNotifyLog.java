package com.chua.payment.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 支付回调日志
 */
@Data
@TableName("pay_notify_log")
public class PaymentNotifyLog {

    @TableId(value = "pay_notify_log_id", type = IdType.AUTO)
    private Long id;

    @TableField("pay_notify_log_notify_type")
    private String notifyType;

    @TableField("pay_notify_log_tenant_id")
    private Long tenantId;

    @TableField("pay_notify_log_merchant_id")
    private Long merchantId;

    @TableField("pay_notify_log_channel_id")
    private Long channelId;

    @TableField("pay_notify_log_channel_type")
    private String channelType;

    @TableField("pay_notify_log_channel_sub_type")
    private String channelSubType;

    @TableField("pay_notify_log_order_id")
    private Long orderId;

    @TableField("pay_notify_log_order_no")
    private String orderNo;

    @TableField("pay_notify_log_refund_id")
    private Long refundId;

    @TableField("pay_notify_log_refund_no")
    private String refundNo;

    @TableField("pay_notify_log_third_party_trade_no")
    private String thirdPartyTradeNo;

    @TableField("pay_notify_log_request_headers")
    private String requestHeaders;

    @TableField("pay_notify_log_request_body")
    private String requestBody;

    @TableField("pay_notify_log_request_params")
    private String requestParams;

    @TableField("pay_notify_log_sign_verified")
    private Integer signVerified;

    @TableField("pay_notify_log_process_status")
    private String processStatus;

    @TableField("pay_notify_log_process_result")
    private String processResult;

    @TableField("pay_notify_log_error_message")
    private String errorMessage;

    @TableField("pay_notify_log_retry_count")
    private Integer retryCount;

    @TableField("pay_notify_log_received_at")
    private LocalDateTime receivedAt;

    @TableField("pay_notify_log_processed_at")
    private LocalDateTime processedAt;

    @TableField("pay_notify_log_create_time")
    private LocalDateTime createTime;

    @TableField("pay_notify_log_update_time")
    private LocalDateTime updateTime;

    @TableField("pay_notify_log_related_order_id")
    private Long relatedOrderId;

    @TableField("pay_notify_log_related_refund_id")
    private Long relatedRefundId;

    @TableField("pay_notify_log_received_time")
    private LocalDateTime receivedTime;

    @TableField("pay_notify_log_processed_time")
    private LocalDateTime processedTime;
}
