package com.chua.payment.support.service;

import com.chua.payment.support.dto.OrderStateLogRequest;
import com.chua.payment.support.entity.OrderStateLog;

import java.util.List;

/**
 * 订单状态流转日志服务接口，定义日志记录与按订单查询契约。
 *
 * @author CH
 * @since 4.0.0.50
 */
public interface OrderStateLogService {

    /**
     * 记录状态流转日志。
     * @param request 请求参数
     */
    void log(OrderStateLogRequest request);

    /**
     * 按订单标识查询日志列表。
     * @param orderId 订单标识
     * @return 按订单标识查询到的日志列表
     */
    List<OrderStateLog> listByOrderId(Long orderId);
}
