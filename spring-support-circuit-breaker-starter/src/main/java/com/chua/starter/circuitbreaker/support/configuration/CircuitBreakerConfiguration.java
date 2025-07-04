package com.chua.starter.circuitbreaker.support.configuration;

import com.chua.starter.circuitbreaker.support.aspect.RateLimiterAspect;
import com.chua.starter.circuitbreaker.support.listener.CircuitBreakerStartupListener;
import com.chua.starter.circuitbreaker.support.metrics.RateLimiterMetrics;
import com.chua.starter.circuitbreaker.support.properties.CircuitBreakerProperties;
import com.chua.starter.circuitbreaker.support.service.CircuitBreakerService;
import com.chua.starter.circuitbreaker.support.service.impl.CircuitBreakerServiceImpl;
import com.chua.starter.circuitbreaker.support.utils.RateLimiterKeyGenerator;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * 熔断降级自动配置类
 * 
 * 提供熔断器、重试、限流、舱壁隔离、超时控制等功能的自动配置。
 * 基于Resilience4j实现，支持注解和编程式两种使用方式。
 * 
 * @author CH
 * @since 2024/12/20
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(CircuitBreakerProperties.class)
@ConditionalOnProperty(prefix = CircuitBreakerProperties.PRE, name = "enable", havingValue = "true", matchIfMissing = true)
public class CircuitBreakerConfiguration {

    private final CircuitBreakerProperties properties;

    /**
     * 熔断器注册表
     * 
     * @return CircuitBreakerRegistry
     */
    @Bean
    @ConditionalOnMissingBean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        log.info(">>>>>>> 初始化熔断器注册表");
        
        // 创建默认配置
        CircuitBreakerConfig defaultConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(properties.getCircuitBreaker().getFailureRateThreshold())
                .slowCallRateThreshold(properties.getCircuitBreaker().getSlowCallRateThreshold())
                .slowCallDurationThreshold(properties.getCircuitBreaker().getSlowCallDurationThreshold())
                .minimumNumberOfCalls(properties.getCircuitBreaker().getMinimumNumberOfCalls())
                .slidingWindowSize(properties.getCircuitBreaker().getSlidingWindowSize())
                .waitDurationInOpenState(properties.getCircuitBreaker().getWaitDurationInOpenState())
                .permittedNumberOfCallsInHalfOpenState(properties.getCircuitBreaker().getPermittedNumberOfCallsInHalfOpenState())
                .automaticTransitionFromOpenToHalfOpenEnabled(properties.getCircuitBreaker().isAutomaticTransitionFromOpenToHalfOpenEnabled())
                .build();

        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(defaultConfig);

        // 注册实例配置
        properties.getCircuitBreaker().getInstances().forEach((name, instanceConfig) -> {
            CircuitBreakerConfig.Builder configBuilder = CircuitBreakerConfig.from(defaultConfig);
            
            if (instanceConfig.getFailureRateThreshold() != null) {
                configBuilder.failureRateThreshold(instanceConfig.getFailureRateThreshold());
            }
            if (instanceConfig.getSlowCallRateThreshold() != null) {
                configBuilder.slowCallRateThreshold(instanceConfig.getSlowCallRateThreshold());
            }
            if (instanceConfig.getSlowCallDurationThreshold() != null) {
                configBuilder.slowCallDurationThreshold(instanceConfig.getSlowCallDurationThreshold());
            }
            if (instanceConfig.getMinimumNumberOfCalls() != null) {
                configBuilder.minimumNumberOfCalls(instanceConfig.getMinimumNumberOfCalls());
            }
            if (instanceConfig.getSlidingWindowSize() != null) {
                configBuilder.slidingWindowSize(instanceConfig.getSlidingWindowSize());
            }
            if (instanceConfig.getWaitDurationInOpenState() != null) {
                configBuilder.waitDurationInOpenState(instanceConfig.getWaitDurationInOpenState());
            }
            if (instanceConfig.getPermittedNumberOfCallsInHalfOpenState() != null) {
                configBuilder.permittedNumberOfCallsInHalfOpenState(instanceConfig.getPermittedNumberOfCallsInHalfOpenState());
            }
            if (instanceConfig.getAutomaticTransitionFromOpenToHalfOpenEnabled() != null) {
                configBuilder.automaticTransitionFromOpenToHalfOpenEnabled(instanceConfig.getAutomaticTransitionFromOpenToHalfOpenEnabled());
            }

            registry.circuitBreaker(name, configBuilder.build());
            log.info(">>>>>>> 注册熔断器实例: {}", name);
        });

