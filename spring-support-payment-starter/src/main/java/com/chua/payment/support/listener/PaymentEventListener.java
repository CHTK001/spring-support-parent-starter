package com.chua.payment.support.listener;

import com.chua.payment.support.event.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 支付事件监听器
 * 监听支付相关事件并记录日志
 *
 * 注意：事件已通过@EventToQueue自动发送到队列
 * 如需从队列接收事件，请使用@QueueEventListener注解
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "plugin.payment.event", name = "log-enabled", havingValue = "true", matchIfMissing = true)
public class PaymentEventListener {

    /**
     * 监听订单创建事件
     */
    @org.springframework.context.event.EventListener
    public void handleOrderCreated(OrderCreatedEvent event) {
        log.info("订单创建事件: orderNo={}, userId={}, amount={}",
            event.getOrderNo(), event.getUserId(), event.getOrderAmount());
    }

    /**
     * 监听支付成功事件
     */
    @org.springframework.context.event.EventListener
    public void handlePaymentSuccess(PaymentSuccessEvent event) {
        log.info("支付成功事件: orderNo={}, userId={}, paidAmount={}",
            event.getOrderNo(), event.getUserId(), event.getPaidAmount());
    }

    /**
     * 监听退款成功事件
     */
    @org.springframework.context.event.EventListener
    public void handleRefundSuccess(RefundSuccessEvent event) {
        log.info("退款成功事件: orderNo={}, refundNo={}, refundAmount={}",
            event.getOrderNo(), event.getRefundNo(), event.getRefundAmount());
    }

    /**
     * 监听支付回调接收事件
     */
    @org.springframework.context.event.EventListener
    public void handlePaymentNotifyReceived(PaymentNotifyReceivedEvent event) {
        log.info("支付回调接收事件: notifyType={}, orderNo={}, refundNo={}, signVerified={}",
            event.getNotifyType(), event.getOrderNo(), event.getRefundNo(), event.getSignVerified());
    }

    /**
     * 监听支付回调失败事件
     */
    @org.springframework.context.event.EventListener
    public void handlePaymentNotifyFailed(PaymentNotifyFailedEvent event) {
        log.error("支付回调失败事件: notifyType={}, orderNo={}, refundNo={}, errorType={}, errorMessage={}",
            event.getNotifyType(), event.getOrderNo(), event.getRefundNo(),
            event.getErrorType(), event.getErrorMessage());
    }
}
