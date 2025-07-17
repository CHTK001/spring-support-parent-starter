package com.chua.starter.plugin.annotation;

import com.chua.starter.plugin.entity.RateLimitConfig;

import java.lang.annotation.*;

/**
 * 限流注解
 * 
 * @author CH
 * @since 2025/1/16
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimit {

    /**
     * 限流类型
     */
    RateLimitConfig.LimitType limitType() default RateLimitConfig.LimitType.API;

    /**
     * 限流键，如果为空则使用方法路径
     */
    String key() default "";

    /**
     * 每秒允许的请求数（QPS）
     */
    int qps() default 100;

    /**
     * 突发容量（令牌桶算法使用）
     */
    int burstCapacity() default 200;

    /**
     * 限流算法类型
     */
    RateLimitConfig.AlgorithmType algorithm() default RateLimitConfig.AlgorithmType.TOKEN_BUCKET;

    /**
     * 超出限制时的处理策略
     */
    RateLimitConfig.OverflowStrategy overflowStrategy() default RateLimitConfig.OverflowStrategy.REJECT;

    /**
     * 时间窗口大小（秒）
     */
    int windowSizeSeconds() default 1;

    /**
     * 是否启用
     */
    boolean enabled() default true;

    /**
     * 配置描述
     */
    String description() default "";

    /**
     * 是否同时限制IP
     */
    boolean limitIp() default false;

    /**
     * IP限流的QPS（当limitIp为true时生效）
     */
    int ipQps() default 1000;

    /**
     * 错误消息
     */
    String message() default "请求过于频繁，请稍后再试";

    /**
     * 错误代码
     */
    int errorCode() default 429;
}
