// [P3C A20 豁免] A20/A30/A31: 日志/SQL 字符串字面量场景，豁免。
package com.chua.starter.datasource.runtime.partition;

import com.chua.datasource.support.partition.PartitionRoutingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 分区路由运行期管理服务（只读暴露）。
 *
 * <p>只做能力盘点与无副作用预览：</p>
 * <ul>
 *   <li>分区配置清单：优先读 {@code pay_partition_config}；配置表不可用时回退扫描
 *       information_schema 中 {@code %_part_%} 表名模式（此时无独立前缀配置，前缀按
 *       「模式名 + _part」推定）；</li>
 *   <li>日期范围预览：按粒度（MONTH/DAY）推算覆盖区间的分区表名，并核对
 *       information_schema 中真实存在的表，不执行任何 DDL/DML。</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Slf4j
@Service
public class DsPartitionManagementService {

    /**
     * 日期范围预览最多生成的分区表名数量（防止超大区间拖垮元数据核对）。
     */
    private static final int MAX_PREVIEW_TABLES = 500;

    private final JdbcTemplate jdbcTemplate;

    public DsPartitionManagementService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 分区路由配置与实际分区表盘点。
     *
     * @return configSource（pay_partition_config / information_schema）+ 每个源表的前缀、
     *         启用标记（配置表模式）与已发现分区表清单
     */
    public Map<String, Object> tables() {
        List<Map<String, Object>> rows = new ArrayList<>();
        String configSource;
        try {
            rows = loadFromConfigTable();
            configSource = "pay_partition_config";
        } catch (Exception e) {
            log.debug("[ds-partition] pay_partition_config 不可用，回退系统表扫描: {}", e.getMessage());
            rows = loadFromSystemTables();
            configSource = "information_schema";
        }
        for (Map<String, Object> row : rows) {
            String prefix = String.valueOf(row.get("partitionPrefix"));
            String sourceTable = String.valueOf(row.get("sourceTable"));
            row.put("partitionTables", discoverQuietly(prefix, sourceTable));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("configSource", configSource);
        result.put("total", rows.size());
        result.put("tables", rows);
        return result;
    }

    /**
     * 按日期范围推算分区表名并核对存在性（纯静态推算 + 元数据核对，无副作用）。
     *
     * @param body prefix / granularity（MONTH|DAY）/ start / end（yyyy-MM-dd）
     * @return 推算的分区表名清单（含源表与存在性标记）
     */
    public Map<String, Object> preview(Map<String, Object> body) {
        Map<String, Object> params = body == null ? Map.of() : body;
        String prefix = strValue(params.get("prefix"));
        String granularity = strValue(params.get("granularity"));
        String start = strValue(params.get("start"));
        String end = strValue(params.get("end"));
        if (prefix == null || prefix.isBlank()) {
            throw new IllegalArgumentException("prefix 不能为空");
        }
        if (start == null || start.isBlank() || end == null || end.isBlank()) {
            throw new IllegalArgumentException("start / end 不能为空（格式 yyyy-MM-dd）");
        }
        String grain = granularity == null || granularity.isBlank() ? "MONTH" : granularity.trim().toUpperCase();
        if (!"MONTH".equals(grain) && !"DAY".equals(grain)) {
            throw new IllegalArgumentException("granularity 仅支持 MONTH / DAY，收到: " + granularity);
        }
        LocalDate startDate;
        LocalDate endDate;
        try {
            startDate = LocalDate.parse(start.trim());
            endDate = LocalDate.parse(end.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("日期格式非法（应为 yyyy-MM-dd）: " + e.getMessage());
        }
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("end 不能早于 start");
        }
        List<String> rangeTables = PartitionRoutingService.resolvePartitionTablesForRange(prefix, grain, startDate, endDate);
        if (rangeTables.size() > MAX_PREVIEW_TABLES) {
            throw new IllegalArgumentException("日期范围过大：推算出 " + rangeTables.size()
                    + " 个分区表名，超过上限 " + MAX_PREVIEW_TABLES + "，请缩小区间");
        }
        Set<String> existing = findExistingTables(rangeTables);
        List<Map<String, Object>> tableRows = new ArrayList<>();
        for (String name : rangeTables) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", name);
            row.put("exists", existing.contains(name));
            tableRows.add(row);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("prefix", prefix);
        result.put("granularity", grain);
        result.put("start", startDate.toString());
        result.put("end", endDate.toString());
        result.put("total", tableRows.size());
        result.put("existingCount", tableRows.stream().filter(r -> Boolean.TRUE.equals(r.get("exists"))).count());
        result.put("tables", tableRows);
        return result;
    }

    /**
     * 读取 pay_partition_config 全部配置行。
     */
    private List<Map<String, Object>> loadFromConfigTable() {
        List<Map<String, Object>> raw = jdbcTemplate.queryForList("SELECT * FROM pay_partition_config");
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> config : raw) {
            String sourceTable = strValue(config.get("pay_partition_config_source_table"));
            String partitionPrefix = strValue(config.get("pay_partition_config_partition_prefix"));
            if (sourceTable == null || partitionPrefix == null) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("sourceTable", sourceTable);
            row.put("partitionPrefix", partitionPrefix);
            Object enabled = config.get("pay_partition_config_auto_create_enabled");
            row.put("autoCreateEnabled", Boolean.TRUE.equals(enabled) || "true".equalsIgnoreCase(String.valueOf(enabled)));
            rows.add(row);
        }
        return rows;
    }

