package com.chua.payment.support.service;

import com.chua.payment.support.enums.OrderEvent;
import com.chua.payment.support.enums.OrderState;
import com.chua.payment.support.enums.OrderTransitionResult;

/**
 * 订单状态机服务接口
 *
 * @since 2026-03-18
 * @author CH
 */
public interface OrderStateMachineService {

    /**
     * 创建订单状态机
     *
     * @param orderId 订单标识
     */
    void createStateMachine(Long orderId);

    /**
     * 发送状态机事件
     *
     * @param orderId   订单标识
     * @param event     事件
     * @param operator  操作人
     * @return 是否流转成功
     */
    OrderTransitionResult sendEvent(Long orderId, OrderEvent event, String operator);

    /**
     * 获取订单当前状态
     *
     * @param orderId 订单标识
     * @return 当前状态
     */
    OrderState getCurrentState(Long orderId);
}
