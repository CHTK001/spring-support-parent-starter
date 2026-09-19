package com.chua.starter.bundle.configuration;

import com.chua.common.support.osgi.OsgiBundle;
import com.chua.common.support.osgi.OsgiLauncher;
import com.chua.osgi.support.FelixOsgiLauncher;
import com.chua.starter.bundle.service.BundleManagerService;
import com.chua.starter.bundle.properties.BundleProperties;
import com.chua.starter.common.support.serviceinfo.ServiceInfoContributor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.Map;

/**
 * Bundle 管理自动配置。
 *
 * <p>将 {@link OsgiLauncher} 包装为 {@link BundleManagerService}，
 * 提供 Maven 坐标驱动的 Bundle 安装/卸载/查询能力。</p>
 *
 * @since 4.0.0.43
 * @author CH
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass({FelixOsgiLauncher.class, OsgiLauncher.class})
@ConditionalOnProperty(prefix = "plugin.bundle", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(BundleProperties.class)
public class BundleAutoConfiguration {

    /**
     * 创建 Bundle 管理器服务。
     *
     * @param launcher   osgi 启动器（由 spring-支持-osgi-启动 提供）
     * @param properties Bundle 配置
     * @return Bundle 管理器
     */
    @Bean
    public BundleManagerService bundleManagerService(
            OsgiLauncher launcher,
            BundleProperties properties) {
        /**
         * bundle管理器服务。
         * @param launcher launcher
         * @param properties 属性
         * @return bundle管理器服务的结果
         */
        return new BundleManagerService(launcher, properties);
    }

    /**
     * 启动时按配置自动安装/卸载 bundle。
     *
     * <p>执行顺序：Spring 容器初始化完成后（SmartInitializingSingleton），
     * 先卸载 {@code uninstall-at-startup} 列表，再安装 {@code install-at-startup} 列表。</p>
     *
     * @param bundleManager  Bundle 管理器
     * @param properties Bundle 配置
     * @return 启动时 Bundle 处理器
     */
    @Bean
    public org.springframework.beans.factory.SmartInitializingSingleton bundleStartupProcessor(
            BundleManagerService bundleManager,
            BundleProperties properties) {
        return () -> {
            for (String symbolicName : properties.getUninstallAtStartup()) {
                if (symbolicName == null || symbolicName.isBlank()) {
                    continue;
                }
                try {
                    bundleManager.uninstall(symbolicName.trim());
                } catch (Exception e) {
                    log.warn("[Bundle] 启动卸载失败 [{}]: {}", symbolicName, e.getMessage());
                }
            }
            if (!properties.getInstallAtStartup().isEmpty()) {
                try {
                    bundleManager.installAll(properties.getInstallAtStartup());
                } catch (Exception e) {
                    log.warn("[Bundle] 启动安装失败: {}", e.getMessage());
                }
            }
        };
    }

    /**
     * 注册 Bundle 模块服务信息。
     * @return bundle服务信息contributor的结果
     */
    @Bean
    public ServiceInfoContributor bundleServiceInfoContributor() {
        return registry -> registry.registerFlag("bundle", "OSGi Bundle", true, "OSGi Bundle 生命周期管理");
    }
}
