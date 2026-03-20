package com.chua.payment.support.statemachine;

import com.chua.payment.support.enums.OrderEvent;
import com.chua.payment.support.enums.OrderState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.statemachine.config.EnableStateMachineFactory;
import org.springframework.statemachine.config.EnumStateMachineConfigurerAdapter;
import org.springframework.statemachine.config.builders.StateMachineStateConfigurer;
import org.springframework.statemachine.config.builders.StateMachineTransitionConfigurer;

import java.util.EnumSet;

/**
 * 订单状态机配置类
 *
 * @author CH
 * @since 2026-03-18
 */
@Slf4j
@Configuration
@EnableStateMachineFactory
public class OrderStateMachineConfig extends EnumStateMachineConfigurerAdapter<OrderState, OrderEvent> {

    /**
     * 配置状态
     */
    @Override
    public void configure(StateMachineStateConfigurer<OrderState, OrderEvent> states) throws Exception {
        states
                .withStates()
                // 初始状态
                .initial(OrderState.PENDING)
                // 所有状态
                .states(EnumSet.allOf(OrderState.class));
    }

    /**
     * 配置状态转换
     */
    @Override
    public void configure(StateMachineTransitionConfigurer<OrderState, OrderEvent> transitions) throws Exception {
        transitions
                // 待支付 → 支付中
                .withExternal()
                .source(OrderState.PENDING).target(OrderState.PAYING)
                .event(OrderEvent.PAY)
                .and()
                // 支付中 → 支付成功
                .withExternal()
                .source(OrderState.PAYING).target(OrderState.PAID)
                .event(OrderEvent.PAY_SUCCESS)
                .and()
                // 支付中 → 支付失败
                .withExternal()
                .source(OrderState.PAYING).target(OrderState.FAILED)
                .event(OrderEvent.PAY_FAIL)
                .and()
                // 支付成功 → 已完成
                .withExternal()
                .source(OrderState.PAID).target(OrderState.COMPLETED)
                .event(OrderEvent.COMPLETE)
                .and()
                // 支付成功 → 退款中
                .withExternal()
                .source(OrderState.PAID).target(OrderState.REFUNDING)
                .event(OrderEvent.REFUND)
                .and()
                // 已完成 → 退款中
                .withExternal()
                .source(OrderState.COMPLETED).target(OrderState.REFUNDING)
                .event(OrderEvent.REFUND)
                .and()
                // 退款中 → 已退款
                .withExternal()
                .source(OrderState.REFUNDING).target(OrderState.REFUNDED)
                .event(OrderEvent.REFUND_SUCCESS)
                .and()
                // 退款中 → 支付成功（退款失败回退）
                .withExternal()
                .source(OrderState.REFUNDING).target(OrderState.PAID)
                .event(OrderEvent.REFUND_FAIL)
                .and()
                // 待支付 → 已取消
                .withExternal()
                .source(OrderState.PENDING).target(OrderState.CANCELLED)
                .event(OrderEvent.CANCEL)
                .and()
                // 支付中 → 已取消
                .withExternal()
                .source(OrderState.PAYING).target(OrderState.CANCELLED)
                .event(OrderEvent.CANCEL);
    }
}
