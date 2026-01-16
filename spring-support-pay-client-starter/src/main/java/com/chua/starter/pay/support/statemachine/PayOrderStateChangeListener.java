package com.chua.starter.pay.support.statemachine;

import com.chua.starter.pay.support.enums.PayOrderEvent;
import com.chua.starter.pay.support.enums.PayOrderStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.statemachine.StateContext;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.listener.StateMachineListenerAdapter;
import org.springframework.statemachine.state.State;
import org.springframework.statemachine.transition.Transition;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 订单状态机监听器
 * <p>
 * 监听状态转换过程，记录日志、执行业务逻辑
 *
 * @author CH
 * @version 1.0
 * @since 2025/10/24
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PayOrderStateChangeListener extends StateMachineListenerAdapter<PayOrderStatus, PayOrderEvent> {

    /**
     * 状态转换开始时触发
     *
     * @param transition 转换信息
     */
    @Override
    public void transitionStarted(Transition<PayOrderStatus, PayOrderEvent> transition) {
        if (transition.getSource() != null && transition.getTarget() != null) {
            PayOrderStatus source = transition.getSource().getId();
            PayOrderStatus target = transition.getTarget().getId();
            PayOrderEvent event = Optional.ofNullable(transition.getTrigger())
                    .map(trigger -> trigger.getEvent())
                    .orElse(null);

            log.info("订单状态转换开始: {} -> {}, 触发事件: {}", 
                    source.getName(), 
                    target.getName(), 
                    event);
        }
    }

    /**
     * 状态转换完成时触发
     *
     * @param transition 转换信息
     */
    @Override
    public void transitionEnded(Transition<PayOrderStatus, PayOrderEvent> transition) {
        if (transition.getSource() != null && transition.getTarget() != null) {
            PayOrderStatus source = transition.getSource().getId();
            PayOrderStatus target = transition.getTarget().getId();
            PayOrderEvent event = Optional.ofNullable(transition.getTrigger())
                    .map(trigger -> trigger.getEvent())
                    .orElse(null);

            log.info("订单状态转换完成: {} -> {}, 触发事件: {}", 
                    source.getName(), 
                    target.getName(), 
                    event);
        }
    }

    /**
     * 状态改变时触发
     *
     * @param from 原状态
     * @param to 目标状态
     */
    @Override
    public void stateChanged(State<PayOrderStatus, PayOrderEvent> from, State<PayOrderStatus, PayOrderEvent> to) {
        if (from != null && to != null) {
            log.info("订单状态已变更: {} -> {}", 
                    from.getId().getName(), 
                    to.getId().getName());
        }
    }

    /**
     * 状态进入时触发
     *
     * @param state 状态
     */
    @Override
    public void stateEntered(State<PayOrderStatus, PayOrderEvent> state) {
        log.debug("订单进入状态: {}", state.getId().getName());
    }

    /**
     * 状态退出时触发
     *
     * @param state 状态
     */
    @Override
    public void stateExited(State<PayOrderStatus, PayOrderEvent> state) {
        log.debug("订单退出状态: {}", state.getId().getName());
    }

    /**
     * 事件未接受时触发（状态转换不合法）
     *
     * @param event 事件
     */
    @Override
    public void eventNotAccepted(Message<PayOrderEvent> event) {
        log.warn("订单状态转换事件未被接受: {}", event.getPayload());
    }

    /**
     * 状态机启动时触发
     *
     * @param stateMachine 状态机
     */
    @Override
    public void stateMachineStarted(StateMachine<PayOrderStatus, PayOrderEvent> stateMachine) {
        log.info("订单状态机已启动");
    }

    /**
     * 状态机停止时触发
     *
     * @param stateMachine 状态机
     */
    @Override
    public void stateMachineStopped(StateMachine<PayOrderStatus, PayOrderEvent> stateMachine) {
        log.info("订单状态机已停止");
    }

    /**
     * 状态机错误时触发
     *
     * @param stateMachine 状态机
     * @param exception 异常信息
     */
    @Override
    public void stateMachineError(StateMachine<PayOrderStatus, PayOrderEvent> stateMachine, Exception exception) {
        log.error("订单状态机发生错误", exception);
    }
}

