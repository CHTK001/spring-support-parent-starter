package com.chua.starter.plugin.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 限流配置实体
 *
 * @author CH
 * @since 2025/1/16
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class PluginRateLimitConfig {

    /**
     * 主键ID
     */
    private Long pluginRateLimitConfigId;

    /**
     * 限流类型：IP、API
     */
    private LimitType pluginRateLimitConfigLimitType;

    /**
     * 限流键（IP地址或API路径）
     */
    private String pluginRateLimitConfigLimitKey;

    /**
     * 每秒允许的请求数（QPS）
     */
    private Integer pluginRateLimitConfigQps = 100;

    /**
     * 突发容量（令牌桶算法使用）
     */
    private Integer pluginRateLimitConfigBurstCapacity = 200;

    /**
     * 限流算法类型
     */
    private AlgorithmType pluginRateLimitConfigAlgorithmType = AlgorithmType.TOKEN_BUCKET;

    /**
     * 是否启用
     */
    private Boolean pluginRateLimitConfigEnabled = true;

    /**
     * 超出限制时的处理策略
     */
    private OverflowStrategy pluginRateLimitConfigOverflowStrategy = OverflowStrategy.REJECT;

    /**
     * 时间窗口大小（秒）
     */
    private Integer pluginRateLimitConfigWindowSizeSeconds = 1;

    /**
     * 配置描述
     */
    private String pluginRateLimitConfigDescription;

    /**
     * 创建时间
     */
    private LocalDateTime pluginRateLimitConfigCreatedTime;

    /**
     * 更新时间
     */
    private LocalDateTime pluginRateLimitConfigUpdatedTime;

    /**
     * 创建者
     */
    private String pluginRateLimitConfigCreatedBy;

    /**
     * 更新者
     */
    private String pluginRateLimitConfigUpdatedBy;

    /**
     * 限流类型枚举
     */
    public enum LimitType {
        /**
         * IP限流
         */
        IP,

        /**
         * API路径限流
         */
        API,

        /**
         * QPS全局限流
         */
        QPS,

        /**
         * 黑名单
         */
        BLACKLIST,

        /**
         * 白名单
         */
        WHITELIST
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

    public void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdTime = now;
        updatedTime = now;
    }

    public void onUpdate() {
        updatedTime = LocalDateTime.now();
    }

    /**
     * 构造函数
     */
    public RateLimitConfig() {
    }

    /**
     * 构造函数
     * 
     * @param limitType 限流类型
     * @param limitKey  限流键
     * @param qps       QPS限制
     */
    public RateLimitConfig(LimitType limitType, String limitKey, Integer qps) {
        this.limitType = limitType;
        this.limitKey = limitKey;
        this.qps = qps;
        this.burstCapacity = qps * 2; // 默认突发容量为QPS的2倍
        LocalDateTime now = LocalDateTime.now();
        this.createdTime = now;
        this.updatedTime = now;
    }

    /**
     * 创建IP限流配置
     * 
     * @param ipAddress IP地址
     * @param qps       QPS限制
     * @return 限流配置
     */
    public static RateLimitConfig createIpConfig(String ipAddress, Integer qps) {
        return new RateLimitConfig(LimitType.IP, ipAddress, qps);
    }

    /**
     * 创建API限流配置
     * 
     * @param apiPath API路径
     * @param qps     QPS限制
     * @return 限流配置
     */
    public static RateLimitConfig createApiConfig(String apiPath, Integer qps) {
        return new RateLimitConfig(LimitType.API, apiPath, qps);
    }

    /**
     * 获取唯一键
     * 
     * @return 唯一键
     */
    public String getUniqueKey() {
        return limitType + ":" + limitKey;
    }

    /**
     * 检查配置是否有效
     * 
     * @return 是否有效
     */
    public boolean isValid() {
        return limitType != null && limitKey != null && !limitKey.trim().isEmpty() && qps != null && qps > 0
                && enabled != null;
    }
}
