package com.chua.starter.datasync.configuration;

import com.chua.starter.datasync.config.DataSyncConfigDefinition;
import com.chua.starter.datasync.mapping.DataSyncFieldMapping;
import com.chua.starter.datasync.mapping.DataSyncMappingManager;
import com.chua.starter.datasync.mapping.DefaultDataSyncMapping;
import com.chua.starter.datasync.model.DataSyncMapping;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 数据库映射加载器，启动时从数据库表加载 数据同步 映射配置。
 * <p>
 * 表结构由 {@code spring-support-datasource-starter} 自动执行
 * {@code db/init/V1.0.0__init_datasync_mapping.sql} 初始化，此类仅负责查询映射数据。
 * </p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
public class DatabaseMappingLoader implements InitializingBean {

    /** 数据来源 */
    private final DataSource dataSource;
    /** 属性 */
    private final DatasyncProperties properties;
    /** Mapping管理器 */
    private final DataSyncMappingManager mappingManager;

    /**
     * 创建 databasemapping加载 实例
     * @param dataSource 数据源
     * @param properties 属性
     * @param mappingManager mapping管理器
     */
    public DatabaseMappingLoader(DataSource dataSource,
                                 DatasyncProperties properties,
                                 DataSyncMappingManager mappingManager) {
        this.dataSource = dataSource;
        this.properties = properties;
        this.mappingManager = mappingManager;
    }

    @Override
    /** 之后属性设置 */
    public void afterPropertiesSet() {
        DatasyncProperties.DatabaseMapping config = properties.getDatabaseMapping();
        if (config == null || !config.isEnabled()) {
            return;
        }

        org.springframework.jdbc.core.JdbcTemplate jdbcTemplate =
                new org.springframework.jdbc.core.JdbcTemplate(dataSource);
        /**
         * 加载mappings。
         * @param jdbcTemplate jdbctemplate
         * @param config.getTablePrefix( 配置.获取table前缀(
         */
        loadMappings(jdbcTemplate, config.getTablePrefix());
    }

    /**
     * 加载Mappings
     *
     * @param jdbcTemplate jdbctemplate
     * @param prefix 前缀
     */
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
                        /**
                         * 解析参数。
                         * @param (String (字符串
                         */
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

    /**
     * 加载字段mappings
     * @param jdbcTemplate jdbctemplate
     * @param prefix 前缀
     * @param mappingId mappingid
     * @param fieldTable 字段table
     * @param mappingId mappingid
     * @param e e
     * @param mappingId mappingid
     * @param e e
     * @param paramsJson 参数json
     * @param e e
     * @param paramsJson 参数json
     * @param e e
     * @param sourceField 源字段
     * @param targetField Target字段
     * @param converter 转换器
     */
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
    /**
     * 解析参数
     *
     * @param paramsJson 参数json
     * @return 解析参数的结果
     */
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

    /**
     * database数据同步字段mapping类。
     *
     * @author CH
     * @since 4.0.0
     */
    private static class DatabaseDataSyncFieldMapping implements DataSyncFieldMapping {
        /** 来源字段 */
        private final String sourceField;
        /** 目标字段 */
        private final String targetField;
        /** 转换器 */
        private final String converter;

        /**
         * database数据同步字段mapping。
         * @param sourceField 源字段
         * @param targetField Target字段
         * @param converter 转换器
         */
        DatabaseDataSyncFieldMapping(String sourceField, String targetField, String converter) {
            this.sourceField = sourceField;
            this.targetField = targetField;
            this.converter = converter;
        }

        @Override
        /** 源字段 */
        public String sourceField() {
            return sourceField;
        }

        @Override
        /** Target字段 */
        public String targetField() {
            return targetField;
        }

        @Override
        /** 转换器 */
        public String converter() {
            return converter;
        }
    }
}
