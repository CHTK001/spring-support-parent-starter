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
    * 日志。
    * @param request 请求
    */
    void log(OrderStateLogRequest request);

    /**
    * 列表by订单标识。
    * @param orderId 订单标识
    * @return 列表by订单id的结果
    */
    List<OrderStateLog> listByOrderId(Long orderId);
}

