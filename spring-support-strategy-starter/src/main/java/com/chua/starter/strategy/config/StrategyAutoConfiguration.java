package com.chua.starter.strategy.config;

import com.chua.starter.strategy.aspect.DebounceAspect;
import com.chua.starter.strategy.aspect.RateLimiterAspect;
import com.chua.starter.strategy.aspect.SysCircuitBreakerConfigurationAspect;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 策略模块自动配置
 * <p>
 * 提供限流、熔断、防抖等策略功能的自动配置。
 * 支持 @RateLimiter 注解的 AOP 扫描。
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2025-12-02
 */
@Configuration
@EnableAsync
@EnableAspectJAutoProxy
@ComponentScan(basePackages = {
        "com.chua.starter.strategy.controller",
        "com.chua.starter.strategy.service",
        "com.chua.starter.strategy.aspect",
        "com.chua.starter.strategy.interceptor",
        "com.chua.starter.strategy.handler"
})
@MapperScan("com.chua.starter.strategy.mapper")
public class StrategyAutoConfiguration {

    /**
     * 熔断配置切面
     *
     * @param circuitBreakerRegistry 熔断器注册中心
     * @return 熔断配置切面
     */
    @Bean
    @ConditionalOnBean(CircuitBreakerRegistry.class)
    @ConditionalOnMissingBean
    public SysCircuitBreakerConfigurationAspect sysCircuitBreakerConfigurationAspect(
            CircuitBreakerRegistry circuitBreakerRegistry) {
        return new SysCircuitBreakerConfigurationAspect(circuitBreakerRegistry);
    }

    /**
     * RateLimiter 注解切面
     * <p>
     * 扫描 @RateLimiter 注解，提供方法级别的限流支持。
     * </p>
     *
     * @return RateLimiter 切面
     */
    @Bean
    @ConditionalOnMissingBean
    public RateLimiterAspect rateLimiterAspect() {
        return new RateLimiterAspect();
    }

    /**
     * Debounce 注解切面
     * <p>
     * 扫描 @Debounce 注解，提供方法级别的防抖支持。
     * </p>
     *
     * @return Debounce 切面
     */
    @Bean
    @ConditionalOnMissingBean
    public DebounceAspect debounceAspect() {
        return new DebounceAspect();
    }

    /**
     * Resilience4j RateLimiter 注册中心
     *
     * @return RateLimiter 注册中心
     */
    @Bean
    @ConditionalOnMissingBean
    public RateLimiterRegistry rateLimiterRegistry() {
        return RateLimiterRegistry.ofDefaults();
    }
}
