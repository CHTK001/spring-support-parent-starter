package com.chua.starter.datasource.provider;

import com.zaxxer.hikari.HikariDataSource;

import java.util.HashMap;
import java.util.Map;

/**
 * HikariCP数据源属性提供者
 *
 * @author CH
 * @since 2024/12/21
 */
public class HikariDataSourcePropertyProvider implements DataSourcePropertyProvider<HikariDataSource> {

    private static final Map<String, String> PROPERTY_MAPPING = Map.of(
            "url", "jdbcUrl",
            "username", "username",
            "password", "password",
            "driverClassName", "driverClassName"
    );

    @Override
    public Class<HikariDataSource> getDataSourceType() {
        return HikariDataSource.class;
    }

    @Override
    public Map<String, Object> getProperties(HikariDataSource dataSource) {
        Map<String, Object> properties = new HashMap<>();
        
        // 连接属性
        properties.put("jdbcUrl", dataSource.getJdbcUrl());
        properties.put("username", dataSource.getUsername());
        properties.put("password", dataSource.getPassword());
        properties.put("driverClassName", dataSource.getDriverClassName());
        
        // 连接池属性 - 只添加有效值（非负数）
        int maximumPoolSize = dataSource.getMaximumPoolSize();
        int minimumIdle = dataSource.getMinimumIdle();
        
        if (maximumPoolSize > 0) {
            properties.put("maximumPoolSize", maximumPoolSize);
        }
        // minimumIdle为-1表示未设置，使用HikariCP默认值
        if (minimumIdle >= 0) {
            properties.put("minimumIdle", minimumIdle);
        }
        
        long idleTimeout = dataSource.getIdleTimeout();
        long maxLifetime = dataSource.getMaxLifetime();
        long connectionTimeout = dataSource.getConnectionTimeout();
        long validationTimeout = dataSource.getValidationTimeout();
        
        if (idleTimeout > 0) {
            properties.put("idleTimeout", idleTimeout);
        }
        if (maxLifetime > 0) {
            properties.put("maxLifetime", maxLifetime);
        }
        if (connectionTimeout > 0) {
            properties.put("connectionTimeout", connectionTimeout);
        }
        if (validationTimeout > 0) {
            properties.put("validationTimeout", validationTimeout);
        }
        properties.put("leakDetectionThreshold", dataSource.getLeakDetectionThreshold());
        
        // 其他属性
        properties.put("poolName", dataSource.getPoolName());
        properties.put("connectionTestQuery", dataSource.getConnectionTestQuery());
        properties.put("autoCommit", dataSource.isAutoCommit());
        properties.put("readOnly", dataSource.isReadOnly());
        properties.put("catalog", dataSource.getCatalog());
        properties.put("schema", dataSource.getSchema());
        
        return properties;
    }

    @Override
    public void setProperties(HikariDataSource dataSource, Map<String, Object> properties) {
        properties.forEach((name, value) -> setProperty(dataSource, name, value));
    }

    @Override
    public void setProperty(HikariDataSource dataSource, String name, Object value) {
        if (value == null) {
            return;
        }
        
        switch (name) {
            // 连接属性
            case "jdbcUrl", "url" -> dataSource.setJdbcUrl(String.valueOf(value));
            case "username" -> dataSource.setUsername(String.valueOf(value));
            case "password" -> dataSource.setPassword(String.valueOf(value));
            case "driverClassName" -> dataSource.setDriverClassName(String.valueOf(value));
            
            // 连接池属性
            case "maximumPoolSize", "maxPoolSize" -> dataSource.setMaximumPoolSize(toInt(value));
            case "minimumIdle", "minIdle" -> {
                int minIdle = toInt(value);
                // 只设置非负值，负值表示未配置，使用HikariCP默认值
                if (minIdle >= 0) {
                    dataSource.setMinimumIdle(minIdle);
                }
            }
            case "idleTimeout" -> dataSource.setIdleTimeout(toLong(value));
            case "maxLifetime" -> dataSource.setMaxLifetime(toLong(value));
            case "connectionTimeout" -> dataSource.setConnectionTimeout(toLong(value));
            case "validationTimeout" -> dataSource.setValidationTimeout(toLong(value));
            case "leakDetectionThreshold" -> dataSource.setLeakDetectionThreshold(toLong(value));
            
            // 其他属性
            case "poolName" -> dataSource.setPoolName(String.valueOf(value));
            case "connectionTestQuery" -> dataSource.setConnectionTestQuery(String.valueOf(value));
            case "autoCommit" -> dataSource.setAutoCommit(toBoolean(value));
            case "readOnly" -> dataSource.setReadOnly(toBoolean(value));
            case "catalog" -> dataSource.setCatalog(String.valueOf(value));
            case "schema" -> dataSource.setSchema(String.valueOf(value));
            case "connectionInitSql" -> dataSource.setConnectionInitSql(String.valueOf(value));
            case "transactionIsolation" -> dataSource.setTransactionIsolation(String.valueOf(value));
            case "initializationFailTimeout" -> dataSource.setInitializationFailTimeout(toLong(value));
            case "isolateInternalQueries" -> dataSource.setIsolateInternalQueries(toBoolean(value));
            case "allowPoolSuspension" -> dataSource.setAllowPoolSuspension(toBoolean(value));
            case "registerMbeans" -> dataSource.setRegisterMbeans(toBoolean(value));
            default -> {
                // 忽略未知属性
            }
        }
    }

    @Override
    public Map<String, String> getPropertyNameMapping() {
        return PROPERTY_MAPPING;
    }

    @Override
    public int getOrder() {
        return 10;
    }

    private int toInt(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private long toLong(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    private boolean toBoolean(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }
}
