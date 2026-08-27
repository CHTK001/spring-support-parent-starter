package com.chua.payment.support.dto;

/**
 * 钱包订单分页查询请求参数对象。
 *
 * @param pageNum     页码
 * @param pageSize    每页大小
 * @param merchantId  商户ID
 * @param userId      用户ID
 * @param orderType   订单类型（可选）
 * @param status      状态（可选）
 * @author CH
 * @since 4.0.0.50
 */
public record WalletOrderQueryRequest(
        int pageNum,
        int pageSize,
        Long merchantId,
        Long userId,
        String orderType,
        String status
) {
}
