package com.chua.starter.strategy.config;

import com.chua.starter.strategy.aspect.*;
import com.chua.starter.strategy.distributed.StrategyDebounce;
import com.chua.starter.strategy.distributed.StrategyRateLimiter;
import com.chua.starter.strategy.config.StrategyProperties;
import com.chua.starter.strategy.interceptor.CircuitBreakerInterceptor;
import com.chua.starter.strategy.interceptor.IpAccessControlInterceptor;
import com.chua.starter.strategy.interceptor.PathTraversalInterceptor;
import com.chua.starter.strategy.interceptor.SqlInjectionProtectionInterceptor;
import com.chua.starter.strategy.interceptor.CsrfProtectionInterceptor;
import com.chua.starter.strategy.interceptor.RequestSizeLimitInterceptor;
import com.chua.starter.strategy.interceptor.HttpMethodRestrictionInterceptor;
import com.chua.starter.strategy.interceptor.RequestTimeoutInterceptor;
import com.chua.starter.strategy.interceptor.ParameterCountLimitInterceptor;
import com.chua.starter.strategy.interceptor.ContentSecurityPolicyInterceptor;
import com.chua.starter.strategy.interceptor.ClickjackingProtectionInterceptor;
import com.chua.starter.strategy.service.SysCircuitBreakerConfigurationService;
import com.chua.starter.strategy.service.SysCircuitBreakerRecordService;
import com.chua.starter.strategy.template.DefaultLockTemplate;
import com.chua.starter.strategy.template.DefaultLimitTemplate;
import com.chua.starter.strategy.template.DefaultStrategyTemplate;
import com.chua.starter.strategy.template.LimitTemplate;
import com.chua.starter.strategy.template.LockTemplate;
import com.chua.starter.strategy.template.StrategyTemplate;
import com.chua.common.support.core.spi.ServiceProvider;
import com.chua.starter.strategy.util.StrategyEventPublisher;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.data.redis.core.StringRedisTemplate;
import jakarta.annotation.PostConstruct;

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
        return new DefaultStrategyTemplate();
    }

    /**
     * 锁模板
     * <p>
     * 提供统一的分布式锁API，通过 SPI 机制自动发现和加载锁实现。
     * 默认使用 local 类型的锁实现（基于 JVM 内存）。
     * 分布式环境可通过 SPI 注册 Redis、Zookeeper 等实现。
     * </p>
     *
     * @return 锁模板
     */
    @Bean
    @ConditionalOnMissingBean
    public LockTemplate lockTemplate(@Value("${plugin.strategy.lock.type:local}") String lockType) {
        var provider = ServiceProvider.of(LockTemplate.class);
        var template = provider.getExtension(lockType);
        if (template == null) {
            // 如果指定类型不存在，使用默认实现
            return new DefaultLockTemplate();
        }
        return template;
    }

    /**
     * 限流模板
     * <p>
     * 提供统一的限流API，通过 SPI 机制自动发现和加载限流器实现。
     * 默认使用 default 类型的限流模板，内部会通过 SPI 获取限流器实现。
     * </p>
     *
     * @return 限流模板
     */
    @Bean
    @ConditionalOnMissingBean
    public LimitTemplate limitTemplate(@Value("${plugin.strategy.limit.type:default}") String limitType) {
        var provider = ServiceProvider.of(LimitTemplate.class);
        var template = provider.getExtension(limitType);
        if (template == null) {
            // 如果指定类型不存在，使用默认实现
            return new DefaultLimitTemplate();
        }
        return template;
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
     * 路径穿透防护拦截器
     */
    @Bean
    @ConditionalOnMissingBean
    public PathTraversalInterceptor pathTraversalInterceptor(StrategyProperties strategyProperties) {
        return new PathTraversalInterceptor(strategyProperties.getPathTraversal());
    }

    /**
     * SQL注入防护拦截器
     */
    @Bean
    @ConditionalOnMissingBean
    public SqlInjectionProtectionInterceptor sqlInjectionProtectionInterceptor(StrategyProperties strategyProperties) {
        return new SqlInjectionProtectionInterceptor(strategyProperties.getSqlInjection());
    }

    /**
     * CSRF防护拦截器
     */
    @Bean
    @ConditionalOnMissingBean
    public CsrfProtectionInterceptor csrfProtectionInterceptor(StrategyProperties strategyProperties) {
        return new CsrfProtectionInterceptor(strategyProperties.getCsrf());
    }

    /**
     * 请求大小限制拦截器
     */
    @Bean
    @ConditionalOnMissingBean
    public RequestSizeLimitInterceptor requestSizeLimitInterceptor(StrategyProperties strategyProperties) {
        return new RequestSizeLimitInterceptor(strategyProperties.getRequestSizeLimit());
    }

    /**
     * HTTP方法限制拦截器
     */
    @Bean
    @ConditionalOnMissingBean
    public HttpMethodRestrictionInterceptor httpMethodRestrictionInterceptor(StrategyProperties strategyProperties) {
        return new HttpMethodRestrictionInterceptor(strategyProperties.getHttpMethodRestriction());
    }

    /**
     * 请求超时控制拦截器
     */
    @Bean
    @ConditionalOnMissingBean
    public RequestTimeoutInterceptor requestTimeoutInterceptor(StrategyProperties strategyProperties) {
        return new RequestTimeoutInterceptor(strategyProperties.getRequestTimeout());
    }

    /**
     * 参数数量限制拦截器
     */
    @Bean
    @ConditionalOnMissingBean
    public ParameterCountLimitInterceptor parameterCountLimitInterceptor(StrategyProperties strategyProperties) {
        return new ParameterCountLimitInterceptor(strategyProperties.getParameterCountLimit());
    }

    /**
     * 内容安全策略拦截器
     */
    @Bean
    @ConditionalOnMissingBean
    public ContentSecurityPolicyInterceptor contentSecurityPolicyInterceptor(StrategyProperties strategyProperties) {
        return new ContentSecurityPolicyInterceptor(strategyProperties.getContentSecurityPolicy());
    }

    /**
     * 点击劫持防护拦截器
     */
    @Bean
    @ConditionalOnMissingBean
    public ClickjackingProtectionInterceptor clickjackingProtectionInterceptor(StrategyProperties strategyProperties) {
        return new ClickjackingProtectionInterceptor(strategyProperties.getClickjackingProtection());
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

    /**
     * 初始化事件发布器
     * <p>
     * 将Spring的ApplicationEventPublisher注入到StrategyEventPublisher中
     * </p>
     *
     * @param eventPublisher Spring事件发布器
     */
    @Bean
    @ConditionalOnMissingBean
    public StrategyEventPublisherInitializer strategyEventPublisherInitializer(
            @Autowired(required = false) ApplicationEventPublisher eventPublisher) {
        return new StrategyEventPublisherInitializer(eventPublisher);
    }

    /**
     * 事件发布器初始化器
     */
    public static class StrategyEventPublisherInitializer {
        private final ApplicationEventPublisher eventPublisher;

        public StrategyEventPublisherInitializer(ApplicationEventPublisher eventPublisher) {
            this.eventPublisher = eventPublisher;
        }

        @PostConstruct
        public void init() {
            StrategyEventPublisher.setEventPublisher(eventPublisher);
        }
    }
}
