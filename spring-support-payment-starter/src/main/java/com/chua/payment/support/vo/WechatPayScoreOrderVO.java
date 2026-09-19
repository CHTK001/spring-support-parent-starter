package com.chua.payment.support.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 微信支付分订单视图
 * @author CH
 * @since 4.0.0
 */
@Data
public class WechatPayScoreOrderVO {
    /** 标识 */
    private Long id;
    /** merchantid */
    private Long merchantId;
    /** 通道标识 */
    private Long channelId;
    /** 用户标识 */
    private Long userId;
    /** 出排序NO */
    private String outOrderNo;
    /** 服务排序NO */
    private String serviceOrderNo;
    /** APPID */
    private String appId;
    /** 服务标识 */
    private String serviceId;
    /** 打开标识 */
    private String openId;
    /** 状态 */
    private String state;
    /** 总数amount */
    private BigDecimal totalAmount;
    /** 通知url */
    private String notifyUrl;
    /** 服务introduction */
    private String serviceIntroduction;
    /** 开始时间 */
    private String startTime;
    /** 结束时间 */
    private String endTime;
    /** 饰面类型 */
    private String finishType;
    /** ReasonMLmlml */
    private String reason;
    /** Finishreason */
    private String finishReason;
    /** 包信息 */
    private String packageInfo;
    /** Attach */
    private String attach;
    /** 请求payload */
    private String requestPayload;
    /** 响应payload */
    private String responsePayload;
    /** 评论 */
    private String remark;
    /** 完成at */
    private LocalDateTime completedAt;
    /** cancelledat */
    private LocalDateTime cancelledAt;
    /** 创建时间 */
    private LocalDateTime createTime;
    /** 更新时间 */
    private LocalDateTime updateTime;
}

