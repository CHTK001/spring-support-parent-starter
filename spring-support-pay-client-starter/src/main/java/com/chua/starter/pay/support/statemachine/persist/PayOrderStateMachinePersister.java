package com.chua.starter.pay.support.statemachine.persist;

import com.chua.starter.pay.support.enums.PayOrderEvent;
import com.chua.starter.pay.support.enums.PayOrderStatus;
import org.springframework.statemachine.StateMachine;

/**
 * 支付订单状态机持久化接口（SPI）
 * <p>
 * 支持多种持久化策略：
 * <ul>
 *   <li>数据库持久化</li>
 *   <li>数据库 + Redis 缓存</li>
 * </ul>
 *
 * @author CH
 * @version 1.0.0
 * @since 2025/10/24
 */
public interface PayOrderStateMachinePersister {

    /**
     * 保存状态机状态
     *
     * @param stateMachine  状态机实例
     * @param orderCode     订单编号
     * @throws Exception 保存失败时抛出异常
     */
    void persist(StateMachine<PayOrderStatus, PayOrderEvent> stateMachine, String orderCode) throws Exception;

    /**
     * 恢复状态机状态
     *
     * @param stateMachine  状态机实例
     * @param orderCode     订单编号
     * @return 恢复后的状态机
     * @throws Exception 恢复失败时抛出异常
     */
    StateMachine<PayOrderStatus, PayOrderEvent> restore(StateMachine<PayOrderStatus, PayOrderEvent> stateMachine, String orderCode) throws Exception;

    /**
     * 删除状态机状态
     *
     * @param orderCode 订单编号
     * @throws Exception 删除失败时抛出异常
     */
    void delete(String orderCode) throws Exception;

    /**
     * 获取持久化类型名称
     *
     * @return 持久化类型名称
     */
    String getType();
}

