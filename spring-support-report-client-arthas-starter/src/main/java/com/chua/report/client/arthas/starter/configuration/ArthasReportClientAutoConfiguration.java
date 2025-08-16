package com.chua.report.client.arthas.starter.configuration;

import com.chua.report.client.arthas.starter.properties.ArthasClientProperties;
import com.chua.report.client.starter.setting.SettingFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Arthas 上报客户端自动配置
 *
 * 自我注入：仅在 report 客户端已启用时生效，通过读取配置决定是否启用。
 */
@Configuration
@EnableConfigurationProperties({ArthasClientProperties.class})
@ConditionalOnClass(SettingFactory.class)
@ConditionalOnProperty(prefix = ArthasClientProperties.PREFIX, name = "enable", havingValue = "true", matchIfMissing = true)
public class ArthasReportClientAutoConfiguration {
    @Bean
    public ArthasConfigReporter arthasConfigReporter(ArthasClientProperties properties,
                                                     ObjectProvider<com.chua.common.support.protocol.server.ProtocolServer> protocolServerProvider) {
        return new ArthasConfigReporter(properties, protocolServerProvider);
    }
}


