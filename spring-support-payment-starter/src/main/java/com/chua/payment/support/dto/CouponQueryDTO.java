package com.chua.payment.support.dto;

import java.io.Serializable;

/**
 * 用户优惠券分页查询参数
 *
 * @param pageNum    页码
 * @param pageSize   每页大小
 * @param merchantId 商户ID
 * @param userId     用户ID
 * @param status     状态: UNUSED/FROZEN/USED/EXPIRED
 * @param couponNo   券号(精确)
 * @since 4.0.0
 * @author CH
 */
public record CouponQueryDTO(
        Integer pageNum,
        Integer pageSize,
        Long merchantId,
        Long userId,
        String status,
        String couponNo
) implements Serializable {

    private static final long serialVersionUID = 1L;
}
