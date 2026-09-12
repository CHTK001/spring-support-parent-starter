package com.chua.datasource.support.partition;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

/**
* 分区路由服务 — 解析冷热表名、按日期裁剪分区。
*
* @author CH
* @since 4.0.0
 */
@Slf4j
public class PartitionRoutingService {

    /** Safe_table */
    private static final Pattern SAFE_TABLE = Pattern.compile("^[a-zA-Z0-9_]+$");
    /** Month_格式化 */
    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyyMM");
    /** Day_格式化 */
    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
    * 发现数据库中已存在的分区表。
    *
    * @param jdbcTemplate   JDBC 模板
    * @param partitionPrefix 分区表前缀，如 薪酬_订单_part
    * @param sourceTable     源表名（排除自身）
    * @return 已排序的分区表名列表（不含源表）
     */
    public static List<String> discoverPartitionTables(JdbcTemplate jdbcTemplate, String partitionPrefix, String sourceTable) {
        /**
        * 校验table名称。
        * @param partitionPrefix 分区前缀
        * @param "分区前缀" "分区前缀"
         */
        validateTableName(partitionPrefix, "分区前缀");
        /**
        * 校验table名称。
        * @param sourceTable 源table
        * @param "源表" "源表"
         */
        validateTableName(sourceTable, "源表");
        String sql = "SELECT TABLE_NAME FROM information_schema.tables " +
                "WHERE TABLE_SCHEMA = (SELECT DATABASE()) " +
                "AND TABLE_NAME LIKE ? " +
                "AND TABLE_NAME != ? " +
                "ORDER BY TABLE_NAME";
        try {
            List<String> tables = jdbcTemplate.queryForList(sql, String.class,
                    partitionPrefix + "_%", sourceTable);
            tables.sort(Comparator.naturalOrder());
            log.debug("[分区路由] 发现 {} 个分区表 (前缀={})", tables.size(), partitionPrefix);
            return tables;
        } catch (Exception e) {
            log.warn("[分区路由] 查询分区表失败: {}", e.getMessage());
            return List.of();
        }
    }

    /**
    * 获取覆盖指定日期范围的全部分区表名（含源表）。
    *
    * @param sourceTable      源表（热表）名
    * @param partitionPrefix  分区表前缀
    * @param granularity      分表粒度 MONTH / DAY
    * @param existingPartitions 已发现的分区表列表
    * @return 完整表名列表 [srctable, part_yyyymm, ...]
     */
    public static List<String> resolveAllTables(String sourceTable, String partitionPrefix,
                                                 String granularity,
                                                 List<String> existingPartitions) {
        List<String> result = new ArrayList<>();
        result.add(sourceTable);
        result.addAll(existingPartitions);
        return result;
    }

    /**
    * 根据日期和粒度生成分区表名。
    * @param partitionPrefix 分区前缀
    * @param date 日期
    * @param granularity granularity
    * @return resolve分区table名称的结果
     */
    public static String resolvePartitionTableName(String partitionPrefix, LocalDate date, String granularity) {
        /**
        * 校验table名称。
        * @param partitionPrefix 分区前缀
        * @param "分区前缀" "分区前缀"
         */
        validateTableName(partitionPrefix, "分区前缀");
        String suffix = "DAY".equalsIgnoreCase(granularity)
                ? DAY_FORMAT.format(date)
                : MONTH_FORMAT.format(date.withDayOfMonth(1));
        return partitionPrefix + "_" + suffix;
    }

    /**
    * 解析日期范围对应的分区表集合。
    *
    * @param partitionPrefix  分区表前缀
    * @param granularity      粒度
    * @param start            开始日期（含）
    * @param end              结束日期（含）
    * @return 分区表名列表（不含源表）
     */
    public static List<String> resolvePartitionTablesForRange(String partitionPrefix, String granularity,
                                                               LocalDate start, LocalDate end) {
        /**
        * 校验table名称。
        * @param partitionPrefix 分区前缀
        * @param "分区前缀" "分区前缀"
         */
        validateTableName(partitionPrefix, "分区前缀");
        List<String> result = new ArrayList<>();
        LocalDate cursor = "DAY".equalsIgnoreCase(granularity) ? start : start.withDayOfMonth(1);
        LocalDate endBound = "DAY".equalsIgnoreCase(granularity) ? end : end.withDayOfMonth(1);
        while (!cursor.isAfter(endBound)) {
            result.add(resolvePartitionTableName(partitionPrefix, cursor, granularity));
            cursor = "DAY".equalsIgnoreCase(granularity) ? cursor.plusDays(1) : cursor.plusMonths(1);
        }
        return result;
    }

    /**
    * SQL 注入防护：校验表名合法性。
    * @param value 价值观
    * @param label 标签
    * @return 校验table名称的结果
     */
    public static String validateTableName(String value, String label) {
        if (value == null || value.isEmpty() || !SAFE_TABLE.matcher(value).matches()) {
            /**
            * illegal参数异常。
            * @param value 价值观
            * @return illegal参数异常的结果
             */
            throw new IllegalArgumentException(label + " 不合法: " + value);
        }
        return value;
    }
}
