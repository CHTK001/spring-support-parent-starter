package com.chua.payment.support.dto;

import java.io.Serializable;
import java.util.List;

/**
 * 优惠券发放请求参数
 *
 * @param merchantId 商户ID
 * @param templateId 模板ID
 * @param userIds    发放用户ID列表
 * @param operator   操作人
 * @param remark     备注
 * @since 4.0.0
 * @author CH
 */
public record CouponIssueDTO(
        Long merchantId,
        Long templateId,
        List<Long> userIds,
        String operator,
        String remark
) implements Serializable {

    private static final long serialVersionUID = 1L;
}
