package com.chua.starter.datasourcehub.support.dbconsole;

import com.chua.common.support.lang.datasource.dialect.SqlName;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 单张表的「结构快照」：拍快照那一刻的建表 DDL + 列 + 索引 + 外键 + 表选项
 *
 * <p>{@code source} 说明 DDL 的出处：<code>SHOW_CREATE</code>（MySQL/H2，DDL 已含索引与外键，
 * 回滚只需 DROP + CREATE）与 <code>METADATA</code>（PostgreSQL 等不背 SHOW CREATE TABLE 的内核，
 * 按列/主键拼装，索引/外键要单独重建）。</p>
 *
 * @param database    数据库名
 * @param table       表名
 * @param ddl         建表 DDL 全文
 * @param columns     列定义（来自 JDBC getColumns）
 * @param indexes     索引定义（来自 JDBC getIndexInfo，仅 METADATA 来源填满）
 * @param foreignKeys 外键定义（来自 JDBC getImportedKeys，仅 METADATA 来源填满）
 * @param options     表选项（存储引擎/字符集/排序规则/注释/自增）
 * @param takenAt     拍快照时刻
 * @param source      来源：SHOW_CREATE / METADATA
 * @author CH
 * @since 4.0.0.51
 */
public record DbTableSpec(
        String database,
        String table,
        String ddl,
        List<Map<String, Object>> columns,
        List<String> indexes,
        List<String> foreignKeys,
        Map<String, Object> options,
        LocalDateTime takenAt,
        String source
) {

    /** SHOW_CREATE TABLE 来源 */
    public static final String SOURCE_SHOW_CREATE = "SHOW_CREATE";
    /** JDBC 元数据拼装来源 */
    public static final String SOURCE_METADATA = "METADATA";

    /**
     * 建表 DDL 是否已包含索引与外键（SHOW_CREATE 来源如此）：回滚时只需
     * DROP TABLE + CREATE TABLE，无需重建约束。
     *
     * @return true 表示 DDL 自包含（SHOW_CREATE TABLE）
     */
    public boolean ddlContainsConstraints() {
        return SOURCE_SHOW_CREATE.equalsIgnoreCase(source);
    }

    /**
     * 把表的结构拍成 DbTableSpec。
     *
     * <p>先 SHOW CREATE TABLE（它的原文已含索引与外键，回滚只需 DROP + CREATE）；
     * 不背 SHOW CREATE TABLE 的内核走 JDBC 元数据拼装（索引/外键要单独重建）。</p>
     *
     * @param conn     连接（数据库已切到 table 所在的库）
     * @param database 拍快照时的目标库（只用于落盘，不决定了查询路径）
     * @param table    表名
     * @return 表结构快照（坏表/不存在的表会往外抛异常）
     */
    public static DbTableSpec showCreateTable(Connection conn, String database, String table) throws Exception {
        try (Statement st = conn.createStatement()) {
            String quoted = SqlName.quoteMysql(table, "表名");
            try (ResultSet rs = st.executeQuery("SHOW CREATE TABLE " + quoted)) {
                String ddl = null;
                if (rs.next()) {
                    // SHOW CREATE TABLE 的列是「表名 / 建表语句」；判定必须用「以 CREATE 开头」，
                    // 用「包含 CREATE」会命中相邻的表名列（表名里带 create 字样时）
                    for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) {
                        Object v = rs.getObject(i);
                        if (v instanceof String s && s.strip().toUpperCase().startsWith("CREATE")) {
                            ddl = s;
                            break;
                        }
                    }
                }
                if (ddl != null) {
                    // SHOW CREATE TABLE 原文：已含索引/外键，回滚只需 DROP + CREATE
                    return new DbTableSpec(database, table, ddl, columns(conn, table), null, null,
                            options(conn, table), LocalDateTime.now(), SOURCE_SHOW_CREATE);
                }
            } catch (SQLException ignored) {
                // 非 SHOW CREATE 内核，走列结构拼装
            }
        }
        // -------- 回退路径：按列+主键+外键/索引拼装 CREATE TABLE --------
        return metadataTableSpec(conn, database, table);
    }

    /**
     * JDBC 元数据拼装建表 DDL（PostgreSQL/H2/ClickHouse 用）
     */
    private static DbTableSpec metadataTableSpec(Connection conn, String database, String table) throws Exception {
        String catalog = conn.getCatalog();
        String schema = conn.getSchema();
        Set<String> pkCols = new java.util.LinkedHashSet<>();
        try (ResultSet pkRs = conn.getMetaData().getPrimaryKeys(catalog, schema, table)) {
            while (pkRs.next()) {
                pkCols.add(pkRs.getString("COLUMN_NAME"));
            }
        }
        StringBuilder ddl = new StringBuilder("CREATE TABLE ").append(SqlName.quoteMysql(table, "表名")).append(" (\n");
        List<String> defs = new ArrayList<>();
        try (ResultSet rs = conn.getMetaData().getColumns(catalog, schema, table, "%")) {
            while (rs.next()) {
                String name = rs.getString("COLUMN_NAME");
                String type = rs.getString("TYPE_NAME");
                int size = rs.getInt("COLUMN_SIZE");
                boolean nullable = rs.getInt("NULLABLE") == 1;
                String def = rs.getString("COLUMN_DEF");
                StringBuilder line = new StringBuilder("  ").append(SqlName.quoteMysql(name, "列名"))
                        .append(' ').append(type);
                if (size > 0 && Set.of("VARCHAR", "CHAR", "DECIMAL", "NUMERIC").contains(type.toUpperCase())) {
                    line.append('(').append(size).append(')');
                }
                line.append(nullable ? " NULL" : " NOT NULL");
                if (def != null && !def.isBlank()) {
                    line.append(" DEFAULT ").append(def);
                }
                if (pkCols.contains(name)) {
                    line.append(" PRIMARY KEY");
                }
                defs.add(line.toString());
            }
        }
        ddl.append(String.join(",\n", defs)).append("\n);");

        // 索引（仅 METADATA 来源可重建）
        List<String> indexes = new ArrayList<>();
        try (ResultSet rs = conn.getMetaData().getIndexInfo(catalog, schema, table, false)) {
            while (rs.next()) {
                String indexName = rs.getString("INDEX_NAME");
                if (indexName == null || indexName.isBlank() || "PRIMARY".equalsIgnoreCase(indexName)) {
                    continue;
                }
                String column = rs.getString("COLUMN_NAME");
                boolean unique = !rs.getBoolean("NON_UNIQUE");
                indexes.add("CREATE " + (unique ? "UNIQUE " : "") + "INDEX " + SqlName.quoteMysql(indexName, "索引名")
                        + " ON " + SqlName.quoteMysql(table, "表名") + " (" + SqlName.quoteMysql(column, "列名") + ");");
            }
        }

        // 外键
        List<String> fks = new ArrayList<>();
        try (ResultSet rs = conn.getMetaData().getImportedKeys(catalog, schema, table)) {
            while (rs.next()) {
                String fkName = rs.getString("FK_NAME");
                if (fkName == null || fkName.isBlank()) {
                    continue;
                }
                String refTable = rs.getString("PKTABLE_NAME");
                String refColumn = rs.getString("PKCOLUMN_NAME");
                String column = rs.getString("FKCOLUMN_NAME");
                fks.add("ALTER TABLE " + SqlName.quoteMysql(table, "表名") + " ADD CONSTRAINT "
                        + SqlName.quoteMysql(fkName, "外键名") + " FOREIGN KEY (" + SqlName.quoteMysql(column, "列名")
                        + ") REFERENCES " + SqlName.quoteMysql(refTable, "引用表")
                        + " (" + SqlName.quoteMysql(refColumn, "引用列") + ");");
            }
        }

        return new DbTableSpec(database, table, ddl.toString(), columns(conn, table), indexes, fks,
                options(conn, table), LocalDateTime.now(), SOURCE_METADATA);
    }

    /**
     * 拍快照用的列列表：给列表页尝试显示列数（SHOW_CREATE / METADATA 来源通用）
     */
    private static List<Map<String, Object>> columns(Connection conn, String table) {
        List<Map<String, Object>> list = new ArrayList<>();
        try {
            String sql = "SELECT COLUMN_NAME, DATA_TYPE, COLUMN_TYPE, COLUMN_COMMENT, "
                    + "IS_NULLABLE, COLUMN_KEY FROM information_schema.COLUMNS "
                    + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? ORDER BY ORDINAL_POSITION";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, table);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("name", rs.getString("COLUMN_NAME"));
                        m.put("type", rs.getString("COLUMN_TYPE"));
                        m.put("basicType", rs.getString("DATA_TYPE"));
                        m.put("nullable", "YES".equals(rs.getString("IS_NULLABLE")));
                        m.put("comments", rs.getString("COLUMN_COMMENT"));
                        m.put("columnKey", rs.getString("COLUMN_KEY"));
                        list.add(m);
                    }
                }
            }
        } catch (SQLException ignored) {
            // 取不到就算，不影响拍快照
        }
        return list;
    }

    /**
     * 表选项（字符集/排序规则/注释等）；只读元数据，不因为它崩溃
     */
    private static Map<String, Object> options(Connection conn, String table) {
        Map<String, Object> options = null;
        try {
            String sql = "SELECT TABLE_TYPE, TABLE_COMMENT, TABLE_COLLATION, AUTO_INCREMENT, "
                    + "CHARACTER_SET_NAME, ROW_FORMAT, ENGINE FROM information_schema.TABLES "
                    + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, table);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        options = new LinkedHashMap<String, Object>();
                        options.put("remarks", rs.getString("TABLE_COMMENT"));
                        options.put("engine", rs.getString("ENGINE"));
                        options.put("charset", rs.getString("CHARACTER_SET_NAME"));
                        options.put("collation", rs.getString("TABLE_COLLATION"));
                        options.put("autoIncrement", rs.getObject("AUTO_INCREMENT"));
                        options.put("tableType", rs.getString("TABLE_TYPE"));
                    }
                }
            }
        } catch (Exception ignored) {
            // 只读元数据，不因为它崩溃（没有这段则忽略）
        }
        return options;
    }

}
