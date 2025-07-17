package com.chua.starter.plugin.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.List;

/**
 * XSS防护配置实体
 *
 * @author CH
 * @since 2025/1/16
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class PluginXssConfig {

    /**
     * 主键ID
     */
    private Long pluginXssConfigId;

    /**
     * 配置名称
     */
    private String pluginXssConfigName;

    /**
     * 是否启用XSS防护
     */
    private Boolean pluginXssConfigEnabled = true;

    /**
     * 防护模式：FILTER（过滤）、ESCAPE（转义）、REJECT（拒绝）
     */
    private ProtectionMode pluginXssConfigProtectionMode = ProtectionMode.FILTER;

    /**
     * 需要防护的URL模式列表（JSON格式存储）
     */
    private String pluginXssConfigUrlPatterns;

    /**
     * 排除的URL模式列表（JSON格式存储）
     */
    private String pluginXssConfigExcludePatterns;

    /**
     * 需要检查的参数名列表（JSON格式存储）
     */
    private String pluginXssConfigCheckParameters;

    /**
     * 排除检查的参数名列表（JSON格式存储）
     */
    private String pluginXssConfigExcludeParameters;

    /**
     * 自定义XSS规则（JSON格式存储）
     */
    private String pluginXssConfigCustomRules;

    /**
     * 是否启用严格模式
     */
    private Boolean pluginXssConfigStrictMode = false;

    /**
     * 是否记录攻击日志
     */
    private Boolean pluginXssConfigLogAttacks = true;

    /**
     * 攻击阈值（单位时间内攻击次数）
     */
    private Integer pluginXssConfigAttackThreshold = 10;

    /**
     * 阈值时间窗口（分钟）
     */
    private Integer pluginXssConfigThresholdWindow = 5;

    /**
     * 超过阈值后的处理策略
     */
    private ThresholdAction pluginXssConfigThresholdAction = ThresholdAction.LOG;

    /**
     * 配置描述
     */
    private String pluginXssConfigDescription;

    /**
     * 创建时间
     */
    private LocalDateTime pluginXssConfigCreatedTime;

    /**
     * 更新时间
     */
    private LocalDateTime pluginXssConfigUpdatedTime;

    /**
     * 创建者
     */
    private String pluginXssConfigCreatedBy;

    /**
     * 更新者
     */
    private String pluginXssConfigUpdatedBy;

    /**
     * 防护模式枚举
     */
    public enum ProtectionMode {
        /**
         * 过滤模式 - 移除危险内容
         */
        FILTER,

        /**
         * 转义模式 - HTML转义
         */
        ESCAPE,

        /**
         * 拒绝模式 - 直接拒绝请求
         */
        REJECT
    }

    /**
     * 阈值处理策略
     */
    public enum ThresholdAction {
        /**
         * 仅记录日志
         */
        LOG,

        /**
         * 加入黑名单
         */
        BLACKLIST,

        /**
         * 临时封禁
         */
        TEMP_BAN
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
    public XssConfig() {
    }

    /**
     * 构造函数
     * 
     * @param configName 配置名称
     * @param enabled 是否启用
     */
    public XssConfig(String configName, Boolean enabled) {
        this.configName = configName;
        this.enabled = enabled;
        onCreate();
    }

    /**
     * 获取唯一键
     * 
     * @return 唯一键
     */
    public String getUniqueKey() {
        return "XSS:" + configName;
    }

    /**
     * 检查配置是否有效
     * 
     * @return 是否有效
     */
    public boolean isValid() {
        return configName != null && !configName.trim().isEmpty() && enabled != null && protectionMode != null;
    }

    /**
     * 创建默认配置
     * 
     * @return 默认配置
     */
    public static XssConfig createDefault() {
        XssConfig config = new XssConfig("default", true);
        config.setProtectionMode(ProtectionMode.FILTER);
        config.setUrlPatterns("/**");
        config.setExcludePatterns("/static/**,/public/**,/webjars/**");
        config.setStrictMode(false);
        config.setLogAttacks(true);
        config.setAttackThreshold(10);
        config.setThresholdWindow(5);
        config.setThresholdAction(ThresholdAction.LOG);
        config.setDescription("默认XSS防护配置");
        return config;
    }
}
