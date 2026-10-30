package com.chua.starter.datasourcehub.support.dbconsole;

import com.chua.common.support.lang.datasource.dialect.SqlName;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 数据导出执行器：把表（可带结构）导出为目标格式文本。
 *
 * <p>「仅结构 / 仅数据 / 结构+数据」三种范围，对齐 Navicat 的导出选项。</p>
 *
 * @author CH
 * @since 4.0.0.43
 */
public final class DbExporter {

    /** 导出格式。 */
    public enum Format {
        /** 逗号分隔 */
        CSV,
        /** 制表符分隔 */
        TSV,
        /** JSON 数组 */
        JSON,
        /** XML 行集 */
        XML,
        /** SQL 脚本（支持结构） */
        SQL;

        /**
         * 解析格式名。
         *
         * @param name 格式名，大小写不敏感
         * @return 格式；不认识时回退 CSV
         */
        public static Format of(String name) {
            String key = DbTransferCodec.normalizeFormat(name);
            for (Format format : values()) {
                if (format.name().toLowerCase(java.util.Locale.ROOT).equals(key)) {
                    return format;
                }
            }
            return CSV;
        }

        /**
         * 文件扩展名（不含点）。
         *
         * @return 扩展名
         */
        public String extension() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** 导出范围。 */
    public enum Scope {
        /** 仅表结构 */
        STRUCTURE,
        /** 仅数据 */
        DATA,
        /** 结构 + 数据 */
        ALL;

        /**
         * 解析范围名。
         *
         * @param name 范围名，大小写不敏感
         * @return 范围；不认识时回退 DATA
         */
        public static Scope of(String name) {
            String key = DbTransferCodec.normalizeFormat(name);
            for (Scope scope : values()) {
                if (scope.name().toLowerCase(java.util.Locale.ROOT).equals(key)) {
                    return scope;
                }
            }
            return DATA;
        }

        /**
         * 是否含结构。
         *
         * @return true=含结构
         */
        public boolean withStructure() {
            return this == STRUCTURE || this == ALL;
        }

        /**
         * 是否含数据。
         *
         * @return true=含数据
         */
        public boolean withData() {
            return this == DATA || this == ALL;
        }
    }

    /**
     * 导出选项。
     *
     * @param table      目标表名（须通过白名单校验）
     * @param format     格式
     * @param scope      范围
     * @param limit      数据行上限，{@code <=0} 用默认值
     * @param where      过滤条件（已由上层校验），可为空
     * @param orderBy    排序列，可为空
     * @param orderDir   排序方向
     * @param batchSize  SQL 格式每条 INSERT 的值组数
     */
    public record ExportOptions(
            String table,
            Format format,
            Scope scope,
            int limit,
            String where,
            String orderBy,
            String orderDir,
            int batchSize
    ) {
    }

    /**
     * 导出结果。
     *
     * @param content   文件内容
     * @param rowCount  数据行数
     * @param truncated 是否因上限截断
     */
    public record ExportResult(String content, int rowCount, boolean truncated) {
    }

    private DbExporter() {
    }

    /**
     * 执行导出。
     *
     * @param conn   数据库连接
     * @param option 导出选项
     * @return 导出结果
     * @throws Exception 读取表数据、建表语句或 JSON 序列化失败
     */
    public static ExportResult export(Connection conn, ExportOptions option) throws Exception {
        String table = SqlName.checkSimple(option.table(), "表名");
        List<String> columns = new ArrayList<>();
        List<Map<String, Object>> rows = new ArrayList<>();
        boolean truncated = false;
        if (option.scope().withData()) {
            int limit = option.limit() > 0 ? Math.min(option.limit(), DbTransferCodec.MAX_EXPORT_ROWS)
                    : DbTransferCodec.MAX_EXPORT_ROWS;
            SqlQuery query = buildDataQuery(conn, table, option, limit);
            columns = query.columns();
            rows = query.rows();
            truncated = rows.size() >= limit;
        }
        String content = render(table, columns, rows, option, conn);
        return new ExportResult(content, rows.size(), truncated);
    }

