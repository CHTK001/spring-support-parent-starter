package com.chua.starter.circuitbreaker.support.annotation;

import java.lang.annotation.*;
import java.time.Duration;

/**
 * 限流注解
 * 
 * 基于Resilience4j RateLimiter实现的限流注解，支持灵活的限流配置。
 * 可以单独使用，也可以与其他容错机制组合使用。
 * 
 * @author CH
 * @since 2024/12/20
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimiter {

    /**
     * 限流器名称
     * 
     * @return 限流器名称，如果为空则使用方法签名生成默认名称
     */
    String name() default "";

    /**
     * 限流键
     * 
     * @return 限流键，支持SpEL表达式，用于区分不同的限流维度
     */
    String key() default "";

    /**
     * 每个周期允许的请求数量
     * 
     * @return 每个周期允许的请求数量，-1表示使用配置文件中的默认值
     */
    int limitForPeriod() default -1;

    /**
     * 限制刷新周期（秒）
     * 
     * @return 限制刷新周期，-1表示使用配置文件中的默认值
     */
    long limitRefreshPeriodSeconds() default -1;

    /**
     * 获取许可的超时时间（毫秒）
     * 
     * @return 超时时间，-1表示使用配置文件中的默认值
     */
    long timeoutDurationMillis() default -1;

    /**
     * 降级方法名称
     * 
     * @return 降级方法名称，当限流触发时调用的方法
     */
    String fallbackMethod() default "";

    /**
     * 限流失败时的错误消息
     * 
     * @return 错误消息
     */
    String message() default "请求过于频繁，请稍后再试";

    /**
     * 限流维度
     * 
     * @return 限流维度，支持 GLOBAL（全局）、IP（按IP）、USER（按用户）、API（按接口）
     */
    Dimension dimension() default Dimension.GLOBAL;

    /**
     * 限流维度枚举
     */
    enum Dimension {
        /**
         * 全局限流
         */
        GLOBAL,
        
        /**
         * 按IP限流
         */
        IP,
        
        /**
         * 按用户限流
         */
        USER,
        
        /**
         * 按接口限流
         */
        API
    }
}
