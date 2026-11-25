package com.chua.payment.support.vo;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 优惠券模板视图对象
 *
 * @param id             主键ID
 * @param merchantId     商户ID
 * @param name           模板名称
 * @param type           类型: CASH/DEDUCT
 * @param typeDesc       类型描述
 * @param amount         优惠金额
 * @param minOrderAmount 最低订单金额门槛
 * @param totalCount     发放总量(0=不限)
 * @param issuedCount    已发放数量
 * @param perUserLimit   每人限领数量
 * @param validDays      领取后有效天数
 * @param validStartAt   固定有效期开始
 * @param validEndAt     固定有效期结束
 * @param status         状态: ENABLED/DISABLED
 * @param remark         备注
 * @param createdAt      创建时间
 * @param updatedAt      更新时间
 * @since 4.0.0
 * @author CH
 */
public record CouponTemplateVO(
        Long id,
        Long merchantId,
        String name,
        String type,
        String typeDesc,
        BigDecimal amount,
        BigDecimal minOrderAmount,
        Integer totalCount,
        Integer issuedCount,
        Integer perUserLimit,
        Integer validDays,
        LocalDateTime validStartAt,
        LocalDateTime validEndAt,
        String status,
        String remark,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) implements Serializable {

    private static final long serialVersionUID = 1L;
}
