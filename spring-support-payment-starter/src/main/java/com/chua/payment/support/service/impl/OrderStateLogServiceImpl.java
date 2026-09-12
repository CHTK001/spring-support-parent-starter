package com.chua.payment.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.payment.support.dto.OrderStateLogRequest;
import com.chua.payment.support.entity.OrderStateLog;
import com.chua.payment.support.enums.OrderState;
import com.chua.payment.support.mapper.OrderStateLogMapper;
import com.chua.payment.support.service.OrderStateLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
* 订单状态流转日志服务实现，记录订单在各状态间的变更历史。
*
* @author CH
* @since 4.0.0.50
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderStateLogServiceImpl implements OrderStateLogService {

    /** 排序状态日志映射器 */
    private final OrderStateLogMapper orderStateLogMapper;

    /**
    * 记录订单状态流转日志，将状态变更写入数据库。
    *
    * @param request 日志请求参数，包含订单标识、原状态、目标状态、事件描述、操作人和备注
     */
    @Override
    public void log(OrderStateLogRequest request) {
        if (request == null) {
            /**
            * illegal参数异常。
            * @param "订单状态日志参数不能为空" "订单状态日志参数不能为空"
            * @return illegal参数异常的结果
             */
            throw new IllegalArgumentException("订单状态日志参数不能为空");
        }
        OrderStateLog stateLog = new OrderStateLog();
        stateLog.setOrderId(request.orderId());
        stateLog.setFromState(request.fromState());
        stateLog.setToState(request.toState());
        stateLog.setEvent(request.event());
        stateLog.setOperator(request.operator());
        stateLog.setRemark(request.remark());
        orderStateLogMapper.insert(stateLog);
        log.info("订单状态流转日志记录成功: orderId={}, {} -> {}, event={}",
                request.orderId(), request.fromState(), request.toState(), request.event());
    }

    @Override
    /** 列表by订单标识 */
    public List<OrderStateLog> listByOrderId(Long orderId) {
        return orderStateLogMapper.selectList(new LambdaQueryWrapper<OrderStateLog>()
                .eq(OrderStateLog::getOrderId, orderId)
                .orderByAsc(OrderStateLog::getCreatedAt));
    }
}

