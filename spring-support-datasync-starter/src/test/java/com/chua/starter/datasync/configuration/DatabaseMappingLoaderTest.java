package com.chua.starter.datasync.configuration;

import com.chua.starter.datasync.mapping.DataSyncFieldMapping;
import com.chua.starter.datasync.mapping.DataSyncMappingManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DatabaseMappingLoader 单元测试（H2 内存库）。
 *
 * @author CH
 * @since 4.0.0.42
 */
class DatabaseMappingLoaderTest {

    private org.h2.jdbcx.JdbcDataSource ds;
    private DataSource dataSource;
    private DataSyncMappingManager mappingManager;

    @BeforeEach
    void setUp() throws Exception {
        ds = new org.h2.jdbcx.JdbcDataSource();
        ds.setURL("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        ds.setPassword("");
        dataSource = ds;

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("DROP TABLE IF EXISTS datasync_field_mapping");
        jdbcTemplate.execute("DROP TABLE IF EXISTS datasync_mapping");
        String schema = """
                CREATE TABLE datasync_mapping (
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
                );
                CREATE TABLE datasync_field_mapping (
                    id VARCHAR(255) PRIMARY KEY,
                    mapping_id VARCHAR(255),
                    source_field VARCHAR(255),
                    target_field VARCHAR(255),
                    converter VARCHAR(255),
                    sort INT,
                    FOREIGN KEY (mapping_id) REFERENCES datasync_mapping(mapping_id)
                );
                """;
        jdbcTemplate.execute(schema);

        jdbcTemplate.update("""
                INSERT INTO datasync_mapping (mapping_id, input_id, source_id, output_id, sink_id, batch, cron_type, cron, params, enabled)
                VALUES ('test-user-sync', 'user-input', 'user-source', 'user-output', 'user-sink', 100, 'fixed', '0/5 * * * * ?', '{}', TRUE)
                """);
        jdbcTemplate.update("""
                INSERT INTO datasync_field_mapping (id, mapping_id, source_field, target_field, converter, sort)
                VALUES ('f1', 'test-user-sync', 'name', 'user_name', 'toString', 0)
                """);

        mappingManager = new com.chua.starter.datasync.mapping.DefaultDataSyncMappingManager();
    }

    @Test
    void testLoadFromDatabase() throws Exception {
        DatasyncProperties properties = new DatasyncProperties();
        properties.getDatabaseMapping().setEnabled(true);
        properties.getDatabaseMapping().setAutoInitTable(false);

        DatabaseMappingLoader loader = new DatabaseMappingLoader(dataSource, properties, mappingManager);
        loader.afterPropertiesSet();

        assertThat(mappingManager.getMappings()).hasSize(1);
        var mapping = mappingManager.getMappings().getFirst();
        assertThat(mapping.mappingId()).isEqualTo("test-user-sync");
        assertThat(mapping.inputId()).isEqualTo("user-input");
        assertThat(mapping.mappings()).hasSize(1);
        DataSyncFieldMapping fm = mapping.mappings().getFirst();
        assertThat(fm.sourceField()).isEqualTo("name");
        assertThat(fm.targetField()).isEqualTo("user_name");
        assertThat(fm.converter()).isEqualTo("toString");
    }

    @Test
    void testDisabledConfigSkipsLoading() throws Exception {
        DatasyncProperties properties = new DatasyncProperties();
        properties.getDatabaseMapping().setEnabled(false);

        DatabaseMappingLoader loader = new DatabaseMappingLoader(dataSource, properties, mappingManager);
        loader.afterPropertiesSet();

        assertThat(mappingManager.getMappings()).isEmpty();
    }
}