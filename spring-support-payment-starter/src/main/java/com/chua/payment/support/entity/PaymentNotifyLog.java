package com.chua.payment.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 支付回调日志
 * @author CH
 */
@Data
@TableName("pay_notify_log")
public class PaymentNotifyLog {

    @TableId(value = "pay_notify_log_id", type = IdType.AUTO)
    /** ID */
    private Long id;

    @TableField("pay_notify_log_notify_type")
    /** Notify类型 */
    private String notifyType;

    @TableField("pay_notify_log_tenant_id")
    /** TenantID */
    private Long tenantId;

    @TableField("pay_notify_log_merchant_id")
    /** MerchantID */
    private Long merchantId;

    @TableField("pay_notify_log_channel_id")
    /** 通道ID */
    private Long channelId;

    @TableField("pay_notify_log_channel_type")
    /** 通道类型 */
    private String channelType;

    @TableField("pay_notify_log_channel_sub_type")
    /** 通道SUB类型 */
    private String channelSubType;

    @TableField("pay_notify_log_order_id")
    /** 排序ID */
    private Long orderId;

    @TableField("pay_notify_log_order_no")
    /** 排序NO */
    private String orderNo;

    @TableField("pay_notify_log_refund_id")
    /** RefundID */
    private Long refundId;

    @TableField("pay_notify_log_refund_no")
    /** RefundNO */
    private String refundNo;

    @TableField("pay_notify_log_third_party_trade_no")
    /** ThirdpartytradeNO */
    private String thirdPartyTradeNo;

    @TableField("pay_notify_log_request_headers")
    /** 请求headers */
    private String requestHeaders;

    @TableField("pay_notify_log_request_body")
    /** 请求请求体 */
    private String requestBody;

    @TableField("pay_notify_log_request_params")
    /** 请求参数 */
    private String requestParams;

    @TableField("pay_notify_log_sign_verified")
    /** Signverified */
    private Integer signVerified;

    @TableField("pay_notify_log_process_status")
    /** Process状态 */
    private String processStatus;

    @TableField("pay_notify_log_process_result")
    /** Process结果 */
    private String processResult;

    @TableField("pay_notify_log_error_message")
    /** 错误消息 */
    private String errorMessage;

    @TableField("pay_notify_log_retry_count")
    /** 重试数量 */
    private Integer retryCount;

    @TableField("pay_notify_log_received_at")
    /** ReceivedAT */
    private LocalDateTime receivedAt;

    @TableField("pay_notify_log_processed_at")
    /** ProcessedAT */
    private LocalDateTime processedAt;

    @TableField("pay_notify_log_create_time")
    /** Create时间 */
    private LocalDateTime createTime;

    @TableField("pay_notify_log_update_time")
    /** Update时间 */
    private LocalDateTime updateTime;

    @TableField("pay_notify_log_related_order_id")
    /** Related排序ID */
    private Long relatedOrderId;

    @TableField("pay_notify_log_related_refund_id")
    /** RelatedrefundID */
    private Long relatedRefundId;

    @TableField("pay_notify_log_received_time")
    /** Received时间 */
    private LocalDateTime receivedTime;

    @TableField("pay_notify_log_processed_time")
    /** Processed时间 */
    private LocalDateTime processedTime;
}

