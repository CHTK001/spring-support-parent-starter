package com.chua.starter.circuitbreaker.support.configuration;

import com.chua.starter.circuitbreaker.support.properties.CircuitBreakerProperties;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.micrometer.tagged.TaggedBulkheadMetrics;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.github.resilience4j.micrometer.tagged.TaggedRateLimiterMetrics;
import io.github.resilience4j.micrometer.tagged.TaggedRetryMetrics;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 熔断降级监控指标配置类
 * 
 * 配置Resilience4j与Micrometer的集成，提供详细的监控指标。
 * 包括熔断器、重试、限流器、舱壁隔离等功能的监控指标。
 * 
 * @author CH
 * @since 2024/12/20
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(CircuitBreakerProperties.class)
@ConditionalOnClass({MeterRegistry.class})
@ConditionalOnProperty(prefix = CircuitBreakerProperties.PRE, name = "enable", havingValue = "true", matchIfMissing = true)
public class CircuitBreakerMetricsConfiguration {

    /**
     * 熔断器监控指标
     * 
     * @param circuitBreakerRegistry 熔断器注册表
     * @param meterRegistry 指标注册表
     * @return TaggedCircuitBreakerMetrics
     */
    @Bean
    public TaggedCircuitBreakerMetrics circuitBreakerMetrics(CircuitBreakerRegistry circuitBreakerRegistry,
                                                           MeterRegistry meterRegistry) {
        log.info(">>>>>>> 初始化熔断器监控指标");
        return TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(circuitBreakerRegistry);
    }

    /**
     * 重试监控指标
     * 
     * @param retryRegistry 重试注册表
     * @param meterRegistry 指标注册表
     * @return TaggedRetryMetrics
     */
    @Bean
    public TaggedRetryMetrics retryMetrics(RetryRegistry retryRegistry,
                                         MeterRegistry meterRegistry) {
        log.info(">>>>>>> 初始化重试监控指标");
        return TaggedRetryMetrics.ofRetryRegistry(retryRegistry);
    }

    /**
     * 限流器监控指标
     * 
     * @param rateLimiterRegistry 限流器注册表
     * @param meterRegistry 指标注册表
     * @return TaggedRateLimiterMetrics
     */
    @Bean
    public TaggedRateLimiterMetrics rateLimiterMetrics(RateLimiterRegistry rateLimiterRegistry,
                                                     MeterRegistry meterRegistry) {
        log.info(">>>>>>> 初始化限流器监控指标");
        return TaggedRateLimiterMetrics.ofRateLimiterRegistry(rateLimiterRegistry);
    }

    /**
     * 舱壁隔离监控指标
     * 
     * @param bulkheadRegistry 舱壁隔离注册表
     * @param meterRegistry 指标注册表
     * @return TaggedBulkheadMetrics
     */
    @Bean
    public TaggedBulkheadMetrics bulkheadMetrics(BulkheadRegistry bulkheadRegistry,
                                               MeterRegistry meterRegistry) {
        log.info(">>>>>>> 初始化舱壁隔离监控指标");
        return TaggedBulkheadMetrics.ofBulkheadRegistry(bulkheadRegistry);
    }

    /**
     * 自定义指标注册器
     * 
     * @return MeterRegistryCustomizer
     */
    @Bean
    public MeterRegistryCustomizer<MeterRegistry> circuitBreakerMeterRegistryCustomizer() {
        return registry -> {
            // 添加通用标签
            registry.config().commonTags(
                    "application", "spring-support-circuit-breaker",
                    "version", "4.0.0.32"
            );
            log.info(">>>>>>> 配置熔断降级监控指标通用标签");
        };
    }
}
