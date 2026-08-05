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
 * @author CH
 * @since 2026/8/5
 */
@Slf4j
public class TriggerReader {

    /**
     * 数据库方言
     */
    private final DatabaseDialect dialect;

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

    private List<TriggerInfo> readMysqlLike(Connection conn) throws SQLException {
        String sql = "SELECT TRIGGER_NAME, EVENT_MANIPULATION, EVENT_OBJECT_TABLE, " +
                "ACTION_TIMING, STATUS, ACTION_ORIENTATION " +
                "FROM information_schema.TRIGGERS";
        List<TriggerInfo> list = new ArrayList<>();
        try (var stmt = conn.createStatement(); var rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                TriggerInfo t = new TriggerInfo();
                t.setName(rs.getString("TRIGGER_NAME"));
                t.setEvent(rs.getString("EVENT_MANIPULATION"));
                t.setTableName(rs.getString("EVENT_OBJECT_TABLE"));
                t.setTiming(rs.getString("ACTION_TIMING"));
                t.setStatus(rs.getString("STATUS"));
                t.setGranularity(rs.getString("ACTION_ORIENTATION"));
                list.add(t);
            }
        }
        return list;
    }

    private List<TriggerInfo> readPostgresql(Connection conn) throws SQLException {
        String sql = "SELECT trigger_name, event_manipulation, event_object_table, " +
                "action_timing, action_orientation " +
                "FROM information_schema.triggers";
        List<TriggerInfo> list = new ArrayList<>();
        try (var stmt = conn.createStatement(); var rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                TriggerInfo t = new TriggerInfo();
                t.setName(rs.getString("trigger_name"));
                t.setEvent(rs.getString("event_manipulation"));
                t.setTableName(rs.getString("event_object_table"));
                t.setTiming(rs.getString("action_timing"));
                t.setGranularity(rs.getString("action_orientation"));
                list.add(t);
            }
        }
        return list;
    }

    private List<TriggerInfo> readOracle(Connection conn) throws SQLException {
        String sql = "SELECT TRIGGER_NAME, TABLE_NAME, STATUS, TRIGGER_TYPE, TRIGGERING_EVENT " +
                "FROM ALL_TRIGGERS WHERE OWNER = USER OR OWNER = SYS_CONTEXT('USERENV','CURRENT_SCHEMA')";
        List<TriggerInfo> list = new ArrayList<>();
        try (var stmt = conn.createStatement(); var rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                TriggerInfo t = new TriggerInfo();
                t.setName(rs.getString("TRIGGER_NAME"));
                t.setTableName(rs.getString("TABLE_NAME"));
                t.setStatus(rs.getString("STATUS"));
                t.setTiming(rs.getString("TRIGGER_TYPE"));
                t.setEvent(rs.getString("TRIGGERING_EVENT"));
                list.add(t);
            }
        }
        return list;
    }

    private List<TriggerInfo> readSqlServer(Connection conn) throws SQLException {
        String sql = "SELECT t.name AS trigger_name, " +
                "       OBJECT_NAME(t.parent_id) AS table_name, " +
                "       t.is_disabled " +
                "FROM sys.triggers t WHERE t.parent_id <> 0";
        List<TriggerInfo> list = new ArrayList<>();
        Map<String, String> events = new HashMap<>();
        try (var stmt = conn.createStatement(); var rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                TriggerInfo t = new TriggerInfo();
                t.setName(rs.getString("trigger_name"));
                t.setTableName(rs.getString("table_name"));
                t.setStatus(rs.getBoolean("is_disabled") ? "DISABLED" : "ENABLED");
                list.add(t);
            }
        }
        // SQL Server 的事件类型在 sys.trigger_events
        try (var stmt = conn.createStatement(); var rs = stmt.executeQuery(
                "SELECT OBJECT_NAME(object_id) AS name, " +
                        "STRING_AGG(type_desc, ',') AS events " +
                        "FROM sys.trigger_events GROUP BY object_id")) {
            while (rs.next()) {
                events.put(rs.getString("name"), rs.getString("events"));
            }
        }
        for (TriggerInfo t : list) {
            t.setEvent(events.get(t.getName()));
        }
        return list;
    }

    private List<TriggerInfo> readSqlite(Connection conn) throws SQLException {
        String sql = "SELECT name, tbl_name FROM sqlite_master WHERE type = 'trigger'";
        List<TriggerInfo> list = new ArrayList<>();
        try (var stmt = conn.createStatement(); var rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                TriggerInfo t = new TriggerInfo();
                t.setName(rs.getString("name"));
                t.setTableName(rs.getString("tbl_name"));
                list.add(t);
            }
        }
        return list;
    }

    /**
     * 通用 information_schema 兜底
     */
    private List<TriggerInfo> readGenericInfoSchema(Connection conn) throws SQLException {
        String sql = "SELECT * FROM information_schema.TRIGGERS";
        List<TriggerInfo> list = new ArrayList<>();
        try (var stmt = conn.createStatement(); var rs = stmt.executeQuery(sql)) {
            var meta = rs.getMetaData();
            Map<String, Integer> cols = new HashMap<>();
            for (int i = 1; i <= meta.getColumnCount(); i++) {
                cols.put(meta.getColumnLabel(i).toUpperCase(Locale.ROOT), i);
            }
            while (rs.next()) {
                TriggerInfo t = new TriggerInfo();
                t.setName(getByName(rs, cols, "TRIGGER_NAME"));
                t.setEvent(getByName(rs, cols, "EVENT_MANIPULATION"));
                t.setTableName(getByName(rs, cols, "EVENT_OBJECT_TABLE"));
                t.setTiming(getByName(rs, cols, "ACTION_TIMING"));
                t.setStatus(getByName(rs, cols, "STATUS"));
                t.setGranularity(getByName(rs, cols, "ACTION_ORIENTATION"));
                list.add(t);
            }
        }
        return list;
    }

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
     */
    public enum DatabaseDialect {
        MYSQL, MARIADB, POSTGRESQL, ORACLE, SQLSERVER, H2, SQLITE, KINGBASE, DM, UNKNOWN;

        /**
         * 从 {@link DatabaseMetaData} 解析方言
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
