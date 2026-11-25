package com.chua.payment.support.enums;

/**
 * 用户优惠券状态
 *
 * <p>状态流转：UNUSED（已发放可用）→ FROZEN（下单锁定，待支付）→ USED（支付成功核销）；
 * 退款成功后 USED/FROZEN → UNUSED（退券恢复）；过期券在核销时惰性判定。</p>
 *
 * @author CH
 * @since 4.0.0
 */
public enum CouponStatus {

    /**
     * 未使用：已发放且在有效期内，可下单锁定
     */
    UNUSED("未使用"),
    /**
     * 下单锁定：已被订单占用，等待支付结果
     */
    FROZEN("下单锁定"),
    /**
     * 已核销：订单支付成功，券完成抵扣
     */
    USED("已核销"),
    /**
     * 已过期：超过有效期，不可使用
     */
    EXPIRED("已过期");

    /**
     * 描述
     */
    private final String description;

    /**
     * 用户优惠券状态
     *
     * @param description 描述
     */
    CouponStatus(String description) {
        this.description = description;
    }

    /**
     * 获取描述
     *
     * @return 描述
     */
    public String getDescription() {
        return description;
    }

    /**
     * 按名称安全解析，未知或空值返回 null
     *
     * @param name 名称
     * @return 枚举值，未匹配返回 null
     */
    public static CouponStatus parse(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        for (CouponStatus value : values()) {
            if (value.name().equalsIgnoreCase(name)) {
                return value;
            }
        }
        return null;
    }
}
