package com.chua.report.client.starter.configuration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.boot.logging.DeferredLog;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;

import java.util.HashMap;
import java.util.Map;

/**
* 上报客户端环境配置后处理器
* <p>
* 根据上报客户端的配置自动设置 同步 模块的配置，实现配置串联。
* </p>
*
* @版本 1.0.0
* @since 2024/12/06
* @author CH
 */
public class ReportEnvironmentPostProcessor implements EnvironmentPostProcessor {

    /** 日志 */
    private static final DeferredLog log = new DeferredLog();
    /** Report_客户端_前缀 */
    private static final String REPORT_CLIENT_PREFIX = "plugin.report.client.";
    /** Report_前缀 */
    private static final String REPORT_PREFIX = "plugin.report.";
    /** 同步_前缀 */
    private static final String SYNC_PREFIX = "plugin.sync.";

    @Override
    /** Post处理环境 */
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        log.info("[ReportEnvPostProcessor] 开始处理环境配置");

        // 检查上报客户端是否启用
        String enabled = environment.getProperty(REPORT_CLIENT_PREFIX + "enable", "true");
        log.info("[ReportEnvPostProcessor] plugin.report.client.enable = " + enabled);

        if (!"true".equalsIgnoreCase(enabled)) {
            log.info("[ReportEnvPostProcessor] 上报客户端未启用，跳过");
            return;
        }

 // 准备要设置的 同步 配置
        Map<String, Object> syncProperties = new HashMap<>();

 // 自动启用 同步 客户端
        if ("true".equals(environment.getProperty(REPORT_CLIENT_PREFIX + "enable"))) {
            syncProperties.put(SYNC_PREFIX + "client.enable", "true");
            log.info("[ReportEnvPostProcessor] 设置 plugin.sync.client.enable = true");
        }

        // 设置为客户端模式
        if (environment.getProperty(REPORT_PREFIX + "type") != null) {
            syncProperties.put(SYNC_PREFIX + "type", environment.getProperty(REPORT_PREFIX + "type"));
            log.info("[ReportEnvPostProcessor] 设置 plugin.sync.type = client");
        } else {
            String property = environment.getProperty(SYNC_PREFIX + "type");
            if (property != null) {
                syncProperties.put(SYNC_PREFIX + "type", property);
                log.info("[ReportEnvPostProcessor] 设置 plugin.sync.type = client");
            } else {
                syncProperties.put(SYNC_PREFIX + "type", "client");
                log.info("[ReportEnvPostProcessor] 设置 plugin.sync.type = client");
            }
        }

        // 串联客户端配置
        /**
        * 链接客户端配置。
        * @param environment 环境
        * @param syncProperties 同步属性
         */
        linkClientConfig(environment, syncProperties);

 // 添加配置源（使用 添加第一个 确保优先级高于默认配置）
        if (!syncProperties.isEmpty()) {
            MutablePropertySources propertySources = environment.getPropertySources();
            propertySources.addFirst(new MapPropertySource("reportSyncProperties", syncProperties));
            log.info("[ReportEnvPostProcessor] 已添加配置源，共 " + syncProperties.size() + " 项配置");
        }
    }

    /**
    * 串联客户端配置
    * @param environment 环境
    * @param syncProperties 同步属性
     */
    private void linkClientConfig(ConfigurableEnvironment environment, Map<String, Object> syncProperties) {
        // 服务端连接配置
        String serverHost = environment.getProperty(REPORT_CLIENT_PREFIX + "host", "localhost");
        String serverPort = environment.getProperty(REPORT_CLIENT_PREFIX + "port", "29170");
        String protocol = environment.getProperty(REPORT_CLIENT_PREFIX + "protocol", "rsocket-sync");
        String clientRealHost = environment.getProperty(REPORT_CLIENT_PREFIX + "info.host");

        if (environment.getProperty(SYNC_PREFIX + "client.server-host") == null) {
            syncProperties.put(SYNC_PREFIX + "client.server-host", serverHost);
        }
        if (environment.getProperty(SYNC_PREFIX + "client.server-port") == null) {
            syncProperties.put(SYNC_PREFIX + "client.server-port", serverPort);
        }
        if (environment.getProperty(SYNC_PREFIX + "client.protocol") == null) {
            syncProperties.put(SYNC_PREFIX + "client.protocol", protocol);
        }
        if (environment.getProperty(SYNC_PREFIX + "client.ip-address") == null) {
            syncProperties.put(SYNC_PREFIX + "client.ip-address", clientRealHost);
        }

        // 多网卡场景指定 IP（优先级更高）
        String clientHost = environment.getProperty(REPORT_CLIENT_PREFIX + "info.host");
        if (clientHost != null && !clientHost.isEmpty()) {
            syncProperties.put(SYNC_PREFIX + "client.ip-address", clientHost);
        }
    }
}
