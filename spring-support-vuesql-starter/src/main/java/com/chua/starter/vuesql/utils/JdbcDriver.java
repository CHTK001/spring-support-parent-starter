package com.chua.starter.vuesql.utils;

import com.alibaba.fastjson2.JSONArray;
import com.chua.common.support.database.ResultSetUtils;
import com.chua.common.support.utils.MapUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.vuesql.entity.system.WebsqlConfig;
import com.chua.starter.vuesql.enums.DatabaseType;
import com.chua.starter.vuesql.enums.Type;
import com.chua.starter.vuesql.pojo.*;
import lombok.extern.slf4j.Slf4j;

import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 数据库驱动
 *
 * @author CH
 */
@Slf4j
public class JdbcDriver {

    /**
     * 创建链接
     *
     * @param databaseType 数据库类型
     * @param config       配置
     * @return connection
     */
    public static Connection createConnection(DatabaseType databaseType, WebsqlConfig config) {
        String driver = databaseType.getDriver();
        try {
            Class.forName(driver);
            return DriverManager.getConnection(config.getConfigUrl(),
                    config.getConfigUsername(), config.getConfigPassword());
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return null;
        }
    }

    public static SqlResult execute(Connection connection, String sortColumn, String sortType, String sql, String pageSql) {
        SqlResult page = new SqlResult();
        List<Map<String, Object>> rs = new LinkedList<>();
        List<Column> columns = new LinkedList<>();
        page.setColumns(columns);
        page.setData(rs);
        try (Statement statement = connection.createStatement()) {
            statement.setFetchSize(1000);
            String upperCase = sql.trim().toUpperCase();
            if (upperCase.startsWith("SELECT")) {
                page.setTotal(withCount(statement, sql));
                if (0 == page.getTotal()) {
                    doColumn(statement, columns, pageSql);
                    return page;
                }
                pageSql = analysisSql(sql, pageSql, sortColumn, sortType);
                doSearch(statement, columns, rs, pageSql);

            } else if (upperCase.startsWith("EXPLAIN")) {
                doSearch(statement, columns, rs, sql);
                page.setTotal(rs.size());
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return page;
    }

    private static String analysisSql(String sql, String pageSql, String sortColumn, String sortType) {
        if (null != pageSql) {
            String s = pageSql.toLowerCase();
            pageSql = s.replace(" limit ", " LIMIT ");
            if (StringUtils.isNotBlank(sortColumn) && !s.contains(" order")) {
                String orderBy = " ORDER BY " + sortColumn + " " + sortType;
                if (pageSql.contains(" LIMIT ")) {
                    pageSql = pageSql.replace(" LIMIT ", orderBy + " LIMIT ");
                } else {
                    pageSql += orderBy;
                }
            }
            return pageSql;
        }
        return sql;
    }

    private static Integer withCount(Statement statement, String sql) {
        try (ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) CNT FROM (" + sql + ") T")) {
            if (!resultSet.next()) {
                return 0;
            }
            return resultSet.getInt("CNT");
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private static void doColumn(Statement statement, List<Column> columns, String pageSql) {
        try (ResultSet executeQuery = statement.executeQuery(pageSql)) {
            ResultSetMetaData metaData = executeQuery.getMetaData();
            int columnCount = metaData.getColumnCount();
            for (int i = 0; i < columnCount; i++) {
                columns.add(Column.builder().columnName(metaData.getColumnLabel(i + 1)).columnType(metaData.getColumnTypeName(i + 1)).build());
            }

        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private static void doSearch(Statement statement, List<Column> columns, List<Map<String, Object>> rs, String pageSql) {
        try (ResultSet executeQuery = statement.executeQuery(pageSql)) {
            ResultSetMetaData metaData = executeQuery.getMetaData();
            int columnCount = metaData.getColumnCount();
            for (int i = 0; i < columnCount; i++) {
                columns.add(Column.builder().columnName(metaData.getColumnLabel(i + 1)).columnType(metaData.getColumnTypeName(i + 1)).build());
            }

            while (executeQuery.next()) {
                Map<String, Object> item = new LinkedHashMap<>();
                for (int i = 0; i < columnCount; i++) {
                    item.put(columns.get(i).getColumnName(), executeQuery.getObject(i + 1));
                }

                rs.add(item);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 查询sql
     *
     * @param connection 链接
     * @param sql        sql
     * @return 结果
     */
    public static OpenResult query(Connection connection, String sql) {
        OpenResult result = new OpenResult();
        List<Map<String, Object>> rs = new LinkedList<>();
        List<Column> columns = new LinkedList<>();
        try (Statement statement = connection.createStatement()) {
            statement.setFetchSize(1000);
            doSearch(statement, columns, rs, sql);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        result.setData(rs);
        result.setColumns(columns);
        result.setTotal(Integer.MAX_VALUE);
        return result;
    }

    /**
     * 创建数据库结构
     *
     * @param connection 连接
     * @param config     数据库配置
     */
    public static List<Construct> doConstruct(Connection connection, WebsqlConfig config) throws SQLException {
        return doConstruct(connection, config, true, true, true);
    }

    /**
     * 创建数据库结构
     *
     * @param connection 连接
     * @param config     数据库配置
     */
    public static List<Construct> doConstruct(Connection connection, WebsqlConfig config, boolean insertEnable, boolean updateEnable, boolean deleteEnable) throws SQLException {
        List<Construct> rs = new LinkedList<>();
        rs.add(Construct.builder().icon("DATABASE").type(Type.DATABASE).id(1).pid(0).name(config.getConfigName()).build());
        rs.add(Construct.builder().icon("TABLE").type(Type.TABLE).pid(1).id(2).name("表").build());
        rs.add(Construct.builder().icon("VIEW").type(Type.VIEW).pid(1).id(3).name("视图").build());
        AtomicInteger index = new AtomicInteger(4);
        ResultSet resultSet = connection.getMetaData().getTables(config.getConfigDatabase(), null, null, new String[]{"TABLE"});
        ResultSetUtils.doLine(resultSet, tables -> {
            rs.add(Construct.builder().icon("TABLE").type(Type.TABLE).pid(2)
                    .realName(tables.getString("TABLE_NAME"))
                    .id(index.getAndIncrement())
                    .updateEnable(updateEnable).insertEnable(insertEnable).deleteEnable(deleteEnable)
                    .name(tables.getString("TABLE_NAME")).build());
        });

        ResultSet resultSet1 = connection.getMetaData().getTables(config.getConfigDatabase(), null, null, new String[]{"VIEW"});
        ResultSetUtils.doLine(resultSet1, tables -> {
            rs.add(Construct.builder().icon("VIEW").type(Type.VIEW)
                    .pid(3)
                    .realName(tables.getString("TABLE_NAME"))
                    .id(index.getAndIncrement())
                    .name(tables.getString("TABLE_NAME"))
                    .build());
        });

        return rs;
    }

    /**
     * 關鍵詞
     *
     * @param connection 连接
     * @param config     配置
     * @return 结果
     * @throws SQLException
     */
    public static List<Keyword> doKeyword(Connection connection, WebsqlConfig config) throws SQLException {
        List<Keyword> rs = new LinkedList<>();
        ResultSet resultSet = connection.getMetaData().getTables(config.getConfigDatabase(), null, null, new String[]{"TABLE"});
        Map<String, Keyword> tpl = new LinkedHashMap<>();
        ResultSetUtils.doLine(resultSet, tables -> {
            Keyword keyword = Keyword.builder().text(tables.getString("TABLE_NAME")).build();
            tpl.put(keyword.getText(), keyword);
            rs.add(keyword);
        });

        Map<String, List<String>> columns = new HashMap<>();
        ResultSet resultSet1 = connection.getMetaData().getColumns(config.getConfigDatabase(), null, null, null);
        ResultSetUtils.doLine(resultSet1, tables1 -> {
            columns.computeIfAbsent(tables1.getString("TABLE_NAME"), it -> new LinkedList<>()).add(tables1.getString("COLUMN_NAME"));
        });

        for (Map.Entry<String, Keyword> entry : tpl.entrySet()) {
            List<String> strings = columns.get(entry.getKey());
            entry.getValue().setColumns(strings.stream().map(it -> {
                return Keyword.ColumnKeyword.builder().text(it).displayText(it).build();
            }).collect(Collectors.toList()));
        }
        return rs;
    }

    /**
     * 更新数据
     *
     * @param connection 连接
     * @param table      表名
     * @param data       数据
     * @return 结果
     */
    @SuppressWarnings("ALL")
    public static Boolean update(Connection connection, String table, JSONArray data) throws Exception {
        for (Object datum : data) {
            Map<String, Object> item = (Map<String, Object>) datum;
            Map newData = MapUtils.getType(item, "newData", Map.class);
            Map oldData = MapUtils.getType(item, "oldData", Map.class);
            String action = MapUtils.getString(item, "action");
            if(MapUtils.isEmpty(newData) && MapUtils.isEmpty(oldData)) {
                continue;
            }
            try {
                connection.setAutoCommit(false);
                JdbcDriver.update(connection, newData, oldData, table, action);
            } catch (SQLException e) {
                connection.rollback();
            } finally {
                connection.commit();
                connection.setAutoCommit(true);
            }
        };

        return true;
    }

    /**
     * 更新数据
     *
     * @param connection 连接
     * @param newData1   新数据
     * @param oldData    老数据
     * @param realName   表名
     * @param mode       模式
     * @return 结果
     */
    public static Boolean update(Connection connection, Map<String, Object> newData1, Map<String, Object> oldData, String realName, String mode) {
        StringBuilder sb = new StringBuilder();
        //去重新数据中和老数据一致的数据
        if ("delete".equalsIgnoreCase(mode)) {
            sb = makeDeleteSql(oldData, realName);
        } else if ("add".equalsIgnoreCase(mode)) {
            sb = makeInsertSql(newData1, realName);
        } else if ("update".equalsIgnoreCase(mode)) {
            Map<String, Object> newData = MapUtils.removeSameData(newData1, oldData);
            if(newData.isEmpty()) {
                return true;
            }
            sb = makeUpdateSql(newData, oldData, realName);
        } else {
            Map<String, Object> newData = MapUtils.removeSameData(newData1, oldData);
            //新增
            if (oldData.values().stream().map(String::valueOf).filter(StringUtils::isNotEmpty).count() == 0L) {
                sb = makeInsertSql(newData, realName);
            } else {
                sb = makeUpdateSql(newData, oldData, realName);
            }
        }

        try (Statement statement = connection.createStatement()) {
            return statement.executeUpdate(sb.toString()) != 0;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * delte sql
     *
     * @param oldData  老数据
     * @param realName 表名
     * @return sql
     */
    private static StringBuilder makeDeleteSql(Map<String, Object> oldData, String realName) {
        StringBuilder sb = new StringBuilder();
        sb.append("DELETE FROM ").append(realName)
                .append(" WHERE 1 = 1 ");
        for (Map.Entry<String, Object> entry : oldData.entrySet()) {
            Object value = entry.getValue();
            if (null == value) {
                continue;
            }

            String key = entry.getKey();

            if (null == value || (value instanceof String && StringUtils.isEmpty(value.toString()))) {
                continue;
            }
            sb.append(" AND `").append(key).append("` = ");
            if (value instanceof Number) {
                sb.append(value);
            } else {
                sb.append("'").append(value.toString().replace("'", "")).append("'");
            }
        }
        return sb;
    }

    /**
     * update sql
     *
     * @param newData  新數據
     * @param oldData  老数据
     * @param realName 表名
     * @return sql
     */
    private static StringBuilder makeUpdateSql(Map<String, Object> newData, Map<String, Object> oldData, String realName) {
        StringBuilder sb = new StringBuilder();

        sb.append("UPDATE ").append(realName)
                .append(" SET ");
        for (String s : newData.keySet()) {
            sb.append("`").append(s).append("` = '")
                    .append(newData.get(s)).append("',");
        }
        sb.delete(sb.length() - 1, sb.length());

        sb.append(" WHERE 1 = 1 ");
        for (Map.Entry<String, Object> entry : oldData.entrySet()) {
            Object value = entry.getValue();
            if (null == value) {
                continue;
            }

            String key = entry.getKey();

            if (newData.containsKey(key)) {
                continue;
            }

            if (null == value || (value instanceof String && StringUtils.isEmpty(value.toString()))) {
                continue;
            }
            sb.append(" AND `").append(key).append("` = ");
            if (value instanceof Number) {
                sb.append(value);
            } else {
                sb.append("'").append(value.toString().replace("'", "")).append("'");
            }
        }
        return sb;
    }

    /**
     * insert sql
     *
     * @param newData  新數據
     * @param realName 表名
     * @return sql
     */
    private static StringBuilder makeInsertSql(Map<String, Object> newData, String realName) {
        StringBuilder sb = new StringBuilder();
        sb.append(" INSERT INTO ").append(realName).append(" (");
        for (String key : newData.keySet()) {
            sb.append("`").append(key).append("`,");
        }
        sb.delete(sb.length() - 1, sb.length());
        sb.append(")");
        sb.append(" VALUES (");
        for (Object o : newData.values()) {
            if(null == o || o instanceof Number) {
                sb.append(o).append(",");
                continue;
            }

            sb.append("'").append(o).append("',");
        }
        sb.delete(sb.length() - 1, sb.length());
        sb.append(")");

        return sb;
    }
}
