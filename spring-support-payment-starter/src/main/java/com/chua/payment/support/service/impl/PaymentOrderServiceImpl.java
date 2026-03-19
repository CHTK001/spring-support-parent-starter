package com.chua.payment.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.payment.support.dto.OrderCreateDTO;
import com.chua.payment.support.entity.PaymentOrder;
import com.chua.payment.support.enums.OrderEvent;
import com.chua.payment.support.exception.PaymentException;
import com.chua.payment.support.mapper.PaymentOrderMapper;
import com.chua.payment.support.service.OrderStateMachineService;
import com.chua.payment.support.service.PaymentOrderService;
import com.chua.payment.support.vo.OrderVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 支付订单服务实现
 *
 * @author CH
 * @since 2026-03-18
 */
@Service
@RequiredArgsConstructor
public class PaymentOrderServiceImpl implements PaymentOrderService {

    private final PaymentOrderMapper orderMapper;
    private final OrderStateMachineService stateMachineService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderVO createOrder(OrderCreateDTO dto) {
        PaymentOrder order = new PaymentOrder();
        BeanUtils.copyProperties(dto, order);
        order.setOrderNo(generateOrderNo());
        order.setStatus("PENDING"); // 待支付
        order.setCreatedAt(LocalDateTime.now());
        
        orderMapper.insert(order);
        
        // 创建状态机
        stateMachineService.createStateMachine(order.getId());
        
        return convertToVO(order);
    }

    @Override
    public OrderVO getOrder(Long id) {
        PaymentOrder order = orderMapper.selectById(id);
        if (order == null) {
            throw new PaymentException("订单不存在");
        }
        return convertToVO(order);
    }

    @Override
    public Page<OrderVO> listOrders(int page, int size, String orderNo, Integer status) {
        LambdaQueryWrapper<PaymentOrder> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(orderNo)) {
            wrapper.eq(PaymentOrder::getOrderNo, orderNo);
        }
        if (status != null) {
            wrapper.eq(PaymentOrder::getStatus, status);
        }
        wrapper.orderByDesc(PaymentOrder::getCreatedAt);
        
        Page<PaymentOrder> orderPage = orderMapper.selectPage(new Page<>(page, size), wrapper);
        Page<OrderVO> voPage = new Page<>(page, size, orderPage.getTotal());
        voPage.setRecords(orderPage.getRecords().stream().map(this::convertToVO).toList());
        
        return voPage;
    }

    @Override
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateOrderStatus(Long id, Integer status, String operator) {
        PaymentOrder order = orderMapper.selectById(id);
        if (order == null) {
            throw new PaymentException("订单不存在");
        }
        
        // 通过状态机更新状态
        OrderEvent event = mapStatusToEvent(status);
        if (event != null) {
            stateMachineService.sendEvent(order.getId(), event, operator);
            // 根据事件更新状态
            OrderState newState = mapEventToState(event);
            if (newState != null) {
                order.setStatus(newState.name());
            }
        }
        
        return orderMapper.updateById(order) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean cancelOrder(Long id, String operator) {
        PaymentOrder order = orderMapper.selectById(id);
        if (order == null) {
            throw new PaymentException("订单不存在");
        }
        
        stateMachineService.sendEvent(order.getId(), OrderEvent.CANCEL, operator);
        order.setStatus(OrderState.CANCELLED.name());
        return orderMapper.updateById(order) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean completeOrder(Long id, String operator) {
        PaymentOrder order = orderMapper.selectById(id);
        if (order == null) {
            throw new PaymentException("订单不存在");
        }
        
        stateMachineService.sendEvent(order.getId(), OrderEvent.COMPLETE, operator);
        order.setStatus(OrderState.COMPLETED.name());
        return orderMapper.updateById(order) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean refundOrder(Long id, String reason, String operator) {
        PaymentOrder order = orderMapper.selectById(id);
        if (order == null) {
            throw new PaymentException("订单不存在");
        }
        
        stateMachineService.sendEvent(order.getId(), OrderEvent.REFUND, operator);
        order.setStatus(OrderState.REFUNDING.name());
        return orderMapper.updateById(order) > 0;
    }

    @Override
    public OrderVO getRefundOrder(Long id) {
        return getOrder(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void autoCancelTimeoutOrders() {
        LambdaQueryWrapper<PaymentOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PaymentOrder::getStatus, OrderState.PENDING.name()); // 待支付
        wrapper.lt(PaymentOrder::getCreatedAt, LocalDateTime.now().minusMinutes(30));
        
        orderMapper.selectList(wrapper).forEach(order -> {
            cancelOrder(order.getId(), "system");
        });
    }

    @Override
    public String generateOrderNo() {
        return "ORD" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private OrderVO convertToVO(PaymentOrder order) {
        OrderVO vo = new OrderVO();
        BeanUtils.copyProperties(order, vo);
        return vo;
    }

    private OrderEvent mapStatusToEvent(Integer status) {
        return switch (status) {
            case 1 -> OrderEvent.PAY;
            case 2 -> OrderEvent.PAY_SUCCESS;
            case 3 -> OrderEvent.COMPLETE;
            case 4 -> OrderEvent.REFUND;
            case 5 -> OrderEvent.REFUND_SUCCESS;
            case 6 -> OrderEvent.CANCEL;
            case 7 -> OrderEvent.PAY_FAIL;
            default -> null;
        };
    }

    private OrderState mapEventToState(OrderEvent event) {
        return switch (event) {
            case PAY -> OrderState.PAYING;
            case PAY_SUCCESS -> OrderState.PAID;
            case COMPLETE -> OrderState.COMPLETED;
            case REFUND -> OrderState.REFUNDING;
            case REFUND_SUCCESS -> OrderState.REFUNDED;
            case CANCEL -> OrderState.CANCELLED;
            case PAY_FAIL -> OrderState.FAILED;
            default -> null;
        };
    }
}
