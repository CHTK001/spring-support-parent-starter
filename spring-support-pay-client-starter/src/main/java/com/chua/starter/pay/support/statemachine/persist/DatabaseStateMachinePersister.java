package com.chua.starter.pay.support.statemachine.persist;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.starter.pay.support.entity.PayMerchantOrder;
import com.chua.starter.pay.support.enums.PayOrderEvent;
import com.chua.starter.pay.support.enums.PayOrderStatus;
import com.chua.starter.pay.support.mapper.PayMerchantOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.StateMachineContext;
import org.springframework.statemachine.config.StateMachineFactory;
import org.springframework.statemachine.support.DefaultStateMachineContext;
import org.springframework.stereotype.Component;

/**
 * 基于数据库的状态机持久化实现
 * <p>
 * 直接使用数据库存储状态机状态，适用于对性能要求不高的场景。
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2025/10/24
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "plugin.pay", name = "state-machine-persist-type", havingValue = "DATABASE", matchIfMissing = false)
public class DatabaseStateMachinePersister implements PayOrderStateMachinePersister {

    private final PayMerchantOrderMapper payMerchantOrderMapper;
    private final StateMachineFactory<PayOrderStatus, PayOrderEvent> stateMachineFactory;

    @Override
    public void persist(StateMachine<PayOrderStatus, PayOrderEvent> stateMachine, String orderCode) throws Exception {
        if (stateMachine == null || orderCode == null) {
            log.warn("状态机或订单编号为空，无法持久化");
            return;
        }

        try {
            // 获取当前状态
            PayOrderStatus currentStatus = stateMachine.getState().getId();

            // 更新数据库中的订单状态
            PayMerchantOrder order = payMerchantOrderMapper.selectOne(
                    new LambdaQueryWrapper<PayMerchantOrder>()
                            .eq(PayMerchantOrder::getPayMerchantOrderCode, orderCode)
            );

            if (order == null) {
                log.error("订单不存在，无法持久化状态机状态, 订单编号: {}", orderCode);
                return;
            }

            order.setPayMerchantOrderStatus(currentStatus);
            payMerchantOrderMapper.updateById(order);

            log.debug("状态机状态已持久化到数据库, 订单编号: {}, 状态: {}", orderCode, currentStatus.getName());

        } catch (Exception e) {
            log.error("持久化状态机状态失败, 订单编号: {}", orderCode, e);
            throw e;
        }
    }

    @Override
    public StateMachine<PayOrderStatus, PayOrderEvent> restore(StateMachine<PayOrderStatus, PayOrderEvent> stateMachine, String orderCode) throws Exception {
        if (orderCode == null) {
            log.warn("订单编号为空，无法恢复状态机");
            return stateMachine;
        }

        try {
            // 从数据库查询订单状态
            PayMerchantOrder order = payMerchantOrderMapper.selectOne(
                    new LambdaQueryWrapper<PayMerchantOrder>()
                            .eq(PayMerchantOrder::getPayMerchantOrderCode, orderCode)
            );

            if (order == null) {
                log.error("订单不存在，无法恢复状态机状态, 订单编号: {}", orderCode);
                return stateMachine;
            }

            PayOrderStatus currentStatus = order.getPayMerchantOrderStatus();
            if (currentStatus == null) {
                log.warn("订单状态为空，使用默认状态, 订单编号: {}", orderCode);
                currentStatus = PayOrderStatus.PAY_CREATE;
            }

            // 创建状态机上下文
            StateMachineContext<PayOrderStatus, PayOrderEvent> context =
                    new DefaultStateMachineContext<>(currentStatus, null, null, null);

            // 重置状态机到指定状态
            stateMachine.stopReactively().block();
            stateMachine.getStateMachineAccessor()
                    .doWithAllRegions(access -> access.resetStateMachineReactively(context).block());
            stateMachine.startReactively().block();

            log.debug("状态机状态已从数据库恢复, 订单编号: {}, 状态: {}", orderCode, currentStatus.getName());

            return stateMachine;

        } catch (Exception e) {
            log.error("恢复状态机状态失败, 订单编号: {}", orderCode, e);
            throw e;
        }
    }

    @Override
    public void delete(String orderCode) throws Exception {
        // 数据库持久化不需要主动删除，订单记录会一直保留
        log.debug("数据库持久化模式，无需删除状态机状态, 订单编号: {}", orderCode);
    }

    @Override
    public String getType() {
        return "DATABASE";
    }
}

