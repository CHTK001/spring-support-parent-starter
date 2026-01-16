package com.chua.sync.support.configuration;

import com.chua.sync.support.client.SyncClient;
import com.chua.sync.support.properties.SyncProperties;
import com.chua.sync.support.server.SyncServer;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import static com.chua.starter.common.support.logger.ModuleLog.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.env.Environment;

/**
 * 同步协议自动配置
 * <p>
 * 支持 server、client、both 三种模式，同时判断对应的 enable 属性
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
        Boolean serverEnable = environment.getProperty("plugin.sync.server.enable", Boolean.class, false);
        Boolean clientEnable = environment.getProperty("plugin.sync.client.enable", Boolean.class, false);
        log.info("[Sync] 模式: {}, 服务端: [{}], 客户端: [{}]", 
                highlight(type), status(serverEnable), status(clientEnable));
    }

    /**
     * 创建同步服务端
     * <p>
     * 条件：type 为 server 或 both，且 server.enable = true
     * </p>
     *
     * @param syncProperties 同步配置属性
     * @return 同步服务端实例，如果未启用则返回 null
     * @author CH
     * @since 1.0.0
     */
    @Bean
    @ConditionalOnMissingBean
    @Conditional(SyncServerCondition.class)
    public SyncServer syncServer(SyncProperties syncProperties) {
        log.info("[Sync] 创建服务端");
        return new SyncServer(syncProperties);
    }

    /**
     * 创建同步客户端
     * <p>
     * 条件：type 为 client 或 both，且 client.enable = true
     * </p>
     *
     * @param syncProperties 同步配置属性
     * @return 同步客户端实例，如果未启用则返回 null
     * @author CH
     * @since 1.0.0
     */
    @Bean
    @ConditionalOnMissingBean
    @Conditional(SyncClientCondition.class)
    public SyncClient syncClient(SyncProperties syncProperties) {
        log.info("[Sync] 创建客户端");
        return new SyncClient(syncProperties, environment);
    }
}
