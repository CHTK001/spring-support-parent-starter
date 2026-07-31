package com.chua.starter.datasync.configuration;

import com.chua.starter.datasync.config.DataSyncConfigDefinition;
import com.chua.starter.datasync.mapping.DataSyncFieldMapping;
import com.chua.starter.datasync.mapping.DataSyncMappingManager;
import com.chua.starter.datasync.mapping.DefaultDataSyncMapping;
import com.chua.starter.datasync.model.DataSyncMapping;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;

import javax.sql.DataSource;
import java.util.*;

/**
 * 数据库映射加载器，启动时从数据库表加载 DataSync 映射配置。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class DatabaseMappingLoader implements InitializingBean {

    private final DataSource dataSource;
    private final DatasyncProperties properties;
    private final DataSyncMappingManager mappingManager;

    public DatabaseMappingLoader(DataSource dataSource,
                                 DatasyncProperties properties,
                                 DataSyncMappingManager mappingManager) {
        this.dataSource = dataSource;
        this.properties = properties;
        this.mappingManager = mappingManager;
    }

    @Override
    public void afterPropertiesSet() {
        DatasyncProperties.DatabaseMapping config = properties.getDatabaseMapping();
        if (config == null || !config.isEnabled()) {
            return;
        }
        org.springframework.jdbc.core.JdbcTemplate jdbcTemplate =
                new org.springframework.jdbc.core.JdbcTemplate(dataSource);
        if (config.isAutoInitTable()) {
            initTable(jdbcTemplate, config.getTablePrefix());
        }
        loadMappings(jdbcTemplate, config.getTablePrefix());
    }

    private void initTable(org.springframework.jdbc.core.JdbcTemplate jdbcTemplate, String prefix) {
        String mappingTable = prefix + "mapping";
        String fieldTable = prefix + "field_mapping";

        String sqlMapping = """
                CREATE TABLE IF NOT EXISTS %s (
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
                """.formatted(mappingTable);

        String sqlField = """
                CREATE TABLE IF NOT EXISTS %s (
                    datasync_field_mapping_id VARCHAR(255) PRIMARY KEY,
                    datasync_field_mapping_mapping_id VARCHAR(255),
                    datasync_field_mapping_source_field VARCHAR(255),
                    datasync_field_mapping_target_field VARCHAR(255),
                    datasync_field_mapping_converter VARCHAR(255),
                    datasync_field_mapping_sort INT,
                    FOREIGN KEY (datasync_field_mapping_mapping_id) REFERENCES %s(datasync_mapping_id)
                )
                """.formatted(fieldTable, mappingTable);

        try {
            jdbcTemplate.execute(sqlMapping);
            jdbcTemplate.execute(sqlField);
            log.info("DataSync 表结构初始化完成: {}, {}", mappingTable, fieldTable);
        } catch (Exception e) {
            log.warn("DataSync 表结构初始化失败（可能已存在或权限不足）", e);
        }
    }

    private void loadMappings(org.springframework.jdbc.core.JdbcTemplate jdbcTemplate, String prefix) {
        String mappingTable = prefix + "mapping";
        String sql = String.format("SELECT * FROM %s WHERE datasync_mapping_enabled = TRUE", mappingTable);
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
            for (Map<String, Object> row : rows) {
                String mappingId = (String) row.get("datasync_mapping_id");
                List<DataSyncFieldMapping> fields = loadFieldMappings(jdbcTemplate, prefix, mappingId);
                com.chua.common.support.task.scheduler.Trigger trigger = null;
                DataSyncMapping mapping = new DefaultDataSyncMapping(
                        mappingId,
                        (String) row.get("datasync_mapping_input_id"),
                        (String) row.get("datasync_mapping_source_id"),
                        (String) row.get("datasync_mapping_output_id"),
                        (String) row.get("datasync_mapping_sink_id"),
                        null,
                        fields,
                        ((Number) row.getOrDefault("datasync_mapping_batch", 100)).intValue(),
                        (String) row.get("datasync_mapping_cron_type"),
                        (String) row.get("datasync_mapping_cron"),
                        parseParams((String) row.get("datasync_mapping_params")),
                        trigger
                );
                mappingManager.addMapping(mapping);
                log.info("数据库加载映射: mappingId={}", mappingId);
            }
        } catch (Exception e) {
            log.error("从数据库加载 DataSync 映射失败", e);
        }
    }

    private List<DataSyncFieldMapping> loadFieldMappings(
            org.springframework.jdbc.core.JdbcTemplate jdbcTemplate,
            String prefix, String mappingId) {
        String fieldTable = prefix + "field_mapping";
        String sql = String.format("SELECT * FROM %s WHERE datasync_field_mapping_mapping_id = ? ORDER BY datasync_field_mapping_sort ASC", fieldTable);
        List<DataSyncFieldMapping> fields = new ArrayList<>();
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, mappingId);
            for (Map<String, Object> row : rows) {
                fields.add(new DatabaseDataSyncFieldMapping(
                        (String) row.get("datasync_field_mapping_source_field"),
                        (String) row.get("datasync_field_mapping_target_field"),
                        (String) row.getOrDefault("datasync_field_mapping_converter", "toString")
                ));
            }
        } catch (Exception e) {
            log.warn("加载字段映射失败: mappingId={}", mappingId, e);
        }
        return fields;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseParams(String paramsJson) {
        if (paramsJson == null || paramsJson.isEmpty()) {
            return Map.of();
        }
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(paramsJson, Map.class);
        } catch (Exception e) {
            log.warn("解析 params JSON 失败: {}", paramsJson, e);
            return Map.of();
        }
    }

    private static class DatabaseDataSyncFieldMapping implements DataSyncFieldMapping {
        private final String sourceField;
        private final String targetField;
        private final String converter;

        DatabaseDataSyncFieldMapping(String sourceField, String targetField, String converter) {
            this.sourceField = sourceField;
            this.targetField = targetField;
            this.converter = converter;
        }

        @Override
        public String sourceField() {
            return sourceField;
        }

        @Override
        public String targetField() {
            return targetField;
        }

        @Override
        public String converter() {
            return converter;
        }
    }
}
