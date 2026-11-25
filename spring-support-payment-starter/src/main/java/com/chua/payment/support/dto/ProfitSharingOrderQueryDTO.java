package com.chua.payment.support.dto;

import java.io.Serializable;

/**
 * 分账订单分页查询参数
 *
 * @param pageNum        页码
 * @param pageSize       每页条数
 * @param merchantId     商户ID
 * @param sharingOrderNo 分账单号
 * @param orderNo        支付订单号
 * @param status         状态: PENDING/PROCESSING/SUCCEEDED/FAILED/RETURNED
 * @author CH
 * @since 4.0.0
 */
public record ProfitSharingOrderQueryDTO(
        Integer pageNum,
        Integer pageSize,
        Long merchantId,
        String sharingOrderNo,
        String orderNo,
        String status
) implements Serializable {

    private static final long serialVersionUID = 1L;
}
