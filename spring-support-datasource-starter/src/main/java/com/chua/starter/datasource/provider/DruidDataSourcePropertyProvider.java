package com.chua.starter.datasource.provider;

import com.alibaba.druid.pool.DruidDataSource;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * Druid数据源属性提供者
 *
 * @author CH
 * @since 2024/12/21
 */
public class DruidDataSourcePropertyProvider implements DataSourcePropertyProvider<DruidDataSource> {

    private static final Map<String, String> PROPERTY_MAPPING = Map.of(
            "jdbcUrl", "url",
            "url", "url",
            "username", "username",
            "password", "password",
            "driverClassName", "driverClassName"
    );

    @Override
    public Class<DruidDataSource> getDataSourceType() {
        return DruidDataSource.class;
    }

    @Override
    public Map<String, Object> getProperties(DruidDataSource dataSource) {
        Map<String, Object> properties = new HashMap<>();
        
        // 连接属性
        properties.put("url", dataSource.getUrl());
        properties.put("username", dataSource.getUsername());
        properties.put("password", dataSource.getPassword());
        properties.put("driverClassName", dataSource.getDriverClassName());
        
        // 连接池属性
        properties.put("initialSize", dataSource.getInitialSize());
        properties.put("maxActive", dataSource.getMaxActive());
        properties.put("minIdle", dataSource.getMinIdle());
        properties.put("maxWait", dataSource.getMaxWait());
        properties.put("maxIdle", dataSource.getMaxIdle());
        
        // 验证属性
        properties.put("validationQuery", dataSource.getValidationQuery());
        properties.put("validationQueryTimeout", dataSource.getValidationQueryTimeout());
        properties.put("testOnBorrow", dataSource.isTestOnBorrow());
        properties.put("testOnReturn", dataSource.isTestOnReturn());
        properties.put("testWhileIdle", dataSource.isTestWhileIdle());
        
        // 其他属性
        properties.put("timeBetweenEvictionRunsMillis", dataSource.getTimeBetweenEvictionRunsMillis());
        properties.put("minEvictableIdleTimeMillis", dataSource.getMinEvictableIdleTimeMillis());
        properties.put("poolPreparedStatements", dataSource.isPoolPreparedStatements());
        properties.put("maxPoolPreparedStatementPerConnectionSize", dataSource.getMaxPoolPreparedStatementPerConnectionSize());
        properties.put("filters", dataSource.getFilterClassNames());
        
        return properties;
    }

    @Override
    public void setProperties(DruidDataSource dataSource, Map<String, Object> properties) {
        properties.forEach((name, value) -> setProperty(dataSource, name, value));
    }

    @Override
    public void setProperty(DruidDataSource dataSource, String name, Object value) {
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
            case "maxActive", "maximumPoolSize" -> dataSource.setMaxActive(toInt(value));
            case "minIdle", "minimumIdle" -> dataSource.setMinIdle(toInt(value));
            case "maxWait" -> dataSource.setMaxWait(toLong(value));
            case "maxIdle" -> dataSource.setMaxIdle(toInt(value));
            
            // 验证属性
            case "validationQuery" -> dataSource.setValidationQuery(String.valueOf(value));
            case "validationQueryTimeout" -> dataSource.setValidationQueryTimeout(toInt(value));
            case "testOnBorrow" -> dataSource.setTestOnBorrow(toBoolean(value));
            case "testOnReturn" -> dataSource.setTestOnReturn(toBoolean(value));
            case "testWhileIdle" -> dataSource.setTestWhileIdle(toBoolean(value));
            
            // 其他属性
            case "timeBetweenEvictionRunsMillis" -> dataSource.setTimeBetweenEvictionRunsMillis(toLong(value));
            case "minEvictableIdleTimeMillis" -> dataSource.setMinEvictableIdleTimeMillis(toLong(value));
            case "maxEvictableIdleTimeMillis" -> dataSource.setMaxEvictableIdleTimeMillis(toLong(value));
            case "poolPreparedStatements" -> dataSource.setPoolPreparedStatements(toBoolean(value));
            case "maxPoolPreparedStatementPerConnectionSize" -> dataSource.setMaxPoolPreparedStatementPerConnectionSize(toInt(value));
            case "connectionProperties" -> dataSource.setConnectionProperties(String.valueOf(value));
            case "connectionInitSqls" -> dataSource.setConnectionInitSqls(java.util.Collections.singletonList(String.valueOf(value)));
            case "asyncInit" -> dataSource.setAsyncInit(toBoolean(value));
            case "filters" -> {
                try {
                    dataSource.setFilters(String.valueOf(value));
                } catch (SQLException ignored) {
                }
            }
            default -> {
                // 忽略未知属性
            }
        }
    }

    @Override
    public void setFilters(DruidDataSource dataSource, String filters) {
        if (filters != null && !filters.isEmpty()) {
            try {
                dataSource.setFilters(filters);
            } catch (SQLException ignored) {
            }
        }
    }

    @Override
    public Map<String, String> getPropertyNameMapping() {
        return PROPERTY_MAPPING;
    }

    @Override
    public int getOrder() {
        return 0; // Druid优先级更高
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
