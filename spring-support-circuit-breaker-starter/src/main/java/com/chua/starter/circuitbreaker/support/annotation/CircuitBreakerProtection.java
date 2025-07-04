package com.chua.starter.circuitbreaker.support.annotation;

import java.lang.annotation.*;

/**
 * 熔断降级保护注解
 * 
 * 用于方法级别的熔断降级保护，支持熔断器、重试、限流、舱壁隔离、超时控制等功能。
 * 可以单独使用某个功能，也可以组合使用多个功能。
 * 
 * @author CH
 * @since 2024/12/20
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CircuitBreakerProtection {

    /**
     * 熔断器名称
     * 
     * @return 熔断器名称，为空则不启用熔断器
     */
    String circuitBreaker() default "";

    /**
     * 重试名称
     * 
     * @return 重试名称，为空则不启用重试
     */
    String retry() default "";

    /**
     * 限流器名称
     * 
     * @return 限流器名称，为空则不启用限流
     */
    String rateLimiter() default "";

    /**
     * 舱壁隔离名称
     * 
     * @return 舱壁隔离名称，为空则不启用舱壁隔离
     */
    String bulkhead() default "";

    /**
     * 超时控制名称
     * 
     * @return 超时控制名称，为空则不启用超时控制
     */
    String timeLimiter() default "";

    /**
     * 降级方法名称
     * 
     * @return 降级方法名称，当发生异常时调用的方法
     */
    String fallbackMethod() default "";

    /**
     * 是否异步执行
     * 
     * @return true表示异步执行，false表示同步执行
     */
    boolean async() default false;
}
