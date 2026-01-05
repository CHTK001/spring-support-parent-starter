package com.chua.plugin.support.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 插件系统配置属性
 * <p>
 * 完整实现 PF4J 风格的配置，支持所有插件管理功能
 * </p>
 *
 * @author CH
 * @since 2026/01/02
 */
@Data
@ConfigurationProperties(prefix = "spring.plugin")
public class PluginProperties {

    /**
     * 是否启用插件系统
     */
    private boolean enabled = true;

    /**
     * 插件根目录路径
     * <p>
     * 支持相对路径和绝对路径
     * 默认：./plugins
     * </p>
     */
    private String pluginsRoot = "./plugins";

    /**
     * 是否在启动时自动加载插件
     */
    private boolean autoLoad = true;

    /**
     * 是否在加载后自动启动插件
     */
    private boolean autoStart = true;

    /**
     * 是否启用插件目录监听（热加载）
     */
    private boolean watchEnabled = false;

    /**
     * 是否启用自动重载
     * <p>
     * 当插件文件被修改时，是否自动重载插件
     * </p>
     */
    private boolean autoReload = true;

    /**
     * 是否在启动时显示插件信息
     */
    private boolean showInfo = true;

    /**
     * 是否在控制台显示详细的插件信息
     */
    private boolean showDetails = false;

    /**
     * 运行模式
     * <p>
     * development: 开发模式（启用热加载、详细日志）
     * production: 生产模式（禁用热加载、简洁日志）
     * </p>
     */
    private RuntimeMode runtimeMode = RuntimeMode.DEVELOPMENT;

    /**
     * 系统插件目录
     * <p>
     * 用于存放系统级别的插件，这些插件不会被目录监听器监控
     * </p>
     */
    private String systemPluginsRoot;

    /**
     * 是否解析依赖
     */
    private boolean resolveDependencies = true;

    /**
     * 是否严格模式
     * <p>
     * 严格模式下，依赖缺失会导致插件加载失败
     * 非严格模式下，会尝试继续加载
     * </p>
     */
    private boolean strictMode = false;

    /**
     * 扩展点查找缓存大小
     */
    private int extensionCacheSize = 100;

    /**
     * 运行模式枚举
     */
    public enum RuntimeMode {
        /**
         * 开发模式
         */
        DEVELOPMENT,

        /**
         * 生产模式
         */
        PRODUCTION
    }
}
