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
* 数据同步 自动配置。
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
    /**
    * 数据同步mapping管理器
    *
    * @return 数据同步mapping管理器的结果
    */
    public DataSyncMappingManager dataSyncMappingManager() {
        /**
        * 默认数据同步mapping管理器。
        * @return 默认数据同步mapping管理器的结果
        */
        return new DefaultDataSyncMappingManager();
    }

    @Bean
    @ConditionalOnMissingBean
    /**
    * 数据同步服务端
    * @param agentServerManager Agent服务端管理器
    * @param mappingManager mapping管理器
    */
    public com.chua.starter.datasync.DataSyncServer dataSyncServer(AgentServerManager agentServerManager,
                                                                   DataSyncMappingManager mappingManager) {
        com.chua.starter.datasync.DefaultDataSyncServer server = new com.chua.starter.datasync.DefaultDataSyncServer(agentServerManager);
        server.setMappingManager(mappingManager);
        return server;
    }

    @Bean
    @ConditionalOnMissingBean
    /**
    * Agent服务端管理器
    *
    * @return Agent服务端管理器的结果
    */
    public AgentServerManager agentServerManager() {
        /**
        * 数据同步Agent服务端。
        * @param null 空
        * @param null 空
        * @param "default-server" "默认-服务端"
        * @return 数据同步Agent服务端的结果
        */
        return new DataSyncAgentServer(null, null, "default-server");
    }

    @Bean
    @ConditionalOnProperty(prefix = "plugin.datasync", name = "mapping-source", havingValue = "file")
    @ConditionalOnMissingBean
    /**
    * 目录数据同步mappingscanner
    * @param properties 属性
    * @param mappingManager mapping管理器
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
    * databasemapping加载
    * @param dataSource 数据源
    * @param properties 属性
    * @param mappingManager mapping管理器
    */
    public DatabaseMappingLoader databaseMappingLoader(javax.sql.DataSource dataSource,
                                                       DatasyncProperties properties,
                                                       DataSyncMappingManager mappingManager) {
        /**
        * databasemapping加载。
        * @param dataSource 数据源
        * @param properties 属性
        * @param mappingManager mapping管理器
        * @return databasemapping加载的结果
        */
        return new DatabaseMappingLoader(dataSource, properties, mappingManager);
    }

    /**
    * 简单目录配置definition类。
    *
    * @author CH
    * @since 4.0.0
    */
    private static class SimpleDirectoryConfigDefinition implements DirectoryConfigDefinition {
        /** 目录路径 */
        private final String directoryPath;

        /**
        * 简单目录配置definition。
        * @param directoryPath 目录路径
        */
        SimpleDirectoryConfigDefinition(String directoryPath) {
            this.directoryPath = directoryPath;
        }

        @Override
        /** 目录路径 */
        public String directoryPath() {
            return directoryPath;
        }

        @Override
        /** 输入标识 */
        public String inputId() {
            return null;
        }

        @Override
        /** 源标识 */
        public String sourceId() {
            return null;
        }

        @Override
        /** 输出标识 */
        public String outputId() {
            return null;
        }

        @Override
        /** sinkid */
        public String sinkId() {
            return null;
        }

        @Override
        /** Mappings */
        public List<DataSyncFieldMapping> mappings() {
            return List.of();
        }

        @Override
        /** 批量 */
        public int batch() {
            return 100;
        }

        @Override
        /** cron类型 */
        public String cronType() {
            return null;
        }

        @Override
        /** Cron */
        public String cron() {
            return null;
        }

        @Override
        /** 参数 */
        public Map<String, Object> params() {
            return Map.of();
        }
    }

    @Bean
    /**
        * 数据同步服务信息contributor
        *
        * @return 数据同步服务信息contributor的结果
        */
    public ServiceInfoContributor dataSyncServiceInfoContributor() {
        return registry -> {
            registry.registerFlag("datasync", "数据同步", true, "数据同步模块");
        };
    }
}
