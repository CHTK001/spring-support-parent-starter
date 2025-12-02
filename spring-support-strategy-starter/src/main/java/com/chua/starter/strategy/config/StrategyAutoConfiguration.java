package com.chua.starter.strategy.config;

import com.chua.starter.strategy.aspect.SysCircuitBreakerConfigurationAspect;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 策略模块自动配置
 *
 * @author CH
 * @version 1.0.0
 * @since 2025-12-02
 */
@Configuration
@EnableAsync
@ComponentScan(basePackages = {
        "com.chua.starter.strategy.controller",
        "com.chua.starter.strategy.service",
        "com.chua.starter.strategy.aspect"
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
}
