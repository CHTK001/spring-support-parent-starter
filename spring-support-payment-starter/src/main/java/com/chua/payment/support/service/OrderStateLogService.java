package com.chua.payment.support.service;

import com.chua.payment.support.entity.OrderStateLog;
import com.chua.payment.support.enums.OrderState;

import java.util.List;

/**
 * 订单状态流转日志服务接口
 */
public interface OrderStateLogService {

    void log(Long orderId, OrderState fromState, OrderState toState, String event, String operator, String remark);

    List<OrderStateLog> listByOrderId(Long orderId);
}
