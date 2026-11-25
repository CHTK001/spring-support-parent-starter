package com.chua.payment.support.dto;

import java.io.Serializable;

/**
 * 优惠券模板分页查询参数
 *
 * @param pageNum   页码
 * @param pageSize  每页大小
 * @param merchantId 商户ID
 * @param name      模板名称(模糊)
 * @param status    状态: ENABLED/DISABLED
 * @since 4.0.0
 * @author CH
 */
public record CouponTemplateQueryDTO(
        Integer pageNum,
        Integer pageSize,
        Long merchantId,
        String name,
        String status
) implements Serializable {

    private static final long serialVersionUID = 1L;
}
