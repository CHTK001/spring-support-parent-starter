package com.chua.starter.monitor.support.support;

import com.chua.common.support.lang.datasource.dialect.SqlName;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 数据导入执行器：把解析好的行批量写入目标表。
 *
 * <p>行为对齐 Navicat 的导入向导：可只导入指定字段、可选先清表、冲突时跳过或替换、
 * 单行失败不中断整批而是汇总成错误报告。</p>
 *
 * <p><b>安全豁免</b>：目标表名与参与写入的列名全部经 {@link SqlName} 白名单校验并用
 * {@link SqlName#quoteMysql} 引用；单元格值一律走 {@link PreparedStatement} 参数绑定，
 * 任何用户输入都不拼进 SQL 文本。字符串值按目标列的 {@link Types} 常量做类型转换，
 * 避免把原始文本直接塞进强类型列。</p>
 *
 * @author CH
 * @since 4.0.0.43
 */
public final class DbImporter {

    /** 错误报告最多保留的明细条数，超出只计数不逐条列出（避免响应体爆炸） */
    private static final int MAX_REPORTED_ERRORS = 50;

    /** 批量写入的攒批大小 */
    private static final int BATCH_SIZE = 500;

    private DbImporter() {
    }

    /**
     * 导入选项。
     *
     * @param table         目标表名（须通过白名单校验）
     * @param sourceColumns 源列名（文件里的表头）
     * @param columnMapping 源列名 → 目标列名；未列出的源列忽略
     * @param truncateFirst 导入前是否清空目标表
     * @param onConflict    冲突策略：{@code skip} 跳过 / {@code replace} 替换
     * @param keyColumns    {@code replace} 模式用于排除主键列的更新（为空则自动取主键）
     * @param batchSize     每批行数
     */
    public record ImportOptions(
            String table,
            List<String> sourceColumns,
            Map<String, String> columnMapping,
            boolean truncateFirst,
            String onConflict,
            List<String> keyColumns,
            int batchSize
    ) {
        /**
         * 规范构造器：集合做防御性拷贝。
         *
         * @param table         目标表名
         * @param sourceColumns 源列名
         * @param columnMapping 列映射
         * @param truncateFirst 是否先清空
         * @param onConflict    冲突策略
         * @param keyColumns    定位列
         * @param batchSize     批大小
         */
        public ImportOptions {
            sourceColumns = sourceColumns == null ? List.of() : List.copyOf(sourceColumns);
            columnMapping = columnMapping == null ? Map.of() : Map.copyOf(columnMapping);
            keyColumns = keyColumns == null ? List.of() : List.copyOf(keyColumns);
        }
    }

    /**
     * 一个「源列 → 目标列」的写入配对。
     *
     * @param source 源列名（行 Map 的键）
     * @param target 目标列名（表里的列）
     */
    private record ColumnPair(String source, String target) {
    }

    /**
     * 执行导入。
     *
     * @param conn   数据库连接
     * @param rows   源行
     * @param option 导入选项
     * @return 导入结果
     * @throws SQLException 目标表不存在、无可导入列、或清表失败
     */
    public static ImportResult importRows(Connection conn, List<Map<String, Object>> rows, ImportOptions option)
            throws SQLException {
        String table = SqlName.checkSimple(option.table(), "表名");
        List<String> actualColumns = readTableColumns(conn, table);
        if (actualColumns.isEmpty()) {
            throw new SQLException("表不存在或读不到列: " + table);
        }
        List<ColumnPair> pairs = resolvePairs(option, actualColumns);
        if (pairs.isEmpty()) {
            throw new SQLException("没有可导入的列：请把文件里的列映射到目标表的列");
        }
        if (option.truncateFirst()) {
            truncate(conn, table);
        }
        boolean replace = "replace".equalsIgnoreCase(option.onConflict());
        List<String> targetNames = pairs.stream().map(ColumnPair::target).toList();
        Map<String, Integer> columnTypes = readColumnTypes(conn, table);
        List<String> quotedColumns = targetNames.stream()
                .map(name -> SqlName.quoteMysql(name, "列名"))
                .toList();
        String sql = buildSql(table, quotedColumns, replace);
        int size = option.batchSize() > 0 ? Math.min(option.batchSize(), 5000) : BATCH_SIZE;
        int inserted = 0;
        int replaced = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();
        boolean autoCommit = conn.getAutoCommit();
        if (autoCommit) {
            conn.setAutoCommit(false);
        }
        try {
            List<Map<String, Object>> buffer = new ArrayList<>(size);
            for (Map<String, Object> row : rows) {
                buffer.add(row);
                if (buffer.size() >= size) {
                    BatchOutcome outcome = flush(conn, sql, pairs, columnTypes, replace, buffer);
                    inserted += outcome.inserted();
                    replaced += outcome.replaced();
                    skipped += outcome.skipped();
                    collectErrors(errors, outcome);
                    buffer.clear();
                }
            }
            if (!buffer.isEmpty()) {
                BatchOutcome outcome = flush(conn, sql, pairs, columnTypes, replace, buffer);
                inserted += outcome.inserted();
                replaced += outcome.replaced();
                skipped += outcome.skipped();
                collectErrors(errors, outcome);
            }
            conn.commit();
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            if (autoCommit) {
                conn.setAutoCommit(true);
            }
        }
        return new ImportResult(rows.size(), inserted, replaced, skipped, errors);
    }

    /**
     * 写入一批。
     *
     * <p>先整批 {@code executeBatch}：全成功即结束，最快路径。任一行失败则整批回滚，
     * 再逐行重试以定位到具体坏行 —— 约束冲突（唯一键、外键、非空）只有真执行才知道，
     * 所以「一行坏数据不毁掉整批」只能靠这个降级。若首行也失败，说明是表级问题
     * （列不存在、表不可写），此时直接抛出，不做无意义的逐行重试。</p>
     */
    private static BatchOutcome flush(Connection conn, String sql, List<ColumnPair> pairs,
                                      Map<String, Integer> columnTypes, boolean replace,
                                      List<Map<String, Object>> buffer) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (Map<String, Object> row : buffer) {
                bind(ps, row, pairs, columnTypes);
                ps.addBatch();
            }
            int[] results = ps.executeBatch();
            int applied = 0;
            for (int result : results) {
                if (result >= 0 || result == Statement.SUCCESS_NO_INFO) {
                    applied++;
                }
            }
            conn.commit();
            return new BatchOutcome(replace ? 0 : applied, replace ? applied : 0, 0, List.of());
        } catch (SQLException batchFailure) {
            conn.rollback();
            return flushRowByRow(conn, sql, pairs, columnTypes, replace, buffer, batchFailure);
        }
    }

    /**
     * 降级逐行写入，定位到具体坏行。
     *
     * <p><b>每行自成事务</b>：逐行 {@code commit}，某行失败只回滚该行，
     * 不会连带回滚已成功的前序行。早期版本在这里调 {@code conn.rollback()}，
     * 实测出现「报 inserted=2 但库里只有后一行」的错账——前一行已被回滚却仍计数。</p>
     */
    private static BatchOutcome flushRowByRow(Connection conn, String sql, List<ColumnPair> pairs,
                                              Map<String, Integer> columnTypes, boolean replace,
                                              List<Map<String, Object>> buffer, SQLException batchFailure)
            throws SQLException {
        int applied = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int index = 0; index < buffer.size(); index++) {
                try {
                    bind(ps, buffer.get(index), pairs, columnTypes);
                    ps.executeUpdate();
                    conn.commit();
                    applied++;
                } catch (SQLException rowFailure) {
                    conn.rollback();
                    if (index == 0) {
                        // 首行就失败 = 表级问题（列不存在 / 表不可写 / 语法错），不值得继续逐行试
                        throw rowFailure;
                    }
                    skipped++;
                    if (errors.size() < MAX_REPORTED_ERRORS) {
                        errors.add("第 " + (index + 1) + " 行: " + rootMessage(rowFailure));
                    }
                }
            }
        }
        if (skipped == buffer.size() && applied == 0 && batchFailure != null) {
            throw batchFailure;
        }
        return new BatchOutcome(replace ? 0 : applied, replace ? applied : 0, skipped, errors);
    }

    /**
     * 解析「源列 → 目标列」配对，按目标表列顺序排列。
     *
     * <p>目标顺序来自表定义，因此生成的 INSERT 列序稳定、可被数据库复用索引。</p>
     */
    private static List<ColumnPair> resolvePairs(ImportOptions option, List<String> actualColumns) {
        Map<String, String> reverse = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : option.columnMapping().entrySet()) {
            reverse.putIfAbsent(entry.getValue().trim().toLowerCase(Locale.ROOT), entry.getKey());
        }
        List<ColumnPair> pairs = new ArrayList<>();
        List<String> usedSources = new ArrayList<>();
        for (String target : actualColumns) {
            String lower = target.toLowerCase(Locale.ROOT);
            String source = reverse.get(lower);
            if (source == null) {
                for (String candidate : option.sourceColumns()) {
                    if (candidate.equalsIgnoreCase(target)) {
                        source = candidate;
                        break;
                    }
                }
            }
            if (source != null && !usedSources.contains(source)) {
                usedSources.add(source);
                pairs.add(new ColumnPair(source, target));
            }
        }
        return pairs;
    }

    /**
     * 拼写入语句。
     *
     * <p>冲突策略直接决定动词，不叠加子句：{@code replace} 用 {@code REPLACE INTO}
     * （MySQL/MariaDB 语义是「冲突时先删后插」，本身就是 upsert）；而
     * {@code REPLACE INTO ... ON DUPLICATE KEY UPDATE} 是**语法冲突**——实测 MariaDB
     * 直接报 {@code You have an error in your SQL syntax ... at line 1}。
     * 只有 {@code INSERT ... ON DUPLICATE KEY UPDATE} 才需要显式列出被更新的列，
     * 但那条路的语义（部分列更新）与「整行替换」不同，故不与 REPLACE 混用。</p>
     *
     * @param table         目标表名
     * @param quotedColumns 已引用的目标列
     * @param replace       是否替换模式
     * @return INSERT / REPLACE 语句
     */
    private static String buildSql(String table, List<String> quotedColumns, boolean replace) {
        return new StringBuilder(replace ? "REPLACE INTO " : "INSERT INTO ")
                .append(SqlName.quoteMysql(table, "表名"))
                .append(" (").append(String.join(", ", quotedColumns)).append(") VALUES (")
                .append("?, ".repeat(quotedColumns.size() - 1)).append("?)")
                .toString();
    }

    private static void bind(PreparedStatement ps, Map<String, Object> row, List<ColumnPair> pairs,
                             Map<String, Integer> columnTypes) throws SQLException {
        for (int index = 0; index < pairs.size(); index++) {
            ColumnPair pair = pairs.get(index);
            ps.setObject(index + 1, coerce(row.get(pair.source()), columnTypes.get(pair.target())));
        }
    }

    /**
     * 把字符串值转成列的目标 JDBC 类型。
     *
     * @param value   源值
     * @param sqlType 目标列的 {@link Types} 常量；读不到时按文本处理
     * @return 可直接 {@code setObject} 的值；空串与 {@code \N} 视为 NULL
     * @throws SQLException 转换失败
     */
    private static Object coerce(Object value, Integer sqlType) throws SQLException {
        if (value == null) {
            return null;
        }
        if (value instanceof Number || value instanceof Boolean || value instanceof java.util.Date) {
            return value;
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty() || "\\N".equals(text)) {
            return null;
        }
        if (sqlType == null) {
            return text;
        }
        try {
            return switch (sqlType) {
                case Types.TINYINT, Types.SMALLINT, Types.INTEGER -> Integer.valueOf(text);
                case Types.BIGINT -> Long.valueOf(text);
                case Types.FLOAT, Types.REAL -> Float.valueOf(text);
                case Types.DOUBLE -> Double.valueOf(text);
                case Types.DECIMAL, Types.NUMERIC -> new BigDecimal(text);
                case Types.BOOLEAN, Types.BIT -> parseBoolean(text);
                default -> text;
            };
        } catch (NumberFormatException e) {
            throw new SQLException("值 \"" + abbreviate(text) + "\" 无法转为该列的数据类型");
        }
    }

    private static Boolean parseBoolean(String text) {
        return switch (text.toLowerCase(Locale.ROOT)) {
            case "1", "true", "yes", "y", "on", "t", "是" -> Boolean.TRUE;
            case "0", "false", "no", "n", "off", "f", "否" -> Boolean.FALSE;
            default -> throw new NumberFormatException("不是合法布尔值: " + abbreviate(text));
        };
    }

    private static String abbreviate(String value) {
        return value.length() <= 40 ? value : value.substring(0, 40) + "…";
    }

    private static void truncate(Connection conn, String table) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("TRUNCATE TABLE " + SqlName.quoteMysql(table, "表名"));
        } catch (SQLException fallback) {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("DELETE FROM " + SqlName.quoteMysql(table, "表名"));
            }
        }
    }

    private static List<String> readTableColumns(Connection conn, String table) throws SQLException {
        List<String> columns = new ArrayList<>();
        for (String[] scope : new String[][]{{conn.getCatalog(), conn.getSchema()}, {null, null}}) {
            try (ResultSet rs = conn.getMetaData().getColumns(scope[0], scope[1], table, "%")) {
                while (rs.next()) {
                    String name = rs.getString("COLUMN_NAME");
                    if (name != null && !columns.contains(name)) {
                        columns.add(name);
                    }
                }
            }
            if (!columns.isEmpty()) {
                return columns;
            }
        }
        return columns;
    }

    private static List<String> resolveKeyColumns(Connection conn, String table, List<String> targetNames,
                                                  ImportOptions option) throws SQLException {
        List<String> keys = new ArrayList<>();
        if (!option.keyColumns().isEmpty()) {
            for (String key : option.keyColumns()) {
                String hit = targetNames.stream().filter(c -> c.equalsIgnoreCase(key)).findFirst().orElse(null);
                if (hit != null) {
                    keys.add(SqlName.quoteMysql(hit, "列名"));
                }
            }
            return keys;
        }
        try (ResultSet rs = conn.getMetaData().getPrimaryKeys(conn.getCatalog(), conn.getSchema(), table)) {
            while (rs.next()) {
                String name = rs.getString("COLUMN_NAME");
                if (targetNames.stream().anyMatch(c -> c.equalsIgnoreCase(name))) {
                    keys.add(SqlName.quoteMysql(name, "主键列"));
                }
            }
        }
        return keys;
    }

    private static Map<String, Integer> readColumnTypes(Connection conn, String table) {
        Map<String, Integer> types = new LinkedHashMap<>();
        try (ResultSet rs = conn.getMetaData().getColumns(conn.getCatalog(), conn.getSchema(), table, "%")) {
            while (rs.next()) {
                types.put(rs.getString("COLUMN_NAME"), rs.getInt("DATA_TYPE"));
            }
        } catch (SQLException ignored) {
            // 读不到类型时按文本处理
        }
        return types;
    }

    private static void collectErrors(List<String> target, BatchOutcome outcome) {
        for (String error : outcome.errors()) {
            if (target.size() < MAX_REPORTED_ERRORS) {
                target.add(error);
            }
        }
    }

    private static String rootMessage(SQLException e) {
        Throwable cause = e;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        return message == null ? cause.getClass().getSimpleName() : message;
    }

    /**
     * 批结果。
     *
     * @param inserted 插入成功行数
     * @param replaced 替换成功行数
     * @param skipped  跳过行数
     * @param errors   错误明细
     */
    private record BatchOutcome(int inserted, int replaced, int skipped, List<String> errors) {
    }

    /**
     * 导入结果。
     *
     * @param totalRows 源行总数
     * @param inserted  新插入行数
     * @param replaced  替换行数
     * @param skipped   跳过行数
     * @param errors    错误明细
     */
    public record ImportResult(int totalRows, int inserted, int replaced, int skipped, List<String> errors) {
        /**
         * 规范构造器：集合做防御性拷贝。
         *
         * @param totalRows 源行总数
         * @param inserted  新插入行数
         * @param replaced  替换行数
         * @param skipped   跳过行数
         * @param errors    错误明细
         */
        public ImportResult {
            errors = errors == null ? List.of() : List.copyOf(errors);
        }

        /**
         * 是否全部成功。
         *
         * @return true=无失败
         */
        public boolean allSucceeded() {
            return skipped == 0 && errors.isEmpty();
        }
    }
}