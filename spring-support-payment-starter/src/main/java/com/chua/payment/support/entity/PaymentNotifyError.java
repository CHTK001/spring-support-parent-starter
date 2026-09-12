package com.chua.payment.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
* 回调异常记录
* @author CH
* @since 4.0.0
 */
@Data
@TableName("pay_notify_error")
public class PaymentNotifyError {
    @TableId(value = "pay_notify_error_id", type = IdType.AUTO)
    /** 标识 */
    private Long id;

    @TableField("pay_notify_error_notify_log_id")
    /** 通知日志标识 */
    private Long notifyLogId;

    @TableField("pay_notify_error_notify_type")
    /** 通知类型 */
    private String notifyType;

    @TableField("pay_notify_error_tenant_id")
    /** tenantid */
    private Long tenantId;

    @TableField("pay_notify_error_merchant_id")
    /** merchantid */
    private Long merchantId;

    @TableField("pay_notify_error_order_no")
    /** 排序NO */
    private String orderNo;

    @TableField("pay_notify_error_refund_no")
    /** 退税no */
    private String refundNo;

    @TableField("pay_notify_error_error_type")
    /** 错误类型 */
    private String errorType;

    @TableField("pay_notify_error_error_message")
    /** 错误消息 */
    private String errorMessage;

    @TableField("pay_notify_error_error_stack")
    /** 错误栈 */
    private String errorStack;

    @TableField("pay_notify_error_request_data")
    /** 请求数据 */
    private String requestData;

    @TableField("pay_notify_error_retry_count")
    /** 重试数量 */
    private Integer retryCount;

    @TableField("pay_notify_error_max_retry_count")
    /** 最大值重试数量 */
    private Integer maxRetryCount;

    @TableField("pay_notify_error_next_retry_time")
    /** 下一个重试时间 */
    private LocalDateTime nextRetryTime;

    @TableField("pay_notify_error_status")
    /** 状态 */
    private String status;

    @TableField("pay_notify_error_resolved_at")
    /** resolvedat */
    private LocalDateTime resolvedAt;

    @TableField("pay_notify_error_resolved_by")
    /** resolvedby */
    private String resolvedBy;

    @TableField("pay_notify_error_remark")
    /** 评论 */
    private String remark;

    @TableField("pay_notify_error_create_time")
    /** 创建时间 */
    private LocalDateTime createTime;

    @TableField("pay_notify_error_update_time")
    /** 更新时间 */
    private LocalDateTime updateTime;
}

