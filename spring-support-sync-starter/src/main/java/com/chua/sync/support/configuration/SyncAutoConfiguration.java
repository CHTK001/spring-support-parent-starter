package com.chua.sync.support.configuration;

import com.chua.sync.support.client.SyncClient;
import com.chua.sync.support.properties.SyncProperties;
import com.chua.sync.support.server.SyncServer;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * 同步协议自动配置
 * <p>
 * 支持 server、client、both 三种模式
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/12/04
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(SyncProperties.class)
public class SyncAutoConfiguration {

    @Autowired
    private Environment environment;

    @PostConstruct
    public void init() {
        String type = environment.getProperty("plugin.sync.type");
        log.info("[SyncAutoConfiguration] plugin.sync.type = {}", type);
    }

    /**
     * 创建同步服务端 (server 或 both 模式)
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = SyncProperties.PRE, name = "type", havingValue = "server")
    public SyncServer syncServerOnly(SyncProperties syncProperties) {
        return new SyncServer(syncProperties);
    }

    /**
     * 创建同步客户端 (client 模式)
     *
     * @param syncProperties 同步配置属性
     * @return 同步客户端实例
     * @author CH
     * @since 1.0.0
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = SyncProperties.PRE, name = "type", havingValue = "client")
    public SyncClient syncClientOnly(SyncProperties syncProperties) {
        return new SyncClient(syncProperties, environment);
    }

    /**
     * both 模式 - 服务端
     */
    @Bean("syncServer")
    @ConditionalOnProperty(prefix = SyncProperties.PRE, name = "type", havingValue = "both")
    public SyncServer syncServerBoth(SyncProperties syncProperties) {
        return new SyncServer(syncProperties);
    }

    /**
     * both 模式 - 客户端
     *
     * @param syncProperties 同步配置属性
     * @return 同步客户端实例
     * @author CH
     * @since 1.0.0
     */
    @Bean("syncClient")
    @ConditionalOnProperty(prefix = SyncProperties.PRE, name = "type", havingValue = "both")
    public SyncClient syncClientBoth(SyncProperties syncProperties) {
        return new SyncClient(syncProperties, environment);
    }
}
