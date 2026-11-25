package com.chua.datasource.support.partition;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 分区感知表工厂 — 从数据库元数据发现分区配置并构建 {@link PartitionRoutingTable}。
 *
 * @author CH
 * @since 4.0.0
 */
@Slf4j
public class PartitionAwareTableFactory {

    /**
     * 数据来源
    */
    private final DataSource dataSource;
    /**
     * JDBC模板
    */
    private final JdbcTemplate jdbcTemplate;
    private final ConcurrentHashMap<String, PartitionRoutingTable> cache = new ConcurrentHashMap<>(); // 缓存

    /**
     * 创建 分区awaretable工厂 实例
     * @param dataSource 数据源
     */
    public PartitionAwareTableFactory(DataSource dataSource) {
        this.dataSource = dataSource;
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    /**
     * 读取分区配置（pay_partition_config 优先）并创建对应的路由表。
     *
     * @return 路由表 映射，Key 为源表名（如 薪酬_订单），值 为路由表实例
     */
    public Map<String, PartitionRoutingTable> buildAllRoutingTables() {
        return buildAllRoutingTables(Collections.emptyMap());
    }

    /**
     * 读取分区配置（pay_partition_config / 系统表扫描 + yaml 追加）并创建对应的路由表。
     *
     * @param yamlSpecs yaml 配置（plugin.datasource.partition.tables）的
     *                  「源表 -> 分区前缀」追加项；仅对配置表未覆盖的源表生效
     * @return 路由表 映射，Key 为源表名（如 薪酬_订单），值 为路由表实例
     */
    public Map<String, PartitionRoutingTable> buildAllRoutingTables(Map<String, String> yamlSpecs) {
        Map<String, PartitionRoutingTable> result = new java.util.LinkedHashMap<>();
        // 合并配置：yaml 追加项在前（优先），pay_partition_config / 系统表扫描兜底，按源表去重
        Map<String, String> merged = new java.util.LinkedHashMap<>();
        if (yamlSpecs != null) {
            yamlSpecs.forEach((sourceTable, partitionPrefix) -> {
                if (sourceTable != null && partitionPrefix != null
                        && !sourceTable.isBlank() && !partitionPrefix.isBlank()) {
                    merged.putIfAbsent(sourceTable.trim(), partitionPrefix.trim());
                }
            });
        }
        for (Map<String, Object> config : queryPartitionConfigs()) {
            String sourceTable = str(config.get("pay_partition_config_source_table"));
            String partitionPrefix = str(config.get("pay_partition_config_partition_prefix"));
            if (sourceTable == null && config.containsKey("source_table_pattern")) {
                // 系统表扫描回退行：源表模式 + 「_part」前缀推定（与 DsPartitionManagementService 同口径）
                sourceTable = str(config.get("source_table_pattern"));
                partitionPrefix = sourceTable == null ? null : sourceTable + "_part";
            }
            if (sourceTable == null || partitionPrefix == null) {
                continue;
            }
            merged.putIfAbsent(sourceTable.trim(), partitionPrefix.trim());
        }
        for (Map.Entry<String, String> entry : merged.entrySet()) {
            try {
                PartitionRoutingTable table = buildRoutingTable(entry.getKey(), entry.getValue());
                result.put(entry.getKey(), table);
                log.info("[分区工厂] 注册路由表: {} -> {} (前缀={})", entry.getKey(), table.getTableNames(), entry.getValue());
            } catch (Exception e) {
                log.warn("[分区工厂] 创建路由表失败: source={}, err={}", entry.getKey(), e.getMessage());
            }
        }
        return result;
    }

    /**
     * 为指定源表创建或获取缓存的路由表。
     *
     * @param sourceTable      源表名
     * @param partitionPrefix  分区表前缀
     * @return 路由表实例
     */
    public PartitionRoutingTable buildRoutingTable(String sourceTable, String partitionPrefix) {
        String safeSourceTable = PartitionRoutingService.validateTableName(sourceTable, "源表");
        String safePartitionPrefix = PartitionRoutingService.validateTableName(partitionPrefix, "分区前缀");
        String cacheKey = safeSourceTable + "|" + safePartitionPrefix;
        return cache.computeIfAbsent(cacheKey, key -> {
            List<String> discovered = PartitionRoutingService.discoverPartitionTables(
                    jdbcTemplate, safePartitionPrefix, safeSourceTable);
            List<String> allTables = PartitionRoutingService.resolveAllTables(
                    safeSourceTable, safePartitionPrefix, "MONTH", discovered);
            /**
             * 分区routingtable。
             * @param sourceTable 源table
             * @param allTables 全部tables
             * @param jdbcTemplate jdbctemplate
             * @return 分区routingtable的结果
             */
            return new PartitionRoutingTable(safeSourceTable, allTables, jdbcTemplate);
        });
    }

    /**
     * 刷新指定路由表的分区列表（发现新建的分区表）。
     * @param routingTable routingtable
     * @param partitionPrefix 分区前缀
     */
    public void refreshRoutingTable(PartitionRoutingTable routingTable, String partitionPrefix) {
        List<String> discovered = PartitionRoutingService.discoverPartitionTables(
                jdbcTemplate, partitionPrefix, routingTable.getSourceTable());
        routingTable.refreshTableNames(discovered);
    }

    /**
     * 查询分区配置
     *
     * @return 查询分区配置的结果
     */
    private List<Map<String, Object>> queryPartitionConfigs() {
        try {
 // 优先使用 薪酬_分区_配置 表
            return jdbcTemplate.queryForList(
                    "SELECT * FROM pay_partition_config WHERE pay_partition_config_auto_create_enabled = true");
        } catch (Exception e) {
            log.debug("[分区工厂] pay_partition_config 表不可用，尝试系统表: {}", e.getMessage());
            return queryFromSystemTables();
        }
    }

    /**
     * 查询从系统tables
     *
     * @return 查询从系统tables的结果
     */
    private List<Map<String, Object>> queryFromSystemTables() {
        try {
 // 扫描 信息_模式 中匹配分区模式的所有表
            // [P3C 1.4 豁免] 拼接值为代码内常量/已校验，无外部输入：整句为固定字面量，无任何变量拼接
            String sql = "SELECT DISTINCT SUBSTRING_INDEX(TABLE_NAME, '_part', 1) AS source_table_pattern " +
                    "FROM information_schema.tables " +
                    "WHERE TABLE_SCHEMA = (SELECT DATABASE()) " +
                    "AND TABLE_NAME LIKE '%_part_%'";
            return jdbcTemplate.queryForList(sql);
        } catch (Exception e2) {
            log.warn("[分区工厂] 系统表扫描也失败，跳过分区注册: {}", e2.getMessage());
            return List.of();
        }
    }

    /**
     * Str
     *
     * @param value 值
     * @return str的结果
     */
    private static String str(Object value) {
        return value instanceof String s ? s : null;
    }
}
