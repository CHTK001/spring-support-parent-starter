package com.chua.starter.server.support.config;

import com.chua.starter.common.support.serviceinfo.ServiceInfoContributor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 服务器管理配置
 *
 * @author CH
 * @version 1.0.0
 * @since 2025/5/8
 */
@Configuration
@EnableConfigurationProperties(ServerManagementProperties.class)
public class ServerManagementConfiguration {

    /**
     * 注册服务器管理模块服务信息。
     *
     * @return 服务信息贡献器
     */
    @Bean
    public ServiceInfoContributor serverServiceInfoContributor() {
        return registry -> {
            registry.registerEndpoint("服务器管理", "服务器管理", "/server/**", "服务器管理与监控");
            registry.registerFlag("server", "服务器管理", true, "服务器管理模块");
        };
    }
}
