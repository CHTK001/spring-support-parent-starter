package com.chua.payment.support.statemachine;

import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.chua.payment.support.entity.PaymentOrder;
import com.chua.payment.support.enums.OrderEvent;
import com.chua.payment.support.enums.OrderState;
import com.chua.payment.support.enums.OrderTransitionResult;
import com.chua.payment.support.mapper.PaymentOrderMapper;
import com.chua.payment.support.service.OrderStateLogService;
import com.chua.payment.support.service.impl.OrderStateMachineServiceImpl;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.statemachine.config.StateMachineFactory;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderStateMachineTest {

    private static AnnotationConfigApplicationContext context;
    private static StateMachineFactory<OrderState, OrderEvent> stateMachineFactory;

    private final PaymentOrderMapper paymentOrderMapper = mock(PaymentOrderMapper.class);
    private final OrderStateLogService orderStateLogService = mock(OrderStateLogService.class);

    private OrderStateMachineServiceImpl service;

    @BeforeAll
    static void initFactory() {
        context = new AnnotationConfigApplicationContext(OrderStateMachineConfig.class);
        stateMachineFactory = context.getBean(StateMachineFactory.class);
        if (TableInfoHelper.getTableInfo(PaymentOrder.class) == null) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), PaymentOrder.class);
        }
    }

    @AfterAll
    static void closeContext() {
        if (context != null) {
            context.close();
        }
    }

    @BeforeEach
    void setUp() {
        service = new OrderStateMachineServiceImpl(stateMachineFactory, paymentOrderMapper, orderStateLogService);
    }

    @Test
    void shouldCreateStateMachineWithoutPersistingAnything() {
        assertDoesNotThrow(() -> service.createStateMachine(1L));
        verify(paymentOrderMapper, never()).selectById(any());
    }

    @Test
    void shouldApplyValidPaymentTransition() {
        PaymentOrder order = order(1L, OrderState.PENDING);
        when(paymentOrderMapper.selectById(1L)).thenReturn(order);
        doAnswer(invocation -> {
            order.setStatus(OrderState.PAYING.name());
            return 1;
        }).when(paymentOrderMapper).update(eq(null), any());

        OrderTransitionResult result = service.sendEvent(1L, OrderEvent.PAY, "tester");

        assertEquals(OrderTransitionResult.APPLIED, result);
        assertEquals(OrderState.PAYING.name(), order.getStatus());
        verify(orderStateLogService).log(1L, OrderState.PENDING, OrderState.PAYING, "PAY", "tester", "状态转换: 待支付 -> 支付中");
    }

    @Test
    void shouldRejectInvalidTransitionFromPendingToPaySuccess() {
        when(paymentOrderMapper.selectById(2L)).thenReturn(order(2L, OrderState.PENDING));

        OrderTransitionResult result = service.sendEvent(2L, OrderEvent.PAY_SUCCESS, "tester");

        assertEquals(OrderTransitionResult.REJECTED, result);
        verify(paymentOrderMapper, never()).update(eq(null), any());
        verify(orderStateLogService, never()).log(any(), any(), any(), anyString(), anyString(), anyString());
    }

    @Test
    void shouldReturnDuplicatedWhenAnotherNodeAlreadyMovedToTargetState() {
        PaymentOrder current = order(3L, OrderState.PENDING);
        PaymentOrder latest = order(3L, OrderState.PAYING);
        when(paymentOrderMapper.selectById(3L)).thenReturn(current, latest);
        when(paymentOrderMapper.update(eq(null), any())).thenReturn(0);

        OrderTransitionResult result = service.sendEvent(3L, OrderEvent.PAY, "tester");

        assertEquals(OrderTransitionResult.DUPLICATED, result);
        verify(orderStateLogService, never()).log(any(), any(), any(), anyString(), anyString(), anyString());
    }

    @Test
    void shouldReadCurrentStateFromPersistedOrder() {
        when(paymentOrderMapper.selectById(4L)).thenReturn(order(4L, OrderState.REFUNDING));

        OrderState currentState = service.getCurrentState(4L);

        assertEquals(OrderState.REFUNDING, currentState);
    }

    private PaymentOrder order(Long id, OrderState state) {
        PaymentOrder order = new PaymentOrder();
        order.setId(id);
        order.setStatus(state.name());
        return order;
    }
}
