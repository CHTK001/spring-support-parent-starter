package com.chua.payment.support.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 优惠券模板实体类
 *
 * @since 4.0.0
 * @author CH
 */
@Data
@TableName("pay_coupon_template")
public class CouponTemplate {

    @TableId(value = "pay_coupon_template_id", type = IdType.AUTO)
    /**
     * 主键ID
     */
    private Long id;

    @TableField("pay_coupon_template_merchant_id")
    /**
     * 商户ID
     */
    private Long merchantId;

    @TableField("pay_coupon_template_name")
    /**
     * 模板名称
     */
    private String name;

    @TableField("pay_coupon_template_type")
    /**
     * 类型: CASH-满减券/DEDUCT-直减券
     */
    private String type;

    @TableField("pay_coupon_template_amount")
    /**
     * 优惠金额
     */
    private BigDecimal amount;

    @TableField("pay_coupon_template_min_order_amount")
    /**
     * 最低订单金额门槛
     */
    private BigDecimal minOrderAmount;

    @TableField("pay_coupon_template_total_count")
    /**
     * 发放总量(0=不限)
     */
    private Integer totalCount;

    @TableField("pay_coupon_template_issued_count")
    /**
     * 已发放数量
     */
    private Integer issuedCount;

    @TableField("pay_coupon_template_per_user_limit")
    /**
     * 每人限领数量
     */
    private Integer perUserLimit;

    @TableField("pay_coupon_template_valid_days")
    /**
     * 领取后有效天数(与固定有效期二选一)
     */
    private Integer validDays;

    @TableField("pay_coupon_template_valid_start_at")
    /**
     * 固定有效期开始
     */
    private LocalDateTime validStartAt;

    @TableField("pay_coupon_template_valid_end_at")
    /**
     * 固定有效期结束
     */
    private LocalDateTime validEndAt;

    @TableField("pay_coupon_template_status")
    /**
     * 状态: ENABLED-启用/DISABLED-停用
     */
    private String status;

    @TableField("pay_coupon_template_remark")
    /**
     * 备注
     */
    private String remark;

    @TableField("pay_coupon_template_deleted")
    /**
     * 逻辑删除
     */
    private Integer deleted;

    @TableField(value = "pay_coupon_template_created_at", fill = FieldFill.INSERT)
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    @TableField(value = "pay_coupon_template_updated_at", fill = FieldFill.INSERT_UPDATE)
    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
