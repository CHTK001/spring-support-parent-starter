package com.chua.starter.datasync.support.db;

import com.chua.starter.datasync.DefaultDataSyncServer;
import com.chua.starter.datasync.configuration.DatasyncProperties;
import com.chua.starter.datasync.mapping.DataSyncMappingManager;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import javax.sql.DataSource;

/**
 * 数据同步模块数据库模式启动类。
 *
 * <p>启动嵌入式 H2 并加载表结构与测试数据，验证数据库映射加载与 DataSyncServer 启动。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@SpringBootApplication(
        scanBasePackages = "com.chua.starter.datasync.support.db",
        exclude = {DataSourceAutoConfiguration.class})
public class DataSyncDatabaseApplication {

    /**
     * 程序入口。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(DataSyncDatabaseApplication.class);
        application.setDefaultProperties(java.util.Map.of(
                "plugin.datasync.mapping-source", "database",
                "plugin.datasync.database-mapping.enabled", "true",
                "plugin.datasync.database-mapping.table-prefix", "datasync_"));
        ApplicationContext context = application.run(args);

        DatasyncProperties properties = context.getBean(DatasyncProperties.class);
        System.out.println("[DATA-SYNC-DB] enabled=" + properties.isEnabled());
        System.out.println("[DATA-SYNC-DB] mapping-source=" + properties.getMappingSource());
        System.out.println("[DATA-SYNC-DB] database-mapping.enabled=" + properties.getDatabaseMapping().isEnabled());
        System.out.println("[DATA-SYNC-DB] database-mapping.table-prefix=" + properties.getDatabaseMapping().getTablePrefix());

        DataSyncMappingManager mappingManager = context.getBean(DataSyncMappingManager.class);
        System.out.println("[DATA-SYNC-DB] 已加载映射数=" + mappingManager.getMappings().size());
        mappingManager.getMappings().forEach(m -> System.out.println("[DATA-SYNC-DB] 映射: id=" + m.mappingId()
                + ", source=" + m.sourceId()
                + ", sink=" + m.sinkId()
                + ", mappings=" + m.mappings()));

        DefaultDataSyncServer server = context.getBean(DefaultDataSyncServer.class);
        server.start();
        System.out.println("[DATA-SYNC-DB] DataSyncServer 启动成功");
    }

    /**
     * 配置嵌入式 H2 数据源并加载 schema 脚本。
     *
     * @return H2 嵌入式数据源
     */
    @Bean
    public DataSource dataSource() {
        return new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .setName("datasync-db-test")
                .addScript("classpath:schema.sql")
                .build();
    }
}
