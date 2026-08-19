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

    /** Dialect */
    private final TriggerReader.DatabaseDialect dialect;

    /**
     * 创建 ProcedureReader 实例
     * @param dialect dialect
     */
    public ProcedureReader(TriggerReader.DatabaseDialect dialect) {
        this.dialect = dialect;
    }

    /**
     * 读取存储过程 + 函数
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

    /** 读取ViaJdbc */
    private List<ProcedureInfo> readViaJdbc(Connection conn) {
        try {
            DatabaseMetaData meta = conn.getMetaData();
            String catalog = conn.getCatalog();
            String schema = conn.getSchema();
            List<ProcedureInfo> list = new ArrayList<>();
            try (ResultSet rs = meta.getProcedures(catalog, schema, "%")) {
                while (rs.next()) {
                    ProcedureInfo p = new ProcedureInfo();
                    p.setName(rs.getString("PROCEDURE_NAME"));
                    p.setSchema(rs.getString("PROCEDURE_SCHEM"));
                    p.setRemark(rs.getString("REMARKS"));
                    String pt = rs.getString("PROCEDURE_TYPE");
                    p.setType("1".equals(pt) ? "PROCEDURE" : "FUNCTION");
                    list.add(p);
                }
            }
            // 单独补函数（部分驱动 getProcedures 不返回函数）
            try (ResultSet rs = meta.getFunctions(catalog, schema, "%")) {
                while (rs.next()) {
                    ProcedureInfo p = new ProcedureInfo();
                    p.setName(rs.getString("FUNCTION_NAME"));
                    p.setSchema(rs.getString("FUNCTION_SCHEM"));
                    p.setRemark(rs.getString("REMARKS"));
                    p.setType("FUNCTION");
                    list.add(p);
                }
            }
            return list;
        } catch (Exception e) {
            log.debug("JDBC 读取存储过程/函数失败：{}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /** 读取MysqlLike */
    private List<ProcedureInfo> readMysqlLike(Connection conn) throws SQLException {
        String sql = "SELECT ROUTINE_NAME, ROUTINE_TYPE, ROUTINE_SCHEMA, ROUTINE_COMMENT " +
                "FROM information_schema.ROUTINES";
        List<ProcedureInfo> list = new ArrayList<>();
        try (var stmt = conn.createStatement(); var rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                ProcedureInfo p = new ProcedureInfo();
                p.setName(rs.getString("ROUTINE_NAME"));
                p.setType(rs.getString("ROUTINE_TYPE"));
                p.setSchema(rs.getString("ROUTINE_SCHEMA"));
                p.setRemark(rs.getString("ROUTINE_COMMENT"));
                list.add(p);
            }
        }
        return list;
    }

    /** 读取Postgresql */
    private List<ProcedureInfo> readPostgresql(Connection conn) throws SQLException {
        String sql = "SELECT routine_name, routine_type, specific_schema " +
                "FROM information_schema.routines";
        List<ProcedureInfo> list = new ArrayList<>();
        try (var stmt = conn.createStatement(); var rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                ProcedureInfo p = new ProcedureInfo();
                p.setName(rs.getString("routine_name"));
                p.setType(rs.getString("routine_type"));
                p.setSchema(rs.getString("specific_schema"));
                list.add(p);
            }
        }
        return list;
    }

    /** 读取Oracle */
    private List<ProcedureInfo> readOracle(Connection conn) throws SQLException {
        String sql = "SELECT OBJECT_NAME, OBJECT_TYPE FROM ALL_OBJECTS " +
                "WHERE OBJECT_TYPE IN ('PROCEDURE','FUNCTION') AND OWNER = USER";
        List<ProcedureInfo> list = new ArrayList<>();
        try (var stmt = conn.createStatement(); var rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                ProcedureInfo p = new ProcedureInfo();
                p.setName(rs.getString("OBJECT_NAME"));
                p.setType(rs.getString("OBJECT_TYPE"));
                p.setSchema("CURRENT");
                list.add(p);
            }
        }
        return list;
    }

    /** 读取SqlServer */
    private List<ProcedureInfo> readSqlServer(Connection conn) throws SQLException {
        String sql = "SELECT o.name, o.type_desc FROM sys.objects o " +
                "WHERE o.type IN ('P','FN','IF','TF')";
        List<ProcedureInfo> list = new ArrayList<>();
        try (var stmt = conn.createStatement(); var rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                ProcedureInfo p = new ProcedureInfo();
                p.setName(rs.getString("name"));
                String type = rs.getString("type_desc");
                p.setType(type != null && type.toUpperCase(Locale.ROOT).contains("FUNCTION") ? "FUNCTION" : "PROCEDURE");
                list.add(p);
            }
        }
        return list;
    }
}
