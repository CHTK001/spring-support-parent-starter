package com.chua.starter.datasource.provider;

import org.apache.commons.dbcp2.BasicDataSource;

import java.util.HashMap;
import java.util.Map;

/**
 * Apache DBCP2数据源属性提供者
 *
 * @author CH
 * @since 2024/12/21
 */
public class Dbcp2DataSourcePropertyProvider implements DataSourcePropertyProvider<BasicDataSource> {

    private static final Map<String, String> PROPERTY_MAPPING = Map.of(
            "jdbcUrl", "url",
            "url", "url",
            "username", "username",
            "password", "password",
            "driverClassName", "driverClassName"
    );

    @Override
    public Class<BasicDataSource> getDataSourceType() {
        return BasicDataSource.class;
    }

    @Override
    public Map<String, Object> getProperties(BasicDataSource dataSource) {
        Map<String, Object> properties = new HashMap<>();
        
        // 连接属性
        properties.put("url", dataSource.getUrl());
        properties.put("username", dataSource.getUsername());
        properties.put("password", dataSource.getPassword());
        properties.put("driverClassName", dataSource.getDriverClassName());
        
        // 连接池属性
        properties.put("initialSize", dataSource.getInitialSize());
        properties.put("maxTotal", dataSource.getMaxTotal());
        properties.put("maxIdle", dataSource.getMaxIdle());
        properties.put("minIdle", dataSource.getMinIdle());
        properties.put("maxWaitMillis", dataSource.getMaxWaitMillis());
        
        // 验证属性
        properties.put("validationQuery", dataSource.getValidationQuery());
        properties.put("validationQueryTimeout", dataSource.getValidationQueryTimeout());
        properties.put("testOnBorrow", dataSource.getTestOnBorrow());
        properties.put("testOnReturn", dataSource.getTestOnReturn());
        properties.put("testWhileIdle", dataSource.getTestWhileIdle());
        
        // 其他属性
        properties.put("timeBetweenEvictionRunsMillis", dataSource.getTimeBetweenEvictionRunsMillis());
        properties.put("minEvictableIdleTimeMillis", dataSource.getMinEvictableIdleTimeMillis());
        properties.put("poolPreparedStatements", dataSource.isPoolPreparedStatements());
        properties.put("maxOpenPreparedStatements", dataSource.getMaxOpenPreparedStatements());
        properties.put("defaultAutoCommit", dataSource.getDefaultAutoCommit());
        properties.put("defaultReadOnly", dataSource.getDefaultReadOnly());
        
        return properties;
    }

    @Override
    public void setProperties(BasicDataSource dataSource, Map<String, Object> properties) {
        properties.forEach((name, value) -> setProperty(dataSource, name, value));
    }

    @Override
    public void setProperty(BasicDataSource dataSource, String name, Object value) {
        if (value == null) {
            return;
        }
        
        switch (name) {
            // 连接属性
            case "url", "jdbcUrl" -> dataSource.setUrl(String.valueOf(value));
            case "username" -> dataSource.setUsername(String.valueOf(value));
            case "password" -> dataSource.setPassword(String.valueOf(value));
            case "driverClassName" -> dataSource.setDriverClassName(String.valueOf(value));
            
            // 连接池属性
            case "initialSize" -> dataSource.setInitialSize(toInt(value));
            case "maxTotal", "maxActive", "maximumPoolSize" -> dataSource.setMaxTotal(toInt(value));
            case "maxIdle" -> dataSource.setMaxIdle(toInt(value));
            case "minIdle", "minimumIdle" -> dataSource.setMinIdle(toInt(value));
            case "maxWaitMillis", "maxWait" -> dataSource.setMaxWaitMillis(toLong(value));
            
            // 验证属性
            case "validationQuery" -> dataSource.setValidationQuery(String.valueOf(value));
            case "validationQueryTimeout" -> dataSource.setValidationQueryTimeout(toInt(value));
            case "testOnBorrow" -> dataSource.setTestOnBorrow(toBoolean(value));
            case "testOnReturn" -> dataSource.setTestOnReturn(toBoolean(value));
            case "testWhileIdle" -> dataSource.setTestWhileIdle(toBoolean(value));
            
            // 其他属性
            case "timeBetweenEvictionRunsMillis" -> dataSource.setTimeBetweenEvictionRunsMillis(toLong(value));
            case "minEvictableIdleTimeMillis" -> dataSource.setMinEvictableIdleTimeMillis(toLong(value));
            case "poolPreparedStatements" -> dataSource.setPoolPreparedStatements(toBoolean(value));
            case "maxOpenPreparedStatements" -> dataSource.setMaxOpenPreparedStatements(toInt(value));
            case "defaultAutoCommit" -> dataSource.setDefaultAutoCommit(toBoolean(value));
            case "defaultReadOnly" -> dataSource.setDefaultReadOnly(toBoolean(value));
            case "connectionInitSqls" -> dataSource.setConnectionInitSqls(java.util.Collections.singletonList(String.valueOf(value)));
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
        return 20;
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