    private static SqlQuery buildDataQuery(Connection conn, String table, ExportOptions option, int limit)
            throws SQLException {
        List<String> columns = readColumns(conn, table);
        if (columns.isEmpty()) {
            return new SqlQuery(List.of(), List.of());
        }
        StringBuilder sql = new StringBuilder("SELECT * FROM ")
                .append(SqlName.quoteMysql(table, "表名"));
        if (option.where() != null && !option.where().isBlank()) {
            sql.append(" WHERE ").append(option.where());
        }
        if (option.orderBy() != null && !option.orderBy().isBlank()) {
            sql.append(" ORDER BY ").append(SqlName.quoteMysql(option.orderBy(), "排序列"))
                    .append("DESC".equalsIgnoreCase(option.orderDir()) ? " DESC" : " ASC");
        }
        sql.append(" LIMIT ?");
        List<Map<String, Object>> rows = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData meta = rs.getMetaData();
                for (int i = 1; i <= meta.getColumnCount(); i++) {
                    labels.add(meta.getColumnLabel(i));
                }
                while (rs.next()) {
                    Map<String, Object> row = new java.util.LinkedHashMap<>();
                    for (int i = 1; i <= labels.size(); i++) {
                        row.put(labels.get(i - 1), rs.getObject(i));
                    }
                    rows.add(row);
                }
            }
        }
        return new SqlQuery(List.copyOf(columns), List.copyOf(rows));
    }

    private static String render(String table, List<String> columns, List<Map<String, Object>> rows,
                                 ExportOptions option, Connection conn) throws Exception {
        return switch (option.format()) {
            case CSV -> DbTransferCodec.writeDelimited(columns, rows, ',');
            case TSV -> DbTransferCodec.writeDelimited(columns, rows, '\t');
            case JSON -> DbTransferCodec.writeJson(columns, rows);
            case XML -> DbTransferCodec.writeXml(columns, rows);
            case SQL -> renderSql(table, columns, rows, option, conn);
        };
    }

    private static String renderSql(String table, List<String> columns, List<Map<String, Object>> rows,
                                    ExportOptions option, Connection conn) throws Exception {
        StringBuilder out = new StringBuilder();
        if (option.scope().withStructure()) {
            out.append(DbTransferCodec.writeSchemaSql(table, readDdl(conn, table)));
        }
        if (option.scope().withData() && !rows.isEmpty()) {
            out.append(DbTransferCodec.writeSql(table, columns, rows, option.batchSize()));
        }
        return out.toString();
    }

    /**
     * read Columns
     * @param conn 参数
     * @param table 字符串
     * @return 返回值
     * @throws SQLException 异常
    */
    private static List<String> readColumns(Connection conn, String table) throws SQLException {
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

    /**
     * read Ddl
     * @param conn 参数
     * @param table 字符串
     * @return 返回值
     * @throws SQLException 异常
    */
    private static String readDdl(Connection conn, String table) throws SQLException {
        String quoted = SqlName.quoteMysql(table, "表名");
        try (java.sql.Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SHOW CREATE TABLE " + quoted)) {
            if (rs.next()) {
                int count = rs.getMetaData().getColumnCount();
                // 判定用「去空白后以 CREATE 开头」：SHOW CREATE TABLE 的第一列是表名，
                // 用「包含 CREATE」会在表名带 create 字样时取错列
                for (int i = 1; i <= count; i++) {
                    Object value = rs.getObject(i);
                    if (value instanceof String text
                            && text.strip().toUpperCase(java.util.Locale.ROOT).startsWith("CREATE")) {
                        return text;
                    }
                }
            }
        } catch (SQLException ignored) {
            // 非 SHOW CREATE 内核，交给上层拼装
        }
        throw new SQLException("当前内核不支持导出建表语句（SHOW CREATE TABLE 不可用）");
    }

    /**
     * 查询出的数据。
     *
     * @param columns 表列名
     * @param rows    数据行
     */
    private record SqlQuery(List<String> columns, List<Map<String, Object>> rows) {
    }
}