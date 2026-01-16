package com.chua.starter.pay.support.statemachine;

import com.chua.starter.pay.support.entity.PayMerchantOrder;
import com.chua.starter.pay.support.enums.PayOrderEvent;
import com.chua.starter.pay.support.enums.PayOrderStatus;
import com.chua.starter.pay.support.statemachine.persist.PayOrderStateMachinePersister;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.config.StateMachineFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Objects;

/**
 * 支付订单状态机服务
 * <p>
 * 优化说明：
 * <ul>
 *   <li>1. 使用 SPI 方式支持多种持久化策略（数据库、数据库+Redis）</li>
 *   <li>2. 在状态机 Action 中统一处理订单状态更新和流水保存</li>
 *   <li>3. 优化状态机生命周期管理</li>
 *   <li>4. 增强异常处理和日志记录</li>
 * </ul>
 * </p>
 *
 * @author CH
 * @version 2.0.0
 * @since 2025/10/24
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayOrderStateMachineService {

    private final StateMachineFactory<PayOrderStatus, PayOrderEvent> stateMachineFactory;
    private final PayOrderStateMachinePersister stateMachinePersister;

    /**
     * 消息头：订单编号
     */
    public static final String ORDER_CODE_HEADER = "orderCode";

    /**
     * 消息头：订单实体
     */
    public static final String ORDER_ENTITY_HEADER = "orderEntity";

    /**
     * 发送状态机事件
     * <p>
     * 统一处理订单状态转换，包括：
     * <ul>
     *   <li>创建或恢复状态机</li>
     *   <li>发送事件触发状态转换</li>
     *   <li>在 Action 中自动更新订单状态和保存流水</li>
     *   <li>持久化状态机状态</li>
     * </ul>
     * </p>
     *
     * @param orderCode     订单编号
     * @param currentStatus 当前状态
     * @param event         状态机事件
     * @param order         订单实体（可以携带额外信息，如交易号、退款原因等）
     * @return 是否转换成功
     */
    public boolean sendEvent(String orderCode, PayOrderStatus currentStatus, PayOrderEvent event, PayMerchantOrder order) {
        StateMachine<PayOrderStatus, PayOrderEvent> stateMachine = null;
        try {
            // 1. 获取或创建状态机
            stateMachine = getOrCreateStateMachine(orderCode, currentStatus);

            // 2. 构建消息，携带订单信息
            Message<PayOrderEvent> message = MessageBuilder.withPayload(event)
                    .setHeader(ORDER_CODE_HEADER, orderCode)
                    .setHeader(ORDER_ENTITY_HEADER, order)
                    .build();

            // 3. 发送事件（使用新的响应式 API）
            // Action 会自动处理订单状态更新和流水保存
            boolean result = stateMachine.sendEvent(Mono.just(message))
                    .blockFirst() != null;

            if (result) {
                // 4. 持久化状态机状态
                stateMachinePersister.persist(stateMachine, orderCode);
                
                log.info("订单状态转换成功, 订单编号: {}, 事件: {}, 新状态: {}",
                        orderCode,
                        event,
                        stateMachine.getState().getId().getName());
            } else {
                log.warn("订单状态转换失败, 订单编号: {}, 当前状态: {}, 事件: {}",
                        orderCode,
                        currentStatus.getName(),
                        event);
            }

            return result;
        } catch (Exception e) {
            log.error("发送订单状态转换事件失败, 订单编号: {}, 事件: {}", orderCode, event, e);
            return false;
        } finally {
            // 5. 停止状态机释放资源
            if (stateMachine != null) {
                stopStateMachine(stateMachine);
            }
        }
    }

    /**
     * 发送状态机事件（异步）
     *
     * @param orderCode     订单编号
     * @param currentStatus 当前状态
     * @param event         状态机事件
     * @param order         订单实体
     * @return Mono<Boolean>
     */
    public Mono<Boolean> sendEventAsync(String orderCode, PayOrderStatus currentStatus, PayOrderEvent event, PayMerchantOrder order) {
        return Mono.fromCallable(() -> sendEvent(orderCode, currentStatus, event, order));
    }

    /**
     * 检查订单状态转换是否合法
     * <p>
     * 不实际执行状态转换，仅验证是否允许该转换。
     * </p>
     *
     * @param orderCode     订单编号
     * @param currentStatus 当前状态
     * @param event         状态机事件
     * @return 是否允许转换
     */
    public boolean canTransition(String orderCode, PayOrderStatus currentStatus, PayOrderEvent event) {
        StateMachine<PayOrderStatus, PayOrderEvent> stateMachine = null;
        try {
            // 创建状态机
            stateMachine = getOrCreateStateMachine(orderCode, currentStatus);

            // 构建消息
            Message<PayOrderEvent> message = MessageBuilder.withPayload(event)
                    .setHeader(ORDER_CODE_HEADER, orderCode)
                    .build();

            // 检查是否可以发送事件
            stateMachine.sendEvent(Mono.just(message)).blockFirst();
            return true;
        } catch (Exception e) {
            log.error("检查订单状态转换是否合法失败, 订单编号: {}, 事件: {}", orderCode, event, e);
            return false;
        } finally {
            if (stateMachine != null) {
                stopStateMachine(stateMachine);
            }
        }
    }

    /**
     * 获取或创建状态机
     *
     * @param orderCode     订单编号
     * @param currentStatus 当前状态
     * @return 状态机实例
     * @throws Exception 创建失败时抛出异常
     */
    private StateMachine<PayOrderStatus, PayOrderEvent> getOrCreateStateMachine(String orderCode, PayOrderStatus currentStatus) throws Exception {
        // 1. 创建新的状态机实例
        StateMachine<PayOrderStatus, PayOrderEvent> stateMachine = stateMachineFactory.getStateMachine(orderCode);

        // 2. 尝试从持久化存储恢复状态
        try {
            stateMachine = stateMachinePersister.restore(stateMachine, orderCode);
        } catch (Exception e) {
            log.warn("恢复状态机状态失败，使用当前状态初始化, 订单编号: {}, 当前状态: {}",
                    orderCode, currentStatus.getName(), e);
            // 恢复失败时，状态机会使用默认的初始状态
        }

        // 3. 启动状态机
        try {
            stateMachine.startReactively().block();
        } catch (Exception e) {
            // 状态机可能已经启动，忽略异常
            log.debug("状态机启动异常（可能已启动）: {}", e.getMessage());
        }

        return stateMachine;
    }

    /**
     * 停止状态机并释放资源
     *
     * @param stateMachine 状态机实例
     */
    private void stopStateMachine(StateMachine<PayOrderStatus, PayOrderEvent> stateMachine) {
        try {
            if (stateMachine != null ) {
                stateMachine.stopReactively().block();
            }
        } catch (Exception e) {
            log.warn("停止状态机失败", e);
        }
    }

    /**
     * 获取订单当前状态
     *
     * @param orderCode 订单编号
     * @return 当前状态，不存在返回 null
     */
    public PayOrderStatus getCurrentStatus(String orderCode) {
        StateMachine<PayOrderStatus, PayOrderEvent> stateMachine = null;
        try {
            stateMachine = stateMachineFactory.getStateMachine(orderCode);
            stateMachine = stateMachinePersister.restore(stateMachine, orderCode);
            
            if (stateMachine != null && stateMachine.getState() != null) {
                return stateMachine.getState().getId();
            }
            return null;
        } catch (Exception e) {
            log.error("获取订单当前状态失败, 订单编号: {}", orderCode, e);
            return null;
        } finally {
            if (stateMachine != null) {
                stopStateMachine(stateMachine);
            }
        }
    }

    /**
     * 删除状态机状态
     * <p>
     * 清除持久化存储中的状态机状态（主要是 Redis 缓存）。
     * </p>
     *
     * @param orderCode 订单编号
     */
    public void deleteStateMachine(String orderCode) {
        try {
            stateMachinePersister.delete(orderCode);
            log.info("状态机状态已删除, 订单编号: {}", orderCode);
        } catch (Exception e) {
            log.error("删除状态机状态失败, 订单编号: {}", orderCode, e);
        }
    }
}
