package com.chua.payment.support.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 优惠券模板创建/更新数据传输对象
 *
 * @param merchantId      商户ID
 * @param name            模板名称
 * @param type            类型: CASH-满减券/DEDUCT-直减券
 * @param amount          优惠金额
 * @param minOrderAmount  最低订单金额门槛
 * @param totalCount      发放总量(0=不限)
 * @param perUserLimit    每人限领数量
 * @param validDays       领取后有效天数(与固定有效期二选一)
 * @param validStartAt    固定有效期开始
 * @param validEndAt      固定有效期结束
 * @param status          状态: ENABLED/DISABLED
 * @param remark          备注
 * @since 4.0.0
 * @author CH
 */
public record CouponTemplateDTO(
        Long merchantId,
        String name,
        String type,
        BigDecimal amount,
        BigDecimal minOrderAmount,
        Integer totalCount,
        Integer perUserLimit,
        Integer validDays,
        LocalDateTime validStartAt,
        LocalDateTime validEndAt,
        String status,
        String remark
) implements Serializable {

    private static final long serialVersionUID = 1L;
}
