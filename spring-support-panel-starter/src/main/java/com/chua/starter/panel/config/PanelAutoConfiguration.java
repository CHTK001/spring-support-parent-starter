package com.chua.starter.panel.config;

import com.chua.starter.panel.cache.InMemoryPanelConnectionCache;
import com.chua.starter.ai.support.chat.ChatClient;
import com.chua.starter.panel.cache.PanelConnectionCache;
import com.chua.starter.panel.controller.PanelJdbcController;
import com.chua.starter.panel.controller.PanelDatasourceController;
import com.chua.starter.panel.service.JdbcPanelService;
import com.chua.starter.panel.service.PanelAiService;
import com.chua.starter.panel.service.PanelConnectionService;
import com.chua.starter.panel.service.PanelDatasourceService;
import com.chua.starter.panel.service.PanelDocumentService;
import com.chua.starter.panel.service.PanelRemarkService;
import com.chua.starter.panel.service.PanelSqlTemplateService;
import com.chua.starter.panel.service.impl.DefaultJdbcPanelService;
import com.chua.starter.panel.service.impl.DefaultPanelAiService;
import com.chua.starter.panel.service.impl.DefaultPanelConnectionService;
import com.chua.starter.panel.service.impl.DefaultPanelDatasourceService;
import com.chua.starter.panel.service.impl.DefaultPanelDocumentService;
import com.chua.starter.panel.service.impl.DefaultPanelRemarkService;
import com.chua.starter.panel.service.impl.DefaultPanelSqlTemplateService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;

/**
 * 面板模块自动配置。
 */
@AutoConfiguration
@EnableConfigurationProperties(PanelProperties.class)
@ConditionalOnProperty(prefix = PanelProperties.PREFIX, name = "enabled", havingValue = "true", matchIfMissing = true)
public class PanelAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public PanelConnectionCache panelConnectionCache(PanelProperties properties) {
        return new InMemoryPanelConnectionCache(properties.getConnectionCacheTtl());
    }

    @Bean
    @ConditionalOnMissingBean
    public PanelConnectionService panelConnectionService(PanelConnectionCache panelConnectionCache) {
        return new DefaultPanelConnectionService(panelConnectionCache);
    }

    @Bean
    @ConditionalOnMissingBean
    public JdbcPanelService jdbcPanelService(
            PanelConnectionCache panelConnectionCache,
            PanelProperties properties,
            ObjectProvider<ChatClient> chatClientProvider) {
        return new DefaultJdbcPanelService(panelConnectionCache, properties, chatClientProvider);
    }

    @Bean
    @ConditionalOnMissingBean
    public PanelDocumentService panelDocumentService(JdbcPanelService jdbcPanelService) {
        return new DefaultPanelDocumentService(jdbcPanelService);
    }

    @Bean
    @ConditionalOnMissingBean
    public PanelAiService panelAiService(
            JdbcPanelService jdbcPanelService,
            PanelProperties properties,
            ObjectProvider<ChatClient> chatClientProvider) {
        return new DefaultPanelAiService(jdbcPanelService, properties, chatClientProvider);
    }

    @Bean
    @ConditionalOnMissingBean
    public PanelRemarkService panelRemarkService() {
        return new DefaultPanelRemarkService();
    }

    @Bean
    @ConditionalOnMissingBean
    public PanelDatasourceService panelDatasourceService(PanelProperties properties, ObjectMapper objectMapper) {
        return new DefaultPanelDatasourceService(properties, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public PanelSqlTemplateService panelSqlTemplateService() {
        return new DefaultPanelSqlTemplateService();
    }

    @Bean
    @ConditionalOnMissingBean
    public PanelDatasourceController panelDatasourceController(PanelDatasourceService panelDatasourceService) {
        return new PanelDatasourceController(panelDatasourceService);
    }

    @Bean
    @ConditionalOnMissingBean
    public PanelJdbcController panelJdbcController(
            PanelConnectionService panelConnectionService,
            JdbcPanelService jdbcPanelService,
            PanelDocumentService panelDocumentService,
            PanelAiService panelAiService,
            PanelRemarkService panelRemarkService,
            PanelSqlTemplateService panelSqlTemplateService) {
        return new PanelJdbcController(
                panelConnectionService,
                jdbcPanelService,
                panelDocumentService,
                panelAiService,
                panelRemarkService,
                panelSqlTemplateService);
    }
}
