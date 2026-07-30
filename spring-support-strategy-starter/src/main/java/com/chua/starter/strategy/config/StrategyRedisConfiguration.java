package com.chua.starter.strategy.config;

import com.chua.starter.strategy.distributed.RedisDebounce;
import com.chua.starter.strategy.distributed.RedisRateLimiter;
import com.chua.starter.strategy.distributed.StrategyDebounce;
import com.chua.starter.strategy.distributed.StrategyRateLimiter;
import com.chua.starter.strategy.support.SpringDataRedisStrategyRedisSupport;
import com.chua.starter.strategy.support.StrategyRedisSupport;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * 策略模块 Redis 配置
 * <p>
 * 提供 StringRedisTemplate 的保底注册。
 * 当项目中存在 RedisConnectionFactory 但没有 StringRedisTemplate 时自动创建。
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2025-12-28
 */
@Configuration
@ConditionalOnClass({RedisConnectionFactory.class, StringRedisTemplate.class})
@AutoConfigureAfter(RedisAutoConfiguration.class)
public class StrategyRedisConfiguration {

    /**
     * 保底 StringRedisTemplate
     * <p>
     * 当存在 RedisConnectionFactory 但没有 StringRedisTemplate Bean 时创建。
     * 使用默认配置，key 和 value 都采用 String 序列化。
     * </p>
     *
     * @param connectionFactory Redis 连接工厂
     * @return StringRedisTemplate
     */
    @Bean
    @ConditionalOnMissingBean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        StringRedisTemplate template = new StringRedisTemplate();
        template.setConnectionFactory(connectionFactory);
        return template;
    }

    /**
     * Redis 支撑能力实现。
     *
     * @param stringRedisTemplate Redis 模板
     * @param listenerContainerProvider Redis 订阅容器提供者
     * @return Redis 能力实现
     */
    @Bean
    @ConditionalOnMissingBean(StrategyRedisSupport.class)
    public StrategyRedisSupport strategyRedisSupport(
            StringRedisTemplate stringRedisTemplate,
            ObjectProvider<RedisMessageListenerContainer> listenerContainerProvider) {
        return new SpringDataRedisStrategyRedisSupport(
                stringRedisTemplate,
                listenerContainerProvider.getIfAvailable()
        );
    }

    /**
     * Redis 策略限流器
     * <p>
     * 仅在显式选择 Redis 限流模式时注册，避免本地模式被无关依赖拖入。
     * </p>
     *
     * @param stringRedisTemplate Redis 模板
     * @return Redis 限流器
     */
    @Bean
    @ConditionalOnMissingBean(StrategyRateLimiter.class)
    @ConditionalOnProperty(name = "plugin.strategy.rate-limiter.type", havingValue = "redis")
    public StrategyRateLimiter redisRateLimiter(StringRedisTemplate stringRedisTemplate) {
        return new RedisRateLimiter(stringRedisTemplate);
    }

    /**
     * Redis 策略防抖器
     * <p>
     * 仅在显式选择 Redis 防抖模式时注册，避免本地模式加载 Redis 相关实现。
     * </p>
     *
     * @param stringRedisTemplate Redis 模板
     * @return Redis 防抖器
     */
    @Bean
    @ConditionalOnMissingBean(StrategyDebounce.class)
    @ConditionalOnProperty(name = "plugin.strategy.debounce.type", havingValue = "redis")
    public StrategyDebounce redisDebounce(StringRedisTemplate stringRedisTemplate) {
        return new RedisDebounce(stringRedisTemplate);
    }
}
