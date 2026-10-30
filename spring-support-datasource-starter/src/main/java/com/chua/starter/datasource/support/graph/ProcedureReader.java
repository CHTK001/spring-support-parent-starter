package com.chua.starter.datasource.support.graph;

import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * 读取数据库存储过程与函数
 * <p>优先使用 JDBC {@link DatabaseMetaData#getProcedures}，跨库通用；</p>
 * <p>对于不暴露函数列表的驱动，再回退到方言 SQL：</p>
 * <ul>
 *     <li>MySQL: {@code information_schema.ROUTINES}</li>
 *     <li>PostgreSQL: {@code information_schema.routines} + {@code pg_proc}</li>
 *     <li>Oracle: {@code ALL_OBJECTS}</li>
 *     <li>SQL Server: {@code sys.objects} + {@code sys.sql_modules}</li>
 * </ul>
 *
 * @since 2026/8/5
 * @author CH
 */
@Slf4j
public class ProcedureReader {

    /**
     * Dialect
    */
    private final TriggerReader.DatabaseDialect dialect;

    /**
     * 创建 procedure读取 实例
     * @param dialect dialect
     */
    public ProcedureReader(TriggerReader.DatabaseDialect dialect) {
        this.dialect = dialect;
    }

    /**
     * 读取存储过程 + 函数
     * @param conn conn
     * @return 读取全部的结果
     */
    public List<ProcedureInfo> readAll(Connection conn) {
        List<ProcedureInfo> result = readViaJdbc(conn);
        if (result != null && !result.isEmpty()) {
            return result;
        }
        // JDBC 没拿到，尝试方言 SQL
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
                default:
                    return Collections.emptyList();
            }
        } catch (Exception e) {
            log.warn("读取存储过程失败 (dialect={})：{}", dialect, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 读取viajdbc
     *
     * @param conn conn
     * @return 读取viajdbc的结果
     */
    private List<ProcedureInfo> readViaJdbc(Connection conn) {
        try {
            DatabaseMetaData meta = conn.getMetaData();
            String catalog = conn.getCatalog();
            String schema = conn.getSchema();
            List<ProcedureInfo> list = new ArrayList<>();
            /**
             * 尝试。
             * @param "%" "%"
             */
            try (ResultSet rs = meta.getProcedures(catalog, schema, "%")) {
                while (rs.next()) {
                    String pt = rs.getString("PROCEDURE_TYPE");
                    list.add(new ProcedureInfo(rs.getString("PROCEDURE_NAME"),
                            "1".equals(pt) ? "PROCEDURE" : "FUNCTION",
                            rs.getString("PROCEDURE_SCHEM"), rs.getString("REMARKS"), null));
                }
            }
 // 单独补函数（部分驱动 获取procedures 不返回函数）
            /**
             * 尝试。
             * @param "%" "%"
             */
            try (ResultSet rs = meta.getFunctions(catalog, schema, "%")) {
                while (rs.next()) {
                    ProcedureInfo p = new ProcedureInfo(
                            rs.getString("FUNCTION_NAME"),
                            "FUNCTION",
                            rs.getString("FUNCTION_SCHEM"),
                            rs.getString("REMARKS"),
                            null
                    );
                    list.add(p);
                }
            }
            return list;
        } catch (Exception e) {
            log.debug("JDBC 读取存储过程/函数失败：{}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 读取mysqllike。 <p>安全豁免：查询 information_schema.ROUTINES 系统视图，SQL 为 MySQL/MariaDB/H2 底层方言固定语句，不拼接用户输入或用户值。</p>
     * @param conn conn
     * @return 读取mysqllike的结果
     * @throws SQLException 异常
    */
    private List<ProcedureInfo> readMysqlLike(Connection conn) throws SQLException {
        String sql = "SELECT ROUTINE_NAME, ROUTINE_TYPE, ROUTINE_SCHEMA, ROUTINE_COMMENT " +
                "FROM information_schema.ROUTINES";
        List<ProcedureInfo> list = new ArrayList<>();
        /**
         * 尝试。
         * @param conn.createStatement( conn.创建对账单(
         */
        try (var stmt = conn.createStatement();
             var rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                ProcedureInfo p = new ProcedureInfo(
                        rs.getString("ROUTINE_NAME"),
                        rs.getString("ROUTINE_TYPE"),
                        rs.getString("ROUTINE_SCHEMA"),
                        rs.getString("ROUTINE_COMMENT"),
                        null
                );
                list.add(p);
            }
        }
        return list;
    }

    /**
     * 读取PostgreSQL <p>安全豁免：查询 information_schema.routines 固定系统视图，不含用户可控参数或值。</p>
     * @param conn conn
     * @return 读取postgresql的结果
     * @throws SQLException 异常
    */
    private List<ProcedureInfo> readPostgresql(Connection conn) throws SQLException {
        String sql = "SELECT routine_name, routine_type, specific_schema " +
                "FROM information_schema.routines";
        List<ProcedureInfo> list = new ArrayList<>();
        /**
         * 尝试。
         * @param conn.createStatement( conn.创建对账单(
         */
        try (var stmt = conn.createStatement();
             var rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                ProcedureInfo p = new ProcedureInfo(
                        rs.getString("routine_name"),
                        rs.getString("routine_type"),
                        rs.getString("specific_schema"),
                        null,
                        null
                );
                list.add(p);
            }
        }
        return list;
    }

    /**
     * 读取Oracle <p>安全豁免：查询 Oracle ALL_OBJECTS 固定系统视图，不含用户可控参数或值。</p>
     * @param conn conn
     * @return 读取oracle的结果
     * @throws SQLException 异常
    */
    private List<ProcedureInfo> readOracle(Connection conn) throws SQLException {
        String sql = "SELECT OBJECT_NAME, OBJECT_TYPE FROM ALL_OBJECTS " +
                "WHERE OBJECT_TYPE IN ('PROCEDURE','FUNCTION') AND OWNER = USER";
        List<ProcedureInfo> list = new ArrayList<>();
        /**
         * 尝试。
         * @param conn.createStatement( conn.创建对账单(
         */
        try (var stmt = conn.createStatement();
             var rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                ProcedureInfo p = new ProcedureInfo(
                        rs.getString("OBJECT_NAME"),
                        rs.getString("OBJECT_TYPE"),
                        "CURRENT",
                        null,
                        null
                );
                list.add(p);
            }
        }
        return list;
    }

    /**
     * 读取SQL服务端 <p>安全豁免：查询 SQL Server sys.objects 固定系统视图，不含用户可控参数或值。</p>
     * @param conn conn
     * @return 读取sql服务端的结果
     * @throws SQLException 异常
    */
    private List<ProcedureInfo> readSqlServer(Connection conn) throws SQLException {
        String sql = "SELECT o.name, o.type_desc FROM sys.objects o " +
                "WHERE o.type IN ('P','FN','IF','TF')";
        List<ProcedureInfo> list = new ArrayList<>();
        /**
         * 尝试。
         * @param conn.createStatement( conn.创建对账单(
         */
        try (var stmt = conn.createStatement();
             var rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String type = rs.getString("type_desc");
                list.add(new ProcedureInfo(rs.getString("name"),
                        type != null && type.toUpperCase(Locale.ROOT).contains("FUNCTION") ? "FUNCTION" : "PROCEDURE",
                        null, null, null));
            }
        }
        return list;
    }
}

