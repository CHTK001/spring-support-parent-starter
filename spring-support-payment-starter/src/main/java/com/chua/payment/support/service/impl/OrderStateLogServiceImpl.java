package com.chua.payment.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.payment.support.entity.OrderStateLog;
import com.chua.payment.support.enums.OrderEvent;
import com.chua.payment.support.enums.OrderState;
import com.chua.payment.support.mapper.OrderStateLogMapper;
import com.chua.payment.support.service.OrderStateLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 订单状态流转日志服务实现类
 *
 * @author CH
 * @since 2026-03-18
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderStateLogServiceImpl implements OrderStateLogService {

    private final OrderStateLogMapper orderStateLogMapper;

    @Override
    public void log(Long orderId, OrderState fromState, OrderState toState, OrderEvent event, String operator, String remark) {
        OrderStateLog stateLog = new OrderStateLog();
        stateLog.setOrderId(orderId);
        stateLog.setFromState(fromState != null ? fromState.name() : null);
        stateLog.setToState(toState.name());
        stateLog.setEvent(event.name());
        stateLog.setOperator(operator);
        stateLog.setRemark(remark);
        
        orderStateLogMapper.insert(stateLog);
        log.info("订单状态流转日志记录成功: orderId={}, {} → {}, event={}", 
                orderId, fromState, toState, event);
    }

    @Override
    public List<OrderStateLog> listByOrderId(Long orderId) {
        LambdaQueryWrapper<OrderStateLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrderStateLog::getOrderId, orderId)
                .orderByAsc(OrderStateLog::getCreatedAt);
        return orderStateLogMapper.selectList(wrapper);
    }
}
