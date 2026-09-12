package com.chua.payment.support.dto;

/**
* 订单状态日志记录请求参数对象。
*
* @param orderId   订单标识
* @param fromState 原状态
* @param toState   目标状态
* @param event     事件描述
* @param operator  操作人
* @param remark    备注
* @author CH
* @since 4.0.0.50
 */
public record OrderStateLogRequest(
        Long orderId,
        String fromState,
        String toState,
        String event,
        String operator,
        String remark
) {
}
