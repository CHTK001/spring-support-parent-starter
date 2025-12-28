package com.chua.starter.strategy.config;

import com.chua.starter.strategy.aspect.*;
import com.chua.starter.strategy.distributed.StrategyDebounce;
import com.chua.starter.strategy.distributed.StrategyRateLimiter;
import com.chua.starter.strategy.interceptor.CircuitBreakerInterceptor;
import com.chua.starter.strategy.interceptor.IpAccessControlInterceptor;
import com.chua.starter.strategy.service.SysCircuitBreakerConfigurationService;
import com.chua.starter.strategy.service.SysCircuitBreakerRecordService;
import com.chua.starter.strategy.template.DefaultLockTemplate;
import com.chua.starter.strategy.template.DefaultStrategyTemplate;
import com.chua.starter.strategy.template.LockTemplate;
import com.chua.starter.strategy.template.StrategyTemplate;
import com.chua.starter.strategy.warmup.WarmupExecutor;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.data.redis.core.StringRedisTemplate;

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
        "com.chua.starter.strategy.handler",
        "com.chua.starter.strategy.logger",
        "com.chua.starter.strategy.actuator",
        "com.chua.starter.strategy.dynamic"
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

    /**
     * Resilience4j CircuitBreaker 注册中心
     *
     * @return CircuitBreaker 注册中心
     */
    @Bean
    @ConditionalOnMissingBean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        return CircuitBreakerRegistry.ofDefaults();
    }

    /**
     * Resilience4j Retry 注册中心
     *
     * @return Retry 注册中心
     */
    @Bean
    @ConditionalOnMissingBean
    public RetryRegistry retryRegistry() {
        return RetryRegistry.ofDefaults();
    }

    /**
     * 策略模板
     * <p>
     * 提供统一的策略管理和执行API
     * </p>
     *
     * @param rateLimiterRegistry    限流器注册中心
     * @param circuitBreakerRegistry 熔断器注册中心
     * @param retryRegistry          重试器注册中心
     * @return 策略模板
     */
    @Bean
    @ConditionalOnMissingBean
    public StrategyTemplate strategyTemplate(
            @Autowired(required = false) RateLimiterRegistry rateLimiterRegistry,
            @Autowired(required = false) CircuitBreakerRegistry circuitBreakerRegistry,
            @Autowired(required = false) RetryRegistry retryRegistry) {
        return new DefaultStrategyTemplate(rateLimiterRegistry, circuitBreakerRegistry, retryRegistry);
    }

    /**
     * 锁模板
     * <p>
     * 提供统一的分布式锁API，默认使用JVM内存锁实现。
     * 分布式环境请引入 redis-starter 使用 Redisson 实现。
     * </p>
     *
     * @return 锁模板
     */
    @Bean
    @ConditionalOnMissingBean
    public LockTemplate lockTemplate() {
        return new DefaultLockTemplate();
    }

    // ==================== 新增组件配置 ====================

    /**
     * Resilience4j Bulkhead 注册中心
     */
    @Bean
    @ConditionalOnMissingBean
    public BulkheadRegistry bulkheadRegistry() {
        return BulkheadRegistry.ofDefaults();
    }

    /**
     * Resilience4j TimeLimiter 注册中心
     */
    @Bean
    @ConditionalOnMissingBean
    public TimeLimiterRegistry timeLimiterRegistry() {
        return TimeLimiterRegistry.ofDefaults();
    }

    /**
     * 重试切面
     */
    @Bean
    @ConditionalOnMissingBean
    public RetryAspect retryAspect() {
        return new RetryAspect();
    }

    /**
     * 超时控制切面
     */
    @Bean
    @ConditionalOnMissingBean
    public TimeLimiterAspect timeLimiterAspect() {
        return new TimeLimiterAspect();
    }

    /**
     * 舱壁隔离切面
     */
    @Bean
    @ConditionalOnMissingBean
    public BulkheadAspect bulkheadAspect() {
        return new BulkheadAspect();
    }

    /**
     * 熔断HTTP拦截器
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({SysCircuitBreakerConfigurationService.class, SysCircuitBreakerRecordService.class})
    public CircuitBreakerInterceptor circuitBreakerInterceptor(
            SysCircuitBreakerConfigurationService configService,
            SysCircuitBreakerRecordService recordService) {
        return new CircuitBreakerInterceptor(configService, recordService);
    }

    /**
     * 策略限流器（默认本地实现）
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "plugin.strategy.rate-limiter.type", havingValue = "local", matchIfMissing = true)
    public StrategyRateLimiter localRateLimiter() {
        return new com.chua.starter.strategy.distributed.LocalRateLimiter();
    }

    /**
     * 策略防抖器（默认本地实现）
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "plugin.strategy.debounce.type", havingValue = "local", matchIfMissing = true)
    public StrategyDebounce localDebounce() {
        return new com.chua.starter.strategy.distributed.LocalDebounce();
    }

    /**
     * 策略限流器（Redis实现）
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "plugin.strategy.rate-limiter.type", havingValue = "redis")
    @ConditionalOnBean(StringRedisTemplate.class)
    public StrategyRateLimiter redisRateLimiter(StringRedisTemplate stringRedisTemplate) {
        return new com.chua.starter.strategy.distributed.RedisRateLimiter(stringRedisTemplate);
    }

    /**
     * 策略防抖器（Redis实现）
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "plugin.strategy.debounce.type", havingValue = "redis")
    @ConditionalOnBean(StringRedisTemplate.class)
    public StrategyDebounce redisDebounce(StringRedisTemplate stringRedisTemplate) {
        return new com.chua.starter.strategy.distributed.RedisDebounce(stringRedisTemplate);
    }

    // ==================== 新增策略组件 ====================

    /**
     * 预热执行器
     */
    @Bean
    @ConditionalOnMissingBean
    public WarmupExecutor warmupExecutor() {
        return new WarmupExecutor();
    }

    /**
     * 分布式锁切面
     */
    @Bean
    @ConditionalOnMissingBean
    public DistributedLockAspect distributedLockAspect() {
        return new DistributedLockAspect();
    }

    /**
     * 幂等性控制切面
     */
    @Bean
    @ConditionalOnMissingBean
    public IdempotentAspect idempotentAspect() {
        return new IdempotentAspect();
    }

    /**
     * 请求合并切面
     */
    @Bean
    @ConditionalOnMissingBean
    public RequestCollapseAspect requestCollapseAspect() {
        return new RequestCollapseAspect();
    }
}
