package com.chua.starter.datasourcehub.support.dbconsole;

import com.chua.common.support.lang.datasource.dialect.SqlName;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 表级属性、索引与主键的库级支撑（Navicat「表属性 / 索引 / 主键」三页）。
 *
 * <p>安全豁免：表名、列名、索引名、约束名一律经 {@link SqlName} 白名单校验并引用；
 * 存储引擎 / 字符集 / 排序规则只允许「先问内核有哪些合法值，再从其中挑」，
 * 不接受前端任意文本（避免把任意标识符拼进 DDL）。</p>
 *
 * @author CH
 * @since 4.0.0.44
 */
public final class DbTableDialect {

    private DbTableDialect() {
    }

    /**
     * 表属性。
     *
     * @param engine           存储引擎，可空表示不改
     * @param charset          字符集，可空表示不改
     * @param collation        排序规则，可空表示不改
     * @param comment          表注释，可空表示不改；传空串表示清空
     * @param autoIncrement    自增起始值，null 表示不改
     */
    public record TableOptions(
            String engine,
            String charset,
            String collation,
            String comment,
            Long autoIncrement
    ) {
    }

    /**
     * 读取表属性。
     *
     * @param conn  数据库连接
     * @param table 表名
     * @return 属性视图；表不存在时返回空 Map
     * @throws SQLException 读取失败
     */
    public static Map<String, Object> readOptions(Connection conn, String table) throws SQLException {
        String safeTable = SqlName.checkSimple(table, "表名");
        Map<String, Object> options = new LinkedHashMap<>();
        String sql = "SELECT ENGINE, TABLE_COLLATION, TABLE_COMMENT, AUTO_INCREMENT, CREATE_OPTIONS, ROW_FORMAT"
                + " FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, safeTable);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return options;
                }
                options.put("engine", rs.getString("ENGINE"));
                options.put("collation", rs.getString("TABLE_COLLATION"));
                options.put("comment", rs.getString("TABLE_COMMENT"));
                options.put("createOptions", rs.getString("CREATE_OPTIONS"));
                options.put("rowFormat", rs.getString("ROW_FORMAT"));
                String charset = null;
                String collation = rs.getString("TABLE_COLLATION");
                if (collation != null && collation.contains("_")) {
                    charset = collation.substring(0, collation.indexOf('_'));
                }
                options.put("charset", charset);
                long next = rs.getLong("AUTO_INCREMENT");
                options.put("autoIncrement", rs.wasNull() ? null : next);
            }
        }
        // 行数单独查：information_schema.TABLES 的 TABLE_ROWS 是估算值，
        // 而下面这条 SQL 的结果集里没有该列，不能从同一个 ResultSet 取。
        try (java.sql.Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT TABLE_ROWS FROM information_schema.TABLES "
                     + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = "
                     + DbColumnDef.quoteSqlLiteral(safeTable))) {
            if (rs.next()) {
                options.put("rows", rs.getLong(1));
            }
        }
        return options;
    }

    /**
     * 列出内核支持的存储引擎。
     *
     * @param conn 数据库连接
     * @return 引擎名列表
     * @throws SQLException 读取失败
     */
    public static List<String> listEngines(Connection conn) throws SQLException {
        List<String> engines = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT DISTINCT ENGINE FROM information_schema.ENGINES ORDER BY ENGINE");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String name = rs.getString(1);
                if (name != null && !"PERFORMANCE_SCHEMA".equalsIgnoreCase(name)) {
                    engines.add(name);
                }
            }
        }
        return engines;
    }

    /**
     * 列出可用字符集与排序规则。
     *
     * @param conn    数据库连接
     * @param charset 限定字符集，可为空表示全部
     * @return 形如 {@code [{charset, collation}]} 的列表
     * @throws SQLException 读取失败
     */
    public static List<Map<String, String>> listCollations(Connection conn, String charset) throws SQLException {
        List<Map<String, String>> list = new ArrayList<>();
        String sql = "SELECT CHARACTER_SET_NAME, COLLATION_NAME FROM information_schema.COLLATIONS"
                + (charset == null || charset.isBlank() ? "" : " WHERE CHARACTER_SET_NAME = ?")
                + " ORDER BY CHARACTER_SET_NAME, COLLATION_NAME";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            if (charset != null && !charset.isBlank()) {
                ps.setString(1, SqlName.checkSimple(charset, "字符集"));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, String> item = new LinkedHashMap<>();
                    item.put("charset", rs.getString(1));
                    item.put("collation", rs.getString(2));
                    list.add(item);
                }
            }
        }
        return list;
    }

    /**
     * 修改表属性。
     *
     * @param conn  数据库连接
     * @param table 表名
     * @param opt   属性
     * @throws SQLException 修改失败
     */
    public static void alterOptions(Connection conn, String table, TableOptions opt) throws SQLException {
        String safeTable = SqlName.checkSimple(table, "表名");
        String quoted = SqlName.quoteMysql(safeTable, "表名");
        List<String> actions = new ArrayList<>(4);
        if (opt.engine() != null && !opt.engine().isBlank()) {
            // 引擎名必须是该内核在 information_schema.ENGINES 里列出的，否则拒绝
            String engine = SqlName.checkSimple(opt.engine(), "存储引擎");
            if (!listEngines(conn).contains(engine)) {
                throw new SQLException("不支持的存储引擎: " + engine);
            }
            actions.add("ENGINE=" + engine);
        }
        if (opt.charset() != null && !opt.charset().isBlank()) {
            actions.add("CHARACTER SET " + SqlName.checkSimple(opt.charset(), "字符集"));
        }
        if (opt.collation() != null && !opt.collation().isBlank()) {
            actions.add("COLLATE " + SqlName.checkSimple(opt.collation(), "排序规则"));
        }
        if (opt.comment() != null) {
            actions.add("COMMENT=" + DbColumnDef.quoteSqlLiteral(opt.comment()));
        }
        if (actions.isEmpty()) {
            return;
        }
        execute(conn, "ALTER TABLE " + quoted + " " + String.join(", ", actions));
        if (opt.autoIncrement() != null && opt.autoIncrement() >= 1) {
            execute(conn, "ALTER TABLE " + quoted + " AUTO_INCREMENT = " + opt.autoIncrement());
        }
    }

    /**
     * 创建索引（支持类型与前缀长度）。
     *
     * <p>索引名与列名经白名单；索引类型只放行内核支持的几种；
     * 前缀长度必须是正整数且不超过 3072。</p>
     *
     * @param conn      数据库连接
     * @param table     表名
     * @param indexName 索引名
     * @param columns   列名（保持顺序 = 复合索引的列序）
     * @param prefixes  各列的前缀长度，与 columns 等长；元素为 null/0 表示整列
     * @param unique    是否唯一
     * @param indexType 索引类型，可空（默认由内核决定）
     * @param primary   是否建为主键索引
     * @throws SQLException 内核拒绝
     */
    public static void createIndex(Connection conn, String table, String indexName, List<String> columns,
                                   List<Integer> prefixes, boolean unique, String indexType, boolean primary)
            throws SQLException {
        String quotedTable = SqlName.quoteMysql(SqlName.checkSimple(table, "表名"), "表名");
        List<String> parts = new ArrayList<>(columns.size());
        for (int i = 0; i < columns.size(); i++) {
            String column = SqlName.quoteMysql(SqlName.checkSimple(columns.get(i), "索引列"), "索引列");
            Integer prefix = prefixes != null && i < prefixes.size() ? prefixes.get(i) : null;
            if (prefix != null && prefix > 0) {
                if (prefix > 3072) {
                    throw new SQLException("索引前缀长度不能超过 3072");
                }
                column = column + "(" + prefix + ")";
            }
            parts.add(column);
        }
        String columnList = String.join(", ", parts);
        if (primary) {
            execute(conn, "ALTER TABLE " + quotedTable + " ADD PRIMARY KEY (" + columnList + ")");
            return;
        }
        String type = normalizeIndexType(indexType);
        StringBuilder sql = new StringBuilder(unique ? "CREATE UNIQUE INDEX " : "CREATE INDEX ")
                .append(SqlName.quoteMysql(SqlName.checkSimple(indexName, "索引名"), "索引名"))
                .append(" ON ").append(quotedTable).append(" (").append(columnList).append(")");
        if (!type.isEmpty()) {
            sql.append(" USING ").append(type);
        }
        execute(conn, sql.toString());
    }

    /**
     * 规整索引类型：只放行内核实际支持的几类。
     *
     * @param raw 原始值，可空
     * @return 规整值，空表示不指定
     * @throws SQLException 类型不认识
     */
    private static String normalizeIndexType(String raw) throws SQLException {
        String key = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
        return switch (key) {
            case "" -> "";
            case "BTREE", "HASH", "RTREE" -> key;
            default -> throw new SQLException("不支持的索引类型: " + raw + "（可选 BTREE / HASH / RTREE）");
        };
    }

    /**
     * 设置主键（可改可加；先清空再设，避免与旧主键冲突）。
     *
     * @param conn    数据库连接
     * @param table   表名
     * @param columns 主键列（顺序即复合主键的列序）
     * @throws SQLException 内核拒绝
     */
    public static void setPrimaryKey(Connection conn, String table, List<String> columns) throws SQLException {
        String quotedTable = SqlName.quoteMysql(SqlName.checkSimple(table, "表名"), "表名");
        if (columns == null || columns.isEmpty()) {
            // 先看有没有主键：有则清空
            if (hasPrimaryKey(conn, table)) {
                execute(conn, "ALTER TABLE " + quotedTable + " DROP PRIMARY KEY");
            }
            return;
        }
        if (hasPrimaryKey(conn, table)) {
            execute(conn, "ALTER TABLE " + quotedTable + " DROP PRIMARY KEY");
        }
        createIndex(conn, table, "PRIMARY", columns, null, true, null, true);
    }

    /**
     * 表是否已有主键。
     *
     * @param conn  数据库连接
     * @param table 表名
     * @return true=有
     * @throws SQLException 读取失败
     */
    public static boolean hasPrimaryKey(Connection conn, String table) throws SQLException {
        try (ResultSet rs = conn.getMetaData().getPrimaryKeys(conn.getCatalog(), conn.getSchema(), table)) {
            return rs.next();
        }
    }

    /**
     * 执行一条 DDL。
     *
     * @param conn 数据库连接
     * @param ddl  DDL 文本
     * @throws SQLException 执行失败
     */
    private static void execute(Connection conn, String ddl) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(ddl);
        }
    }
}