        return registry;
    }

    /**
     * 重试注册表
     * 
     * @return RetryRegistry
     */
    @Bean
    @ConditionalOnMissingBean
    public RetryRegistry retryRegistry() {
        log.info(">>>>>>> 初始化重试注册表");
        
        // 创建默认配置
        RetryConfig defaultConfig = RetryConfig.custom()
                .maxAttempts(properties.getRetry().getMaxAttempts())
                .waitDuration(properties.getRetry().getWaitDuration())
                .intervalFunction(IntervalFunction.ofExponentialBackoff(
                        properties.getRetry().getWaitDuration(),
                        properties.getRetry().getIntervalMultiplier(),
                        properties.getRetry().getMaxWaitDuration()))
                .build();

        RetryRegistry registry = RetryRegistry.of(defaultConfig);

        // 注册实例配置
        properties.getRetry().getInstances().forEach((name, instanceConfig) -> {
            RetryConfig.Builder configBuilder = RetryConfig.from(defaultConfig);
            
            if (instanceConfig.getMaxAttempts() != null) {
                configBuilder.maxAttempts(instanceConfig.getMaxAttempts());
            }
            if (instanceConfig.getWaitDuration() != null) {
                configBuilder.waitDuration(instanceConfig.getWaitDuration());
            }
            if (instanceConfig.getIntervalMultiplier() != null && instanceConfig.getMaxWaitDuration() != null) {
                configBuilder.intervalFunction(IntervalFunction.ofExponentialBackoff(
                        instanceConfig.getWaitDuration() != null ? instanceConfig.getWaitDuration() : properties.getRetry().getWaitDuration(),
                        instanceConfig.getIntervalMultiplier(),
                        instanceConfig.getMaxWaitDuration()));
            }

            registry.retry(name, configBuilder.build());
            log.info(">>>>>>> 注册重试实例: {}", name);
        });

        return registry;
    }

    /**
     * 限流器注册表
     * 
     * @return RateLimiterRegistry
     */
    @Bean
    @ConditionalOnMissingBean
    public RateLimiterRegistry rateLimiterRegistry() {
        log.info(">>>>>>> 初始化限流器注册表");
        
        // 创建默认配置
        RateLimiterConfig defaultConfig = RateLimiterConfig.custom()
                .limitRefreshPeriod(properties.getRateLimiter().getLimitRefreshPeriod())
                .limitForPeriod(properties.getRateLimiter().getLimitForPeriod())
                .timeoutDuration(properties.getRateLimiter().getTimeoutDuration())
                .build();

        RateLimiterRegistry registry = RateLimiterRegistry.of(defaultConfig);

        // 注册实例配置
        properties.getRateLimiter().getInstances().forEach((name, instanceConfig) -> {
            RateLimiterConfig.Builder configBuilder = RateLimiterConfig.from(defaultConfig);
            
            if (instanceConfig.getLimitRefreshPeriod() != null) {
                configBuilder.limitRefreshPeriod(instanceConfig.getLimitRefreshPeriod());
            }
            if (instanceConfig.getLimitForPeriod() != null) {
                configBuilder.limitForPeriod(instanceConfig.getLimitForPeriod());
            }
            if (instanceConfig.getTimeoutDuration() != null) {
                configBuilder.timeoutDuration(instanceConfig.getTimeoutDuration());
            }

            registry.rateLimiter(name, configBuilder.build());
            log.info(">>>>>>> 注册限流器实例: {}", name);
        });

        return registry;
    }

    /**
     * 舱壁隔离注册表
     * 
     * @return BulkheadRegistry
     */
    @Bean
    @ConditionalOnMissingBean
    public BulkheadRegistry bulkheadRegistry() {
        log.info(">>>>>>> 初始化舱壁隔离注册表");
        
        // 创建默认配置
        BulkheadConfig defaultConfig = BulkheadConfig.custom()
                .maxConcurrentCalls(properties.getBulkhead().getMaxConcurrentCalls())
                .maxWaitDuration(properties.getBulkhead().getMaxWaitDuration())
                .build();

        BulkheadRegistry registry = BulkheadRegistry.of(defaultConfig);

        // 注册实例配置
        properties.getBulkhead().getInstances().forEach((name, instanceConfig) -> {
            BulkheadConfig.Builder configBuilder = BulkheadConfig.from(defaultConfig);
            
            if (instanceConfig.getMaxConcurrentCalls() != null) {
                configBuilder.maxConcurrentCalls(instanceConfig.getMaxConcurrentCalls());
            }
            if (instanceConfig.getMaxWaitDuration() != null) {
                configBuilder.maxWaitDuration(instanceConfig.getMaxWaitDuration());
            }

            registry.bulkhead(name, configBuilder.build());
            log.info(">>>>>>> 注册舱壁隔离实例: {}", name);
        });

        return registry;
    }

    /**
     * 超时控制注册表
     * 
     * @return TimeLimiterRegistry
     */
    @Bean
    @ConditionalOnMissingBean
    public TimeLimiterRegistry timeLimiterRegistry() {
        log.info(">>>>>>> 初始化超时控制注册表");
        
        // 创建默认配置
        TimeLimiterConfig defaultConfig = TimeLimiterConfig.custom()
                .timeoutDuration(properties.getTimeLimiter().getTimeoutDuration())
                .cancelRunningFuture(properties.getTimeLimiter().isCancelRunningFuture())
                .build();

        TimeLimiterRegistry registry = TimeLimiterRegistry.of(defaultConfig);

        // 注册实例配置
        properties.getTimeLimiter().getInstances().forEach((name, instanceConfig) -> {
            TimeLimiterConfig.Builder configBuilder = TimeLimiterConfig.from(defaultConfig);
            
            if (instanceConfig.getTimeoutDuration() != null) {
                configBuilder.timeoutDuration(instanceConfig.getTimeoutDuration());
            }
            if (instanceConfig.getCancelRunningFuture() != null) {
                configBuilder.cancelRunningFuture(instanceConfig.getCancelRunningFuture());
            }

            registry.timeLimiter(name, configBuilder.build());
            log.info(">>>>>>> 注册超时控制实例: {}", name);
        });

        return registry;
    }

    /**
     * 熔断降级服务
     * 
     * @param circuitBreakerRegistry 熔断器注册表
     * @param retryRegistry 重试注册表
     * @param rateLimiterRegistry 限流器注册表
     * @param bulkheadRegistry 舱壁隔离注册表
     * @param timeLimiterRegistry 超时控制注册表
     * @return CircuitBreakerService
     */
    @Bean
    @ConditionalOnMissingBean
    public CircuitBreakerService circuitBreakerService(
            CircuitBreakerRegistry circuitBreakerRegistry,
            RetryRegistry retryRegistry,
            RateLimiterRegistry rateLimiterRegistry,
            BulkheadRegistry bulkheadRegistry,
            TimeLimiterRegistry timeLimiterRegistry) {
        log.info(">>>>>>> 初始化熔断降级服务");
        return new CircuitBreakerServiceImpl(
                circuitBreakerRegistry,
                retryRegistry,
                rateLimiterRegistry,
                bulkheadRegistry,
                timeLimiterRegistry
        );
    }

    /**
     * 限流键生成器
     */
    @Bean
    @ConditionalOnMissingBean
    public RateLimiterKeyGenerator rateLimiterKeyGenerator() {
        log.info(">>>>>>> 初始化限流键生成器");
        return new RateLimiterKeyGenerator();
    }

    /**
     * 限流指标收集器
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(name = "io.micrometer.core.instrument.MeterRegistry")
    public RateLimiterMetrics rateLimiterMetrics(io.micrometer.core.instrument.MeterRegistry meterRegistry) {
        log.info(">>>>>>> 初始化限流指标收集器");
        return new RateLimiterMetrics(meterRegistry);
    }

    /**
     * 限流切面
     */
    @Bean
    @ConditionalOnMissingBean
    public RateLimiterAspect rateLimiterAspect(
            RateLimiterRegistry rateLimiterRegistry,
            CircuitBreakerProperties properties,
            CircuitBreakerService circuitBreakerService,
            RateLimiterKeyGenerator keyGenerator,
            @Autowired(required = false) RateLimiterMetrics rateLimiterMetrics) {
        log.info(">>>>>>> 初始化限流切面");
        return rateLimiterMetrics != null ?
            new RateLimiterAspect(rateLimiterRegistry, properties, circuitBreakerService, keyGenerator, rateLimiterMetrics) :
            new RateLimiterAspect(rateLimiterRegistry, properties, circuitBreakerService, keyGenerator, null);
    }

    /**
     * 启动监听器
     */
    @Bean
    @ConditionalOnMissingBean
    public CircuitBreakerStartupListener circuitBreakerStartupListener(Environment environment) {
        log.info(">>>>>>> 初始化启动监听器");
        return new CircuitBreakerStartupListener(properties, environment);
    }
}
