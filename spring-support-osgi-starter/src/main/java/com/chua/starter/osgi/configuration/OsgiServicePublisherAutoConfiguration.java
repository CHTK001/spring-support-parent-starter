package com.chua.starter.osgi.configuration;

import com.chua.osgi.support.FelixOsgiLauncher;
import com.chua.starter.osgi.support.publish.DefaultOsgiServicePublisher;
import com.chua.starter.osgi.support.publish.OsgiServicePublisher;
import com.chua.starter.osgi.support.publish.OsgiSpiBeanPublisher;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

/**
 * OSGi 服务发布门面自动配置（P3：SPI 注册门面）。
 *
 * <p>提供 {@link OsgiServicePublisher} 门面 Bean，供 common 等依赖本模块的
 * 组件把 {@code @Spi} 标注的服务发布进 OSGi。本配置<b>不受</b>
 * {@code plugin.osgi.server.enabled} 门控——门面始终装配，Felix 框架未运行时
 * 发布操作为 no-op，保证依赖方在任何宿主中都能注入。</p>
 *
 * <p>{@link OsgiSpiBeanPublisher}（@Spi Bean 自动发布）属于服务器侧行为，
 * 仅在 {@code plugin.osgi.server.enabled=true} 时装配。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@AutoConfiguration
@ConditionalOnClass(FelixOsgiLauncher.class)
public class OsgiServicePublisherAutoConfiguration {

    /**
     * OSGi 服务发布门面（始终可用，框架未运行时 no-op）。
     *
     * @param launcherProvider Felix 启动器提供者
     * @return 服务发布门面
     */
    @Bean
    @ConditionalOnMissingBean(OsgiServicePublisher.class)
    public OsgiServicePublisher osgiServicePublisher(org.springframework.beans.factory.ObjectProvider<FelixOsgiLauncher> launcherProvider) {
        return new DefaultOsgiServicePublisher(launcherProvider);
    }

    /**
     * @Spi Bean 自动发布器（仅服务器设施启用时装配）。
     *
     * @param publisher          服务发布门面
     * @param applicationContext 应用上下文
     * @return 自动发布器
     */
    @Bean
    @ConditionalOnProperty(prefix = "plugin.osgi", name = "server.enabled", havingValue = "true")
    public OsgiSpiBeanPublisher osgiSpiBeanPublisher(OsgiServicePublisher publisher,
                                                     ApplicationContext applicationContext) {
        return new OsgiSpiBeanPublisher(publisher, applicationContext);
    }
}
