package com.chua.starter.monitor.support.service.db;

import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 数据库 Web 管理服务。
 *
 * <p>提供数据库/表/列/数据的只读浏览与受限 SQL 执行能力,
 * 用于监控模块的数据库 Web 管理器(navicat 风格)。
 * 所有连接经 {@link DataSourceStoreService#buildConnection(Long)} 构建,由调用方关闭。</p>
 *
 * <p>安全约束:</p>
 * <ul>
 *   <li>表名/库名/列名标识符只允许 {@code [A-Za-z0-9_]}(防注入)</li>
 *   <li>ORDER BY 列名做白名单校验,方向只允许 ASC/DESC</li>
 *   <li>SQL 执行白名单:SELECT/SHOW/DESC/EXPLAIN/INSERT/UPDATE/DELETE/CREATE/ALTER/DROP/TRUNCATE,
 *       禁止多语句(分号),禁止 SET</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Service
public class JdbcAdminService {

    /** 标识符(表名/库名/列名)合法字符:字母数字下划线。 */
    private static final Pattern IDENTIFIER = Pattern.compile("^[A-Za-z0-9_]+$");

    /** ORDER BY 列名白名单:字母数字下划线逗号与空格。 */
    private static final Pattern ORDER_BY_COLUMNS = Pattern.compile("^[A-Za-z0-9_,\\s]+$");

    /** SQL 语句允许的首关键字白名单(小写)。 */
    private static final Set<String> ALLOWED_SQL_KEYWORDS = Set.of(
            "select", "show", "desc", "describe", "explain",
            "insert", "update", "delete",
            "create", "alter", "drop", "truncate");

    /** SQL 语句最大长度(字符)。 */
    private static final int MAX_SQL_LENGTH = 100_000;

    /** 查询结果最大行数。 */
    private static final int MAX_ROWS = 1000;

    /** 数据库 Web 管理服务构建。 */
    private final DataSourceStoreService dataSourceStoreService;

    /**
    * 构造数据库 Web 管理服务。
    *
    * @param dataSourceStoreService 数据源配置存储服务,不能为 null
    */
    public JdbcAdminService(DataSourceStoreService dataSourceStoreService) {
        this.dataSourceStoreService = dataSourceStoreService;
    }

    /**
    * 获取指定数据源的数据库(目录)列表。
    *
    * @param datasourceId 数据源 id,null 或 0 表示默认主数据源
    * @return 数据库名列表,无数据时为空列表
    * @throws SQLException 连接或查询失败
    */
    public List<String> listDatabases(Long datasourceId) throws SQLException {
        try (Connection conn = dataSourceStoreService.buildConnection(datasourceId);
             ResultSet rs = conn.getMetaData().getCatalogs()) {
            List<String> databases = new ArrayList<>();
            while (rs.next()) {
                databases.add(rs.getString(1));
            }
            return databases;
        }
    }

    /**
    * 获取指定库的表列表。
    *
    * @param datasourceId 数据源 id,null 或 0 表示默认主数据源
    * @param database     数据库名,允许为空(使用连接当前目录)
    * @return 表信息列表(每项含 name/type/remark),无数据时为空列表
    * @throws SQLException 连接或查询失败
    */
    public List<Map<String, Object>> listTables(Long datasourceId, String database) throws SQLException {
        requireIdentifier(database, "database");
        try (Connection conn = dataSourceStoreService.buildConnection(datasourceId);
             ResultSet rs = conn.getMetaData().getTables(
                     database != null ? database : conn.getCatalog(),
                     null, "%", new String[]{"TABLE", "VIEW"})) {
            List<Map<String, Object>> tables = new ArrayList<>();
            while (rs.next()) {
                Map<String, Object> table = new LinkedHashMap<>(4);
                table.put("name", rs.getString("TABLE_NAME"));
                table.put("type", rs.getString("TABLE_TYPE"));
                table.put("remark", rs.getString("REMARKS"));
                tables.add(table);
            }
            return tables;
        }
    }

    /**
    * 获取指定表的结构(列信息,含主键标记)。
    *
    * @param datasourceId 数据源 id,null 或 0 表示默认主数据源
    * @param database     数据库名,允许为空
    * @param tableName    表名,必须符合标识符规范
    * @return 列信息列表(每项含 name/type/size/nullable/default/position/isPrimaryKey/remark)
    * @throws SQLException 连接或查询失败
    */
    public List<Map<String, Object>> getTableColumns(Long datasourceId, String database, String tableName) throws SQLException {
        requireIdentifier(database, "database");
        requireIdentifier(tableName, "tableName");
        try (Connection conn = dataSourceStoreService.buildConnection(datasourceId)) {
            String catalog = database != null ? database : conn.getCatalog();
            List<Map<String, Object>> columns = new ArrayList<>();
            try (ResultSet rs = conn.getMetaData().getColumns(catalog, null, tableName, "%")) {
                while (rs.next()) {
                    Map<String, Object> col = new LinkedHashMap<>(8);
                    col.put("name", rs.getString("COLUMN_NAME"));
                    col.put("type", rs.getString("TYPE_NAME"));
                    col.put("size", rs.getInt("COLUMN_SIZE"));
                    col.put("nullable", rs.getInt("NULLABLE") == 1);
                    col.put("remark", rs.getString("REMARKS"));
                    col.put("default", rs.getString("COLUMN_DEF"));
                    col.put("position", rs.getInt("ORDINAL_POSITION"));
                    col.put("isPrimaryKey", false);
                    columns.add(col);
                }
            }
            // 补充主键标记
            Set<String> pkCols = new HashSet<>();
            try (ResultSet pkRs = conn.getMetaData().getPrimaryKeys(catalog, null, tableName)) {
                while (pkRs.next()) {
                    pkCols.add(pkRs.getString("COLUMN_NAME"));
                }
            }
            for (Map<String, Object> col : columns) {
                col.put("isPrimaryKey", pkCols.contains(col.get("name")));
            }
            return columns;
        }
    }

    /**
    * 获取指定表的数据(分页)。
    *
    * @param datasourceId 数据源 id,null 或 0 表示默认主数据源
    * @param database     数据库名,允许为空
    * @param tableName    表名,必须符合标识符规范
    * @param page         页码(从 1 起),小于 1 时按 1 处理
    * @param pageSize     每页行数,小于 1 时按 50 处理,超过 1000 时按 1000 处理
    * @param orderBy      排序列(逗号分隔,白名单校验),允许为空
    * @param orderDir     排序方向,只允许 ASC/DESC(大小写不敏感),允许为空
    * @return 查询结果(含 columns/rows/total/page/pageSize),无数据时 rows 为空列表
    * @throws SQLException 连接或查询失败
    */
    public Map<String, Object> getTableData(
            Long datasourceId,
            String database,
            String tableName,
            int page,
            int pageSize,
            String orderBy,
            String orderDir) throws SQLException {
        requireIdentifier(database, "database");
        requireIdentifier(tableName, "tableName");
        requireOrderBy(orderBy, orderDir);
        int safePage = Math.max(1, page);
        int safePageSize = Math.min(Math.max(1, pageSize), MAX_ROWS);
        int offset = (safePage - 1) * safePageSize;

        String orderByClause = "";
        if (orderBy != null && !orderBy.isBlank()) {
            orderByClause = "ORDER BY " + orderBy + " " + (orderDir != null ? orderDir.toUpperCase(Locale.ROOT) : "ASC");
        }
        String dataSql = String.format(
                "SELECT * FROM `%s` %s LIMIT %d OFFSET %d",
                tableName, orderByClause, safePageSize, offset);
        String countSql = String.format("SELECT COUNT(*) AS total FROM `%s`", tableName);

        try (Connection conn = dataSourceStoreService.buildConnection(datasourceId);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(dataSql)) {
            ResultSetMetaData meta = rs.getMetaData();
            int colCount = meta.getColumnCount();
            List<String> columns = new ArrayList<>(colCount);
            for (int i = 1; i <= colCount; i++) {
                columns.add(meta.getColumnLabel(i));
            }
            List<Map<String, Object>> rows = new ArrayList<>();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>(colCount);
                for (int i = 1; i <= colCount; i++) {
                    row.put(columns.get(i - 1), rs.getObject(i));
                }
                rows.add(row);
            }
            long total = 0;
            try (ResultSet countRs = stmt.executeQuery(countSql)) {
                if (countRs.next()) {
                    total = countRs.getLong(1);
                }
            }
            Map<String, Object> result = new LinkedHashMap<>(5);
            result.put("columns", columns);
            result.put("rows", rows);
            result.put("total", total);
            result.put("page", safePage);
            result.put("pageSize", safePageSize);
            return result;
        }
    }

    /**
    * 执行单条 SQL(白名单校验)。
    *
    * <p>允许 SELECT/SHOW/DESC/EXPLAIN/INSERT/UPDATE/DELETE/CREATE/ALTER/DROP/TRUNCATE;
    * 禁止多语句(分号)、禁止 SET。查询语句最多返回 {@value #MAX_ROWS} 行。</p>
    *
    * @param datasourceId 数据源 id,null 或 0 表示默认主数据源
    * @param sql          待执行 SQL(单条)
    * @return 执行结果:查询含 columns/rows/rowCount/truncated,非查询含 affectedRows/message
    * @throws SQLException 执行失败或 SQL 不合法
    */
    public Map<String, Object> executeSql(Long datasourceId, String sql) throws SQLException {
        validateSql(sql);
        boolean isQuery = isQueryStatement(sql);
        try (Connection conn = dataSourceStoreService.buildConnection(datasourceId);
             Statement stmt = conn.createStatement()) {
            Map<String, Object> result = new LinkedHashMap<>(4);
            if (isQuery) {
                try (ResultSet rs = stmt.executeQuery(sql)) {
                    ResultSetMetaData meta = rs.getMetaData();
                    int colCount = meta.getColumnCount();
                    List<String> columns = new ArrayList<>(colCount);
                    for (int i = 1; i <= colCount; i++) {
                        columns.add(meta.getColumnLabel(i));
                    }
                    List<Map<String, Object>> rows = new ArrayList<>();
                    int rowCount = 0;
                    while (rs.next() && rowCount < MAX_ROWS) {
                        Map<String, Object> row = new LinkedHashMap<>(colCount);
                        for (int i = 1; i <= colCount; i++) {
                            row.put(columns.get(i - 1), rs.getObject(i));
                        }
                        rows.add(row);
                        rowCount++;
                    }
                    result.put("columns", columns);
                    result.put("rows", rows);
                    result.put("rowCount", rowCount);
                    result.put("truncated", rowCount >= MAX_ROWS);
                }
            } else {
                int affectedRows = stmt.executeUpdate(sql);
                result.put("affectedRows", affectedRows);
                result.put("message", "执行成功，影响 " + affectedRows + " 行");
            }
            return result;
        }
    }

    /**
    * 删除指定表。
    *
    * @param datasourceId 数据源 id,null 或 0 表示默认主数据源
    * @param database     数据库名,允许为空
    * @param tableName    表名,必须符合标识符规范
    * @throws SQLException 执行失败
    */
    public void dropTable(Long datasourceId, String database, String tableName) throws SQLException {
        requireIdentifier(database, "database");
        requireIdentifier(tableName, "tableName");
        String sql = String.format("DROP TABLE IF EXISTS `%s`", tableName);
        try (Connection conn = dataSourceStoreService.buildConnection(datasourceId);
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
        }
    }

    /**
    * 校验 SQL 语句合法性(白名单 + 单语句 + 长度)。
    *
    * @param sql 待校验 SQL,不能为空
    * @throws SQLException SQL 不合法时抛出
    */
    private void validateSql(String sql) throws SQLException {
        if (sql == null || sql.isBlank()) {
            throw new SQLException("SQL 不能为空");
        }
        if (sql.length() > MAX_SQL_LENGTH) {
            throw new SQLException("SQL 超长");
        }
        // 单语句校验:去掉末尾分号后,不允许再含分号
        String normalized = sql.trim();
        if (normalized.endsWith(";")) {
            normalized = normalized.substring(0, normalized.length() - 1).trim();
        }
        if (normalized.contains(";")) {
            throw new SQLException("仅允许单条 SQL 语句");
        }
        String keyword = extractKeyword(normalized);
        if (!ALLOWED_SQL_KEYWORDS.contains(keyword)) {
            throw new SQLException("不允许的 SQL 类型: " + keyword.toUpperCase(Locale.ROOT));
        }
    }

    /**
    * 提取 SQL 首关键字(小写)。
    *
    * @param sql 单条 SQL
    * @return 首关键字
    */
    private String extractKeyword(String sql) {
        String upper = sql.trim();
        int end = 0;
        while (end < upper.length() && Character.isLetter(upper.charAt(end))) {
            end++;
        }
        return upper.substring(0, end).toLowerCase(Locale.ROOT);
    }

    /**
    * 判断 SQL 是否为查询语句。
    *
    * @param sql 单条 SQL
    * @return SELECT/SHOW/DESC/EXPLAIN 为 true
    */
    private boolean isQueryStatement(String sql) {
        String keyword = extractKeyword(sql);
        return "select".equals(keyword)
                || "show".equals(keyword)
                || "desc".equals(keyword)
                || "describe".equals(keyword)
                || "explain".equals(keyword);
    }

    /**
    * 校验 ORDER BY 列白名单与方向。
    *
    * @param orderBy  排序列(逗号分隔),允许为空
    * @param orderDir 排序方向,只允许 ASC/DESC(大小写不敏感),允许为空
    * @throws SQLException 非法时抛出
    */
    private void requireOrderBy(String orderBy, String orderDir) throws SQLException {
        if (orderBy != null && !orderBy.isBlank() && !ORDER_BY_COLUMNS.matcher(orderBy).matches()) {
            throw new SQLException("ORDER BY 列包含非法字符: " + orderBy);
        }
        if (orderDir != null && !orderDir.isBlank()) {
            String dir = orderDir.toUpperCase(Locale.ROOT);
            if (!"ASC".equals(dir) && !"DESC".equals(dir)) {
                throw new SQLException("ORDER BY 方向只允许 ASC/DESC: " + orderDir);
            }
        }
    }

    /**
    * 校验标识符(表名/库名)合法。
    *
    * @param value     待校验标识符,允许为 null(表示使用当前目录)
    * @param fieldName 字段名(异常信息用)
    * @throws SQLException 非法时抛出
    */
    private void requireIdentifier(String value, String fieldName) throws SQLException {
        if (value != null && !value.isBlank() && !IDENTIFIER.matcher(value).matches()) {
            throw new SQLException("非法标识符(" + fieldName + "): " + value);
        }
    }
}
