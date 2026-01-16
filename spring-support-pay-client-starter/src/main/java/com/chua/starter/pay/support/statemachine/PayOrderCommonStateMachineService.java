package com.chua.starter.pay.support.statemachine;

import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.statemachine.builder.StateMachineBuilder;
import com.chua.common.support.statemachine.core.StateMachine;
import com.chua.common.support.statemachine.core.StateMachineContext;
import com.chua.common.support.statemachine.core.StateMachineProvider;
import com.chua.common.support.statemachine.core.StatePersistence;
import com.chua.common.support.statemachine.listener.StateChangeListener;
import com.chua.starter.pay.support.entity.PayMerchantOrder;
import com.chua.starter.pay.support.enums.PayOrderEvent;
import com.chua.starter.pay.support.enums.PayOrderStatus;
import com.chua.starter.pay.support.properties.PayProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.EnumSet;

/**
 * 基于 utils-common 模块的支付订单状态机服务
 * <p>
 * 使用 utils-common 模块的通用状态机实现，替代 Spring StateMachine。
 * 优点：
 * <ul>
 *   <li>轻量级实现，无需依赖 Spring StateMachine</li>
 *   <li>支持 SPI 扩展持久化策略</li>
 *   <li>更灵活的状态转换配置</li>
 * </ul>
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2025/12/03
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayOrderCommonStateMachineService {

    /**
     * 消息头：订单编号
     */
    public static final String ORDER_CODE_KEY = "orderCode";

    /**
     * 消息头：订单实体
     */
    public static final String ORDER_ENTITY_KEY = "orderEntity";

    /**
     * 状态机实例
     */
    private StateMachine<PayOrderStatus, PayOrderEvent> stateMachine;

    /**
     * 状态机提供者
     */
    private StateMachineProvider<PayOrderStatus, PayOrderEvent> stateMachineProvider;

    /**
     * 状态机持久化
     */
    private StatePersistence<PayOrderStatus, PayOrderEvent> statePersistence;

    /**
     * 支付配置属性
     */
    private final PayProperties payProperties;

    /**
     * 初始化状态机
     */
    @PostConstruct
    @SuppressWarnings("unchecked")
    public void init() {
        String providerName = payProperties.getStateMachineProvider();
        log.info("初始化支付订单状态机, 提供者: {}...", providerName);

        // 通过 SPI 加载状态机提供者
        stateMachineProvider = ServiceProvider.of(StateMachineProvider.class)
                .getExtension(providerName);

        if (stateMachineProvider == null) {
            // 使用默认提供者
            stateMachineProvider = ServiceProvider.of(StateMachineProvider.class).getExtension();
            log.warn("未找到状态机提供者: {}, 使用默认提供者: {}", providerName,
                    stateMachineProvider != null ? stateMachineProvider.name() : "null");
        }

        if (stateMachineProvider == null) {
            throw new IllegalStateException("无法加载状态机提供者");
        }

        log.info("使用状态机提供者: {}", stateMachineProvider.name());

        // 获取持久化实现
        statePersistence = stateMachineProvider.getPersistence();
        if (statePersistence != null) {
            log.info("状态机持久化已配置: {}", statePersistence.getClass().getSimpleName());
        }

        // 创建状态机构建器
        StateMachineBuilder<PayOrderStatus, PayOrderEvent> builder = stateMachineProvider
                .createBuilder("pay-order")
                .name("支付订单状态机")
                .initialState(PayOrderStatus.PAY_CREATE);

        // 添加所有状态
        for (PayOrderStatus status : EnumSet.allOf(PayOrderStatus.class)) {
            builder.state(status);
        }

        // 配置状态转换规则
        configureTransitions(builder);

        // 设置持久化
        if (statePersistence != null) {
            builder.persistence(statePersistence);
        }

        // 构建状态机
        stateMachine = builder.build();

        log.info("支付订单状态机初始化完成, 提供者: {}", stateMachineProvider.name());
    }

    /**
     * 配置状态转换规则
     *
     * @param builder 状态机构建器
     */
    private void configureTransitions(StateMachineBuilder<PayOrderStatus, PayOrderEvent> builder) {
        // 创建订单流程
        // 创建 -> 创建失败
        builder.transition(PayOrderStatus.PAY_CREATE, PayOrderStatus.PAY_CREATE_FAILED, PayOrderEvent.CREATE_FAILED);
        // 创建 -> 待支付
        builder.transition(PayOrderStatus.PAY_CREATE, PayOrderStatus.PAY_WAITING, PayOrderEvent.WAIT_PAY);

        // 支付流程
        // 待支付 -> 支付中
        builder.transition(PayOrderStatus.PAY_WAITING, PayOrderStatus.PAY_PAYING, PayOrderEvent.START_PAY);
        // 支付中 -> 支付成功
        builder.transition(PayOrderStatus.PAY_PAYING, PayOrderStatus.PAY_SUCCESS, PayOrderEvent.PAY_SUCCESS);
        // 待支付 -> 支付成功（直接支付成功，如钱包支付）
        builder.transition(PayOrderStatus.PAY_WAITING, PayOrderStatus.PAY_SUCCESS, PayOrderEvent.PAY_SUCCESS);

        // 超时流程
        // 创建 -> 超时
        builder.transition(PayOrderStatus.PAY_CREATE, PayOrderStatus.PAY_TIMEOUT, PayOrderEvent.TIMEOUT);
        // 待支付 -> 超时
        builder.transition(PayOrderStatus.PAY_WAITING, PayOrderStatus.PAY_TIMEOUT, PayOrderEvent.TIMEOUT);

        // 取消流程
        // 创建 -> 取消
        builder.transition(PayOrderStatus.PAY_CREATE, PayOrderStatus.PAY_CANCEL_SUCCESS, PayOrderEvent.CANCEL);
        // 待支付 -> 取消
        builder.transition(PayOrderStatus.PAY_WAITING, PayOrderStatus.PAY_CANCEL_SUCCESS, PayOrderEvent.CANCEL);

        // 关闭流程
        // 创建 -> 关闭
        builder.transition(PayOrderStatus.PAY_CREATE, PayOrderStatus.PAY_CLOSE_SUCCESS, PayOrderEvent.CLOSE);
        // 待支付 -> 关闭
        builder.transition(PayOrderStatus.PAY_WAITING, PayOrderStatus.PAY_CLOSE_SUCCESS, PayOrderEvent.CLOSE);
        // 支付中 -> 关闭
        builder.transition(PayOrderStatus.PAY_PAYING, PayOrderStatus.PAY_CLOSE_SUCCESS, PayOrderEvent.CLOSE);

        // 退款流程
        // 支付成功 -> 正在退款
        builder.transition(PayOrderStatus.PAY_SUCCESS, PayOrderStatus.PAY_REFUND_WAITING, PayOrderEvent.REFUND);
        // 正在退款 -> 退款成功
        builder.transition(PayOrderStatus.PAY_REFUND_WAITING, PayOrderStatus.PAY_REFUND_SUCCESS, PayOrderEvent.REFUND_SUCCESS);
        // 正在退款 -> 部分退款
        builder.transition(PayOrderStatus.PAY_REFUND_WAITING, PayOrderStatus.PAY_REFUND_PART_SUCCESS, PayOrderEvent.REFUND_PART_SUCCESS);
        // 部分退款 -> 正在退款（再次申请退款）
        builder.transition(PayOrderStatus.PAY_REFUND_PART_SUCCESS, PayOrderStatus.PAY_REFUND_WAITING, PayOrderEvent.REFUND);
        // 部分退款 -> 退款成功（全部退完）
        builder.transition(PayOrderStatus.PAY_REFUND_PART_SUCCESS, PayOrderStatus.PAY_REFUND_SUCCESS, PayOrderEvent.REFUND_SUCCESS);

        log.debug("状态转换规则配置完成");
    }

    /**
     * 发送状态机事件
     * <p>
     * 统一处理订单状态转换，包括：
     * <ul>
     *   <li>创建或恢复状态机上下文</li>
     *   <li>发送事件触发状态转换</li>
     *   <li>持久化状态机状态</li>
     * </ul>
     * </p>
     *
     * @param orderCode     订单编号
     * @param currentStatus 当前状态
     * @param event         状态机事件
     * @param order         订单实体
     * @return 是否转换成功
     */
    public boolean sendEventForPay(String orderCode, PayOrderStatus currentStatus, PayOrderEvent event, PayMerchantOrder order) {
        try {
            // 获取或创建上下文
            StateMachineContext<PayOrderStatus, PayOrderEvent> context = getOrCreateContextForPay(orderCode, currentStatus);

            // 设置订单信息到上下文变量
            context.setVariable(ORDER_CODE_KEY, orderCode);
            if (order != null) {
                context.setVariable(ORDER_ENTITY_KEY, order);
            }

            // 发送事件
            boolean result = stateMachine.sendEvent(context, event);

            if (result) {
                log.info("订单状态转换成功, 订单编号: {}, 事件: {}, 新状态: {}",
                        orderCode,
                        event,
                        context.getCurrentState().getName());
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
        }
    }

    /**
     * 检查订单状态转换是否合法
     *
     * @param orderCode     订单编号
     * @param currentStatus 当前状态
     * @param event         状态机事件
     * @return 是否允许转换
     */
    public boolean canTransitionForPay(String orderCode, PayOrderStatus currentStatus, PayOrderEvent event) {
        try {
            // 检查当前状态是否有对应事件的转换
            return stateMachine.getTransitions(currentStatus).stream()
                    .anyMatch(transition -> transition.getEventType().equals(event));
        } catch (Exception e) {
            log.error("检查订单状态转换是否合法失败, 订单编号: {}, 事件: {}", orderCode, event, e);
            return false;
        }
    }

    /**
     * 获取订单当前状态
     *
     * @param orderCode 订单编号
     * @return 当前状态，不存在返回 null
     */
    public PayOrderStatus getCurrentStatusForPay(String orderCode) {
        try {
            StateMachineContext<PayOrderStatus, PayOrderEvent> context = stateMachine.getContext(orderCode);
            if (context != null) {
                return context.getCurrentState();
            }

            // 尝试从持久化存储加载
            if (statePersistence != null) {
                context = statePersistence.loadByBusinessId(orderCode);
                if (context != null) {
                    return context.getCurrentState();
                }
            }

            return null;
        } catch (Exception e) {
            log.error("获取订单当前状态失败, 订单编号: {}", orderCode, e);
            return null;
        }
    }

    /**
     * 删除状态机上下文
     *
     * @param orderCode 订单编号
     */
    public void deleteContextForPay(String orderCode) {
        try {
            if (statePersistence != null) {
                StateMachineContext<PayOrderStatus, PayOrderEvent> context = statePersistence.loadByBusinessId(orderCode);
                if (context != null) {
                    statePersistence.delete(context.getTraceId());
                    log.info("状态机上下文已删除, 订单编号: {}", orderCode);
                }
            }
        } catch (Exception e) {
            log.error("删除状态机上下文失败, 订单编号: {}", orderCode, e);
        }
    }

    /**
     * 获取或创建状态机上下文
     *
     * @param orderCode     订单编号
     * @param currentStatus 当前状态
     * @return 状态机上下文
     */
    private StateMachineContext<PayOrderStatus, PayOrderEvent> getOrCreateContextForPay(String orderCode, PayOrderStatus currentStatus) {
        try {
            // 先尝试从缓存获取
            StateMachineContext<PayOrderStatus, PayOrderEvent> context = stateMachine.getContext(orderCode);
            if (context != null) {
                return context;
            }

            // 从持久化存储加载
            if (statePersistence != null) {
                context = statePersistence.loadByBusinessId(orderCode);
                if (context != null) {
                    return context;
                }
            }

            // 创建新的上下文
            context = stateMachine.createContext(orderCode);
            context.setCurrentState(currentStatus);

            // 保存到持久化存储
            if (statePersistence != null) {
                statePersistence.save(context);
            }

            return context;
        } catch (Exception e) {
            log.error("获取或创建状态机上下文失败, 订单编号: {}", orderCode, e);
            // 返回一个新的上下文
            StateMachineContext<PayOrderStatus, PayOrderEvent> context = stateMachine.createContext(orderCode);
            context.setCurrentState(currentStatus);
            return context;
        }
    }

    /**
     * 获取状态机链路
     *
     * @param orderCode 订单编号
     * @return 链路信息字符串
     */
    public String getChainPathForPay(String orderCode) {
        try {
            StateMachineContext<PayOrderStatus, PayOrderEvent> context = stateMachine.getContext(orderCode);
            if (context == null && statePersistence != null) {
                context = statePersistence.loadByBusinessId(orderCode);
            }

            if (context != null) {
                return stateMachine.getChainPath(context).toString();
            }

            return "上下文不存在";
        } catch (Exception e) {
            log.error("获取状态机链路失败, 订单编号: {}", orderCode, e);
            return "获取失败: " + e.getMessage();
        }
    }

    /**
     * 销毁状态机
     */
    @PreDestroy
    public void destroy() {
        try {
            if (stateMachine != null) {
                stateMachine.close();
                log.info("支付订单状态机已销毁");
            }
        } catch (Exception e) {
            log.error("销毁状态机失败", e);
        }
    }
}
