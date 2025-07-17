package com.chua.starter.plugin.entity;

import com.chua.starter.plugin.annotation.RateLimit;
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
    private Long id;

    /**
     * 限流类型：IP、API
     */
    private LimitType limitType;

    /**
     * 限流键（IP地址或API路径）
     */
    private String limitKey;

    /**
     * 每秒允许的请求数（QPS）
     */
    private Integer qps = 100;

    /**
     * 突发容量（令牌桶算法使用）
     */
    private Integer burstCapacity = 200;

    /**
     * 限流算法类型
     */
    private AlgorithmType algorithmType = AlgorithmType.TOKEN_BUCKET;

    /**
     * 是否启用
     */
    private Boolean enabled = true;

    /**
     * 超出限制时的处理策略
     */
    private OverflowStrategy overflowStrategy = OverflowStrategy.REJECT;

    /**
     * 时间窗口大小（秒）
     */
    private Integer windowSizeSeconds = 1;

    /**
     * 配置描述
     */
    private String description;

    /**
     * 创建时间
     */
    private LocalDateTime createdTime;

    /**
     * 更新时间
     */
    private LocalDateTime updatedTime;

    /**
     * 创建者
     */
    private String createdBy;

    /**
     * 更新者
     */
    private String updatedBy;

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
    public PluginRateLimitConfig() {
    }

    /**
     * 构造函数
     *
     * @param limitType 限流类型
     * @param limitKey  限流键
     * @param qps       QPS限制
     */
    public PluginRateLimitConfig(LimitType limitType, String limitKey, Integer qps) {
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
    public static PluginRateLimitConfig createIpConfig(String ipAddress, Integer qps) {
        return new PluginRateLimitConfig(LimitType.IP, ipAddress, qps);
    }

    /**
     * 创建API限流配置
     *
     * @param apiPath API路径
     * @param qps     QPS限制
     * @return 限流配置
     */
    public static PluginRateLimitConfig createApiConfig(String apiPath, Integer qps) {
        return new PluginRateLimitConfig(LimitType.API, apiPath, qps);
    }

    /**
     * 从 RateLimit 注解的 LimitType 转换为实体的 LimitType
     *
     * @param annotationLimitType 注解中的限流类型
     * @return 实体中的限流类型
     */
    public static LimitType fromAnnotationLimitType(RateLimit.LimitType annotationLimitType) {
        switch (annotationLimitType) {
            case IP:
                return LimitType.IP;
            case API:
                return LimitType.API;
            default:
                return LimitType.API;
        }
    }

    /**
     * 从 RateLimit 注解的 AlgorithmType 转换为实体的 AlgorithmType
     *
     * @param annotationAlgorithmType 注解中的算法类型
     * @return 实体中的算法类型
     */
    public static AlgorithmType fromAnnotationAlgorithmType(RateLimit.AlgorithmType annotationAlgorithmType) {
        switch (annotationAlgorithmType) {
            case TOKEN_BUCKET:
                return AlgorithmType.TOKEN_BUCKET;
            case LEAKY_BUCKET:
                return AlgorithmType.LEAKY_BUCKET;
            case FIXED_WINDOW:
                return AlgorithmType.FIXED_WINDOW;
            case SLIDING_WINDOW:
                return AlgorithmType.SLIDING_WINDOW;
            default:
                return AlgorithmType.TOKEN_BUCKET;
        }
    }

    /**
     * 从 RateLimit 注解的 OverflowStrategy 转换为实体的 OverflowStrategy
     *
     * @param annotationOverflowStrategy 注解中的溢出策略
     * @return 实体中的溢出策略
     */
    public static OverflowStrategy fromAnnotationOverflowStrategy(RateLimit.OverflowStrategy annotationOverflowStrategy) {
        switch (annotationOverflowStrategy) {
            case REJECT:
                return OverflowStrategy.REJECT;
            case QUEUE:
                return OverflowStrategy.QUEUE;
            case FALLBACK:
                return OverflowStrategy.FALLBACK;
            default:
                return OverflowStrategy.REJECT;
        }
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
