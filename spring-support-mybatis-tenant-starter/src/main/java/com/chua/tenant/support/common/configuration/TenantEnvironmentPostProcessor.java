package com.chua.tenant.support.common.configuration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;

import java.util.HashMap;
import java.util.Map;

/**
 * 租户环境配置后处理器
 * <p>
 * 根据租户模块的配置自动设置 sync 模块的配置，实现配置串联。
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/12/06
 */
public class TenantEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final String TENANT_PREFIX = "plugin.mybatis-plus.tenant.";
    private static final String SYNC_PREFIX = "plugin.sync.";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        // 检查服务端同步是否启用
        boolean serverEnabled = "true".equalsIgnoreCase(environment.getProperty(TENANT_PREFIX + "server.enable", "false"));
        boolean serverSyncEnabled = "true".equalsIgnoreCase(environment.getProperty(TENANT_PREFIX + "server.sync-enable", "false"));

        // 检查客户端同步是否启用
        boolean clientEnabled = "true".equalsIgnoreCase(environment.getProperty(TENANT_PREFIX + "client.enable", "false"));
        boolean clientSyncEnabled = "true".equalsIgnoreCase(environment.getProperty(TENANT_PREFIX + "client.sync-enable", "false"));

        // 准备要设置的 sync 配置
        Map<String, Object> syncProperties = new HashMap<>();

        // 服务端模式
        if (serverEnabled && serverSyncEnabled) {
            if (environment.getProperty(SYNC_PREFIX + "enable") == null) {
                syncProperties.put(SYNC_PREFIX + "enable", "true");
            }
            if (environment.getProperty(SYNC_PREFIX + "type") == null) {
                syncProperties.put(SYNC_PREFIX + "type", "server");
            }
            linkServerConfig(environment, syncProperties);
        }

        // 客户端模式
        if (clientEnabled && clientSyncEnabled) {
            if (environment.getProperty(SYNC_PREFIX + "enable") == null) {
                syncProperties.put(SYNC_PREFIX + "enable", "true");
            }
            if (environment.getProperty(SYNC_PREFIX + "type") == null) {
                syncProperties.put(SYNC_PREFIX + "type", "client");
            }
            linkClientConfig(environment, syncProperties);
        }

        // 添加配置源
        if (!syncProperties.isEmpty()) {
            MutablePropertySources propertySources = environment.getPropertySources();
            propertySources.addLast(new MapPropertySource("tenantSyncProperties", syncProperties));
        }
    }

    /**
     * 串联服务端配置
     */
    private void linkServerConfig(ConfigurableEnvironment environment, Map<String, Object> syncProperties) {
        String host = environment.getProperty(TENANT_PREFIX + "server.host", "0.0.0.0");
        String port = environment.getProperty(TENANT_PREFIX + "server.port", "19380");
        String protocol = environment.getProperty(TENANT_PREFIX + "server.protocol", "rsocket");

        if (environment.getProperty(SYNC_PREFIX + "server.host") == null) {
            syncProperties.put(SYNC_PREFIX + "server.host", host);
        }
        if (environment.getProperty(SYNC_PREFIX + "server.port") == null) {
            syncProperties.put(SYNC_PREFIX + "server.port", port);
        }
        if (environment.getProperty(SYNC_PREFIX + "server.protocol") == null) {
            syncProperties.put(SYNC_PREFIX + "server.protocol", protocol);
        }
    }

    /**
     * 串联客户端配置
     */
    private void linkClientConfig(ConfigurableEnvironment environment, Map<String, Object> syncProperties) {
        String serverHost = environment.getProperty(TENANT_PREFIX + "client.server-host", "localhost");
        String serverPort = environment.getProperty(TENANT_PREFIX + "client.server-port", "19380");
        String protocol = environment.getProperty(TENANT_PREFIX + "client.protocol", "rsocket");

        if (environment.getProperty(SYNC_PREFIX + "client.server-host") == null) {
            syncProperties.put(SYNC_PREFIX + "client.server-host", serverHost);
        }
        if (environment.getProperty(SYNC_PREFIX + "client.server-port") == null) {
            syncProperties.put(SYNC_PREFIX + "client.server-port", serverPort);
        }
        if (environment.getProperty(SYNC_PREFIX + "client.protocol") == null) {
            syncProperties.put(SYNC_PREFIX + "client.protocol", protocol);
        }
    }
}
