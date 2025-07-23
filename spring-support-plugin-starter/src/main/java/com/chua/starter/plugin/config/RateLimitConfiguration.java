package com.chua.starter.plugin.config;

import lombok.Data;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * 限流配置
 *
 * @author CH
 * @since 2025/1/16
 */
@Data
@Configuration
@ComponentScan(basePackages = "com.chua.starter.plugin")
@EnableConfigurationProperties({ RateLimitConfiguration.class, SqliteConfiguration.SqliteProperties.class })
@ConditionalOnProperty(prefix = "plugin.rate-limit", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConfigurationProperties(prefix = "plugin.rate-limit")
public class RateLimitConfiguration {

    /**
     * 是否启用限流
     */
    private boolean enabled = true;

    /**
     * 默认限流配置
     */
    private RateLimitRule defaultRule = new RateLimitRule();

    /**
     * 针对特定API的限流配置
     */
    private Map<String, RateLimitRule> apiRules = new HashMap<>();

    /**
     * 针对特定IP的限流配置
     */
    private Map<String, RateLimitRule> ipRules = new HashMap<>();

    /**
     * 存储类型
     */
    private StorageType storageType = StorageType.MEMORY;

    /**
     * Redis配置（当storageType为REDIS时使用）
     */
    private RedisConfig redis = new RedisConfig();

    /**
     * 限流规则
     */
    @Data
    public static class RateLimitRule {

        /**
         * 每秒允许的请求数
         */
        private int permitsPerSecond = 100;

        /**
         * 时间窗口大小
         */
        private Duration windowSize = Duration.ofSeconds(1);

        /**
         * 最大突发请求数
         */
        private int burstCapacity = 200;

        /**
         * 限流算法类型
         */
        private AlgorithmType algorithm = AlgorithmType.TOKEN_BUCKET;

        /**
         * 是否启用
         */
        private boolean enabled = true;

        /**
         * 超出限制时的处理策略
         */
        private OverflowStrategy overflowStrategy = OverflowStrategy.REJECT;
    }

    /**
     * Redis配置
     */
    @Data
    public static class RedisConfig {

        /**
         * Redis键前缀
         */
        private String keyPrefix = "rate_limit:";

        /**
         * 键过期时间
         */
        private Duration keyExpiration = Duration.ofMinutes(5);

        /**
         * 数据库索引
         */
        private int database = 0;
    }

    /**
     * 存储类型枚举
     */
    public enum StorageType {
        /**
         * 内存存储
         */
        MEMORY,

        /**
         * Redis存储
         */
        REDIS,

        /**
         * SQLite存储
         */
        SQLITE
    }

    /**
     * 限流算法类型
     */
    public enum AlgorithmType {
        /**
         * 令牌桶算法
         */
        TOKEN_BUCKET,

        /**
         * 漏桶算法
         */
        LEAKY_BUCKET,

        /**
         * 固定窗口算法
         */
        FIXED_WINDOW,

        /**
         * 滑动窗口算法
         */
        SLIDING_WINDOW
    }

    /**
     * 超出限制时的处理策略
     */
    public enum OverflowStrategy {
        /**
         * 拒绝请求
         */
        REJECT,

        /**
         * 排队等待
         */
        QUEUE,

        /**
         * 降级处理
         */
        FALLBACK
    }

    /**
     * 获取指定API的限流规则
     * 
     * @param apiPath API路径
     * @return 限流规则
     */
    public RateLimitRule getRuleForApi(String apiPath) {
        return apiRules.getOrDefault(apiPath, defaultRule);
    }

    /**
     * 获取指定IP的限流规则
     * 
     * @param ip IP地址
     * @return 限流规则
     */
    public RateLimitRule getRuleForIp(String ip) {
        return ipRules.getOrDefault(ip, defaultRule);
    }

    /**
     * 添加API限流规则
     * 
     * @param apiPath API路径
     * @param rule    限流规则
     */
    public void addApiRule(String apiPath, RateLimitRule rule) {
        apiRules.put(apiPath, rule);
    }

    /**
     * 添加IP限流规则
     * 
     * @param ip   IP地址
     * @param rule 限流规则
     */
    public void addIpRule(String ip, RateLimitRule rule) {
        ipRules.put(ip, rule);
    }

    /**
     * 移除API限流规则
     * 
     * @param apiPath API路径
     */
    public void removeApiRule(String apiPath) {
        apiRules.remove(apiPath);
    }

    /**
     * 移除IP限流规则
     * 
     * @param ip IP地址
     */
    public void removeIpRule(String ip) {
        ipRules.remove(ip);
    }
}
