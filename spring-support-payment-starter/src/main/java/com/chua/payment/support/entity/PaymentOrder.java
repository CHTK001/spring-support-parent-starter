package com.chua.payment.support.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付订单实体类
 *
 * @since 2026-03-18
 * @author CH
 */
@Data
@TableName("pay_order")
public class PaymentOrder {
    @TableId(value = "pay_order_id", type = IdType.AUTO)
    /**
     * 标识
     */
    private Long id;

    @TableField("pay_order_order_no")
    /**
     * 订单号
     */
    private String orderNo;

    @TableField("pay_order_business_order_no")
    /**
     * 业务订单号
     */
    private String businessOrderNo;

    @TableField("pay_order_merchant_id")
    /**
     * 商户标识
     */
    private Long merchantId;

    @TableField("pay_order_channel_id")
    /**
     * 渠道标识
     */
    private Long channelId;

    @TableField("pay_order_user_id")
    /**
     * 用户标识
     */
    private Long userId;

    @TableField("pay_order_channel_type")
    /**
     * 渠道类型
     */
    private String channelType;

    @TableField("pay_order_channel_sub_type")
    /**
     * 渠道子类型
     */
    private String channelSubType;

    @TableField("pay_order_amount")
    /**
     * 订单金额
     */
    private BigDecimal orderAmount;

    @TableField("pay_order_paid_amount")
    /**
     * 实付金额
     */
    private BigDecimal paidAmount;

    @TableField("pay_order_refund_amount")
    /**
     * 退款金额
     */
    private BigDecimal refundAmount;

    @TableField("pay_order_discount_amount")
    /**
     * 优惠金额
     */
    private BigDecimal discountAmount;

    @TableField("pay_order_currency")
    /**
     * 币种
     */
    private String currency;

    @TableField("pay_order_status")
    /**
     * 订单状态
     */
    private String status;

    @TableField("pay_order_subject")
    /**
     * 主题
     */
    private String subject;

    @TableField("pay_order_body")
    /**
     * 请求体
     */
    private String body;

    @TableField("pay_order_notify_url")
    /**
     * 通知URL
     */
    private String notifyUrl;

    @TableField("pay_order_return_url")
    /**
     * 返回值URL
     */
    private String returnUrl;

    @TableField("pay_order_expire_time")
    /**
     * 过期时间
     */
    private LocalDateTime expireTime;

    @TableField("pay_order_pay_time")
    /**
     * 支付时间
     */
    private LocalDateTime payTime;

    @TableField("pay_order_complete_time")
    /**
     * 完成时间
     */
    private LocalDateTime completeTime;

    @TableField("pay_order_refund_time")
    /**
     * 退款时间
     */
    private LocalDateTime refundTime;

    @TableField("pay_order_third_party_order_no")
    /**
     * 第三方订单号
     */
    private String thirdPartyOrderNo;

    @TableField("pay_order_remark")
    /**
     * 备注
     */
    private String remark;

    @TableField("pay_order_deleted")
    /**
     * 删除标记
     */
    private Integer deleted;

    @TableField(value = "pay_order_created_at", fill = FieldFill.INSERT)
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    @TableField(value = "pay_order_updated_at", fill = FieldFill.INSERT_UPDATE)
    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
