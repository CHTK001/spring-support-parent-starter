package com.chua.payment.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 回调异常记录
 */
@Data
@TableName("payment_notify_error")
public class PaymentNotifyError {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long notifyLogId;
    private String notifyType;
    private Long tenantId;
    private Long merchantId;
    private String orderNo;
    private String refundNo;
    private String errorType;
    private String errorMessage;
    private String errorStack;
    private String requestData;
    private Integer retryCount;
    private Integer maxRetryCount;
    private LocalDateTime nextRetryTime;
    private String status;
    private LocalDateTime resolvedAt;
    private String resolvedBy;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
