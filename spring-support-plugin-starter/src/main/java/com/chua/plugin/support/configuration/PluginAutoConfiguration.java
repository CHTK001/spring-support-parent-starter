package com.chua.plugin.support.configuration;

import com.chua.common.support.objects.plugin.DefaultPluginManager;
import com.chua.common.support.objects.plugin.PluginManager;
import com.chua.common.support.objects.plugin.PluginWrapper;
import com.chua.plugin.support.properties.PluginProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;

/**
 * 插件系统自动配置类
 * <p>
 * 完整实现 PF4J 风格的插件管理，包括：
 * - 插件自动加载和启动
 * - 目录监听和热加载
 * - 运行模式支持（开发/生产）
 * - 完整的生命周期管理
 * </p>
 *
 * @author CH
 * @since 2026/01/02
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(PluginProperties.class)
@ConditionalOnProperty(
    prefix = "spring.plugin",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class PluginAutoConfiguration {

    /**
     * 创建插件管理器Bean
     *
     * @param properties 插件配置属性
     * @return 插件管理器实例
     */
    @Bean
    @ConditionalOnMissingBean
    public PluginManager pluginManager(PluginProperties properties) {
        log.info("=".repeat(60));
        log.info("Initializing Plugin System (PF4J Compatible)");
        log.info("Runtime Mode: {}", properties.getRuntimeMode());
        log.info("=".repeat(60));

        // 根据运行模式调整配置
        adjustPropertiesByRuntimeMode(properties);

        // 创建插件目录
        File pluginsRoot = new File(properties.getPluginsRoot());
        if (!pluginsRoot.exists()) {
            pluginsRoot.mkdirs();
            log.info("Created plugins directory: {}", pluginsRoot.getAbsolutePath());
        }

        // 创建插件管理器
        DefaultPluginManager pluginManager = new DefaultPluginManager(
            pluginsRoot,
            properties.isAutoStart()
        );

        log.info("Plugin Manager created: {}", pluginManager.getClass().getSimpleName());
        log.info("Plugins Root: {}", pluginsRoot.getAbsolutePath());

        // 自动加载插件
        if (properties.isAutoLoad()) {
            loadPlugins(pluginManager, properties);
        }

        // 启用目录监听
        if (properties.isWatchEnabled()) {
            enableDirectoryWatcher(pluginManager, properties);
        }

        // 显示插件信息
        if (properties.isShowInfo()) {
            printPluginInfo(pluginManager, properties);
        }

        log.info("=".repeat(60));
        log.info("Plugin System Initialized Successfully");
        log.info("Total Plugins: {}", pluginManager.getPluginCount());
        log.info("=".repeat(60));

        return pluginManager;
    }

    /**
     * 根据运行模式调整配置
     */
    private void adjustPropertiesByRuntimeMode(PluginProperties properties) {
        if (properties.getRuntimeMode() == PluginProperties.RuntimeMode.PRODUCTION) {
            // 生产模式：禁用热加载，简化输出
            if (properties.isWatchEnabled()) {
                log.warn("Production mode detected, disabling directory watcher");
                properties.setWatchEnabled(false);
            }
            if (properties.isShowDetails()) {
                properties.setShowDetails(false);
            }
        } else {
            // 开发模式：建议启用热加载
            if (!properties.isWatchEnabled()) {
                log.info("Development mode detected, you may want to enable watch-enabled for hot reload");
            }
        }
    }

    /**
     * 加载插件
     */
    private void loadPlugins(PluginManager pluginManager, PluginProperties properties) {
        long startTime = System.currentTimeMillis();
        log.info("Loading plugins from: {}", pluginManager.getPluginsRoot().getAbsolutePath());

        try {
            pluginManager.loadPlugins();
            long elapsed = System.currentTimeMillis() - startTime;

            int pluginCount = pluginManager.getPluginCount();
            if (pluginCount > 0) {
                log.info("Loaded {} plugin(s) in {}ms", pluginCount, elapsed);
            } else {
                log.info("No plugins found in directory");
            }
        } catch (Exception e) {
            log.error("Failed to load plugins", e);
        }
    }

    /**
     * 启用目录监听器
     */
    private void enableDirectoryWatcher(PluginManager pluginManager, PluginProperties properties) {
        if (pluginManager instanceof DefaultPluginManager) {
            DefaultPluginManager defaultManager = (DefaultPluginManager) pluginManager;

            try {
                defaultManager.startDirectoryWatcher();
                defaultManager.setAutoReload(properties.isAutoReload());

                log.info("Directory watcher enabled");
                log.info("  - Auto Reload: {}", properties.isAutoReload());
                log.info("  - Watch Directory: {}", pluginManager.getPluginsRoot().getAbsolutePath());
            } catch (Exception e) {
                log.error("Failed to start directory watcher", e);
            }
        }
    }

    /**
     * 打印插件信息
     */
    private void printPluginInfo(PluginManager pluginManager, PluginProperties properties) {
        int pluginCount = pluginManager.getPluginCount();
        if (pluginCount == 0) {
            return;
        }

        log.info("");
        log.info("=".repeat(60));
        log.info("Loaded Plugins ({}):", pluginCount);
        log.info("=".repeat(60));

        for (PluginWrapper wrapper : pluginManager.getPlugins()) {
            printPluginDetails(wrapper, properties.isShowDetails());
        }

        log.info("=".repeat(60));
    }

    /**
     * 打印插件详情
     */
    private void printPluginDetails(PluginWrapper wrapper, boolean showDetails) {
        String pluginInfo = String.format(
            "  [%s] %s v%s - %s",
            wrapper.getPluginState(),
            wrapper.getPluginId(),
            wrapper.getDescriptor().getVersion(),
            wrapper.getDescriptor().getPluginDescription()
        );

        log.info(pluginInfo);

        if (showDetails) {
            log.info("    Provider: {}", wrapper.getDescriptor().getProvider());
            log.info("    License: {}", wrapper.getDescriptor().getLicense());
            if (wrapper.getDescriptor().hasDependencies()) {
                log.info("    Dependencies: {}", wrapper.getDescriptor().getDependencies());
            }
            log.info("    Path: {}", wrapper.getPluginPath().getAbsolutePath());
        }
    }
}
