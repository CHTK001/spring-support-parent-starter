package com.chua.starter.plugin.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 插件安全配置实体
 * 统一管理限流配置和黑白名单配置
 *
 * @author CH
 * @since 2025/1/16
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class PluginSecurityConfig {

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 配置类型：RATE_LIMIT（限流）、BLACKLIST（黑名单）、WHITELIST（白名单）
     */
    private ConfigType configType;

    /**
     * 限流类型：IP、API（仅当configType为RATE_LIMIT时有效）
     */
    private LimitType limitType;

    /**
     * 配置键（IP地址、API路径、名单值等）
     */
    private String configKey;

    /**
     * 每秒允许的请求数（仅限流配置使用）
     */
    private Integer qps;

    /**
     * 突发容量（仅限流配置使用）
     */
    private Integer burstCapacity;

    /**
     * 限流算法类型（仅限流配置使用）
     */
    private AlgorithmType algorithmType = AlgorithmType.TOKEN_BUCKET;

    /**
     * 超出限制时的处理策略（仅限流配置使用）
     */
    private OverflowStrategy overflowStrategy = OverflowStrategy.REJECT;

    /**
     * 时间窗口大小（秒）（仅限流配置使用）
     */
    private Integer windowSizeSeconds = 60;

    /**
     * 匹配类型（仅黑白名单配置使用）
     */
    private MatchType matchType = MatchType.EXACT;

    /**
     * 优先级（数字越小优先级越高）
     */
    private Integer priority = 0;

    /**
     * 是否启用
     */
    private Boolean enabled = true;

    /**
     * 描述
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
     * 过期时间（可选）
     */
    private LocalDateTime expireTime;

    /**
     * 创建IP限流配置
     *
     * @param ipAddress IP地址
     * @param qps       每秒请求数
     * @return 限流配置
     */
    public static PluginSecurityConfig createIpRateLimit(String ipAddress, Integer qps) {
        PluginSecurityConfig config = new PluginSecurityConfig();
        config.setConfigType(ConfigType.RATE_LIMIT);
        config.setLimitType(LimitType.IP);
        config.setConfigKey(ipAddress);
        config.setQps(qps);
        config.setBurstCapacity(qps * 2);
        config.setDescription("IP限流 - " + ipAddress);
        config.onCreate();
        return config;
    }

    /**
     * 创建API限流配置
     *
     * @param apiPath API路径
     * @param qps     每秒请求数
     * @return 限流配置
     */
    public static PluginSecurityConfig createApiRateLimit(String apiPath, Integer qps) {
        PluginSecurityConfig config = new PluginSecurityConfig();
        config.setConfigType(ConfigType.RATE_LIMIT);
        config.setLimitType(LimitType.API);
        config.setConfigKey(apiPath);
        config.setQps(qps);
        config.setBurstCapacity(qps * 2);
        config.setDescription("API限流 - " + apiPath);
        config.onCreate();
        return config;
    }

    /**
     * 创建黑名单配置
     *
     * @param value     值
     * @param matchType 匹配类型
     * @return 黑名单配置
     */
    public static PluginSecurityConfig createBlacklist(String value, MatchType matchType) {
        PluginSecurityConfig config = new PluginSecurityConfig();
        config.setConfigType(ConfigType.BLACKLIST);
        config.setConfigKey(value);
        config.setMatchType(matchType);
        config.setDescription("黑名单 - " + value);
        config.onCreate();
        return config;
    }

    /**
     * 创建白名单配置
     *
     * @param value     值
     * @param matchType 匹配类型
     * @return 白名单配置
     */
    public static PluginSecurityConfig createWhitelist(String value, MatchType matchType) {
        PluginSecurityConfig config = new PluginSecurityConfig();
        config.setConfigType(ConfigType.WHITELIST);
        config.setConfigKey(value);
        config.setMatchType(matchType);
        config.setDescription("白名单 - " + value);
        config.onCreate();
        return config;
    }

    /**
     * 创建时调用
     */
    public void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdTime = now;
        updatedTime = now;
    }

    /**
     * 更新时调用
     */
    public void onUpdate() {
        updatedTime = LocalDateTime.now();
    }

    /**
     * 获取唯一键
     *
     * @return 唯一键
     */
    public String getUniqueKey() {
        if (configType == ConfigType.RATE_LIMIT) {
            return limitType + ":" + configKey;
        } else {
            return configType + ":" + configKey;
        }
    }

    /**
     * 检查配置是否有效
     *
     * @return 是否有效
     */
    public boolean isValid() {
        if (configType == null || configKey == null || configKey.trim().isEmpty() || enabled == null) {
            return false;
        }

        if (configType == ConfigType.RATE_LIMIT) {
            return limitType != null && qps != null && qps > 0;
        } else {
            return matchType != null;
        }
    }

    /**
     * 检查是否过期
     *
     * @return 是否过期
     */
    public boolean isExpired() {
        return expireTime != null && LocalDateTime.now().isAfter(expireTime);
    }

    /**
     * 检查值是否匹配（用于黑白名单）
     *
     * @param value 要检查的值
     * @return 是否匹配
     */
    public boolean matches(String value) {
        if (configType == ConfigType.RATE_LIMIT || value == null || configKey == null) {
            return false;
        }

        switch (matchType) {
            case EXACT:
                return configKey.equals(value);
            case WILDCARD:
                return matchWildcard(configKey, value);
            case REGEX:
                try {
                    return value.matches(configKey);
                } catch (Exception e) {
                    return false;
                }
            default:
                return false;
        }
    }

    /**
     * 通配符匹配
     *
     * @param pattern 模式
     * @param value   值
     * @return 是否匹配
     */
    private boolean matchWildcard(String pattern, String value) {
        String regex = pattern.replace(".", "\\.").replace("*", ".*").replace("?", ".");
        try {
            return value.matches(regex);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 配置类型枚举
     */
    public enum ConfigType {
        /**
         * 限流配置
         */
        RATE_LIMIT,
        /**
         * 黑名单配置
         */
        BLACKLIST,
        /**
         * 白名单配置
         */
        WHITELIST
    }

    // ==================== 静态工厂方法 ====================

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
        API
    }

    /**
     * 限流算法类型枚举
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
     * 超出限制时的处理策略枚举
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
     * 匹配类型枚举
     */
    public enum MatchType {
        /**
         * 精确匹配
         */
        EXACT,
        /**
         * 通配符匹配
         */
        WILDCARD,
        /**
         * 正则表达式匹配
         */
        REGEX
    }
}
