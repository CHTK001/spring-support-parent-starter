package com.chua.starter.vuesql.utils;

import com.chua.common.support.utils.StringUtils;
import com.chua.starter.vuesql.entity.system.WebsqlConfig;
import com.chua.starter.vuesql.enums.DatabaseType;
import com.chua.starter.vuesql.pojo.SqlResult;
import lombok.extern.slf4j.Slf4j;

import java.sql.*;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

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
        List<String> columns = new LinkedList<>();
        page.setColumns(columns);
        page.setData(rs);
        try (Statement statement = connection.createStatement()) {
            statement.setFetchSize(1000);
            String upperCase = sql.trim().toUpperCase();
            if (upperCase.startsWith("SELECT")) {
                page.setTotal(withCount(statement, sql));
                if (0 == page.getTotal()) {
                    return page;
                }
                pageSql = analysisSql(sql, pageSql, sortColumn, sortType);
                doSearch(statement, columns, rs, pageSql);

            } else if (upperCase.startsWith("EXPLAIN")) {
                doSearch(statement, columns, rs, sql);
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

    private static void doSearch(Statement statement, List<String> columns, List<Map<String, Object>> rs, String pageSql) {
        try (ResultSet executeQuery = statement.executeQuery(pageSql)) {
            ResultSetMetaData metaData = executeQuery.getMetaData();
            int columnCount = metaData.getColumnCount();
            for (int i = 0; i < columnCount; i++) {
                columns.add(metaData.getColumnLabel(i + 1));
            }

            while (executeQuery.next()) {
                Map<String, Object> item = new LinkedHashMap<>();
                for (int i = 0; i < columnCount; i++) {
                    item.put(columns.get(i), executeQuery.getObject(i + 1));
                }

                rs.add(item);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
