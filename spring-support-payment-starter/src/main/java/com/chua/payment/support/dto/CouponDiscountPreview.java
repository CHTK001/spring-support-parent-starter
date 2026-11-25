package com.chua.payment.support.dto;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 优惠券下单抵扣预览结果
 *
 * @param couponId       券标识
 * @param discountAmount 本次可抵扣的优惠金额（不超过订单金额）
 * @since 4.0.0
 * @author CH
 */
public record CouponDiscountPreview(
        Long couponId,
        BigDecimal discountAmount
) implements Serializable {

    private static final long serialVersionUID = 1L;
}
