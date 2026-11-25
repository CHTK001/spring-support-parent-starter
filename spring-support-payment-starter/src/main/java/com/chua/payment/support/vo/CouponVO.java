package com.chua.payment.support.vo;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户优惠券视图对象
 *
 * @param id             主键ID
 * @param merchantId     商户ID
 * @param userId         用户ID
 * @param templateId     模板ID
 * @param couponNo       券号
 * @param name           券名称
 * @param type           类型: CASH/DEDUCT
 * @param typeDesc       类型描述
 * @param amount         优惠金额
 * @param minOrderAmount 最低订单金额门槛
 * @param status         状态: UNUSED/FROZEN/USED/EXPIRED
 * @param statusDesc     状态描述
 * @param orderId        核销订单ID
 * @param orderNo        核销订单号
 * @param usedAt         核销时间
 * @param expireAt       过期时间
 * @param createdAt      领取时间
 * @since 4.0.0
 * @author CH
 */
public record CouponVO(
        Long id,
        Long merchantId,
        Long userId,
        Long templateId,
        String couponNo,
        String name,
        String type,
        String typeDesc,
        BigDecimal amount,
        BigDecimal minOrderAmount,
        String status,
        String statusDesc,
        Long orderId,
        String orderNo,
        LocalDateTime usedAt,
        LocalDateTime expireAt,
        LocalDateTime createdAt
) implements Serializable {

    private static final long serialVersionUID = 1L;
}
