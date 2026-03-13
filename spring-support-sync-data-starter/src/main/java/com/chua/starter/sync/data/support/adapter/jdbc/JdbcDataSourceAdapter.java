package com.chua.starter.sync.data.support.adapter.jdbc;

import com.chua.starter.sync.data.support.adapter.*;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * JDBC数据源适配器基础类
 */
@Slf4j
public class JdbcDataSourceAdapter implements DataSourceAdapter {
    
    protected DataSource dataSource;
    protected Connection connection;
    protected DataSourceConfig config;
    
    @Override
    public void connect(DataSourceConfig config) throws DataSourceException {
        this.config = config;
        try {
            this.dataSource = createDataSource(config);
            this.connection = dataSource.getConnection();
            log.info("JDBC数据源连接成功: {}", config.getUrl());
        } catch (SQLException e) {
            throw new DataSourceException("JDBC数据源连接失败", e);
        }
    }
    
    protected DataSource createDataSource(DataSourceConfig config) {
        com.zaxxer.hikari.HikariConfig hikariConfig = new com.zaxxer.hikari.HikariConfig();
        hikariConfig.setJdbcUrl(config.getUrl());
        hikariConfig.setUsername(config.getUsername());
        hikariConfig.setPassword(config.getPassword());
        hikariConfig.setDriverClassName(config.getDriverClassName());
        
        hikariConfig.setMaximumPoolSize(config.getMaxPoolSize() != null ? config.getMaxPoolSize() : 10);
        hikariConfig.setMinimumIdle(config.getMinIdle() != null ? config.getMinIdle() : 2);
        hikariConfig.setConnectionTimeout(30000);
        hikariConfig.setIdleTimeout(600000);
        hikariConfig.setMaxLifetime(1800000);
        
        return new com.zaxxer.hikari.HikariDataSource(hikariConfig);
    }
    
    @Override
    public Stream<Map<String, Object>> read(ReadConfig readConfig) {
        String sql = readConfig.getSql();
        int fetchSize = readConfig.getFetchSize() > 0 ? readConfig.getFetchSize() : 1000;
        
        try {
            PreparedStatement ps = connection.prepareStatement(sql, 
                ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
            ps.setFetchSize(fetchSize);
            
            ResultSet rs = ps.executeQuery();
            
            return StreamSupport.stream(
                new JdbcResultSetSpliterator(rs, ps), false
            ).onClose(() -> {
                try {
                    rs.close();
                    ps.close();
                } catch (SQLException e) {
                    log.error("关闭ResultSet失败", e);
                }
            });
            
        } catch (SQLException e) {
            throw new DataSourceException("读取数据失败", e);
        }
    }
    
    @Override
    public void write(List<Map<String, Object>> records, WriteConfig writeConfig) {
        if (records == null || records.isEmpty()) {
            return;
        }
        
        String tableName = writeConfig.getTableName();
        String sql = buildInsertSql(tableName, records.get(0).keySet());
        
        try {
            connection.setAutoCommit(false);
            
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                for (Map<String, Object> record : records) {
                    int index = 1;
                    for (Object value : record.values()) {
                        ps.setObject(index++, value);
                    }
                    ps.addBatch();
                }
                
                ps.executeBatch();
                connection.commit();
                log.debug("批量写入{}条记录到表: {}", records.size(), tableName);
                
            } catch (SQLException e) {
                connection.rollback();
                throw new DataSourceException("批量写入失败", e);
            }
            
        } catch (SQLException e) {
            throw new DataSourceException("数据库操作失败", e);
        }
    }
    
    protected String buildInsertSql(String tableName, Set<String> columns) {
        StringBuilder sql = new StringBuilder("INSERT INTO ");
        sql.append(tableName).append(" (");
        
        StringJoiner columnJoiner = new StringJoiner(", ");
        StringJoiner valueJoiner = new StringJoiner(", ");
        
        for (String column : columns) {
            columnJoiner.add(column);
            valueJoiner.add("?");
        }
        
        sql.append(columnJoiner).append(") VALUES (").append(valueJoiner).append(")");
        return sql.toString();
    }
    
    @Override
    public boolean testConnection() {
        try {
            return connection != null && !connection.isClosed() && connection.isValid(5);
        } catch (SQLException e) {
            log.error("测试连接失败", e);
            return false;
        }
    }
    
    @Override
    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
            if (dataSource instanceof com.zaxxer.hikari.HikariDataSource) {
                ((com.zaxxer.hikari.HikariDataSource) dataSource).close();
            }
            log.info("JDBC数据源连接已关闭");
        } catch (SQLException e) {
            log.error("关闭JDBC连接失败", e);
        }
    }
    
    @Override
    public DataSourceMetadata getMetadata() {
        try {
            DatabaseMetaData metaData = connection.getMetaData();
            
            DataSourceMetadata metadata = new DataSourceMetadata();
            metadata.setDatabaseType(metaData.getDatabaseProductName());
            metadata.setDatabaseVersion(metaData.getDatabaseProductVersion());
            metadata.setDriverName(metaData.getDriverName());
            metadata.setDriverVersion(metaData.getDriverVersion());
            
            return metadata;
            
        } catch (SQLException e) {
            throw new DataSourceException("获取元数据失败", e);
        }
    }
}
