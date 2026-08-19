package com.chua.payment.support.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 微信支付分订单视图
 * @author CH
 */
@Data
public class WechatPayScoreOrderVO {
    /** ID */
    private Long id;
    /** MerchantID */
    private Long merchantId;
    /** 通道ID */
    private Long channelId;
    /** 用户ID */
    private Long userId;
    /** OUT排序NO */
    private String outOrderNo;
    /** 服务排序NO */
    private String serviceOrderNo;
    /** APPID */
    private String appId;
    /** 服务ID */
    private String serviceId;
    /** OpenID */
    private String openId;
    /** 状态 */
    private String state;
    /** 总数amount */
    private BigDecimal totalAmount;
    /** NotifyURL */
    private String notifyUrl;
    /** 服务introduction */
    private String serviceIntroduction;
    /** 开始时间 */
    private String startTime;
    /** 结束时间 */
    private String endTime;
    /** Finish类型 */
    private String finishType;
    /** Reason */
    private String reason;
    /** Finishreason */
    private String finishReason;
    /** Package信息 */
    private String packageInfo;
    /** Attach */
    private String attach;
    /** 请求payload */
    private String requestPayload;
    /** 响应payload */
    private String responsePayload;
    /** Remark */
    private String remark;
    /** CompletedAT */
    private LocalDateTime completedAt;
    /** CancelledAT */
    private LocalDateTime cancelledAt;
    /** Create时间 */
    private LocalDateTime createTime;
    /** Update时间 */
    private LocalDateTime updateTime;
}

