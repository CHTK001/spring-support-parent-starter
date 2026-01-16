package com.chua.plugin.support.configuration;

import com.chua.common.support.objects.ConfigureObjectContext;
import com.chua.common.support.objects.DefaultConfigureObjectContext;
import com.chua.common.support.objects.ObjectContextSetting;
import com.chua.common.support.objects.plugin.DefaultPluginManager;
import com.chua.common.support.objects.plugin.PluginManager;
import com.chua.common.support.objects.plugin.PluginWrapper;
import com.chua.spring.support.plugin.SpringPluginManager;
import com.chua.spring.support.plugin.registry.PluginBeanDynamicRegistry;
import com.chua.plugin.support.properties.PluginProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
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
     * 创建 ObjectContext Bean
     *
     * @return ObjectContext 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public ConfigureObjectContext objectContext() {
        ObjectContextSetting setting = ObjectContextSetting.builder()
                .build();
        ConfigureObjectContext context = new DefaultConfigureObjectContext(setting);
        log.info("[插件系统][初始化]ObjectContext created: {}", context.getClass().getSimpleName());
        return context;
    }

    /**
     * 创建 PluginBeanDynamicRegistry Bean
     *
     * @return PluginBeanDynamicRegistry
     */
    @Bean
    @ConditionalOnMissingBean
    public PluginBeanDynamicRegistry pluginBeanDynamicRegistry() {
        return new PluginBeanDynamicRegistry();
    }

    /**
     * 创建插件管理器Bean
     *
     * @param properties 插件配置属性
     * @param objectContext ObjectContext 实例
     * @param dynamicRegistry 动态注册器
     * @return 插件管理器实例
     */
    @Bean
    @ConditionalOnMissingBean
    public PluginManager pluginManager(PluginProperties properties,
                                       ConfigureObjectContext objectContext,
                                       PluginBeanDynamicRegistry dynamicRegistry) {
        log.info("=".repeat(60));
        log.info("[插件系统][初始化]Initializing Plugin System (PF4J Compatible with Spring Integration)");
        log.info("[插件系统][初始化]Runtime Mode: {}", properties.getRuntimeMode());
        log.info("=".repeat(60));

        // 根据运行模式调整配置
        adjustPropertiesByRuntimeMode(properties);

        // 创建插件目录（配置验证中已确保目录存在）
        File pluginsRoot = new File(properties.getPluginsRoot());

        // 创建 Spring 集成的插件管理器
        SpringPluginManager pluginManager = new SpringPluginManager(
            pluginsRoot,
            objectContext,
            properties.getAuto().isStart()
        );

        // 设置动态注册器
        pluginManager.setDynamicRegistry(dynamicRegistry);
        dynamicRegistry.setPluginManager(pluginManager);

        log.info("[插件系统][初始化]Plugin Manager created: {}", pluginManager.getClass().getSimpleName());
        log.info("[插件系统][初始化]Plugins Root: {}", pluginsRoot.getAbsolutePath());

        // 自动加载插件
        if (properties.getAuto().isLoad()) {
            loadPlugins(pluginManager, properties);
        }

        // 启用目录监听
        if (properties.getWatch().isEnabled()) {
            enableDirectoryWatcher(pluginManager, properties);
        }

        // 显示插件信息
        if (properties.getDisplay().isInfo()) {
            printPluginInfo(pluginManager, properties);
        }

        log.info("=".repeat(60));
        log.info("[插件系统][初始化]Plugin System Initialized Successfully");
        log.info("[插件系统][初始化]Total Plugins: {}", pluginManager.getPlugins().size());
        log.info("=".repeat(60));

        return pluginManager;
    }

    /**
     * 插件管理器关闭处理器
     * <p>
     * 确保应用关闭时正确清理插件资源
     * </p>
     *
     * @param pluginManager 插件管理器实例
     * @return DisposableBean 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public DisposableBean pluginManagerShutdown(PluginManager pluginManager) {
        return () -> {
            try {
                log.info("[插件系统][关闭]开始关闭插件管理器");
                if (pluginManager instanceof SpringPluginManager springManager) {
                    // 停止目录监听器
                    try {
                        springManager.stopDirectoryWatcher();
                        log.debug("[插件系统][关闭]目录监听器已停止");
                    } catch (Exception e) {
                        log.warn("[插件系统][关闭]停止目录监听器失败", e);
                    }
                    // 停止所有插件
                    try {
                        springManager.stopPlugins();
                        log.debug("[插件系统][关闭]所有插件已停止");
                    } catch (Exception e) {
                        log.warn("[插件系统][关闭]停止插件失败", e);
                    }
                    // 卸载所有插件
                    try {
                        springManager.unloadPlugins();
                        log.debug("[插件系统][关闭]所有插件已卸载");
                    } catch (Exception e) {
                        log.warn("[插件系统][关闭]卸载插件失败", e);
                    }
                } else if (pluginManager instanceof DefaultPluginManager defaultManager) {
                    // 降级处理：使用 DefaultPluginManager 的方法
                    try {
                        defaultManager.stopDirectoryWatcher();
                        log.debug("[插件系统][关闭]目录监听器已停止");
                    } catch (Exception e) {
                        log.warn("[插件系统][关闭]停止目录监听器失败", e);
                    }
                    try {
                        defaultManager.stopPlugins();
                        log.debug("[插件系统][关闭]所有插件已停止");
                    } catch (Exception e) {
                        log.warn("[插件系统][关闭]停止插件失败", e);
                    }
                    try {
                        defaultManager.unloadPlugins();
                        log.debug("[插件系统][关闭]所有插件已卸载");
                    } catch (Exception e) {
                        log.warn("[插件系统][关闭]卸载插件失败", e);
                    }
                }
                log.info("[插件系统][关闭]插件管理器已关闭");
            } catch (Exception e) {
                log.error("[插件系统][关闭]关闭插件管理器时发生异常", e);
                // 不抛出异常，避免影响应用关闭流程
            }
        };
    }

    /**
     * 插件路由 BeanDefinition 统一包装处理器。
     *
     * @param properties 插件配置属性
     * @return BeanDefinitionPostProcessor 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public PluginRoutingBeanDefinitionPostProcessor pluginRoutingBeanDefinitionPostProcessor(PluginProperties properties) {
        return new PluginRoutingBeanDefinitionPostProcessor(properties);
    }

    /**
     * 根据运行模式调整配置。
     *
     * @param properties 插件配置属性
     */
    private void adjustPropertiesByRuntimeMode(PluginProperties properties) {
        if (properties.getRuntimeMode() == PluginProperties.RuntimeMode.PRODUCTION) {
            // 生产模式：禁用热加载，简化输出
            if (properties.getWatch().isEnabled()) {
                log.warn("[插件系统][配置]Production mode detected, disabling directory watcher");
                properties.getWatch().setEnabled(false);
            }
            if (properties.getDisplay().isDetails()) {
                properties.getDisplay().setDetails(false);
            }
        } else {
            // 开发模式：建议启用热加载
            if (!properties.getWatch().isEnabled()) {
                log.info("[插件系统][配置]Development mode detected, you may want to enable watch-enabled for hot reload");
            }
        }
    }

    /**
     * 加载插件
     *
     * @param pluginManager 插件管理器
     * @param properties 插件配置属性
     */
    private void loadPlugins(PluginManager pluginManager, PluginProperties properties) {
        long startTime = System.currentTimeMillis();
        log.info("[插件系统][加载]Loading plugins from: {}", pluginManager.getPluginsRoot().getAbsolutePath());

        try {
            pluginManager.loadPlugins();
            long elapsed = System.currentTimeMillis() - startTime;

            int pluginCount = pluginManager.getPlugins().size();
            if (pluginCount > 0) {
                log.info("[插件系统][加载]Loaded {} plugin(s) in {}ms", pluginCount, elapsed);
            } else {
                log.info("[插件系统][加载]No plugins found in directory");
            }
        } catch (Exception e) {
            String errorMsg = String.format("Failed to load plugins: %s", e.getMessage());
            log.error("[插件系统][加载]{}", errorMsg, e);

            // 根据失败策略决定是否中断启动
            if (properties.getLoadFailureStrategy() == PluginProperties.PluginLoadFailureStrategy.FAIL_FAST) {
                throw new IllegalStateException(errorMsg, e);
            }
            // CONTINUE 策略：记录错误但继续启动
            log.warn("[插件系统][加载]插件加载失败，但应用将继续启动（策略：CONTINUE）");
        }
    }

    /**
     * 启用目录监听器
     *
     * @param pluginManager 插件管理器
     * @param properties 插件配置属性
     */
    private void enableDirectoryWatcher(PluginManager pluginManager, PluginProperties properties) {
        if (pluginManager instanceof SpringPluginManager springManager) {
            try {
                // 使用 SpringPluginManager 的方法，确保文件监听时能触发 Spring Bean 注册
                springManager.startDirectoryWatcher();
                springManager.setAutoReload(properties.getAuto().isReload());

                log.info("[插件系统][监听]Directory watcher enabled (with Spring integration)");
                log.info("[插件系统][监听]  - Auto Reload: {}", properties.getAuto().isReload());
                log.info("[插件系统][监听]  - Watch Directory: {}", pluginManager.getPluginsRoot().getAbsolutePath());
            } catch (Exception e) {
                log.error("[插件系统][监听]Failed to start directory watcher", e);
                // 目录监听器启动失败不影响应用启动，仅记录错误
                log.warn("[插件系统][监听]目录监听器启动失败，应用将继续运行（热加载功能不可用）");
            }
        } else if (pluginManager instanceof DefaultPluginManager defaultManager) {
            // 降级：如果不是 SpringPluginManager，使用 DefaultPluginManager 的方法
            try {
                defaultManager.startDirectoryWatcher();
                defaultManager.setAutoReload(properties.getAuto().isReload());

                log.info("[插件系统][监听]Directory watcher enabled");
                log.info("[插件系统][监听]  - Auto Reload: {}", properties.getAuto().isReload());
                log.info("[插件系统][监听]  - Watch Directory: {}", pluginManager.getPluginsRoot().getAbsolutePath());
            } catch (Exception e) {
                log.error("[插件系统][监听]Failed to start directory watcher", e);
                // 目录监听器启动失败不影响应用启动，仅记录错误
                log.warn("[插件系统][监听]目录监听器启动失败，应用将继续运行（热加载功能不可用）");
            }
        }
    }

    /**
     * 打印插件信息
     *
     * @param pluginManager 插件管理器
     * @param properties 插件配置属性
     */
    private void printPluginInfo(PluginManager pluginManager, PluginProperties properties) {
        int pluginCount = pluginManager.getPlugins().size();
        if (pluginCount == 0) {
            return;
        }

        log.info("");
        log.info("=".repeat(60));
        log.info("[插件系统][信息]Loaded Plugins ({}):", pluginCount);
        log.info("=".repeat(60));

        for (PluginWrapper wrapper : pluginManager.getPlugins()) {
            printPluginDetails(wrapper, properties.getDisplay().isDetails());
        }

        log.info("=".repeat(60));
    }

    /**
     * 打印插件详情
     *
     * @param wrapper 插件包装器
     * @param showDetails 是否显示详细信息
     */
    private void printPluginDetails(PluginWrapper wrapper, boolean showDetails) {
        String pluginInfo = String.format(
            "  [%s] %s v%s - %s",
            wrapper.getPluginState(),
            wrapper.getPluginId(),
            wrapper.getDescriptor().getVersion(),
            wrapper.getDescriptor().getPluginDescription()
        );

        log.info("[插件系统][信息]{}", pluginInfo);

        if (showDetails) {
            log.info("[插件系统][信息]    Provider: {}", wrapper.getDescriptor().getProvider());
            log.info("[插件系统][信息]    License: {}", wrapper.getDescriptor().getLicense());
            if (wrapper.getDescriptor().hasDependencies()) {
                log.info("[插件系统][信息]    Dependencies: {}", wrapper.getDescriptor().getDependencies());
            }
            log.info("[插件系统][信息]    Path: {}", wrapper.getPluginPath().getAbsolutePath());
        }
    }
}
