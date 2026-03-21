package com.chua.payment.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 支付回调日志
 */
@Data
@TableName("payment_notify_log")
public class PaymentNotifyLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String notifyType;

    private Long tenantId;

    private Long merchantId;

    private Long channelId;

    private String channelType;

    private String channelSubType;

    private Long orderId;

    private String orderNo;

    private Long refundId;

    private String refundNo;

    private String thirdPartyTradeNo;

    private String requestHeaders;

    private String requestBody;

    private String requestParams;

    private Integer signVerified;

    private String processStatus;

    private String processResult;

    private String errorMessage;

    private Integer retryCount;

    private LocalDateTime receivedAt;

    private LocalDateTime processedAt;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Long relatedOrderId;

    private Long relatedRefundId;

    private LocalDateTime receivedTime;

    private LocalDateTime processedTime;
}
