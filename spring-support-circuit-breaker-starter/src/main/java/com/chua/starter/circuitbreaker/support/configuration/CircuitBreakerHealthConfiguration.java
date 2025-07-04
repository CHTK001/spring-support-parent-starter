package com.chua.starter.circuitbreaker.support.configuration;

import com.chua.starter.circuitbreaker.support.health.CircuitBreakerHealthIndicator;
import com.chua.starter.circuitbreaker.support.properties.CircuitBreakerProperties;
import com.chua.starter.circuitbreaker.support.service.CircuitBreakerService;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 熔断降级健康检查配置类
 * 
 * 提供熔断器、限流器、舱壁隔离等功能的健康检查端点。
 * 集成Spring Boot Actuator，提供详细的健康状态信息。
 * 
 * @author CH
 * @since 2024/12/20
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(CircuitBreakerProperties.class)
@ConditionalOnClass({HealthIndicator.class})
@ConditionalOnProperty(prefix = CircuitBreakerProperties.PRE, name = "enable", havingValue = "true", matchIfMissing = true)
public class CircuitBreakerHealthConfiguration {

    /**
     * 熔断降级健康检查指示器
     * 
     * @param circuitBreakerRegistry 熔断器注册表
     * @param rateLimiterRegistry 限流器注册表
     * @param bulkheadRegistry 舱壁隔离注册表
     * @param circuitBreakerService 熔断降级服务
     * @return CircuitBreakerHealthIndicator
     */
    @Bean
    public CircuitBreakerHealthIndicator circuitBreakerHealthIndicator(
            CircuitBreakerRegistry circuitBreakerRegistry,
            RateLimiterRegistry rateLimiterRegistry,
            BulkheadRegistry bulkheadRegistry,
            CircuitBreakerService circuitBreakerService) {
        log.info(">>>>>>> 初始化熔断降级健康检查指示器");
        return new CircuitBreakerHealthIndicator(
                circuitBreakerRegistry,
                rateLimiterRegistry,
                bulkheadRegistry,
                circuitBreakerService
        );
    }
}
