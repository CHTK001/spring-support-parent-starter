package com.chua.datasource.support.partition;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 分区感知表工厂 — 从数据库元数据发现分区配置并构建 {@link PartitionRoutingTable}。
 *
 * @author CH
 */
@Slf4j
public class PartitionAwareTableFactory {

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;
    private final ConcurrentHashMap<String, PartitionRoutingTable> cache = new ConcurrentHashMap<>();

    public PartitionAwareTableFactory(DataSource dataSource) {
        this.dataSource = dataSource;
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    /**
     * 读取支付分表配置并创建对应的路由表。
     *
     * @return 路由表 Map，key 为源表名（如 pay_order），value 为路由表实例
     */
    public Map<String, PartitionRoutingTable> buildAllRoutingTables() {
        Map<String, PartitionRoutingTable> result = new java.util.LinkedHashMap<>();
        List<Map<String, Object>> configs = queryPartitionConfigs();
        for (Map<String, Object> config : configs) {
            String sourceTable = str(config.get("pay_partition_config_source_table"));
            String partitionPrefix = str(config.get("pay_partition_config_partition_prefix"));
            if (sourceTable == null || partitionPrefix == null) {
                continue;
            }
            try {
                PartitionRoutingTable table = buildRoutingTable(sourceTable, partitionPrefix);
                result.put(sourceTable, table);
                log.info("[分区工厂] 注册路由表: {} -> {} (前缀={})", sourceTable, table.getTableNames(), partitionPrefix);
            } catch (Exception e) {
                log.warn("[分区工厂] 创建路由表失败: source={}, err={}", sourceTable, e.getMessage());
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
        String cacheKey = sourceTable + "|" + partitionPrefix;
        return cache.computeIfAbsent(cacheKey, key -> {
            List<String> discovered = PartitionRoutingService.discoverPartitionTables(
                    jdbcTemplate, partitionPrefix, sourceTable);
            List<String> allTables = PartitionRoutingService.resolveAllTables(
                    sourceTable, partitionPrefix, "MONTH", discovered);
            return new PartitionRoutingTable(sourceTable, allTables, jdbcTemplate);
        });
    }

    /**
     * 刷新指定路由表的分区列表（发现新建的分区表）。
     */
    public void refreshRoutingTable(PartitionRoutingTable routingTable, String partitionPrefix) {
        List<String> discovered = PartitionRoutingService.discoverPartitionTables(
                jdbcTemplate, partitionPrefix, routingTable.getSourceTable());
        routingTable.refreshTableNames(discovered);
    }

    private List<Map<String, Object>> queryPartitionConfigs() {
        try {
            // 优先使用 pay_partition_config 表
            return jdbcTemplate.queryForList(
                    "SELECT * FROM pay_partition_config WHERE pay_partition_config_auto_create_enabled = true");
        } catch (Exception e) {
            log.debug("[分区工厂] pay_partition_config 表不可用，尝试系统表: {}", e.getMessage());
            return queryFromSystemTables();
        }
    }

    private List<Map<String, Object>> queryFromSystemTables() {
        try {
            // 扫描 information_schema 中匹配分区模式的所有表
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

    private static String str(Object value) {
        return value instanceof String s ? s : null;
    }
}
