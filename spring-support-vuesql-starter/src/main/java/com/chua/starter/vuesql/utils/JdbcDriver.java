package com.chua.starter.vuesql.utils;

import com.chua.common.support.database.factory.DelegateDataSource;
import com.chua.common.support.utils.MapUtils;
import com.chua.starter.vuesql.entity.system.WebsqlConfig;
import com.chua.starter.vuesql.enums.DatabaseType;
import com.chua.starter.vuesql.pojo.SqlResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.ColumnMapRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.rowset.SqlRowSet;
import org.springframework.jdbc.support.rowset.SqlRowSetMetaData;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.*;

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
        try
        {
            Class.forName(driver);
            return DriverManager.getConnection(config.getConfigUrl(),
                    config.getConfigUsername(), config.getConfigPassword());
        }
        catch (Exception e)
        {
            log.error(e.getMessage(), e);
            return null;
        }
    }

    public static SqlResult execute(Connection connection, String sql, Integer pageNum, Integer pageSize) {
        SqlResult page = new SqlResult();
        DelegateDataSource delegateDataSource = new DelegateDataSource(() -> connection);
        delegateDataSource.afterPropertiesSet();
        JdbcTemplate jdbcTemplate = new JdbcTemplate(delegateDataSource);
        jdbcTemplate.setFetchSize(1000);
        if(sql.trim().toUpperCase().startsWith("SELECT")) {
            Optional<Map<String, Object>> first = jdbcTemplate.queryForStream("SELECT COUNT(*) CNT FROM (" + sql + ") T", new ColumnMapRowMapper()).findFirst();
            if(!first.isPresent()) {
                return page;
            }

            List<String> columns = new LinkedList<>();
            Integer integer = MapUtils.getInteger(first.get(), "CNT");
            page.setTotal(integer);
            SqlRowSet sqlRowSet = jdbcTemplate.queryForRowSet(sql);
            SqlRowSetMetaData metaData = sqlRowSet.getMetaData();
            int columnCount = metaData.getColumnCount();
            for (int i = 0; i < columnCount; i++) {
                columns.add(metaData.getColumnLabel(i + 1));
            }

            page.setColumns(columns);

            List<Map<String, Object>> rs = new LinkedList<>();
            while (sqlRowSet.next()) {
                Map<String, Object> item = new LinkedHashMap<>();
                for (int i = 0; i < columnCount; i++) {
                    item.put(columns.get(i ), sqlRowSet.getObject(i + 1));
                }

                rs.add(item);
            }
            page.setData(rs);
        }
        return page;
    }
}
