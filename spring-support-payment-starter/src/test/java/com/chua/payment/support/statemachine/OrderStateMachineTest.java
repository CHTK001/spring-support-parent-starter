package com.chua.payment.support.statemachine;

import com.chua.payment.support.enums.OrderEvent;
import com.chua.payment.support.enums.OrderState;
import com.chua.payment.support.service.OrderStateMachineService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.statemachine.StateMachine;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 订单状态机单元测试
 *
 * @author CH
 * @since 2026-03-18
 */
@SpringBootTest
class OrderStateMachineTest {

    @Autowired
    private OrderStateMachineService stateMachineService;

    /**
     * 测试正常支付流程：待支付 → 支付中 → 支付成功 → 已完成
     */
    @Test
    void testNormalPaymentFlow() {
        String orderId = "TEST_ORDER_001";
        
        // 创建订单（待支付）
        StateMachine<OrderState, OrderEvent> machine = stateMachineService.createStateMachine(orderId);
        assertEquals(OrderState.PENDING_PAYMENT, machine.getState().getId());
        
        // 发起支付（待支付 → 支付中）
        boolean result1 = stateMachineService.sendEvent(orderId, OrderEvent.PAY);
        assertTrue(result1);
        assertEquals(OrderState.PAYING, machine.getState().getId());
        
        // 支付成功（支付中 → 支付成功）
        boolean result2 = stateMachineService.sendEvent(orderId, OrderEvent.PAY_SUCCESS);
        assertTrue(result2);
        assertEquals(OrderState.PAID, machine.getState().getId());
        
        // 订单完成（支付成功 → 已完成）
        boolean result3 = stateMachineService.sendEvent(orderId, OrderEvent.COMPLETE);
        assertTrue(result3);
        assertEquals(OrderState.COMPLETED, machine.getState().getId());
    }

    /**
     * 测试支付失败流程：待支付 → 支付中 → 支付失败
     */
    @Test
    void testPaymentFailureFlow() {
        String orderId = "TEST_ORDER_002";
        
        // 创建订单（待支付）
        StateMachine<OrderState, OrderEvent> machine = stateMachineService.createStateMachine(orderId);
        assertEquals(OrderState.PENDING_PAYMENT, machine.getState().getId());
        
        // 发起支付（待支付 → 支付中）
        stateMachineService.sendEvent(orderId, OrderEvent.PAY);
        assertEquals(OrderState.PAYING, machine.getState().getId());
        
        // 支付失败（支付中 → 支付失败）
        boolean result = stateMachineService.sendEvent(orderId, OrderEvent.PAY_FAIL);
        assertTrue(result);
        assertEquals(OrderState.FAILED, machine.getState().getId());
    }

    /**
     * 测试订单取消流程：待支付 → 已取消
     */
    @Test
    void testCancelFlow() {
        String orderId = "TEST_ORDER_003";
        
        // 创建订单（待支付）
        StateMachine<OrderState, OrderEvent> machine = stateMachineService.createStateMachine(orderId);
        assertEquals(OrderState.PENDING_PAYMENT, machine.getState().getId());
        
        // 取消订单（待支付 → 已取消）
        boolean result = stateMachineService.sendEvent(orderId, OrderEvent.CANCEL);
        assertTrue(result);
        assertEquals(OrderState.CANCELLED, machine.getState().getId());
    }

    /**
     * 测试退款流程：支付成功 → 退款中 → 已退款
     */
    @Test
    void testRefundFlow() {
        String orderId = "TEST_ORDER_004";
        
        // 创建订单并完成支付
        StateMachine<OrderState, OrderEvent> machine = stateMachineService.createStateMachine(orderId);
        stateMachineService.sendEvent(orderId, OrderEvent.PAY);
        stateMachineService.sendEvent(orderId, OrderEvent.PAY_SUCCESS);
        assertEquals(OrderState.PAID, machine.getState().getId());
        
        // 申请退款（支付成功 → 退款中）
        boolean result1 = stateMachineService.sendEvent(orderId, OrderEvent.REFUND);
        assertTrue(result1);
        assertEquals(OrderState.REFUNDING, machine.getState().getId());
        
        // 退款成功（退款中 → 已退款）
        boolean result2 = stateMachineService.sendEvent(orderId, OrderEvent.REFUND_SUCCESS);
        assertTrue(result2);
        assertEquals(OrderState.REFUNDED, machine.getState().getId());
    }

    /**
     * 测试非法状态转换：待支付 → 支付成功（跳过支付中状态）
     */
    @Test
    void testInvalidTransition() {
        String orderId = "TEST_ORDER_005";
        
        // 创建订单（待支付）
        StateMachine<OrderState, OrderEvent> machine = stateMachineService.createStateMachine(orderId);
        assertEquals(OrderState.PENDING_PAYMENT, machine.getState().getId());
        
        // 尝试直接支付成功（应该失败）
        boolean result = stateMachineService.sendEvent(orderId, OrderEvent.PAY_SUCCESS);
        assertFalse(result);
        assertEquals(OrderState.PENDING_PAYMENT, machine.getState().getId());
    }

    /**
     * 测试状态机获取
     */
    @Test
    void testGetStateMachine() {
        String orderId = "TEST_ORDER_006";
        
        // 创建状态机
        stateMachineService.createStateMachine(orderId);
        
        // 获取状态机
        StateMachine<OrderState, OrderEvent> machine = stateMachineService.getStateMachine(orderId);
        assertNotNull(machine);
        assertEquals(OrderState.PENDING_PAYMENT, machine.getState().getId());
    }

    /**
     * 测试状态机删除
     */
    @Test
    void testRemoveStateMachine() {
        String orderId = "TEST_ORDER_007";
        
        // 创建状态机
        stateMachineService.createStateMachine(orderId);
        assertNotNull(stateMachineService.getStateMachine(orderId));
        
        // 删除状态机
        stateMachineService.removeStateMachine(orderId);
        assertNull(stateMachineService.getStateMachine(orderId));
    }

    /**
     * 测试获取当前状态
     */
    @Test
    void testGetCurrentState() {
        String orderId = "TEST_ORDER_008";
        
        // 创建订单
        stateMachineService.createStateMachine(orderId);
        assertEquals(OrderState.PENDING_PAYMENT, stateMachineService.getCurrentState(orderId));
        
        // 发起支付
        stateMachineService.sendEvent(orderId, OrderEvent.PAY);
        assertEquals(OrderState.PAYING, stateMachineService.getCurrentState(orderId));
    }
}
