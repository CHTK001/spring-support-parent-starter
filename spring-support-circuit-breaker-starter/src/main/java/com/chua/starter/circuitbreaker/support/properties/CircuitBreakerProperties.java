package com.chua.starter.circuitbreaker.support.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * 熔断降级配置属性
 * 
 * 提供熔断器、重试、限流、舱壁隔离、超时控制等功能的配置属性。
 * 支持全局默认配置和实例级别的个性化配置。
 * 
 * @author CH
 * @since 2024/12/20
 */
@Data
@ConfigurationProperties(prefix = CircuitBreakerProperties.PRE)
public class CircuitBreakerProperties {

    public static final String PRE = "plugin.circuit-breaker";

    /**
     * 是否启用熔断降级功能
     */
    private boolean enable = true;

    /**
     * 熔断器配置
     */
    private CircuitBreaker circuitBreaker = new CircuitBreaker();

    /**
     * 重试配置
     */
    private Retry retry = new Retry();

    /**
     * 限流器配置
     */
    private RateLimiter rateLimiter = new RateLimiter();

    /**
     * 舱壁隔离配置
     */
    private Bulkhead bulkhead = new Bulkhead();

    /**
     * 超时控制配置
     */
    private TimeLimiter timeLimiter = new TimeLimiter();

    /**
     * 缓存配置
     */
    private Cache cache = new Cache();

    /**
     * 熔断器配置
     */
    @Data
    public static class CircuitBreaker {
        /**
         * 实例配置映射
         */
        private Map<String, CircuitBreakerInstance> instances = new HashMap<>();

        /**
         * 失败率阈值（百分比）
         */
        private float failureRateThreshold = 50.0f;

        /**
         * 慢调用率阈值（百分比）
         */
        private float slowCallRateThreshold = 100.0f;

        /**
         * 慢调用持续时间阈值
         */
        private Duration slowCallDurationThreshold = Duration.ofSeconds(60);

        /**
         * 最小调用数量
         */
        private int minimumNumberOfCalls = 10;

        /**
         * 滑动窗口大小
         */
        private int slidingWindowSize = 10;

        /**
         * 滑动窗口类型（COUNT_BASED 或 TIME_BASED）
         */
        private String slidingWindowType = "COUNT_BASED";

        /**
         * 等待持续时间（半开状态）
         */
        private Duration waitDurationInOpenState = Duration.ofSeconds(60);

        /**
         * 半开状态下允许的调用数量
         */
        private int permittedNumberOfCallsInHalfOpenState = 3;

        /**
         * 是否自动从开启状态转换到半开状态
         */
        private boolean automaticTransitionFromOpenToHalfOpenEnabled = false;
    }

    /**
     * 熔断器实例配置
     */
    @Data
    public static class CircuitBreakerInstance {
        private Float failureRateThreshold;
        private Float slowCallRateThreshold;
        private Duration slowCallDurationThreshold;
        private Integer minimumNumberOfCalls;
        private Integer slidingWindowSize;
        private String slidingWindowType;
        private Duration waitDurationInOpenState;
        private Integer permittedNumberOfCallsInHalfOpenState;
        private Boolean automaticTransitionFromOpenToHalfOpenEnabled;
    }

    /**
     * 重试配置
     */
    @Data
    public static class Retry {
        /**
         * 实例配置映射
         */
        private Map<String, RetryInstance> instances = new HashMap<>();

        /**
         * 最大重试次数
         */
        private int maxAttempts = 3;

        /**
         * 重试间隔
         */
        private Duration waitDuration = Duration.ofMillis(500);

        /**
         * 重试间隔倍数
         */
        private double intervalMultiplier = 1.0;

        /**
         * 最大重试间隔
         */
        private Duration maxWaitDuration = Duration.ofSeconds(10);
    }

    /**
     * 重试实例配置
     */
    @Data
    public static class RetryInstance {
        private Integer maxAttempts;
        private Duration waitDuration;
        private Double intervalMultiplier;
        private Duration maxWaitDuration;
    }

