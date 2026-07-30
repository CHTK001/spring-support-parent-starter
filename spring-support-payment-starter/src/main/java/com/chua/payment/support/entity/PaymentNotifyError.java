package com.chua.payment.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 回调异常记录
 */
@Data
@TableName("pay_notify_error")
public class PaymentNotifyError {
    @TableId(value = "pay_notify_error_id", type = IdType.AUTO)
    private Long id;

    @TableField("pay_notify_error_notify_log_id")
    private Long notifyLogId;

    @TableField("pay_notify_error_notify_type")
    private String notifyType;

    @TableField("pay_notify_error_tenant_id")
    private Long tenantId;

    @TableField("pay_notify_error_merchant_id")
    private Long merchantId;

    @TableField("pay_notify_error_order_no")
    private String orderNo;

    @TableField("pay_notify_error_refund_no")
    private String refundNo;

    @TableField("pay_notify_error_error_type")
    private String errorType;

    @TableField("pay_notify_error_error_message")
    private String errorMessage;

    @TableField("pay_notify_error_error_stack")
    private String errorStack;

    @TableField("pay_notify_error_request_data")
    private String requestData;

    @TableField("pay_notify_error_retry_count")
    private Integer retryCount;

    @TableField("pay_notify_error_max_retry_count")
    private Integer maxRetryCount;

    @TableField("pay_notify_error_next_retry_time")
    private LocalDateTime nextRetryTime;

    @TableField("pay_notify_error_status")
    private String status;

    @TableField("pay_notify_error_resolved_at")
    private LocalDateTime resolvedAt;

    @TableField("pay_notify_error_resolved_by")
    private String resolvedBy;

    @TableField("pay_notify_error_remark")
    private String remark;

    @TableField("pay_notify_error_create_time")
    private LocalDateTime createTime;

    @TableField("pay_notify_error_update_time")
    private LocalDateTime updateTime;
}
