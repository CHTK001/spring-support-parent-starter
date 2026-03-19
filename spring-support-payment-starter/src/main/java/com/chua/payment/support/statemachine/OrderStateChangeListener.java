package com.chua.payment.support.statemachine;

import com.chua.payment.support.enums.OrderEvent;
import com.chua.payment.support.enums.OrderState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.statemachine.annotation.OnTransition;
import org.springframework.statemachine.annotation.WithStateMachine;
import org.springframework.stereotype.Component;

/**
 * 订单状态转换监听器
 *
 * @author CH
 * @since 2026-03-18
 */
@Slf4j
@Component
@WithStateMachine
public class OrderStateChangeListener {

    /**
     * 待支付 → 支付中
     */
    @OnTransition(source = "PENDING", target = "PAYING")
    public void pendingToPaying(Message<OrderEvent> message) {
        log.info("订单状态转换: PENDING → PAYING, 事件: {}", message.getPayload());
    }

    /**
     * 支付中 → 支付成功
     */
    @OnTransition(source = "PAYING", target = "PAID")
    public void payingToPaid(Message<OrderEvent> message) {
        log.info("订单状态转换: PAYING → PAID, 事件: {}", message.getPayload());
    }

    /**
     * 支付中 → 支付失败
     */
    @OnTransition(source = "PAYING", target = "FAILED")
    public void payingToFailed(Message<OrderEvent> message) {
        log.info("订单状态转换: PAYING → FAILED, 事件: {}", message.getPayload());
    }

    /**
     * 支付成功 → 已完成
     */
    @OnTransition(source = "PAID", target = "COMPLETED")
    public void paidToCompleted(Message<OrderEvent> message) {
        log.info("订单状态转换: PAID → COMPLETED, 事件: {}", message.getPayload());
    }

    /**
     * 支付成功/已完成 → 退款中
     */
    @OnTransition(target = "REFUNDING")
    public void toRefunding(Message<OrderEvent> message) {
        log.info("订单状态转换: → REFUNDING, 事件: {}", message.getPayload());
    }

    /**
     * 退款中 → 已退款
     */
    @OnTransition(source = "REFUNDING", target = "REFUNDED")
    public void refundingToRefunded(Message<OrderEvent> message) {
        log.info("订单状态转换: REFUNDING → REFUNDED, 事件: {}", message.getPayload());
    }

    /**
     * 待支付 → 已取消
     */
    @OnTransition(source = "PENDING", target = "CANCELLED")
    public void pendingToCancelled(Message<OrderEvent> message) {
        log.info("订单状态转换: PENDING → CANCELLED, 事件: {}", message.getPayload());
    }
}
