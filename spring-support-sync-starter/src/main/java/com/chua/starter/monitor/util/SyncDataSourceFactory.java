package com.chua.starter.monitor.util;

import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Map;
import java.util.logging.Logger;

/**
 * 同步任务数据源工厂
 * 根据配置创建 DataSource 实例
 *
 * @author CH
 * @since 2024/12/21
 */
@Slf4j
public class SyncDataSourceFactory {

    /**
     * 根据配置创建 DataSource
     *
     * @param config 配置参数
     * @return DataSource 实例
     */
    public static DataSource createDataSource(Map<String, Object> config) {
        String url = getString(config, "url", null);
        String username = getString(config, "username", null);
        String password = getString(config, "password", null);
        String driverClassName = getString(config, "driverClassName", null);
        String connectionPool = getString(config, "connectionPool", "none");

        if (url == null || url.isEmpty()) {
            throw new IllegalArgumentException("数据库URL不能为空");
        }

        if (driverClassName == null || driverClassName.isEmpty()) {
            driverClassName = inferDriverClassName(url);
        }

        loadDriver(driverClassName);

        switch (connectionPool.toLowerCase()) {
            case "hikari":
                return createHikariDataSource(url, username, password, driverClassName, config);
            case "druid":
                return createDruidDataSource(url, username, password, driverClassName, config);
            default:
                return createSimpleDataSource(url, username, password);
        }
    }

    /**
     * 测试数据库连接
     *
     * @param config 配置参数
     * @return 测试结果描述
     */
    public static String testConnection(Map<String, Object> config) {
        String url = getString(config, "url", null);
        String username = getString(config, "username", null);
        String password = getString(config, "password", null);
        String driverClassName = getString(config, "driverClassName", null);

        if (url == null || url.isEmpty()) {
            return "数据库URL不能为空";
        }

        if (driverClassName == null || driverClassName.isEmpty()) {
            driverClassName = inferDriverClassName(url);
        }

        try {
            loadDriver(driverClassName);

            try (Connection conn = DriverManager.getConnection(url, username, password)) {
                if (conn.isValid(5)) {
                    String dbInfo = conn.getMetaData().getDatabaseProductName() + " " +
                            conn.getMetaData().getDatabaseProductVersion();
                    return "连接成功: " + dbInfo;
                } else {
                    return "连接验证失败";
                }
            }
        } catch (SQLException e) {
            return "连接失败: " + e.getMessage();
        } catch (Exception e) {
            return "连接失败: " + e.getMessage();
        }
    }

    /**
     * 根据URL推断驱动类名
     */
    private static String inferDriverClassName(String url) {
        if (url == null) {
            return null;
        }

        String lowerUrl = url.toLowerCase();
        if (lowerUrl.contains(":mysql:")) {
            return "com.mysql.cj.jdbc.Driver";
        } else if (lowerUrl.contains(":postgresql:")) {
            return "org.postgresql.Driver";
        } else if (lowerUrl.contains(":oracle:")) {
            return "oracle.jdbc.OracleDriver";
        } else if (lowerUrl.contains(":sqlserver:")) {
            return "com.microsoft.sqlserver.jdbc.SQLServerDriver";
        } else if (lowerUrl.contains(":h2:")) {
            return "org.h2.Driver";
        } else if (lowerUrl.contains(":sqlite:")) {
            return "org.sqlite.JDBC";
        } else if (lowerUrl.contains(":dm:")) {
            return "dm.jdbc.driver.DmDriver";
        } else if (lowerUrl.contains(":kingbase:")) {
            return "com.kingbase8.Driver";
        }

        return null;
    }

    /**
     * 加载驱动类
     */
    private static void loadDriver(String driverClassName) {
        if (driverClassName != null && !driverClassName.isEmpty()) {
            try {
                Class.forName(driverClassName);
            } catch (ClassNotFoundException e) {
                log.warn("驱动类加载失败: {}", driverClassName);
            }
        }
    }

