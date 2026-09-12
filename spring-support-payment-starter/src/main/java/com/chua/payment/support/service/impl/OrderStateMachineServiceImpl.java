package com.chua.payment.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.chua.payment.support.entity.PaymentOrder;
import com.chua.payment.support.enums.OrderEvent;
import com.chua.payment.support.enums.OrderState;
import com.chua.payment.support.enums.OrderTransitionResult;
import com.chua.payment.support.mapper.PaymentOrderMapper;
import com.chua.payment.support.service.OrderStateLogService;
import com.chua.payment.support.service.OrderStateMachineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.config.StateMachineFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 订单状态机服务实现，基于Spring Statemachine管理订单生命周期状态流转。
 *
 * @author CH
 * @since 4.0.0.50
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderStateMachineServiceImpl implements OrderStateMachineService {

    private final StateMachineFactory<OrderState, OrderEvent> stateMachineFactory; // 状态machine工厂
    /** 薪酬排序映射器 */
    private final PaymentOrderMapper paymentOrderMapper;
    /** 排序状态日志服务 */
    private final OrderStateLogService orderStateLogService;

    @Override
    /** 创建状态machine */
    public void createStateMachine(Long orderId) {
        log.debug("订单状态机初始化完成: orderId={}", orderId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 发送事件
     *
     * @param orderId 订单标识
     * @param event 事件
     * @param operator 操作符
     * @return 发送事件的结果
     */
    public OrderTransitionResult sendEvent(Long orderId, OrderEvent event, String operator) {
        PaymentOrder order = paymentOrderMapper.selectById(orderId);
        if (order == null) {
            return OrderTransitionResult.REJECTED;
        }

        OrderState fromState = OrderState.valueOf(order.getStatus());
        StateMachine<OrderState, OrderEvent> stateMachine = stateMachineFactory.getStateMachine();
        stateMachine.start();
        stateMachine.getStateMachineAccessor().doWithAllRegions(accessor ->
                accessor.resetStateMachine(new org.springframework.statemachine.support.DefaultStateMachineContext<>(
                        fromState, null, null, null))
        );

        Message<OrderEvent> message = MessageBuilder.withPayload(event)
                .setHeader("orderId", orderId)
                .setHeader("operator", operator)
                .build();

        boolean accepted = stateMachine.sendEvent(message);
        if (!accepted) {
            stateMachine.stop();
            PaymentOrder latest = paymentOrderMapper.selectById(orderId);
            if (isDuplicateTargetState(event, latest)) {
                log.info("订单状态已由其他节点满足事件结果: orderId={}, event={}, state={}", orderId, event, latest.getStatus());
                return OrderTransitionResult.DUPLICATED;
            }
            log.warn("订单状态机拒绝事件: orderId={}, state={}, event={}", orderId, fromState, event);
            return OrderTransitionResult.REJECTED;
        }

        OrderState toState = stateMachine.getState().getId();
        try {
            int updated = paymentOrderMapper.update(null, new LambdaUpdateWrapper<PaymentOrder>()
                    .eq(PaymentOrder::getId, orderId)
                    .eq(PaymentOrder::getStatus, fromState.name())
                    .and(wrapper -> wrapper.isNull(PaymentOrder::getDeleted).or().eq(PaymentOrder::getDeleted, 0))
                    .set(PaymentOrder::getStatus, toState.name()));
            if (updated == 0) {
                PaymentOrder latest = paymentOrderMapper.selectById(orderId);
                if (latest != null && toState.name().equals(latest.getStatus())) {
                    log.info("订单状态已由其他节点完成转换: orderId={}, event={}, target={}", orderId, event, toState);
                    return OrderTransitionResult.DUPLICATED;
                }
                log.warn("订单状态并发更新失败: orderId={}, fromState={}, event={}", orderId, fromState, event);
                return OrderTransitionResult.REJECTED;
            }

            orderStateLogService.log(new com.chua.payment.support.dto.OrderStateLogRequest(
                    orderId, fromState.name(), toState.name(), event.name(), operator,
                    String.format("状态转换: %s -> %s", fromState.getDescription(), toState.getDescription())));
            return OrderTransitionResult.APPLIED;
        } finally {
            stateMachine.stop();
        }
    }

    @Override
    /** 获取当前状态 */
    public OrderState getCurrentState(Long orderId) {
        PaymentOrder order = paymentOrderMapper.selectById(orderId);
        return order != null ? OrderState.valueOf(order.getStatus()) : null;
    }

    /**
     * 是否重复Target状态
     *
     * @param event 事件
     * @param order 订单
     * @return 是否重复Target状态的结果
     */
    private boolean isDuplicateTargetState(OrderEvent event, PaymentOrder order) {
        if (order == null) {
            return false;
        }
        String status = order.getStatus();
        return switch (event) {
            case PAY -> OrderState.PAYING.name().equals(status)
                    || OrderState.PAID.name().equals(status)
                    || OrderState.COMPLETED.name().equals(status)
                    || OrderState.REFUNDING.name().equals(status)
                    || OrderState.REFUNDED.name().equals(status);
            case PAY_SUCCESS -> OrderState.PAID.name().equals(status)
                    || OrderState.COMPLETED.name().equals(status)
                    || OrderState.REFUNDING.name().equals(status)
                    || OrderState.REFUNDED.name().equals(status);
            case PAY_FAIL -> OrderState.FAILED.name().equals(status);
            case COMPLETE -> OrderState.COMPLETED.name().equals(status);
            case CANCEL -> OrderState.CANCELLED.name().equals(status);
            case REFUND -> OrderState.REFUNDING.name().equals(status) || OrderState.REFUNDED.name().equals(status);
            case REFUND_SUCCESS -> OrderState.REFUNDED.name().equals(status);
            case REFUND_FAIL -> OrderState.PAID.name().equals(status) || OrderState.COMPLETED.name().equals(status);
            case TIMEOUT -> OrderState.TIMEOUT.name().equals(status);
        };
    }
}

