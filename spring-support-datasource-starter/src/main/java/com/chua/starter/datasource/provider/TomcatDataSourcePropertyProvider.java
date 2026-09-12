package com.chua.starter.datasource.provider;

import org.apache.tomcat.jdbc.pool.DataSource;

import java.util.HashMap;
import java.util.Map;

/**
 * Tomcat JDBC连接池数据源属性提供者
 *
 * @since 2024/12/21
 * @author CH
 */
public class TomcatDataSourcePropertyProvider implements DataSourcePropertyProvider<DataSource> {

    private static final Map<String, String> PROPERTY_MAPPING = Map.of(
            "jdbcUrl", "url",
            "url", "url",
            "username", "username",
            "password", "password",
            "driverClassName", "driverClassName"
    );

    @Override
    /** 获取数据源类型 */
    public Class<DataSource> getDataSourceType() {
        return DataSource.class;
    }

    @Override
    /** 获取属性 */
    public Map<String, Object> getProperties(DataSource dataSource) {
        Map<String, Object> properties = new HashMap<>();
        
        // 连接属性
        properties.put("url", dataSource.getUrl());
        properties.put("username", dataSource.getUsername());
        properties.put("password", dataSource.getPassword());
        properties.put("driverClassName", dataSource.getDriverClassName());
        
        // 连接池属性
        properties.put("initialSize", dataSource.getInitialSize());
        properties.put("maxActive", dataSource.getMaxActive());
        properties.put("maxIdle", dataSource.getMaxIdle());
        properties.put("minIdle", dataSource.getMinIdle());
        properties.put("maxWait", dataSource.getMaxWait());
        
        // 验证属性
        properties.put("validationQuery", dataSource.getValidationQuery());
        properties.put("validationQueryTimeout", dataSource.getValidationQueryTimeout());
        properties.put("testOnBorrow", dataSource.isTestOnBorrow());
        properties.put("testOnReturn", dataSource.isTestOnReturn());
        properties.put("testWhileIdle", dataSource.isTestWhileIdle());
        
        // 其他属性
        properties.put("timeBetweenEvictionRunsMillis", dataSource.getTimeBetweenEvictionRunsMillis());
        properties.put("minEvictableIdleTimeMillis", dataSource.getMinEvictableIdleTimeMillis());
        properties.put("defaultAutoCommit", dataSource.getDefaultAutoCommit());
        properties.put("defaultReadOnly", dataSource.getDefaultReadOnly());
        properties.put("connectionProperties", dataSource.getConnectionProperties());
        
        return properties;
    }

    @Override
    /** 设置属性 */
    public void setProperties(DataSource dataSource, Map<String, Object> properties) {
        properties.forEach((name, value) -> setProperty(dataSource, name, value));
    }

    @Override
    /** 设置财产 */
    public void setProperty(DataSource dataSource, String name, Object value) {
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
            case "maxActive", "maxTotal", "maximumPoolSize" -> dataSource.setMaxActive(toInt(value));
            case "maxIdle" -> dataSource.setMaxIdle(toInt(value));
            case "minIdle", "minimumIdle" -> dataSource.setMinIdle(toInt(value));
            case "maxWait", "maxWaitMillis" -> dataSource.setMaxWait(toInt(value));
            
            // 验证属性
            case "validationQuery" -> dataSource.setValidationQuery(String.valueOf(value));
            case "validationQueryTimeout" -> dataSource.setValidationQueryTimeout(toInt(value));
            case "validationInterval" -> dataSource.setValidationInterval(toLong(value));
            case "testOnBorrow" -> dataSource.setTestOnBorrow(toBoolean(value));
            case "testOnReturn" -> dataSource.setTestOnReturn(toBoolean(value));
            case "testWhileIdle" -> dataSource.setTestWhileIdle(toBoolean(value));
            case "testOnConnect" -> dataSource.setTestOnConnect(toBoolean(value));
            
            // 其他属性
            case "timeBetweenEvictionRunsMillis" -> dataSource.setTimeBetweenEvictionRunsMillis(toInt(value));
            case "minEvictableIdleTimeMillis" -> dataSource.setMinEvictableIdleTimeMillis(toInt(value));
            case "defaultAutoCommit" -> dataSource.setDefaultAutoCommit(toBoolean(value));
            case "defaultReadOnly" -> dataSource.setDefaultReadOnly(toBoolean(value));
            case "connectionProperties" -> dataSource.setConnectionProperties(String.valueOf(value));
            case "initSQL" -> dataSource.setInitSQL(String.valueOf(value));
            case "jdbcInterceptors" -> dataSource.setJdbcInterceptors(String.valueOf(value));
            case "removeAbandoned" -> dataSource.setRemoveAbandoned(toBoolean(value));
            case "removeAbandonedTimeout" -> dataSource.setRemoveAbandonedTimeout(toInt(value));
            case "logAbandoned" -> dataSource.setLogAbandoned(toBoolean(value));
            default -> {
                // 忽略未知属性
            }
        }
    }

    @Override
    /** 获取财产名称mapping */
    public Map<String, String> getPropertyNameMapping() {
        return PROPERTY_MAPPING;
    }

    @Override
    /** 获取订单 */
    public int getOrder() {
        return 30;
    }

    /**
     * 转为int
     *
     * @param value 价值观
     * @return 转为int的结果
     */
    private int toInt(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return Integer.parseInt(String.valueOf(value));
    }

    /**
     * 转为long
     *
     * @param value 价值观
     * @return 转为long的结果
     */
    private long toLong(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    /**
     * 转为布尔值
     *
     * @param value 价值观
     * @return 转为布尔值的结果
     */
    private boolean toBoolean(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }
}