    /**
     * 创建 HikariCP 数据源
     */
    private static DataSource createHikariDataSource(String url, String username, String password,
                                                     String driverClassName, Map<String, Object> config) {
        try {
            Class<?> hikariConfigClass = Class.forName("com.zaxxer.hikari.HikariConfig");
            Class<?> hikariDataSourceClass = Class.forName("com.zaxxer.hikari.HikariDataSource");

            Object hikariConfig = hikariConfigClass.getDeclaredConstructor().newInstance();

            hikariConfigClass.getMethod("setJdbcUrl", String.class).invoke(hikariConfig, url);
            if (username != null) {
                hikariConfigClass.getMethod("setUsername", String.class).invoke(hikariConfig, username);
            }
            if (password != null) {
                hikariConfigClass.getMethod("setPassword", String.class).invoke(hikariConfig, password);
            }
            if (driverClassName != null) {
                hikariConfigClass.getMethod("setDriverClassName", String.class).invoke(hikariConfig, driverClassName);
            }

            int maxPoolSize = getInt(config, "maxPoolSize", 10);
            int minIdle = getInt(config, "minIdle", 2);
            long connectionTimeout = getLong(config, "connectionTimeout", 30000L);
            long idleTimeout = getLong(config, "idleTimeout", 600000L);

            hikariConfigClass.getMethod("setMaximumPoolSize", int.class).invoke(hikariConfig, maxPoolSize);
            hikariConfigClass.getMethod("setMinimumIdle", int.class).invoke(hikariConfig, minIdle);
            hikariConfigClass.getMethod("setConnectionTimeout", long.class).invoke(hikariConfig, connectionTimeout);
            hikariConfigClass.getMethod("setIdleTimeout", long.class).invoke(hikariConfig, idleTimeout);

            return (DataSource) hikariDataSourceClass.getDeclaredConstructor(hikariConfigClass)
                    .newInstance(hikariConfig);

        } catch (Exception e) {
            log.warn("HikariCP 创建失败，回退到简单数据源: {}", e.getMessage());
            return createSimpleDataSource(url, username, password);
        }
    }

    /**
     * 创建 Druid 数据源
     */
    private static DataSource createDruidDataSource(String url, String username, String password,
                                                    String driverClassName, Map<String, Object> config) {
        try {
            Class<?> druidDataSourceClass = Class.forName("com.alibaba.druid.pool.DruidDataSource");
            Object druidDataSource = druidDataSourceClass.getDeclaredConstructor().newInstance();

            druidDataSourceClass.getMethod("setUrl", String.class).invoke(druidDataSource, url);
            if (username != null) {
                druidDataSourceClass.getMethod("setUsername", String.class).invoke(druidDataSource, username);
            }
            if (password != null) {
                druidDataSourceClass.getMethod("setPassword", String.class).invoke(druidDataSource, password);
            }
            if (driverClassName != null) {
                druidDataSourceClass.getMethod("setDriverClassName", String.class).invoke(druidDataSource, driverClassName);
            }

            int maxActive = getInt(config, "maxPoolSize", 10);
            int minIdle = getInt(config, "minIdle", 2);
            int initialSize = getInt(config, "initialSize", 2);

            druidDataSourceClass.getMethod("setMaxActive", int.class).invoke(druidDataSource, maxActive);
            druidDataSourceClass.getMethod("setMinIdle", int.class).invoke(druidDataSource, minIdle);
            druidDataSourceClass.getMethod("setInitialSize", int.class).invoke(druidDataSource, initialSize);

            return (DataSource) druidDataSource;

        } catch (Exception e) {
            log.warn("Druid 创建失败，回退到简单数据源: {}", e.getMessage());
            return createSimpleDataSource(url, username, password);
        }
    }

    /**
     * 创建简单数据源（无连接池）
     */
    private static DataSource createSimpleDataSource(String url, String username, String password) {
        return new SimpleDataSource(url, username, password);
    }

    /**
     * 获取字符串配置
     */
    private static String getString(Map<String, Object> config, String key, String defaultValue) {
        Object value = config.get(key);
        if (value == null) {
            return defaultValue;
        }
        return value.toString();
    }

    /**
     * 获取整数配置
     */
    private static int getInt(Map<String, Object> config, String key, int defaultValue) {
        Object value = config.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 获取长整数配置
     */
    private static long getLong(Map<String, Object> config, String key, long defaultValue) {
        Object value = config.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 简单数据源实现（无连接池）
     */
    private static class SimpleDataSource implements DataSource {
        private final String url;
        private final String username;
        private final String password;
        private PrintWriter logWriter;
        private int loginTimeout = 0;

        public SimpleDataSource(String url, String username, String password) {
            this.url = url;
            this.username = username;
            this.password = password;
        }

        @Override
        public Connection getConnection() throws SQLException {
            return DriverManager.getConnection(url, username, password);
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return DriverManager.getConnection(url, username, password);
        }

        @Override
        public PrintWriter getLogWriter() {
            return logWriter;
        }

        @Override
        public void setLogWriter(PrintWriter out) {
            this.logWriter = out;
        }

        @Override
        public void setLoginTimeout(int seconds) {
            this.loginTimeout = seconds;
        }

        @Override
        public int getLoginTimeout() {
            return loginTimeout;
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            throw new SQLFeatureNotSupportedException();
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            if (iface.isInstance(this)) {
                return iface.cast(this);
            }
            throw new SQLException("Cannot unwrap to " + iface.getName());
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return iface.isInstance(this);
        }
    }
}
