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
     * @param launcher   OSGi 启动器（由 spring-support-osgi-starter 提供）
     * @param properties Bundle 配置
     * @return Bundle 管理器
     */
    @Bean
    public BundleManagerService bundleManagerService(
            OsgiLauncher launcher,
            BundleProperties properties) {
        return new BundleManagerService(launcher, properties);
    }

    /**
     * 注册 Bundle 模块服务信息。
     */
    @Bean
    public ServiceInfoContributor bundleServiceInfoContributor() {
        return registry -> registry.registerFlag("bundle", "OSGi Bundle", true, "OSGi Bundle 生命周期管理");
    }
}
