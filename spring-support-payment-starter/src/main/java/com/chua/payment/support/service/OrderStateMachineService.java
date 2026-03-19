package com.chua.payment.support.service;

import com.chua.payment.support.enums.OrderEvent;
import com.chua.payment.support.enums.OrderState;

/**
 * 订单状态机服务接口
 *
 * @author CH
 * @since 2026-03-18
 */
public interface OrderStateMachineService {

    /**
     * 创建状态机
     *
     * @param orderId 订单ID
     */
    void createStateMachine(Long orderId);

    /**
     * 发送状态机事件
     *
     * @param orderId   订单ID
     * @param event     事件
     * @param operator  操作人
     * @return 是否成功
     */
    boolean sendEvent(Long orderId, OrderEvent event, String operator);

    /**
     * 获取订单当前状态
     *
     * @param orderId 订单ID
     * @return 当前状态
     */
    OrderState getCurrentState(Long orderId);
}
