package com.chua.starter.pay.support.statemachine;

import com.chua.starter.pay.support.enums.PayOrderEvent;
import com.chua.starter.pay.support.enums.PayOrderStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.statemachine.StateMachineContext;
import org.springframework.statemachine.StateMachinePersist;
import org.springframework.statemachine.persist.DefaultStateMachinePersister;
import org.springframework.statemachine.persist.StateMachinePersister;

/**
 * 订单状态机持久化配置
 * <p>
 * 使用Redis存储状态机状态，支持：
 * 1. 状态机状态持久化
 * 2. 状态机状态恢复
 * 3. 分布式环境下的状态共享
 *
 * @author CH
 * @version 1.0
 * @since 2025/10/24
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class PayOrderStateMachinePersistConfig {

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * Redis状态机持久化适配器
     */
    private static final String STATE_MACHINE_KEY_PREFIX = "pay:statemachine:order:";

    /**
     * 创建状态机持久化器
     *
     * @return 状态机持久化器
     */
    @Bean
    public StateMachinePersister<PayOrderStatus, PayOrderEvent, String> stateMachinePersister() {
        return new DefaultStateMachinePersister<>(stateMachinePersist());
    }

    /**
     * 创建状态机持久化存储
     *
     * @return 状态机持久化存储
     */
    @Bean
    public StateMachinePersist<PayOrderStatus, PayOrderEvent, String> stateMachinePersist() {
        return new StateMachinePersist<PayOrderStatus, PayOrderEvent, String>() {
            
            /**
             * 写入状态机上下文到Redis
             *
             * @param context 状态机上下文
             * @param contextObj 订单编号
             * @throws Exception 写入异常
             */
            @Override
            public void write(StateMachineContext<PayOrderStatus, PayOrderEvent> context, String contextObj) throws Exception {
                try {
                    String key = STATE_MACHINE_KEY_PREFIX + contextObj;
                    log.debug("持久化订单状态机, 订单编号: {}, 状态: {}", contextObj, context.getState());
                    redisTemplate.opsForValue().set(key, context);
                } catch (Exception e) {
                    log.error("持久化订单状态机失败, 订单编号: {}", contextObj, e);
                    throw new RuntimeException("持久化订单状态机失败", e);
                }
            }

            /**
             * 从Redis读取状态机上下文
             *
             * @param contextObj 订单编号
             * @return 状态机上下文
             * @throws Exception 读取异常
             */
            @Override
            public StateMachineContext<PayOrderStatus, PayOrderEvent> read(String contextObj) throws Exception {
                try {
                    String key = STATE_MACHINE_KEY_PREFIX + contextObj;
                    Object value = redisTemplate.opsForValue().get(key);
                    
                    if (value instanceof StateMachineContext) {
                        @SuppressWarnings("unchecked")
                        StateMachineContext<PayOrderStatus, PayOrderEvent> context = 
                                (StateMachineContext<PayOrderStatus, PayOrderEvent>) value;
                        log.debug("恢复订单状态机, 订单编号: {}, 状态: {}", contextObj, context.getState());
                        return context;
                    }
                    
                    log.debug("订单状态机不存在, 订单编号: {}", contextObj);
                    return null;
                } catch (Exception e) {
                    log.error("恢复订单状态机失败, 订单编号: {}", contextObj, e);
                    return null;
                }
            }
        };
    }
}

