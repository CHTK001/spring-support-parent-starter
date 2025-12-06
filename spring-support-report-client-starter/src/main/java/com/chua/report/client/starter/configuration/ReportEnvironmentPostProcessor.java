package com.chua.report.client.starter.configuration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;

import java.util.HashMap;
import java.util.Map;

/**
 * 上报客户端环境配置后处理器
 * <p>
 * 根据上报客户端的配置自动设置 sync 模块的配置，实现配置串联。
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/12/06
 */
public class ReportEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final String REPORT_PREFIX = "plugin.report.client.";
    private static final String SYNC_PREFIX = "plugin.sync.";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        // 检查上报客户端是否启用
        String enabled = environment.getProperty(REPORT_PREFIX + "enable", "true");
        if (!"true".equalsIgnoreCase(enabled)) {
            return;
        }

        // 检查同步是否启用
        String syncEnabled = environment.getProperty(REPORT_PREFIX + "sync.enable", "false");
        if (!"true".equalsIgnoreCase(syncEnabled)) {
            return;
        }

        // 准备要设置的 sync 配置
        Map<String, Object> syncProperties = new HashMap<>();

        // 自动启用 sync
        if (environment.getProperty(SYNC_PREFIX + "enable") == null) {
            syncProperties.put(SYNC_PREFIX + "enable", "true");
        }

        // 设置为客户端模式
        if (environment.getProperty(SYNC_PREFIX + "type") == null) {
            syncProperties.put(SYNC_PREFIX + "type", "client");
        }

        // 串联客户端配置
        linkClientConfig(environment, syncProperties);

        // 添加配置源
        if (!syncProperties.isEmpty()) {
            MutablePropertySources propertySources = environment.getPropertySources();
            propertySources.addLast(new MapPropertySource("reportSyncProperties", syncProperties));
        }
    }

    /**
     * 串联客户端配置
     */
    private void linkClientConfig(ConfigurableEnvironment environment, Map<String, Object> syncProperties) {
        String serverHost = environment.getProperty(REPORT_PREFIX + "sync.server-host", "localhost");
        String serverPort = environment.getProperty(REPORT_PREFIX + "sync.server-port", "19380");
        String protocol = environment.getProperty(REPORT_PREFIX + "sync.protocol", "rsocket");

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
