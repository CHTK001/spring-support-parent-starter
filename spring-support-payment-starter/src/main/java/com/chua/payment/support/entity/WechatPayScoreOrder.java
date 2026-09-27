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
 *
 * @author CH
 * @since 4.0.0
 */
@Data
@TableName("pay_wechat_pay_score_order")
public class WechatPayScoreOrder {
    @TableId(value = "pay_wechat_pay_score_order_id", type = IdType.AUTO)
    /**
     * 标识
     */
    private Long id;

    @TableField("pay_wechat_pay_score_order_merchant_id")
    /**
     * 商户标识
     */
    private Long merchantId;

    @TableField("pay_wechat_pay_score_order_channel_id")
    /**
     * 渠道标识
     */
    private Long channelId;

    @TableField("pay_wechat_pay_score_order_user_id")
    /**
     * 用户标识
     */
    private Long userId;

    @TableField("pay_wechat_pay_score_order_out_order_no")
    /**
     * 商户订单号
     */
    private String outOrderNo;

    @TableField("pay_wechat_pay_score_order_service_order_no")
    /**
     * 服务订单号
     */
    private String serviceOrderNo;

    @TableField("pay_wechat_pay_score_order_app_id")
    /**
     * 应用ID
     */
    private String appId;

    @TableField("pay_wechat_pay_score_order_service_id")
    /**
     * 服务标识
     */
    private String serviceId;

    @TableField("pay_wechat_pay_score_order_open_id")
    /**
     * 打开标识
     */
    private String openId;

    @TableField("pay_wechat_pay_score_order_state")
    /**
     * 订单状态
     */
    private String state;

    @TableField("pay_wechat_pay_score_order_total_amount")
    /**
     * 总金额
     */
    private BigDecimal totalAmount;

    @TableField("pay_wechat_pay_score_order_notify_url")
    /**
     * 通知URL
     */
    private String notifyUrl;

    @TableField("pay_wechat_pay_score_order_service_introduction")
    /**
     * 服务说明
     */
    private String serviceIntroduction;

    @TableField("pay_wechat_pay_score_order_start_time")
    /**
     * 开始时间
     */
    private String startTime;

    @TableField("pay_wechat_pay_score_order_end_time")
    /**
     * 结束时间
     */
    private String endTime;

    @TableField("pay_wechat_pay_score_order_finish_type")
    /**
     * 完成类型
     */
    private String finishType;

    @TableField("pay_wechat_pay_score_order_reason")
    /**
     * 原因
     */
    private String reason;

    @TableField("pay_wechat_pay_score_order_finish_reason")
    /**
     * 完成原因
     */
    private String finishReason;

    @TableField("pay_wechat_pay_score_order_package_info")
    /**
     * 包信息
     */
    private String packageInfo;

    @TableField("pay_wechat_pay_score_order_attach")
    /**
     * 附加数据
     */
    private String attach;

    @TableField("pay_wechat_pay_score_order_request_payload")
    /**
     * 请求报文
     */
    private String requestPayload;

    @TableField("pay_wechat_pay_score_order_response_payload")
    /**
     * 响应报文
     */
    private String responsePayload;

    @TableField("pay_wechat_pay_score_order_remark")
    /**
     * 备注
     */
    private String remark;

    @TableField("pay_wechat_pay_score_order_completed_at")
    /**
     * 完成时间
     */
    private LocalDateTime completedAt;

    @TableField("pay_wechat_pay_score_order_cancelled_at")
    /**
     * 取消时间
     */
    private LocalDateTime cancelledAt;

    @TableField("pay_wechat_pay_score_order_create_time")
    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    @TableField("pay_wechat_pay_score_order_update_time")
    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
