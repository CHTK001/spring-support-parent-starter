package com.chua.starter.datasync.app;

import com.chua.starter.datasync.configuration.DataSyncAutoConfiguration;
import com.chua.starter.datasync.configuration.DatasyncProperties;
import com.chua.starter.datasync.DefaultDataSyncServer;
import com.chua.starter.datasync.mapping.DataSyncMappingManager;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * DataSync 数据库模式集成测试启动应用。
 *
 * @author CH
 * @since 4.0.0.42
 */
public class DataSyncApplicationDb {

    public static void main(String[] args) throws Exception {
        ConfigurableApplicationContext context = new SpringApplicationBuilder()
                .sources(DataSyncApplicationDb.class, DataSyncAutoConfiguration.class, DbTestConfig.class)
                .web(WebApplicationType.NONE)
                .run(args);

        DatasyncProperties properties = context.getBean(DatasyncProperties.class);
        System.out.println("[APP-DB] plugin.datasync.enabled=" + properties.isEnabled());
        System.out.println("[APP-DB] plugin.datasync.mapping-source=" + properties.getMappingSource());
        System.out.println("[APP-DB] plugin.datasync.database-mapping.enabled=" + properties.getDatabaseMapping().isEnabled());
        System.out.println("[APP-DB] plugin.datasync.database-mapping.table-prefix=" + properties.getDatabaseMapping().getTablePrefix());
        System.out.println("[APP-DB] plugin.datasync.database-mapping.auto-init-table=" + properties.getDatabaseMapping().isAutoInitTable());

        DefaultDataSyncServer server = context.getBean(DefaultDataSyncServer.class);
        DataSyncMappingManager mappingManager = server.mappingManager();
        System.out.println("[APP-DB] 启动后映射数=" + mappingManager.getMappings().size());
        for (var mapping : mappingManager.getMappings()) {
            System.out.println("[APP-DB] 映射: mappingId=" + mapping.mappingId()
                    + ", sourceId=" + mapping.sourceId()
                    + ", sinkId=" + mapping.sinkId()
                    + ", batch=" + mapping.batch()
                    + ", mappings=" + mapping.mappings());
        }

        System.out.println("[APP-DB] 按回车退出");
        System.in.read();
        server.stop();
        context.close();
    }

    @Configuration
    static class DbTestConfig {
        @Bean
        public DataSource dataSource() throws SQLException {
            org.h2.jdbcx.JdbcDataSource ds = new org.h2.jdbcx.JdbcDataSource();
            ds.setURL("jdbc:h2:mem:testdb-datasync;DB_CLOSE_DELAY=-1");
            ds.setUser("sa");
            ds.setPassword("");
            try (Connection conn = ds.getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.execute("""
                        CREATE TABLE IF NOT EXISTS datasync_mapping (
                            mapping_id VARCHAR(255) PRIMARY KEY,
                            input_id VARCHAR(255),
                            source_id VARCHAR(255),
                            output_id VARCHAR(255),
                            sink_id VARCHAR(255),
                            batch INT DEFAULT 100,
                            cron_type VARCHAR(255),
                            cron VARCHAR(255),
                            params TEXT,
                            enabled BOOLEAN DEFAULT TRUE,
                            create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                            update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                        )
                        """);
                stmt.execute("""
                        CREATE TABLE IF NOT EXISTS datasync_field_mapping (
                            id VARCHAR(255) PRIMARY KEY,
                            mapping_id VARCHAR(255),
                            source_field VARCHAR(255),
                            target_field VARCHAR(255),
                            converter VARCHAR(255),
                            sort INT,
                            FOREIGN KEY (mapping_id) REFERENCES datasync_mapping(mapping_id)
                        )
                        """);
                stmt.execute("""
                        INSERT INTO datasync_mapping (mapping_id, input_id, source_id, output_id, sink_id, batch, cron_type, cron, params, enabled)
                        VALUES ('db-user-sync', 'user-input', 'user-source', 'user-output', 'user-sink', 100, 'fixed', '0/5 * * * * ?', '{}', TRUE)
                        """);
                stmt.execute("""
                        INSERT INTO datasync_field_mapping (id, mapping_id, source_field, target_field, converter, sort)
                        VALUES ('f1', 'db-user-sync', 'name', 'user_name', 'toString', 0)
                        """);
            }
            return ds;
        }

        @Bean
        public com.chua.starter.datasync.mapping.DataSyncMappingManager dataSyncMappingManager() {
            return new com.chua.starter.datasync.mapping.DefaultDataSyncMappingManager();
        }
    }
}
