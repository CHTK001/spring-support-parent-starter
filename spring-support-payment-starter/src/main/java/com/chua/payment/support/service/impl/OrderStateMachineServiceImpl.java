package com.chua.payment.support.service.impl;

import com.chua.payment.support.entity.PaymentOrder;
import com.chua.payment.support.enums.OrderEvent;
import com.chua.payment.support.enums.OrderState;
import com.chua.payment.support.mapper.PaymentOrderMapper;
import com.chua.payment.support.service.OrderStateLogService;
import com.chua.payment.support.service.OrderStateMachineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.config.StateMachineFactory;
import org.springframework.statemachine.persist.StateMachinePersister;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 订单状态机服务实现类
 *
 * @author CH
 * @since 2026-03-18
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderStateMachineServiceImpl implements OrderStateMachineService {

    private final StateMachineFactory<OrderState, OrderEvent> stateMachineFactory;
    private final PaymentOrderMapper paymentOrderMapper;
    private final OrderStateLogService orderStateLogService;

    @Override
    public void createStateMachine(Long orderId) {
        // 状态机在sendEvent时动态创建，此方法保留用于接口兼容
        log.debug("状态机将在首次事件发送时创建: orderId={}", orderId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean sendEvent(Long orderId, OrderEvent event, String operator) {
        try {
            // 查询订单
            PaymentOrder order = paymentOrderMapper.selectById(orderId);
            if (order == null) {
                log.error("订单不存在: orderId={}", orderId);
                return false;
            }

            // 创建状态机
            StateMachine<OrderState, OrderEvent> stateMachine = stateMachineFactory.getStateMachine();
            
            // 获取当前状态
            OrderState currentState = OrderState.valueOf(order.getStatus());
            OrderState fromState = currentState;
            
            // 启动状态机并设置初始状态
            stateMachine.start();
            stateMachine.getStateMachineAccessor()
                    .doWithAllRegions(accessor -> {
                        accessor.resetStateMachine(new org.springframework.statemachine.support.DefaultStateMachineContext<>(
                                currentState, null, null, null));
                    });

            // 发送事件
            Message<OrderEvent> message = MessageBuilder
                    .withPayload(event)
                    .setHeader("orderId", orderId)
                    .setHeader("operator", operator)
                    .build();
            
            boolean result = stateMachine.sendEvent(message);
            
            if (result) {
                // 获取新状态
                OrderState newState = stateMachine.getState().getId();
                
                // 更新订单状态
                order.setStatus(newState.name());
                paymentOrderMapper.updateById(order);
                
                // 记录状态流转日志
                orderStateLogService.log(orderId, fromState, newState, event, operator, 
                        String.format("状态转换: %s → %s", fromState.getDescription(), newState.getDescription()));
                
                log.info("订单状态机事件处理成功: orderId={}, event={}, {} → {}", 
                        orderId, event, fromState, newState);
            } else {
                log.warn("订单状态机事件处理失败: orderId={}, event={}, currentState={}", 
                        orderId, event, currentState);
            }
            
            // 停止状态机
            stateMachine.stop();
            
            return result;
        } catch (Exception e) {
            log.error("订单状态机事件处理异常: orderId={}, event={}", orderId, event, e);
            throw new RuntimeException("状态机事件处理失败", e);
        }
    }

    @Override
    public OrderState getCurrentState(Long orderId) {
        PaymentOrder order = paymentOrderMapper.selectById(orderId);
        return order != null ? OrderState.valueOf(order.getStatus()) : null;
    }
}
