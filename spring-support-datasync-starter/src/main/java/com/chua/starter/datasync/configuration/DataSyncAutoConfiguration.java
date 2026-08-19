package com.chua.starter.datasync.configuration;

import com.chua.starter.common.support.serviceinfo.ServiceInfoContributor;
import com.chua.starter.common.support.serviceinfo.ServiceInfoRegistry;
import com.chua.starter.datasync.DefaultDataSyncServer;
import com.chua.starter.datasync.agent.AgentServerManager;
import com.chua.starter.datasync.agent.DataSyncAgentServer;
import com.chua.starter.datasync.config.DirectoryConfigDefinition;
import com.chua.starter.datasync.mapping.DataSyncFieldMapping;
import com.chua.starter.datasync.mapping.DataSyncMappingManager;
import com.chua.starter.datasync.mapping.DefaultDataSyncMappingManager;
import com.chua.starter.datasync.scanner.ConfigFileParser;
import com.chua.starter.datasync.scanner.DirectoryDataSyncMappingScanner;
import com.chua.starter.datasync.scanner.JsonConfigFileParser;
import com.chua.starter.datasync.scanner.PropertiesConfigFileParser;
import com.chua.starter.datasync.scanner.YamlConfigFileParser;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

/**
 * DataSync 自动配置。
 *
 * @since 4.0.0.42
 * @author CH
 */
@AutoConfiguration
@ConditionalOnClass(name = "com.chua.starter.datasync.DataSyncServer")
@ConditionalOnProperty(prefix = "plugin.datasync", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(DatasyncProperties.class)
public class DataSyncAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    /** DataSyncMappingManager */
    public DataSyncMappingManager dataSyncMappingManager() {
        return new DefaultDataSyncMappingManager();
    }

    @Bean
    @ConditionalOnMissingBean
    /**
     * DataSyncServer
     * @param agentServerManager agentServerManager
     * @param mappingManager mappingManager
     */
    public com.chua.starter.datasync.DataSyncServer dataSyncServer(AgentServerManager agentServerManager,
                                                                   DataSyncMappingManager mappingManager) {
        com.chua.starter.datasync.DefaultDataSyncServer server = new com.chua.starter.datasync.DefaultDataSyncServer(agentServerManager);
        server.setMappingManager(mappingManager);
        return server;
    }

    @Bean
    @ConditionalOnMissingBean
    /** AgentServerManager */
    public AgentServerManager agentServerManager() {
        return new DataSyncAgentServer(null, null, "default-server");
    }

    @Bean
    @ConditionalOnProperty(prefix = "plugin.datasync", name = "mapping-source", havingValue = "file")
    @ConditionalOnMissingBean
    /**
     * DirectoryDataSyncMappingScanner
     * @param properties properties
     * @param mappingManager mappingManager
     */
    public DirectoryDataSyncMappingScanner directoryDataSyncMappingScanner(DatasyncProperties properties,
                                                                          DataSyncMappingManager mappingManager) {
        return new DirectoryDataSyncMappingScanner(
                mappingManager,
                new SimpleDirectoryConfigDefinition(properties.getFileMapping().getDirectoryPath()),
                List.of(
                        new YamlConfigFileParser(),
                        new JsonConfigFileParser(),
                        new PropertiesConfigFileParser()
                )
        );
    }

    @Bean
    @ConditionalOnProperty(prefix = "plugin.datasync.database-mapping", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean
    /**
     * DatabaseMappingLoader
     * @param dataSource dataSource
     * @param properties properties
     * @param mappingManager mappingManager
     */
    public DatabaseMappingLoader databaseMappingLoader(javax.sql.DataSource dataSource,
                                                       DatasyncProperties properties,
                                                       DataSyncMappingManager mappingManager) {
        return new DatabaseMappingLoader(dataSource, properties, mappingManager);
    }

    private static class SimpleDirectoryConfigDefinition implements DirectoryConfigDefinition {
        /** Directory路径 */
        private final String directoryPath;

        SimpleDirectoryConfigDefinition(String directoryPath) {
            this.directoryPath = directoryPath;
        }

        @Override
        /** DirectoryPath */
        public String directoryPath() {
            return directoryPath;
        }

        @Override
        /** InputId */
        public String inputId() {
            return null;
        }

        @Override
        /** SourceId */
        public String sourceId() {
            return null;
        }

        @Override
        /** OutputId */
        public String outputId() {
            return null;
        }

        @Override
        /** SinkId */
        public String sinkId() {
            return null;
        }

        @Override
        /** Mappings */
        public List<DataSyncFieldMapping> mappings() {
            return List.of();
        }

        @Override
        /** Batch */
        public int batch() {
            return 100;
        }

        @Override
        /** CronType */
        public String cronType() {
            return null;
        }

        @Override
        /** Cron */
        public String cron() {
            return null;
        }

        @Override
        /** Params */
        public Map<String, Object> params() {
            return Map.of();
        }
    }

    @Bean
    /** DataSyncServiceInfoContributor */
    public ServiceInfoContributor dataSyncServiceInfoContributor() {
        return registry -> {
            registry.registerFlag("datasync", "数据同步", true, "数据同步模块");
        };
    }
}
