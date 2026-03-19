package com.chua.payment.support.service;

import com.chua.payment.support.entity.OrderStateLog;
import com.chua.payment.support.enums.OrderEvent;
import com.chua.payment.support.enums.OrderState;

import java.util.List;

/**
 * 订单状态流转日志服务接口
 *
 * @author CH
 * @since 2026-03-18
 */
public interface OrderStateLogService {

    /**
     * 记录状态流转日志
     *
     * @param orderId   订单ID
     * @param fromState 原状态
     * @param toState   目标状态
     * @param event     触发事件
     * @param operator  操作人
     * @param remark    备注
     */
    void log(Long orderId, OrderState fromState, OrderState toState, OrderEvent event, String operator, String remark);

    /**
     * 查询订单状态流转日志
     *
     * @param orderId 订单ID
     * @return 状态流转日志列表
     */
    List<OrderStateLog> listByOrderId(Long orderId);
}
