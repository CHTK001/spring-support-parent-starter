package com.chua.plugin.support.properties;

import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.io.File;

/**
 * 插件系统配置属性
 * <p>
 * 完整实现 PF4J 风格的配置，支持所有插件管理功能
 * </p>
 *
 * @author CH
 * @since 2026/01/02
 */
@Slf4j
@Data
@Validated
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
    @NotBlank(message = "插件根目录不能为空")
    private String pluginsRoot = "./plugins";

    /**
     * 自动相关配置
     */
    private AutoConfig auto = new AutoConfig();

    /**
     * 监听相关配置
     */
    private WatchConfig watch = new WatchConfig();

    /**
     * 显示相关配置
     */
    private DisplayConfig display = new DisplayConfig();

    /**
     * 路由相关配置
     */
    private RoutingConfig routing = new RoutingConfig();

    /**
     * 依赖相关配置
     */
    private DependencyConfig dependency = new DependencyConfig();

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
     * 扩展点查找缓存大小
     */
    @Min(value = 1, message = "扩展点缓存大小必须大于0")
    @Max(value = 10000, message = "扩展点缓存大小不能超过10000")
    private int extensionCacheSize = 100;

    /**
     * 插件加载失败时的策略
     * <p>
     * FAIL_FAST: 加载失败时中断应用启动
     * CONTINUE: 加载失败时记录日志但继续启动
     * </p>
     */
    private PluginLoadFailureStrategy loadFailureStrategy = PluginLoadFailureStrategy.CONTINUE;

    /**
     * 自动相关配置
     */
    @Data
    public static class AutoConfig {
        /**
         * 是否在启动时自动加载插件
         */
        private boolean load = true;

        /**
         * 是否在加载后自动启动插件
         */
        private boolean start = true;

        /**
         * 是否启用自动重载
         * <p>
         * 当插件文件被修改时，是否自动重载插件
         * </p>
         */
        private boolean reload = true;
    }

    /**
     * 监听相关配置
     */
    @Data
    public static class WatchConfig {
        /**
         * 是否启用插件目录监听（热加载）
         */
        private boolean enabled = false;
    }

    /**
     * 显示相关配置
     */
    @Data
    public static class DisplayConfig {
        /**
         * 是否在启动时显示插件信息
         */
        private boolean info = true;

        /**
         * 是否在控制台显示详细的插件信息
         */
        private boolean details = false;
    }

    /**
     * 路由相关配置
     */
    @Data
    public static class RoutingConfig {
        /**
         * 插件路由白名单（Bean 名或包前缀）。
         * <p>
         * 仅当 Bean 名或 Bean 所在类的包名匹配任一白名单规则时，才会参与
         * 插件路由 FactoryBean 包装；为空表示不启用白名单限制。
         * </p>
         */
        private String[] includePatterns;

        /**
         * 插件路由黑名单（Bean 名或包前缀）。
         * <p>
         * 当 Bean 名或 Bean 所在类的包名匹配任一黑名单规则时，将被显式
         * 排除在插件路由 FactoryBean 包装之外，用于保护基础设施 Bean。
         * </p>
         */
        private String[] excludePatterns;
    }

    /**
     * 依赖相关配置
     */
    @Data
    public static class DependencyConfig {
        /**
         * 是否解析依赖
         */
        private boolean resolve = true;

        /**
         * 是否严格模式
         * <p>
         * 严格模式下，依赖缺失会导致插件加载失败
         * 非严格模式下，会尝试继续加载
         * </p>
         */
        private boolean strict = false;
    }

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

    /**
     * 插件加载失败策略枚举
     */
    public enum PluginLoadFailureStrategy {
        /**
         * 快速失败：加载失败时中断应用启动
         */
        FAIL_FAST,

        /**
         * 继续运行：加载失败时记录日志但继续启动
         */
        CONTINUE
    }

    /**
     * 配置验证方法
     * <p>
     * 在配置加载后验证配置参数的有效性
     * </p>
     */
    @PostConstruct
    public void validate() {
        // 验证插件目录
        File pluginsDir = new File(pluginsRoot);
        if (!pluginsDir.exists()) {
            log.warn("[插件系统][配置]插件目录不存在，将自动创建: {}", pluginsRoot);
            if (!pluginsDir.mkdirs()) {
                throw new IllegalStateException("无法创建插件目录: " + pluginsRoot);
            }
        }
        if (!pluginsDir.isDirectory()) {
            throw new IllegalStateException("插件路径不是目录: " + pluginsRoot);
        }
        if (!pluginsDir.canRead()) {
            throw new IllegalStateException("插件目录不可读: " + pluginsRoot);
        }

        // 验证缓存大小
        if (extensionCacheSize <= 0 || extensionCacheSize > 10000) {
            throw new IllegalStateException(
                String.format("扩展点缓存大小必须在 1-10000 之间: %d", extensionCacheSize)
            );
        }

        log.debug("[插件系统][配置]配置验证通过");
    }
}