    /**
     * 限流器配置
     */
    @Data
    public static class RateLimiter {
        /**
         * 实例配置映射
         */
        private Map<String, RateLimiterInstance> instances = new HashMap<>();

        /**
         * 限制刷新周期
         */
        private Duration limitRefreshPeriod = Duration.ofSeconds(1);

        /**
         * 每个周期的许可数量
         */
        private int limitForPeriod = 10;

        /**
         * 超时持续时间
         */
        private Duration timeoutDuration = Duration.ofMillis(500);

        /**
         * 是否启用动态管理页面
         */
        private boolean enableManagement = true;

        /**
         * 管理页面路径
         */
        private String managementPath = "/actuator/rate-limiter";

        /**
         * 默认限流维度
         */
        private String defaultDimension = "GLOBAL";

        /**
         * 限流规则配置
         */
        private Map<String, RateLimitRule> rules = new HashMap<>();
    }

    /**
     * 限流器实例配置
     */
    @Data
    public static class RateLimiterInstance {
        private Duration limitRefreshPeriod;
        private Integer limitForPeriod;
        private Duration timeoutDuration;
    }

    /**
     * 限流规则配置
     */
    @Data
    public static class RateLimitRule {
        /**
         * 规则名称
         */
        private String name;

        /**
         * 匹配模式（支持Ant路径模式）
         */
        private String pattern;

        /**
         * 每个周期的许可数量
         */
        private Integer limitForPeriod;

        /**
         * 限制刷新周期
         */
        private Duration limitRefreshPeriod;

        /**
         * 超时持续时间
         */
        private Duration timeoutDuration;

        /**
         * 是否启用
         */
        private boolean enabled = true;

        /**
         * 限流维度（GLOBAL, IP, USER, API）
         */
        private String dimension = "GLOBAL";

        /**
         * 自定义键表达式（SpEL）
         */
        private String keyExpression;

        /**
         * 降级方法名称
         */
        private String fallbackMethod;

        /**
         * 错误消息
         */
        private String message = "请求过于频繁，请稍后再试";
    }

    /**
     * 舱壁隔离配置
     */
    @Data
    public static class Bulkhead {
        /**
         * 实例配置映射
         */
        private Map<String, BulkheadInstance> instances = new HashMap<>();

        /**
         * 最大并发调用数
         */
        private int maxConcurrentCalls = 25;

        /**
         * 最大等待持续时间
         */
        private Duration maxWaitDuration = Duration.ofMillis(0);
    }

    /**
     * 舱壁隔离实例配置
     */
    @Data
    public static class BulkheadInstance {
        private Integer maxConcurrentCalls;
        private Duration maxWaitDuration;
    }

    /**
     * 超时控制配置
     */
    @Data
    public static class TimeLimiter {
        /**
         * 实例配置映射
         */
        private Map<String, TimeLimiterInstance> instances = new HashMap<>();

        /**
         * 超时持续时间
         */
        private Duration timeoutDuration = Duration.ofSeconds(1);

        /**
         * 是否取消运行中的Future
         */
        private boolean cancelRunningFuture = true;
    }

    /**
     * 超时控制实例配置
     */
    @Data
    public static class TimeLimiterInstance {
        private Duration timeoutDuration;
        private Boolean cancelRunningFuture;
    }

    /**
     * 缓存配置
     */
    @Data
    public static class Cache {
        /**
         * 实例配置映射
         */
        private Map<String, CacheInstance> instances = new HashMap<>();

        /**
         * 缓存最大大小
         */
        private long maximumSize = 1000;

        /**
         * 缓存过期时间
         */
        private Duration expireAfterWrite = Duration.ofMinutes(10);
    }

    /**
     * 缓存实例配置
     */
    @Data
    public static class CacheInstance {
        private Long maximumSize;
        private Duration expireAfterWrite;
    }
}
