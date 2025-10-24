package com.chua.starter.pay.support.statemachine.persist;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.starter.pay.support.entity.PayMerchantOrder;
import com.chua.starter.pay.support.enums.PayOrderEvent;
import com.chua.starter.pay.support.enums.PayOrderStatus;
import com.chua.starter.pay.support.mapper.PayMerchantOrderMapper;
import com.chua.starter.pay.support.properties.PayProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.StateMachineContext;
import org.springframework.statemachine.config.StateMachineFactory;
import org.springframework.statemachine.support.DefaultStateMachineContext;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 基于数据库 + Redis 的状态机持久化实现
 * <p>
 * 使用 Redis 作为一级缓存，数据库作为持久化存储。
 * 读取时优先从 Redis 获取，不存在时从数据库加载并缓存到 Redis。
 * 写入时同时更新 Redis 和数据库。
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2025/10/24
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "plugin.pay", name = "state-machine-persist-type", havingValue = "DATABASE_REDIS")
public class DatabaseRedisStateMachinePersister implements PayOrderStateMachinePersister {

    private final PayMerchantOrderMapper payMerchantOrderMapper;
    private final StateMachineFactory<PayOrderStatus, PayOrderEvent> stateMachineFactory;
    private final RedisTemplate<String, Object> redisTemplate;
    private final PayProperties payProperties;

    /**
     * Redis 缓存键前缀
     */
    private static final String CACHE_KEY_PREFIX = "pay:state_machine:";

    @Override
    public void persist(StateMachine<PayOrderStatus, PayOrderEvent> stateMachine, String orderCode) throws Exception {
        if (stateMachine == null || orderCode == null) {
            log.warn("状态机或订单编号为空，无法持久化");
            return;
        }

        try {
            // 获取当前状态
            PayOrderStatus currentStatus = stateMachine.getState().getId();

            // 1. 更新数据库
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

            // 2. 更新 Redis 缓存
            String cacheKey = getCacheKey(orderCode);
            redisTemplate.opsForValue().set(
                    cacheKey,
                    currentStatus.getCode(),
                    payProperties.getRedisCacheExpireSeconds(),
                    TimeUnit.SECONDS
            );

            log.debug("状态机状态已持久化（数据库+Redis）, 订单编号: {}, 状态: {}", orderCode, currentStatus.getName());

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
            PayOrderStatus currentStatus = null;

            // 1. 先从 Redis 获取
            String cacheKey = getCacheKey(orderCode);
            String statusCode = (String) redisTemplate.opsForValue().get(cacheKey);

            if (statusCode != null) {
                currentStatus = PayOrderStatus.parse(statusCode);
                log.debug("从 Redis 缓存恢复状态机状态, 订单编号: {}, 状态: {}",
                        orderCode, currentStatus != null ? currentStatus.getName() : "null");
            }

            // 2. Redis 不存在，从数据库加载
            if (currentStatus == null) {
                PayMerchantOrder order = payMerchantOrderMapper.selectOne(
                        new LambdaQueryWrapper<PayMerchantOrder>()
                                .eq(PayMerchantOrder::getPayMerchantOrderCode, orderCode)
                );

                if (order == null) {
                    log.error("订单不存在，无法恢复状态机状态, 订单编号: {}", orderCode);
                    return stateMachine;
                }

                currentStatus = order.getPayMerchantOrderStatus();
                if (currentStatus == null) {
                    log.warn("订单状态为空，使用默认状态, 订单编号: {}", orderCode);
                    currentStatus = PayOrderStatus.PAY_CREATE;
                }

                // 3. 缓存到 Redis
                redisTemplate.opsForValue().set(
                        cacheKey,
                        currentStatus.getCode(),
                        payProperties.getRedisCacheExpireSeconds(),
                        TimeUnit.SECONDS
                );

                log.debug("从数据库恢复状态机状态并缓存到 Redis, 订单编号: {}, 状态: {}",
                        orderCode, currentStatus.getName());
            }

            // 4. 创建状态机上下文
            StateMachineContext<PayOrderStatus, PayOrderEvent> context =
                    new DefaultStateMachineContext<>(currentStatus, null, null, null);

            // 5. 重置状态机到指定状态
            stateMachine.stopReactively().block();
            stateMachine.getStateMachineAccessor()
                    .doWithAllRegions(access -> access.resetStateMachineReactively(context).block());
            stateMachine.startReactively().block();

            return stateMachine;

        } catch (Exception e) {
            log.error("恢复状态机状态失败, 订单编号: {}", orderCode, e);
            throw e;
        }
    }

    @Override
    public void delete(String orderCode) throws Exception {
        try {
            // 删除 Redis 缓存
            String cacheKey = getCacheKey(orderCode);
            redisTemplate.delete(cacheKey);

            log.debug("状态机 Redis 缓存已删除, 订单编号: {}", orderCode);

        } catch (Exception e) {
            log.error("删除状态机 Redis 缓存失败, 订单编号: {}", orderCode, e);
            throw e;
        }
    }

    @Override
    public String getType() {
        return "DATABASE_REDIS";
    }

    /**
     * 获取 Redis 缓存键
     *
     * @param orderCode 订单编号
     * @return Redis 缓存键
     */
    private String getCacheKey(String orderCode) {
        return CACHE_KEY_PREFIX + orderCode;
    }
}

