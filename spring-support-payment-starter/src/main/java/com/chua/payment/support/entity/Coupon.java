package com.chua.payment.support.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户优惠券实体类
 *
 * @since 4.0.0
 * @author CH
 */
@Data
@TableName("pay_coupon")
public class Coupon {

    @TableId(value = "pay_coupon_id", type = IdType.AUTO)
    /**
     * 主键ID
     */
    private Long id;

    @TableField("pay_coupon_merchant_id")
    /**
     * 商户ID
     */
    private Long merchantId;

    @TableField("pay_coupon_user_id")
    /**
     * 用户ID
     */
    private Long userId;

    @TableField("pay_coupon_template_id")
    /**
     * 模板ID
     */
    private Long templateId;

    @TableField("pay_coupon_no")
    /**
     * 券号
     */
    private String couponNo;

    @TableField("pay_coupon_name")
    /**
     * 券名称
     */
    private String name;

    @TableField("pay_coupon_type")
    /**
     * 类型: CASH/DEDUCT
     */
    private String type;

    @TableField("pay_coupon_amount")
    /**
     * 优惠金额
     */
    private BigDecimal amount;

    @TableField("pay_coupon_min_order_amount")
    /**
     * 最低订单金额门槛
     */
    private BigDecimal minOrderAmount;

    @TableField("pay_coupon_status")
    /**
     * 状态: UNUSED/FROZEN/USED/EXPIRED
     */
    private String status;

    @TableField("pay_coupon_order_id")
    /**
     * 核销订单ID
     */
    private Long orderId;

    @TableField("pay_coupon_order_no")
    /**
     * 核销订单号
     */
    private String orderNo;

    @TableField("pay_coupon_used_at")
    /**
     * 核销时间
     */
    private LocalDateTime usedAt;

    @TableField("pay_coupon_expire_at")
    /**
     * 过期时间
     */
    private LocalDateTime expireAt;

    @TableField("pay_coupon_deleted")
    /**
     * 逻辑删除
     */
    private Integer deleted;

    @TableField(value = "pay_coupon_created_at", fill = FieldFill.INSERT)
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    @TableField(value = "pay_coupon_updated_at", fill = FieldFill.INSERT_UPDATE)
    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
