package com.chua.starter.strategy.config;

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

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
}