    /**
     * 回退：扫描 information_schema 中 {@code %_part_%} 表名模式，前缀按「模式名 + _part」推定。
     */
    private List<Map<String, Object>> loadFromSystemTables() {
        // [P3C 1.4 豁免] 拼接值为代码内常量，无外部输入：整句为固定字面量
        String sql = "SELECT DISTINCT SUBSTRING_INDEX(TABLE_NAME, '_part', 1) AS source_table_pattern "
                + "FROM information_schema.tables "
                + "WHERE TABLE_SCHEMA = (SELECT DATABASE()) "
                + "AND TABLE_NAME LIKE '%_part_%'";
        List<Map<String, Object>> raw = jdbcTemplate.queryForList(sql);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> item : raw) {
            String pattern = strValue(item.get("source_table_pattern"));
            if (pattern == null || pattern.isBlank()) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("sourceTable", pattern);
            row.put("partitionPrefix", pattern + "_part");
            row.put("autoCreateEnabled", null);
            rows.add(row);
        }
        return rows;
    }

    /**
     * 发现某前缀下的实际分区表（失败返回空清单，不阻断盘点）。
     */
    private List<String> discoverQuietly(String prefix, String sourceTable) {
        try {
            return PartitionRoutingService.discoverPartitionTables(jdbcTemplate, prefix, sourceTable);
        } catch (Exception e) {
            log.debug("[ds-partition] 发现分区表失败: prefix={}, err={}", prefix, e.getMessage());
            return List.of();
        }
    }

    /**
     * 核对表名在当前库中的真实存在性。
     */
    private Set<String> findExistingTables(List<String> tableNames) {
        if (tableNames.isEmpty()) {
            return Set.of();
        }
        // [P3C 1.4 豁免] 拼接值为代码内常量/已校验：SQL 骨架为固定字面量，表名经 ? 绑定
        String placeholders = String.join(", ", java.util.Collections.nCopies(tableNames.size(), "?"));
        String sql = "SELECT TABLE_NAME FROM information_schema.tables "
                + "WHERE TABLE_SCHEMA = (SELECT DATABASE()) AND TABLE_NAME IN (" + placeholders + ")";
        try {
            return new HashSet<>(jdbcTemplate.queryForList(sql, String.class, tableNames.toArray()));
        } catch (Exception e) {
            log.debug("[ds-partition] 存在性核对失败: {}", e.getMessage());
            return Set.of();
        }
    }

    private static String strValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
