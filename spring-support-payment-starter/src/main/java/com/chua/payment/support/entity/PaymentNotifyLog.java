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
* @since 4.0.0
 */
@Data
@TableName("pay_notify_log")
public class PaymentNotifyLog {

    @TableId(value = "pay_notify_log_id", type = IdType.AUTO)
    /** 标识 */
    private Long id;

    @TableField("pay_notify_log_notify_type")
    /** 通知类型 */
    private String notifyType;

    @TableField("pay_notify_log_tenant_id")
    /** tenantid */
    private Long tenantId;

    @TableField("pay_notify_log_merchant_id")
    /** merchantid */
    private Long merchantId;

    @TableField("pay_notify_log_channel_id")
    /** 通道标识 */
    private Long channelId;

    @TableField("pay_notify_log_channel_type")
    /** 通道类型 */
    private String channelType;

    @TableField("pay_notify_log_channel_sub_type")
    /** 通道SUB类型 */
    private String channelSubType;

    @TableField("pay_notify_log_order_id")
    /** 排序标识 */
    private Long orderId;

    @TableField("pay_notify_log_order_no")
    /** 排序NO */
    private String orderNo;

    @TableField("pay_notify_log_refund_id")
    /** 退税标识 */
    private Long refundId;

    @TableField("pay_notify_log_refund_no")
    /** 退税no */
    private String refundNo;

    @TableField("pay_notify_log_third_party_trade_no")
    /** thirdpartytradeno */
    private String thirdPartyTradeNo;

    @TableField("pay_notify_log_request_headers")
    /** 请求头部 */
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
    /** 处理状态 */
    private String processStatus;

    @TableField("pay_notify_log_process_result")
    /** 处理结果 */
    private String processResult;

    @TableField("pay_notify_log_error_message")
    /** 错误消息 */
    private String errorMessage;

    @TableField("pay_notify_log_retry_count")
    /** 重试数量 */
    private Integer retryCount;

    @TableField("pay_notify_log_received_at")
    /** 接收at */
    private LocalDateTime receivedAt;

    @TableField("pay_notify_log_processed_at")
    /** 处理at */
    private LocalDateTime processedAt;

    @TableField("pay_notify_log_create_time")
    /** 创建时间 */
    private LocalDateTime createTime;

    @TableField("pay_notify_log_update_time")
    /** 更新时间 */
    private LocalDateTime updateTime;

    @TableField("pay_notify_log_related_order_id")
    /** Related排序标识 */
    private Long relatedOrderId;

    @TableField("pay_notify_log_related_refund_id")
    /** relatedrefundid */
    private Long relatedRefundId;

    @TableField("pay_notify_log_received_time")
    /** Received时间 */
    private LocalDateTime receivedTime;

    @TableField("pay_notify_log_processed_time")
    /** 处理时间 */
    private LocalDateTime processedTime;
}

