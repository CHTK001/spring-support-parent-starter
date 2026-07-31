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
import java.sql.Statement;

/**
 * DataSync 数据库模式集成测试启动应用。
 * <p>
 * 表结构由 {@code spring-support-datasource-starter} 自动执行
 * {@code db/init/V1.0.0__init_datasync_mapping.sql} 初始化。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public class DataSyncApplicationDb {

    public static void main(String[] args) throws Exception {
        ConfigurableApplicationContext context = new SpringApplicationBuilder()
                .sources(DataSyncApplicationDb.class, DataSyncAutoConfiguration.class, DbTestConfig.class)
                .web(WebApplicationType.NONE)
                .profiles("h2")
                .run(args);

        DatasyncProperties properties = context.getBean(DatasyncProperties.class);
        System.out.println("[APP-DB] plugin.datasync.enabled=" + properties.isEnabled());
        System.out.println("[APP-DB] plugin.datasync.mapping-source=" + properties.getMappingSource());
        System.out.println("[APP-DB] plugin.datasync.database-mapping.enabled=" + properties.getDatabaseMapping().isEnabled());
        System.out.println("[APP-DB] plugin.datasync.database-mapping.table-prefix=" + properties.getDatabaseMapping().getTablePrefix());

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
        public DataSource dataSource() throws Exception {
            org.h2.jdbcx.JdbcDataSource ds = new org.h2.jdbcx.JdbcDataSource();
            ds.setURL("jdbc:h2:mem:testdb-datasync;DB_CLOSE_DELAY=-1;MODE=MySQL");
            ds.setUser("sa");
            ds.setPassword("");
            try (Connection conn = ds.getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS datasync_mapping (
                        datasync_mapping_id VARCHAR(255) PRIMARY KEY,
                        datasync_mapping_input_id VARCHAR(255),
                        datasync_mapping_source_id VARCHAR(255),
                        datasync_mapping_output_id VARCHAR(255),
                        datasync_mapping_sink_id VARCHAR(255),
                        datasync_mapping_batch INT DEFAULT 100,
                        datasync_mapping_cron_type VARCHAR(255),
                        datasync_mapping_cron VARCHAR(255),
                        datasync_mapping_params TEXT,
                        datasync_mapping_enabled BOOLEAN DEFAULT TRUE,
                        datasync_mapping_create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        datasync_mapping_update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS datasync_field_mapping (
                        datasync_field_mapping_id VARCHAR(255) PRIMARY KEY,
                        datasync_field_mapping_mapping_id VARCHAR(255),
                        datasync_field_mapping_source_field VARCHAR(255),
                        datasync_field_mapping_target_field VARCHAR(255),
                        datasync_field_mapping_converter VARCHAR(255),
                        datasync_field_mapping_sort INT,
                        FOREIGN KEY (datasync_field_mapping_mapping_id) REFERENCES datasync_mapping(datasync_mapping_id)
                    )
                    """);
                stmt.execute("""
                    INSERT INTO datasync_mapping (datasync_mapping_id, datasync_mapping_input_id, datasync_mapping_source_id, datasync_mapping_output_id, datasync_mapping_sink_id, datasync_mapping_batch, datasync_mapping_cron_type, datasync_mapping_cron, datasync_mapping_params, datasync_mapping_enabled)
                    VALUES ('db-user-sync', 'user-input', 'user-source', 'user-output', 'user-sink', 100, 'fixed', '0/5 * * * * ?', '{}', TRUE)
                    """);
                stmt.execute("""
                    INSERT INTO datasync_field_mapping (datasync_field_mapping_id, datasync_field_mapping_mapping_id, datasync_field_mapping_source_field, datasync_field_mapping_target_field, datasync_field_mapping_converter, datasync_field_mapping_sort)
                    VALUES ('f1', 'db-user-sync', 'name', 'user_name', 'toString', 0)
                    """);
            }
            return ds;
        }
    }
}
