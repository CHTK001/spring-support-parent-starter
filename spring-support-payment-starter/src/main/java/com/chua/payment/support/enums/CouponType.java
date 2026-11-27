package com.chua.payment.support.enums;

import com.chua.common.support.utils.StringUtils;

/**
 * 优惠券类型
 *
 * @author CH
 * @since 4.0.0
 */
public enum CouponType {

    /**
     * 满减券：订单金额达到门槛后减固定面额
     */
    CASH("满减券"),
    /**
     * 直减券：无门槛直接抵扣固定面额
     */
    DEDUCT("直减券");

    /**
     * 描述
     */
    private final String description;

    /**
     * 优惠券类型
     *
     * @param description 描述
     */
    CouponType(String description) {
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
    public static CouponType parse(String name) {
        if (StringUtils.isEmpty(name)) {
            return null;
        }
        for (CouponType value : values()) {
            if (value.name().equalsIgnoreCase(name)) {
                return value;
            }
        }
        return null;
    }
}
