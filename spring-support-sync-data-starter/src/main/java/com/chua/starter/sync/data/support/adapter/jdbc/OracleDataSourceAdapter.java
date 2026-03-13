package com.chua.starter.sync.data.support.adapter.jdbc;

import com.chua.starter.sync.data.support.adapter.DataSourceConfig;
import lombok.extern.slf4j.Slf4j;

/**
 * Oracle数据源适配器
 */
@Slf4j
public class OracleDataSourceAdapter extends JdbcDataSourceAdapter {
    
    @Override
    protected javax.sql.DataSource createDataSource(DataSourceConfig config) {
        if (config.getDriverClassName() == null) {
            config.setDriverClassName("oracle.jdbc.OracleDriver");
        }
        
        log.info("创建Oracle数据源: {}", config.getUrl());
        return super.createDataSource(config);
    }
    
    @Override
    protected String buildInsertSql(String tableName, java.util.Set<String> columns) {
        StringBuilder sql = new StringBuilder("INSERT INTO ");
        sql.append(tableName).append(" (");
        
        java.util.StringJoiner columnJoiner = new java.util.StringJoiner(", ");
        java.util.StringJoiner valueJoiner = new java.util.StringJoiner(", ");
        
        for (String column : columns) {
            columnJoiner.add(column.toUpperCase());
            valueJoiner.add("?");
        }
        
        sql.append(columnJoiner).append(") VALUES (").append(valueJoiner).append(")");
        return sql.toString();
    }
}
