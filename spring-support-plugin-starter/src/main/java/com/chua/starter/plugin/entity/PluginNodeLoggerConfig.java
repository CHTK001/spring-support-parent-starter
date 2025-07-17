package com.chua.starter.plugin.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 节点日志配置实体
 *
 * @author CH
 * @since 2025/1/16
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class PluginNodeLoggerConfig {

    /**
     * 主键ID
     */
    private Long pluginNodeLoggerConfigId;

    /**
     * 节点名称
     */
    private String pluginNodeLoggerConfigNodeName;

    /**
     * 节点地址
     */
    private String pluginNodeLoggerConfigNodeUrl;

    /**
     * 应用名称
     */
    private String pluginNodeLoggerConfigApplicationName;

    /**
     * 日志器名称
     */
    private String pluginNodeLoggerConfigLoggerName;

    /**
     * 当前日志等级
     */
    private LogLevel pluginNodeLoggerConfigCurrentLevel;

    /**
     * 有效日志等级列表（JSON格式）
     */
    private String pluginNodeLoggerConfigEffectiveLevels;

    /**
     * 配置的日志等级
     */
    private LogLevel pluginNodeLoggerConfigConfiguredLevel;

    /**
     * 是否启用
     */
    private Boolean pluginNodeLoggerConfigEnabled = true;

    /**
     * 最后更新时间
     */
    private LocalDateTime pluginNodeLoggerConfigLastUpdated;

    /**
     * 创建时间
     */
    private LocalDateTime pluginNodeLoggerConfigCreatedTime;

    /**
     * 更新时间
     */
    private LocalDateTime pluginNodeLoggerConfigUpdatedTime;

    /**
     * 创建者
     */
    private String pluginNodeLoggerConfigCreatedBy;

    /**
     * 更新者
     */
    private String pluginNodeLoggerConfigUpdatedBy;

    /**
     * 日志等级枚举
     */
    public enum LogLevel {
        /**
         * 关闭
         */
        OFF,

        /**
         * 致命错误
         */
        FATAL,

        /**
         * 错误
         */
        ERROR,

        /**
         * 警告
         */
        WARN,

        /**
         * 信息
         */
        INFO,

        /**
         * 调试
         */
        DEBUG,

        /**
         * 跟踪
         */
        TRACE,

        /**
         * 全部
         */
        ALL
    }

    public void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        pluginLoggerConfigCreatedTime = now;
        pluginLoggerConfigUpdatedTime = now;
        pluginLoggerConfigLastUpdated = now;
    }

    public void onUpdate() {
        pluginLoggerConfigUpdatedTime = LocalDateTime.now();
        pluginLoggerConfigLastUpdated = LocalDateTime.now();
    }

    /**
     * 构造函数
     */
    public PluginLoggerConfig() {
    }

    /**
     * 构造函数
     * 
     * @param nodeName 节点名称
     * @param nodeUrl 节点地址
     * @param loggerName 日志器名称
     */
    public PluginLoggerConfig(String nodeName, String nodeUrl, String loggerName) {
        this.pluginLoggerConfigNodeName = nodeName;
        this.pluginLoggerConfigNodeUrl = nodeUrl;
        this.pluginLoggerConfigLoggerName = loggerName;
        onCreate();
    }

    /**
     * 获取唯一键
     * 
     * @return 唯一键
     */
    public String getUniqueKey() {
        return pluginLoggerConfigNodeName + ":" + pluginLoggerConfigLoggerName;
    }

    /**
     * 检查配置是否有效
     * 
     * @return 是否有效
     */
    public boolean isValid() {
        return pluginLoggerConfigNodeName != null && !pluginLoggerConfigNodeName.trim().isEmpty()
                && pluginLoggerConfigNodeUrl != null && !pluginLoggerConfigNodeUrl.trim().isEmpty()
                && pluginLoggerConfigLoggerName != null && !pluginLoggerConfigLoggerName.trim().isEmpty();
    }

    /**
     * 获取所有可用的日志等级
     * 
     * @return 日志等级列表
     */
    public static List<LogLevel> getAllLogLevels() {
        return List.of(LogLevel.values());
    }

    /**
     * 获取常用的日志等级
     * 
     * @return 常用日志等级列表
     */
    public static List<LogLevel> getCommonLogLevels() {
        return List.of(LogLevel.ERROR, LogLevel.WARN, LogLevel.INFO, LogLevel.DEBUG, LogLevel.TRACE);
    }
}
