package com.chua.sync.support.configuration;

import com.chua.sync.support.client.SyncClient;
import com.chua.sync.support.properties.SyncProperties;
import com.chua.sync.support.server.SyncServer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
@Configuration
@EnableConfigurationProperties(SyncProperties.class)
@ConditionalOnProperty(prefix = SyncProperties.PRE, name = "enable", havingValue = "true")
public class SyncAutoConfiguration {

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
     * 创建同步客户端 (client 模式，默认)
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = SyncProperties.PRE, name = "type", havingValue = "client", matchIfMissing = true)
    public SyncClient syncClientOnly(SyncProperties syncProperties) {
        return new SyncClient(syncProperties);
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
     */
    @Bean("syncClient")
    @ConditionalOnProperty(prefix = SyncProperties.PRE, name = "type", havingValue = "both")
    public SyncClient syncClientBoth(SyncProperties syncProperties) {
        return new SyncClient(syncProperties);
    }
}
