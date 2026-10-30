package com.chua.starter.datasource.support.graph;

import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 读取数据库触发器
 * <p>JDBC {@link DatabaseMetaData#getTriggers} 在很多驱动不可用，因此走方言 SQL：</p>
 * <ul>
 *     <li>MySQL / H2: {@code information_schema.TRIGGERS}</li>
 *     <li>PostgreSQL: {@code information_schema.TRIGGERS}（列名小写）</li>
 *     <li>Oracle: {@code ALL_TRIGGERS}</li>
 *     <li>SQL Server: {@code sys.triggers} + {@code sys.tables}</li>
 *     <li>SQLite / DM / Kingbase: 走通用 {@code information_schema.TRIGGERS}，列名容错</li>
 * </ul>
 *
 * @since 2026/8/5
 * @author CH
 */
@Slf4j
public class TriggerReader {

    /**
     * 数据库方言
     */
    private final DatabaseDialect dialect;

    /**
     * 创建 trigger读取 实例
     * @param dialect dialect
     */
    public TriggerReader(DatabaseDialect dialect) {
        this.dialect = dialect;
    }

    /**
     * 读取全部触发器
     *
     * @param conn 活跃连接（调用方负责关闭）
     * @return 触发器列表
     */
    public List<TriggerInfo> readAll(Connection conn) {
        try {
            switch (dialect) {
                case MYSQL:
                case H2:
                case MARIADB:
                    return readMysqlLike(conn);
                case POSTGRESQL:
                    return readPostgresql(conn);
                case ORACLE:
                    return readOracle(conn);
                case SQLSERVER:
                    return readSqlServer(conn);
                case SQLITE:
                    return readSqlite(conn);
                case KINGBASE:
                case DM:
                default:
                    return readGenericInfoSchema(conn);
            }
        } catch (Exception e) {
            log.warn("读取触发器失败 (dialect={})：{}", dialect, e.getMessage());
            return Collections.emptyList();
        }
    }

    // ============================ 各方言 ============================

    /**
     * 读取mysqllike <p>安全豁免：查询 information_schema.TRIGGERS 固定系统视图，不拼接用户输入或用户值。</p>
     * @param conn conn
     * @return 读取mysqllike的结果
     * @throws SQLException 异常
    */
    private List<TriggerInfo> readMysqlLike(Connection conn) throws SQLException {
        String sql = "SELECT TRIGGER_NAME, EVENT_MANIPULATION, EVENT_OBJECT_TABLE, " +
                "ACTION_TIMING, STATUS, ACTION_ORIENTATION " +
                "FROM information_schema.TRIGGERS";
        List<TriggerInfo> list = new ArrayList<>();
        /**
         * 尝试。
         * @param conn.createStatement( conn.创建对账单(
         */
        try (var stmt = conn.createStatement(); var rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                TriggerInfo t = new TriggerInfo(
                        rs.getString("TRIGGER_NAME"),
                        rs.getString("EVENT_OBJECT_TABLE"),
                        rs.getString("ACTION_TIMING"),
                        rs.getString("EVENT_MANIPULATION"),
                        rs.getString("STATUS"),
                        rs.getString("ACTION_ORIENTATION")
                );
                list.add(t);
            }
        }
        return list;
    }

    /**
     * 读取PostgreSQL <p>安全豁免：查询 information_schema.triggers 固定系统视图，不含用户可控参数或值。</p>
     * @param conn conn
     * @return 读取postgresql的结果
     * @throws SQLException 异常
    */
    private List<TriggerInfo> readPostgresql(Connection conn) throws SQLException {
        String sql = "SELECT trigger_name, event_manipulation, event_object_table, " +
                "action_timing, action_orientation " +
                "FROM information_schema.triggers";
        List<TriggerInfo> list = new ArrayList<>();
        /**
         * 尝试。
         * @param conn.createStatement( conn.创建对账单(
         */
        try (var stmt = conn.createStatement(); var rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                TriggerInfo t = new TriggerInfo(
                        rs.getString("trigger_name"),
                        rs.getString("event_object_table"),
                        rs.getString("action_timing"),
                        rs.getString("event_manipulation"),
                        null,
                        rs.getString("action_orientation")
                );
                list.add(t);
            }
        }
        return list;
    }

    /**
     * 读取Oracle <p>安全豁免：查询 Oracle ALL_TRIGGERS 固定系统视图，不含用户可控参数或值。</p>
     * @param conn conn
     * @return 读取oracle的结果
     * @throws SQLException 异常
    */
    private List<TriggerInfo> readOracle(Connection conn) throws SQLException {
        String sql = "SELECT TRIGGER_NAME, TABLE_NAME, STATUS, TRIGGER_TYPE, TRIGGERING_EVENT " +
                "FROM ALL_TRIGGERS WHERE OWNER = USER OR OWNER = SYS_CONTEXT('USERENV','CURRENT_SCHEMA')";
        List<TriggerInfo> list = new ArrayList<>();
        /**
         * 尝试。
         * @param conn.createStatement( conn.创建对账单(
         */
        try (var stmt = conn.createStatement(); var rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                TriggerInfo t = new TriggerInfo(
                        rs.getString("TRIGGER_NAME"),
                        rs.getString("TABLE_NAME"),
                        rs.getString("TRIGGER_TYPE"),
                        rs.getString("TRIGGERING_EVENT"),
                        rs.getString("STATUS"),
                        null
                );
                list.add(t);
            }
        }
        return list;
    }

    /**
     * 读取SQL服务端 <p>安全豁免：查询 SQL Server sys.triggers 固定系统视图，不含用户可控参数或值。</p>
     * @param conn conn
     * @return 读取sql服务端的结果
     * @throws SQLException 异常
    */
    private List<TriggerInfo> readSqlServer(Connection conn) throws SQLException {
        String sql = "SELECT t.name AS trigger_name, " +
                "       OBJECT_NAME(t.parent_id) AS table_name, " +
                "       t.is_disabled " +
                "FROM sys.triggers t WHERE t.parent_id <> 0";
        List<TriggerInfo> list = new ArrayList<>();
        Map<String, String> events = new HashMap<>();
        /**
         * 尝试。
         * @param conn.createStatement( conn.创建对账单(
         */
        try (var stmt = conn.createStatement(); var rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                TriggerInfo t = new TriggerInfo(
                        rs.getString("trigger_name"),
                        rs.getString("table_name"),
                        null,
                        null,
                        rs.getBoolean("is_disabled") ? "DISABLED" : "ENABLED",
                        null
                );
                list.add(t);
            }
        }
 // SQL 服务端 的事件类型在 sys.trigger_事件
        /**
         * 尝试。
         * @param conn.createStatement( conn.创建对账单(
         */
        try (var stmt = conn.createStatement(); var rs = stmt.executeQuery(
                "SELECT OBJECT_NAME(object_id) AS name, " +
                        "STRING_AGG(type_desc, ',') AS events " +
                        "FROM sys.trigger_events GROUP BY object_id")) {
            while (rs.next()) {
                events.put(rs.getString("name"), rs.getString("events"));
            }
        }
        for (int i = 0; i < list.size(); i++) {
            TriggerInfo t = list.get(i);
            list.set(i, new TriggerInfo(t.name(), t.tableName(), t.timing(),
                    events.get(t.name()), t.status(), t.granularity()));
        }
        return list;
    }

    /**
     * 读取Sqlite <p>安全豁免：查询 SQLite sqlite_master 固定系统表，不含用户可控参数或值。</p>
     * @param conn conn
     * @return 读取sqlite的结果
     * @throws SQLException 异常
    */
    private List<TriggerInfo> readSqlite(Connection conn) throws SQLException {
        String sql = "SELECT name, tbl_name FROM sqlite_master WHERE type = 'trigger'";
        List<TriggerInfo> list = new ArrayList<>();
        /**
         * 尝试。
         * @param conn.createStatement( conn.创建对账单(
         */
        try (var stmt = conn.createStatement(); var rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                TriggerInfo t = new TriggerInfo(
                        rs.getString("name"),
                        rs.getString("tbl_name"),
                        null,
                        null,
                        null,
                        null
                );
                list.add(t);
            }
        }
        return list;
    }

    /**
     * 通用 信息_模式 兜底 <p>安全豁免：查询 information_schema.TRIGGERS 固定系统视图，不含用户可控参数或值。</p>
     * @param conn conn
     * @return 读取generic信息模式的结果
     * @throws SQLException 异常
    */
    private List<TriggerInfo> readGenericInfoSchema(Connection conn) throws SQLException {
        String sql = "SELECT * FROM information_schema.TRIGGERS";
        List<TriggerInfo> list = new ArrayList<>();
        /**
         * 尝试。
         * @param conn.createStatement( conn.创建对账单(
         */
        try (var stmt = conn.createStatement(); var rs = stmt.executeQuery(sql)) {
            var meta = rs.getMetaData();
            Map<String, Integer> cols = new HashMap<>();
            for (int i = 1; i <= meta.getColumnCount(); i++) {
                cols.put(meta.getColumnLabel(i).toUpperCase(Locale.ROOT), i);
            }
            while (rs.next()) {
                TriggerInfo t = new TriggerInfo(
                        getByName(rs, cols, "TRIGGER_NAME"),
                        getByName(rs, cols, "EVENT_OBJECT_TABLE"),
                        getByName(rs, cols, "ACTION_TIMING"),
                        getByName(rs, cols, "EVENT_MANIPULATION"),
                        getByName(rs, cols, "STATUS"),
                        getByName(rs, cols, "ACTION_ORIENTATION")
                );
                list.add(t);
            }
        }
        return list;
    }

    /**
     * 获取by名称
     *
     * @param rs R
     * @param cols cols
     * @param name 名称
     * @return 获取by名称的结果
     */
    private static String getByName(ResultSet rs, Map<String, Integer> cols, String name) {
        Integer idx = cols.get(name);
        if (idx == null) {
            return null;
        }
        try {
            return rs.getString(idx);
        } catch (SQLException e) {
            return null;
        }
    }

    /**
     * 数据库方言枚举
     * @author CH
     * @since 4.0.0
     */
    public enum DatabaseDialect {
        MYSQL, MARIADB, POSTGRESQL, ORACLE, SQLSERVER, H2, SQLITE, KINGBASE, DM, UNKNOWN; // UNKNOWN

        /**
         * 从 {@link DatabaseMetaData} 解析方言
         * @param meta meta
         * @return resolve的结果
         * @throws SQLException 异常
        */
        public static DatabaseDialect resolve(DatabaseMetaData meta) throws SQLException {
            String name = meta.getDatabaseProductName();
            if (name == null) {
                return UNKNOWN;
            }
            String upper = name.toUpperCase(Locale.ROOT);
            if (upper.contains("MYSQL")) {
                return MYSQL;
            }
            if (upper.contains("MARIADB")) {
                return MARIADB;
            }
            if (upper.contains("POSTGRES") || upper.contains("GREENPLUM")) {
                return POSTGRESQL;
            }
            if (upper.contains("ORACLE")) {
                return ORACLE;
            }
            if (upper.contains("SQL SERVER") || upper.contains("MSSQL")) {
                return SQLSERVER;
            }
            if (upper.contains("H2")) {
                return H2;
            }
            if (upper.contains("SQLITE")) {
                return SQLITE;
            }
            if (upper.contains("KINGBASE")) {
                return KINGBASE;
            }
            if (upper.contains("DM ") || upper.contains("DMDB") || upper.contains("达梦")) {
                return DM;
            }
            return UNKNOWN;
        }
    }
}

