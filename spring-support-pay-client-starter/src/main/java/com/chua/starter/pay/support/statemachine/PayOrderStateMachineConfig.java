package com.chua.starter.pay.support.statemachine;

import com.chua.starter.pay.support.enums.PayOrderEvent;
import com.chua.starter.pay.support.enums.PayOrderStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.statemachine.config.EnableStateMachineFactory;
import org.springframework.statemachine.config.StateMachineConfigurerAdapter;
import org.springframework.statemachine.config.builders.StateMachineStateConfigurer;
import org.springframework.statemachine.config.builders.StateMachineTransitionConfigurer;

import java.util.EnumSet;

/**
 * 订单状态机配置类
 * <p>
 * 配置订单状态流转规则：
 * 1. 定义所有可能的订单状态
 * 2. 定义状态之间的转换关系
 * 3. 定义转换触发的事件
 *
 * @author CH
 * @version 1.0
 * @since 2025/10/24
 */
@Slf4j
@Configuration
@EnableStateMachineFactory(name = "payOrderStateMachineFactory")
@RequiredArgsConstructor
public class PayOrderStateMachineConfig extends StateMachineConfigurerAdapter<PayOrderStatus, PayOrderEvent> {

    private final PayOrderStateChangeListener stateChangeListener;

    /**
     * 配置状态
     * <p>
     * 定义初始状态、所有可能的状态
     *
     * @param states 状态配置器
     * @throws Exception 配置异常
     */
    @Override
    public void configure(StateMachineStateConfigurer<PayOrderStatus, PayOrderEvent> states) throws Exception {
        states
                .withStates()
                // 初始状态：创建
                .initial(PayOrderStatus.PAY_CREATE)
                // 所有可能的状态
                .states(EnumSet.allOf(PayOrderStatus.class))
                // 结束状态
                .end(PayOrderStatus.PAY_SUCCESS)
                .end(PayOrderStatus.PAY_TIMEOUT)
                .end(PayOrderStatus.PAY_CANCEL_SUCCESS)
                .end(PayOrderStatus.PAY_CLOSE_SUCCESS)
                .end(PayOrderStatus.PAY_REFUND_SUCCESS);
    }

    /**
     * 配置状态转换
     * <p>
     * 定义状态之间的合法转换路径和触发事件
     *
     * @param transitions 转换配置器
     * @throws Exception 配置异常
     */
    @Override
    public void configure(StateMachineTransitionConfigurer<PayOrderStatus, PayOrderEvent> transitions) throws Exception {
        transitions
                // ========== 创建订单流程 ==========
                // 创建 -> 创建失败
                .withExternal()
                .source(PayOrderStatus.PAY_CREATE)
                .target(PayOrderStatus.PAY_CREATE_FAILED)
                .event(PayOrderEvent.CREATE_FAILED)
                .and()

                // 创建 -> 待支付
                .withExternal()
                .source(PayOrderStatus.PAY_CREATE)
                .target(PayOrderStatus.PAY_WAITING)
                .event(PayOrderEvent.WAIT_PAY)
                .and()

                // ========== 支付流程 ==========
                // 待支付 -> 支付中
                .withExternal()
                .source(PayOrderStatus.PAY_WAITING)
                .target(PayOrderStatus.PAY_PAYING)
                .event(PayOrderEvent.START_PAY)
                .and()

                // 支付中 -> 支付成功
                .withExternal()
                .source(PayOrderStatus.PAY_PAYING)
                .target(PayOrderStatus.PAY_SUCCESS)
                .event(PayOrderEvent.PAY_SUCCESS)
                .and()

                // 待支付 -> 支付成功（直接支付成功，如钱包支付）
                .withExternal()
                .source(PayOrderStatus.PAY_WAITING)
                .target(PayOrderStatus.PAY_SUCCESS)
                .event(PayOrderEvent.PAY_SUCCESS)
                .and()

                // ========== 超时流程 ==========
                // 创建 -> 超时
                .withExternal()
                .source(PayOrderStatus.PAY_CREATE)
                .target(PayOrderStatus.PAY_TIMEOUT)
                .event(PayOrderEvent.TIMEOUT)
                .and()

                // 待支付 -> 超时
                .withExternal()
                .source(PayOrderStatus.PAY_WAITING)
                .target(PayOrderStatus.PAY_TIMEOUT)
                .event(PayOrderEvent.TIMEOUT)
                .and()

                // ========== 取消流程 ==========
                // 创建 -> 取消
                .withExternal()
                .source(PayOrderStatus.PAY_CREATE)
                .target(PayOrderStatus.PAY_CANCEL_SUCCESS)
                .event(PayOrderEvent.CANCEL)
                .and()

                // 待支付 -> 取消
                .withExternal()
                .source(PayOrderStatus.PAY_WAITING)
                .target(PayOrderStatus.PAY_CANCEL_SUCCESS)
                .event(PayOrderEvent.CANCEL)
                .and()

                // ========== 关闭流程 ==========
                // 创建 -> 关闭
                .withExternal()
                .source(PayOrderStatus.PAY_CREATE)
                .target(PayOrderStatus.PAY_CLOSE_SUCCESS)
                .event(PayOrderEvent.CLOSE)
                .and()

                // 待支付 -> 关闭
                .withExternal()
                .source(PayOrderStatus.PAY_WAITING)
                .target(PayOrderStatus.PAY_CLOSE_SUCCESS)
                .event(PayOrderEvent.CLOSE)
                .and()

                // 支付中 -> 关闭
                .withExternal()
                .source(PayOrderStatus.PAY_PAYING)
                .target(PayOrderStatus.PAY_CLOSE_SUCCESS)
                .event(PayOrderEvent.CLOSE)
                .and()

                // ========== 退款流程 ==========
                // 支付成功 -> 正在退款
                .withExternal()
                .source(PayOrderStatus.PAY_SUCCESS)
                .target(PayOrderStatus.PAY_REFUND_WAITING)
                .event(PayOrderEvent.REFUND)
                .and()

                // 正在退款 -> 退款成功
                .withExternal()
                .source(PayOrderStatus.PAY_REFUND_WAITING)
                .target(PayOrderStatus.PAY_REFUND_SUCCESS)
                .event(PayOrderEvent.REFUND_SUCCESS)
                .and()

                // 正在退款 -> 部分退款
                .withExternal()
                .source(PayOrderStatus.PAY_REFUND_WAITING)
                .target(PayOrderStatus.PAY_REFUND_PART_SUCCESS)
                .event(PayOrderEvent.REFUND_PART_SUCCESS)
                .and()

                // 部分退款 -> 正在退款（再次申请退款）
                .withExternal()
                .source(PayOrderStatus.PAY_REFUND_PART_SUCCESS)
                .target(PayOrderStatus.PAY_REFUND_WAITING)
                .event(PayOrderEvent.REFUND)
                .and()

                // 部分退款 -> 退款成功（全部退完）
                .withExternal()
                .source(PayOrderStatus.PAY_REFUND_PART_SUCCESS)
                .target(PayOrderStatus.PAY_REFUND_SUCCESS)
                .event(PayOrderEvent.REFUND_SUCCESS)
                .and();
    }
}

