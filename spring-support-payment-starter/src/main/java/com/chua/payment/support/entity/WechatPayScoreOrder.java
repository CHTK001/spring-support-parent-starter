package com.chua.payment.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 微信支付分订单
 * @author CH
 */
@Data
@TableName("pay_wechat_pay_score_order")
public class WechatPayScoreOrder {
    @TableId(value = "pay_wechat_pay_score_order_id", type = IdType.AUTO)
    /** ID */
    private Long id;

    @TableField("pay_wechat_pay_score_order_merchant_id")
    /** MerchantID */
    private Long merchantId;

    @TableField("pay_wechat_pay_score_order_channel_id")
    /** 通道ID */
    private Long channelId;

    @TableField("pay_wechat_pay_score_order_user_id")
    /** 用户ID */
    private Long userId;

    @TableField("pay_wechat_pay_score_order_out_order_no")
    /** OUT排序NO */
    private String outOrderNo;

    @TableField("pay_wechat_pay_score_order_service_order_no")
    /** 服务排序NO */
    private String serviceOrderNo;

    @TableField("pay_wechat_pay_score_order_app_id")
    /** APPID */
    private String appId;

    @TableField("pay_wechat_pay_score_order_service_id")
    /** 服务ID */
    private String serviceId;

    @TableField("pay_wechat_pay_score_order_open_id")
    /** OpenID */
    private String openId;

    @TableField("pay_wechat_pay_score_order_state")
    /** 状态 */
    private String state;

    @TableField("pay_wechat_pay_score_order_total_amount")
    /** 总数amount */
    private BigDecimal totalAmount;

    @TableField("pay_wechat_pay_score_order_notify_url")
    /** NotifyURL */
    private String notifyUrl;

    @TableField("pay_wechat_pay_score_order_service_introduction")
    /** 服务introduction */
    private String serviceIntroduction;

    @TableField("pay_wechat_pay_score_order_start_time")
    /** 开始时间 */
    private String startTime;

    @TableField("pay_wechat_pay_score_order_end_time")
    /** 结束时间 */
    private String endTime;

    @TableField("pay_wechat_pay_score_order_finish_type")
    /** Finish类型 */
    private String finishType;

    @TableField("pay_wechat_pay_score_order_reason")
    /** Reason */
    private String reason;

    @TableField("pay_wechat_pay_score_order_finish_reason")
    /** Finishreason */
    private String finishReason;

    @TableField("pay_wechat_pay_score_order_package_info")
    /** Package信息 */
    private String packageInfo;

    @TableField("pay_wechat_pay_score_order_attach")
    /** Attach */
    private String attach;

    @TableField("pay_wechat_pay_score_order_request_payload")
    /** 请求payload */
    private String requestPayload;

    @TableField("pay_wechat_pay_score_order_response_payload")
    /** 响应payload */
    private String responsePayload;

    @TableField("pay_wechat_pay_score_order_remark")
    /** Remark */
    private String remark;

    @TableField("pay_wechat_pay_score_order_completed_at")
    /** CompletedAT */
    private LocalDateTime completedAt;

    @TableField("pay_wechat_pay_score_order_cancelled_at")
    /** CancelledAT */
    private LocalDateTime cancelledAt;

    @TableField("pay_wechat_pay_score_order_create_time")
    /** Create时间 */
    private LocalDateTime createTime;

    @TableField("pay_wechat_pay_score_order_update_time")
    /** Update时间 */
    private LocalDateTime updateTime;
}

