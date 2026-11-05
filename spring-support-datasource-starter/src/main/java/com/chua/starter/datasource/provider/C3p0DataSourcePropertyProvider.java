// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.datasource.provider;

import com.mchange.v2.c3p0.ComboPooledDataSource;

import java.beans.PropertyVetoException;
import java.util.HashMap;
import java.util.Map;

/**
 * C3P0数据源属性提供者
 *
 * @since 2024/12/21
 * @author CH
 */
public class C3p0DataSourcePropertyProvider implements DataSourcePropertyProvider<ComboPooledDataSource> {

    /**
     * 日志
     */
    private static final System.Logger LOG = System.getLogger(C3p0DataSourcePropertyProvider.class.getName());

    private static final Map<String, String> PROPERTY_MAPPING = Map.of(
            "url", "jdbcUrl",
            "jdbcUrl", "jdbcUrl",
            "username", "user",
            "password", "password",
            "driverClassName", "driverClass"
    );

    @Override
    /**
     * 获取数据源类型
     * @return 返回值
    */
    public Class<ComboPooledDataSource> getDataSourceType() {
        return ComboPooledDataSource.class;
    }

    @Override
    /**
     * 获取属性
     * @param dataSource 参数
     * @return 返回值
    */
    public Map<String, Object> getProperties(ComboPooledDataSource dataSource) {
        Map<String, Object> properties = new HashMap<>();
        
        // 连接属性
        properties.put("jdbcUrl", dataSource.getJdbcUrl());
        properties.put("user", dataSource.getUser());
        properties.put("password", dataSource.getPassword());
        properties.put("driverClass", dataSource.getDriverClass());
        
        // 连接池属性
        properties.put("initialPoolSize", dataSource.getInitialPoolSize());
        properties.put("maxPoolSize", dataSource.getMaxPoolSize());
        properties.put("minPoolSize", dataSource.getMinPoolSize());
        properties.put("acquireIncrement", dataSource.getAcquireIncrement());
        properties.put("maxIdleTime", dataSource.getMaxIdleTime());
        
        // 验证属性
        properties.put("preferredTestQuery", dataSource.getPreferredTestQuery());
        properties.put("testConnectionOnCheckout", dataSource.isTestConnectionOnCheckout());
        properties.put("testConnectionOnCheckin", dataSource.isTestConnectionOnCheckin());
        properties.put("idleConnectionTestPeriod", dataSource.getIdleConnectionTestPeriod());
        
        // 其他属性
        properties.put("checkoutTimeout", dataSource.getCheckoutTimeout());
        properties.put("acquireRetryAttempts", dataSource.getAcquireRetryAttempts());
        properties.put("acquireRetryDelay", dataSource.getAcquireRetryDelay());
        properties.put("maxStatements", dataSource.getMaxStatements());
        properties.put("maxStatementsPerConnection", dataSource.getMaxStatementsPerConnection());
        
        return properties;
    }

    @Override
    /**
     * 设置属性
     * @param Map<String 参数
     * @param dataSource 参数
     * @param properties 参数
     * @return 返回值
    */
    public void setProperties(ComboPooledDataSource dataSource, Map<String, Object> properties) {
        properties.forEach((name, value) -> setProperty(dataSource, name, value));
    }

    @Override
    /**
     * 设置财产
     * @param dataSource 参数
     * @param name 字符串
     * @param value 对象
     * @return 返回值
    */
    public void setProperty(ComboPooledDataSource dataSource, String name, Object value) {
        if (value == null) {
            return;
        }
        
        try {
            switch (name) {
                // 连接属性
                case "jdbcUrl", "url" -> dataSource.setJdbcUrl(String.valueOf(value));
                case "user", "username" -> dataSource.setUser(String.valueOf(value));
                case "password" -> dataSource.setPassword(String.valueOf(value));
                case "driverClass", "driverClassName" -> dataSource.setDriverClass(String.valueOf(value));
                
                // 连接池属性
                case "initialPoolSize", "initialSize" -> dataSource.setInitialPoolSize(toInt(value));
                case "maxPoolSize", "maxActive", "maximumPoolSize" -> dataSource.setMaxPoolSize(toInt(value));
                case "minPoolSize", "minIdle", "minimumIdle" -> dataSource.setMinPoolSize(toInt(value));
                case "acquireIncrement" -> dataSource.setAcquireIncrement(toInt(value));
                case "maxIdleTime", "idleTimeout" -> dataSource.setMaxIdleTime(toInt(value));
                
                // 验证属性
                case "preferredTestQuery", "validationQuery" -> dataSource.setPreferredTestQuery(String.valueOf(value));
                case "testConnectionOnCheckout", "testOnBorrow" -> dataSource.setTestConnectionOnCheckout(toBoolean(value));
                case "testConnectionOnCheckin", "testOnReturn" -> dataSource.setTestConnectionOnCheckin(toBoolean(value));
                case "idleConnectionTestPeriod" -> dataSource.setIdleConnectionTestPeriod(toInt(value));
                
                // 其他属性
                case "checkoutTimeout", "maxWait" -> dataSource.setCheckoutTimeout(toInt(value));
                case "acquireRetryAttempts" -> dataSource.setAcquireRetryAttempts(toInt(value));
                case "acquireRetryDelay" -> dataSource.setAcquireRetryDelay(toInt(value));
                case "maxStatements" -> dataSource.setMaxStatements(toInt(value));
                case "maxStatementsPerConnection" -> dataSource.setMaxStatementsPerConnection(toInt(value));
                case "autoCommitOnClose" -> dataSource.setAutoCommitOnClose(toBoolean(value));
                case "numHelperThreads" -> dataSource.setNumHelperThreads(toInt(value));
                default -> {
                    // 忽略未知属性
                }
            }
        } catch (PropertyVetoException e) {
            LOG.log(System.Logger.Level.DEBUG, "[datasource] 设置 C3P0 属性失败，已忽略: " + e.getMessage(), e);
        }
    }

    @Override
    /**
     * 获取财产名称mapping
     * @return 返回值
    */
    public Map<String, String> getPropertyNameMapping() {
        return PROPERTY_MAPPING;
    }

    @Override
    /**
     * 获取订单
     * @return 返回值
    */
    public int getOrder() {
        return 40;
    }

    /**
     * 转为int
     *
     * @param value 值
     * @return 转为int的结果
     */
    private int toInt(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return Integer.parseInt(String.valueOf(value));
    }

    /**
     * 转为布尔值
     *
     * @param value 值
     * @return 转为布尔值的结果
     */
    private boolean toBoolean(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }
}