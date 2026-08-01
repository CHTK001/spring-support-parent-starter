package com.chua.starter.datasync.configuration;

import com.chua.starter.datasync.mapping.DataSyncMappingManager;
import com.chua.starter.datasync.mapping.DefaultDataSyncMappingManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 数据库映射加载器单元测试。
 *
 * <p>使用嵌入式 H2 验证从数据库表加载映射配置。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
class DatabaseMappingLoaderTest {

    /**
     * 嵌入式 H2 数据源
     */
    private EmbeddedDatabase dataSource;

    @BeforeEach
    void setUp() {
        dataSource = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .setName("datasync-loader-test")
                .addScript("classpath:db/init/V1.0.0__init_datasync_mapping.sql")
                .build();
    }

    @AfterEach
    void tearDown() {
        dataSource.shutdown();
    }

    @Test
    void loadMappingsFromDatabase() {
        DatasyncProperties properties = new DatasyncProperties();
        properties.getDatabaseMapping().setEnabled(true);
        DataSyncMappingManager manager = new DefaultDataSyncMappingManager();

        DatabaseMappingLoader loader = new DatabaseMappingLoader(dataSource, properties, manager);
        loader.afterPropertiesSet();

        assertEquals(1, manager.getMappings().size());
        assertEquals("db-user-sync", manager.getMappings().get(0).mappingId());
        assertEquals("user-source", manager.getMappings().get(0).sourceId());
        assertEquals("user-sink", manager.getMappings().get(0).sinkId());
        assertEquals(1, manager.getMappings().get(0).mappings().size());
        assertEquals("name", manager.getMappings().get(0).mappings().get(0).sourceField());
    }

    @Test
    void disabledDatabaseMappingSkips() {
        DatasyncProperties properties = new DatasyncProperties();
        properties.getDatabaseMapping().setEnabled(false);
        DataSyncMappingManager manager = new DefaultDataSyncMappingManager();

        DatabaseMappingLoader loader = new DatabaseMappingLoader(dataSource, properties, manager);
        loader.afterPropertiesSet();

        assertEquals(0, manager.getMappings().size());
    }

    @Test
    void customTablePrefix() {
        // 建一张带自定义前缀的表
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE IF NOT EXISTS sync_mapping (datasync_mapping_id VARCHAR(255) PRIMARY KEY, datasync_mapping_input_id VARCHAR(255), datasync_mapping_source_id VARCHAR(255), datasync_mapping_output_id VARCHAR(255), datasync_mapping_sink_id VARCHAR(255), datasync_mapping_batch INT DEFAULT 100, datasync_mapping_cron_type VARCHAR(255), datasync_mapping_cron VARCHAR(255), datasync_mapping_params TEXT, datasync_mapping_enabled BOOLEAN DEFAULT TRUE)");
        jdbc.update("INSERT INTO sync_mapping (datasync_mapping_id, datasync_mapping_input_id, datasync_mapping_source_id, datasync_mapping_output_id, datasync_mapping_sink_id, datasync_mapping_enabled) VALUES ('custom-sync', 'in', 'src', 'out', 'sink', TRUE)");

        DatasyncProperties properties = new DatasyncProperties();
        properties.getDatabaseMapping().setEnabled(true);
        properties.getDatabaseMapping().setTablePrefix("sync_");
        DataSyncMappingManager manager = new DefaultDataSyncMappingManager();

        DatabaseMappingLoader loader = new DatabaseMappingLoader(dataSource, properties, manager);
        loader.afterPropertiesSet();

        assertEquals(1, manager.getMappings().size());
        assertEquals("custom-sync", manager.getMappings().get(0).mappingId());
    }
}
