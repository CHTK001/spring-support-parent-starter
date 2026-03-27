package com.chua.payment.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 微信支付分订单
 */
@Data
@TableName("wechat_pay_score_order")
public class WechatPayScoreOrder {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long merchantId;
    private Long channelId;
    private Long userId;
    private String outOrderNo;
    private String serviceOrderNo;
    private String appId;
    private String serviceId;
    private String openId;
    private String state;
    private BigDecimal totalAmount;
    private String notifyUrl;
    private String serviceIntroduction;
    private String startTime;
    private String endTime;
    private String finishType;
    private String reason;
    private String finishReason;
    private String packageInfo;
    private String attach;
    private String requestPayload;
    private String responsePayload;
    private String remark;
    private LocalDateTime completedAt;
    private LocalDateTime cancelledAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
