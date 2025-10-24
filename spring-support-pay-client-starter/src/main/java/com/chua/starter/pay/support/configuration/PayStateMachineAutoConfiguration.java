package com.chua.starter.pay.support.configuration;

import com.chua.starter.pay.support.properties.PayProperties;
import com.chua.starter.pay.support.statemachine.action.PayOrderTransitionAction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 支付状态机自动配置
 * <p>
 * 优化说明：
 * <ul>
 *   <li>1. 启用 PayProperties 配置属性</li>
 *   <li>2. 自动扫描状态机相关组件</li>
 *   <li>3. 根据配置自动选择持久化策略（DATABASE 或 DATABASE_REDIS）</li>
 * </ul>
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2025/10/24
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(PayProperties.class)
@ComponentScan(basePackages = {
        "com.chua.starter.pay.support.statemachine",
        "com.chua.starter.pay.support.properties"
})
public class PayStateMachineAutoConfiguration {

    /**
     * 提供默认的 RedisTemplate（如果不存在）
     * <p>
     * 用于 DATABASE_REDIS 持久化模式。
     * 如果应用中已经配置了 RedisTemplate，则使用应用的配置。
     * </p>
     */
    @Bean
    @ConditionalOnMissingBean(name = "redisTemplate")
    public RedisTemplate<String, Object> redisTemplate(StringRedisTemplate stringRedisTemplate) {
        log.info("使用默认的 RedisTemplate 配置");
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(stringRedisTemplate.getConnectionFactory());
        template.setKeySerializer(stringRedisTemplate.getStringSerializer());
        template.setValueSerializer(stringRedisTemplate.getStringSerializer());
        template.setHashKeySerializer(stringRedisTemplate.getStringSerializer());
        template.setHashValueSerializer(stringRedisTemplate.getStringSerializer());
        return template;
    }

    /**
     * 配置完成后的日志输出
     */
    @Bean
    public Object payStateMachineConfigurationLogger(PayProperties payProperties) {
        log.info("========================================");
        log.info("支付状态机配置");
        log.info("========================================");
        log.info("持久化类型: {}", payProperties.getStateMachinePersistType().getDescription());
        log.info("Redis 缓存过期时间: {} 秒", payProperties.getRedisCacheExpireSeconds());
        log.info("状态机超时时间: {} 毫秒", payProperties.getStateMachineTimeoutMillis());
        log.info("启用状态机日志: {}", payProperties.getEnableStateMachineLog());
        log.info("订单超时时间: {} 分钟", payProperties.getOrderTimeoutMinutes());
        log.info("========================================");
        return new Object();
    }
}

