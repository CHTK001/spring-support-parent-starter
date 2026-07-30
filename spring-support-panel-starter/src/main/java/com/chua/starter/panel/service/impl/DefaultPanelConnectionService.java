package com.chua.starter.panel.service.impl;

import com.chua.common.support.utils.StringUtils;
import com.chua.common.support.data.datasource.jdbc.JdbcDataSourceFactory;
import com.chua.starter.panel.cache.PanelConnectionCache;
import com.chua.starter.panel.model.PanelConnectionDefinition;
import com.chua.starter.panel.model.PanelConnectionDescriptor;
import com.chua.starter.panel.model.PanelConnectionHandle;
import com.chua.starter.panel.model.PanelConnectionType;
import com.chua.starter.panel.service.PanelConnectionService;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * 默认连接服务。
 *
 * @author CH
 */
public class DefaultPanelConnectionService implements PanelConnectionService {

    private static final String ATTR_DIALECT_TYPE = "panelDialectType";
    private static final String ATTR_DRIVER_CLASS_NAME = "panelDriverClassName";
    private static final String ATTR_DRIVER_JAR_PATH = "panelDriverJarPath";

    private final PanelConnectionCache panelConnectionCache;

    public DefaultPanelConnectionService(PanelConnectionCache panelConnectionCache) {
        this.panelConnectionCache = panelConnectionCache;
    }

    @Override
    public void evict(String connectionId) {
        panelConnectionCache.evict(connectionId);
    }

    @Override
    public List<PanelConnectionDescriptor> listCachedConnections() {
        return panelConnectionCache.handles().stream()
                .sorted(Comparator.comparing(
                        PanelConnectionHandle::getLastAccessTime,
                        Comparator.nullsLast(Comparator.reverseOrder())
                ))
                .map(handle -> PanelConnectionDescriptor.builder()
                        .connectionId(handle.getConnectionId())
                        .connectionName(handle.getDefinition() == null ? null : handle.getDefinition().getConnectionName())
                        .connectionType(handle.getDefinition() == null ? null : handle.getDefinition().getConnectionType())
                        .enabled(handle.getDefinition() != null && handle.getDefinition().isEnabled())
                        .cached(true)
                        .lastAccessTime(handle.getLastAccessTime())
                        .build())
                .toList();
    }

    @Override
    public PanelConnectionHandle open(PanelConnectionDefinition definition) {
        LocalDateTime now = LocalDateTime.now();
        if (StringUtils.isBlank(definition.getConnectionId())) {
            definition.setConnectionId(UUID.randomUUID().toString());
        }
        if (StringUtils.isBlank(definition.getConnectionName())) {
            definition.setConnectionName(definition.getHost() + ":" + definition.getPort());
        }
        Object nativeConnection = null;
        if (definition.getConnectionType() == PanelConnectionType.JDBC) {
            nativeConnection = createJdbcDataSource(definition);
        }
        PanelConnectionHandle handle = PanelConnectionHandle.builder()
                .connectionId(definition.getConnectionId())
                .definition(definition)
                .nativeConnection(nativeConnection)
                .createdTime(now)
                .lastAccessTime(now)
                .build();
        return panelConnectionCache.put(handle);
    }

    @Override
    public PanelConnectionHandle get(String connectionId) {
        return panelConnectionCache.touch(connectionId);
    }

    private DataSource createJdbcDataSource(PanelConnectionDefinition definition) {
        String jdbcUrl = sanitizeJdbcUrl(resolveJdbcUrl(definition), definition);
        String driverClassName = resolveDriverClassName(definition);
        String driverJarPath = resolveDriverJarPath(definition);
        if (StringUtils.isNotBlank(driverJarPath) && StringUtils.isNotBlank(driverClassName)) {
            return createExternalDriverDataSource(jdbcUrl, definition, driverClassName, driverJarPath);
        }
        try {
            DataSource dataSource = JdbcDataSourceFactory.createDataSource(
                    jdbcUrl,
                    definition.getUsername(),
                    definition.getPassword(),
                    driverClassName,
                    5
            );
            if (dataSource != null) {
                return dataSource;
            }
        } catch (Exception ignored) {
            // fallback to HikariDataSource for standalone panel runtime
        }
        try {
            return createHikariDataSource(jdbcUrl, definition, driverClassName);
        } catch (RuntimeException exception) {
            if (!shouldRetryWithoutDatabase(exception, definition)) {
                throw exception;
            }
            return createHikariDataSource(buildServerJdbcUrl(definition), definition, driverClassName);
        }
    }

    private String resolveJdbcUrl(PanelConnectionDefinition definition) {
        String protocol = StringUtils.trim(definition.getProtocol());
        if (StringUtils.isBlank(protocol)) {
            return buildJdbcUrl(definition);
        }
        if (protocol.startsWith("jdbc:")) {
            return protocol;
        }
        return buildJdbcUrl(definition);
    }

    /**
     * 清洗 JDBC URL 中的字符集配置，避免把 MySQL 的 {@code utf8mb4} 误用为 Java Charset 名称。
     *
     * @param jdbcUrl JDBC URL
     * @param definition 连接定义
     * @return 清洗后的 JDBC URL
     */
    private String sanitizeJdbcUrl(String jdbcUrl, PanelConnectionDefinition definition) {
        if (StringUtils.isBlank(jdbcUrl)) {
            return jdbcUrl;
        }
        String lower = jdbcUrl.toLowerCase(Locale.ROOT);
        boolean mysqlLike = lower.startsWith("jdbc:mysql:") || lower.startsWith("jdbc:mariadb:")
                || lower.startsWith("jdbc:p6spy:mysql:") || lower.startsWith("jdbc:p6spy:mariadb:");
        if (!mysqlLike) {
            return jdbcUrl;
        }
        // MySQL Connector/J 的 characterEncoding 需要是 Java Charset 名称；utf8mb4 不是合法的 Java Charset。
        // 兼容面板里手工填入的 characterEncoding=utf8mb4（或大小写混用）。
        return jdbcUrl.replaceAll("(?i)characterEncoding=utf8mb4", "characterEncoding=UTF-8");
    }

    private DataSource createHikariDataSource(String jdbcUrl, PanelConnectionDefinition definition, String driverClassName) {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(jdbcUrl);
        hikariConfig.setUsername(definition.getUsername());
        hikariConfig.setPassword(definition.getPassword());
        hikariConfig.setMaximumPoolSize(5);
        hikariConfig.setMinimumIdle(1);
        hikariConfig.setPoolName("panel-" + definition.getConnectionId());
        if (StringUtils.isNotBlank(driverClassName)) {
            hikariConfig.setDriverClassName(driverClassName);
        }
        return new HikariDataSource(hikariConfig);
    }

    private String buildJdbcUrl(PanelConnectionDefinition definition) {
        if (StringUtils.isBlank(definition.getHost()) || definition.getPort() == null) {
            throw new IllegalArgumentException("JDBC 连接缺少 host/port 或 protocol");
        }
        String databaseName = StringUtils.trim(definition.getDatabaseName());
        String host = definition.getHost().trim();
        int port = definition.getPort();
        return switch (resolveDialectType(definition)) {
            case "ORACLE" -> StringUtils.isBlank(databaseName)
                    ? "jdbc:oracle:thin:@" + host + ":" + port
                    : "jdbc:oracle:thin:@//" + host + ":" + port + "/" + databaseName;
            case "POSTGRESQL" -> "jdbc:postgresql://" + host + ":" + port
                    + (StringUtils.isBlank(databaseName) ? "" : "/" + databaseName);
            case "SQLSERVER" -> "jdbc:sqlserver://" + host + ":" + port
                    + (StringUtils.isBlank(databaseName) ? "" : ";databaseName=" + databaseName);
            case "CLICKHOUSE" -> "jdbc:clickhouse://" + host + ":" + port
                    + (StringUtils.isBlank(databaseName) ? "" : "/" + databaseName);
            case "MARIADB" -> "jdbc:mariadb://" + host + ":" + port
                    + (StringUtils.isBlank(databaseName) ? "" : "/" + databaseName);
            default -> "jdbc:mysql://" + host + ":" + port
                    + (StringUtils.isBlank(databaseName) ? "" : "/" + databaseName);
        };
    }

    private String buildServerJdbcUrl(PanelConnectionDefinition definition) {
        String host = definition.getHost().trim();
        int port = definition.getPort();
        return switch (resolveDialectType(definition)) {
            case "ORACLE" -> "jdbc:oracle:thin:@" + host + ":" + port;
            case "POSTGRESQL" -> "jdbc:postgresql://" + host + ":" + port;
            case "SQLSERVER" -> "jdbc:sqlserver://" + host + ":" + port;
            case "CLICKHOUSE" -> "jdbc:clickhouse://" + host + ":" + port;
            case "MARIADB" -> "jdbc:mariadb://" + host + ":" + port;
            default -> "jdbc:mysql://" + host + ":" + port;
        };
    }

    private boolean shouldRetryWithoutDatabase(RuntimeException exception, PanelConnectionDefinition definition) {
        if (StringUtils.isBlank(definition.getDatabaseName())) {
            return false;
        }
        Throwable current = exception;
        while (current != null) {
            if (current instanceof SQLException sqlException) {
                String message = sqlException.getMessage();
                String lowerCaseMessage = message == null ? "" : message.toLowerCase(Locale.ROOT);
                if (lowerCaseMessage.contains("unknown database")
                        || lowerCaseMessage.contains("database") && lowerCaseMessage.contains("does not exist")
                        || lowerCaseMessage.contains("fatal") && lowerCaseMessage.contains("database")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    private String resolveDialectType(PanelConnectionDefinition definition) {
        String protocol = trimToNull(definition.getProtocol());
        if (StringUtils.isNotBlank(protocol) && protocol.startsWith("jdbc:")) {
            String normalizedProtocol = protocol.substring("jdbc:".length()).toLowerCase(Locale.ROOT);
            // 以 URL 为准：避免历史 attributes 的方言类型带偏（例如误标为 H2 导致分页/语法判断异常）
            if (normalizedProtocol.startsWith("mysql:") || normalizedProtocol.startsWith("p6spy:mysql:")) {
                return "MYSQL";
            }
            if (normalizedProtocol.startsWith("mariadb:") || normalizedProtocol.startsWith("p6spy:mariadb:")) {
                return "MARIADB";
            }
            if (normalizedProtocol.startsWith("oracle:")) {
                return "ORACLE";
            }
            if (normalizedProtocol.startsWith("postgresql:")) {
                return "POSTGRESQL";
            }
            if (normalizedProtocol.startsWith("sqlserver:")) {
                return "SQLSERVER";
            }
            if (normalizedProtocol.startsWith("clickhouse:")) {
                return "CLICKHOUSE";
            }
        }
        String configuredDialectType = trimToNull(attributeValue(definition, ATTR_DIALECT_TYPE));
        if (StringUtils.isNotBlank(configuredDialectType)) {
            return configuredDialectType.toUpperCase(Locale.ROOT);
        }
        return "MYSQL";
    }

    private String resolveDriverClassName(PanelConnectionDefinition definition) {
        String configuredDriverClassName = trimToNull(attributeValue(definition, ATTR_DRIVER_CLASS_NAME));
        if (StringUtils.isNotBlank(configuredDriverClassName)) {
            return configuredDriverClassName;
        }
        return switch (resolveDialectType(definition)) {
            case "ORACLE" -> "oracle.jdbc.OracleDriver";
            case "POSTGRESQL" -> "org.postgresql.Driver";
            case "SQLSERVER" -> "com.microsoft.sqlserver.jdbc.SQLServerDriver";
            case "CLICKHOUSE" -> "com.clickhouse.jdbc.ClickHouseDriver";
            case "MARIADB" -> "org.mariadb.jdbc.Driver";
            default -> "com.mysql.cj.jdbc.Driver";
        };
    }

    private String resolveDriverJarPath(PanelConnectionDefinition definition) {
        return trimToNull(attributeValue(definition, ATTR_DRIVER_JAR_PATH));
    }

    private String attributeValue(PanelConnectionDefinition definition, String key) {
        Map<String, Object> attributes = definition.getAttributes();
        if (attributes == null || !attributes.containsKey(key)) {
            return null;
        }
        Object value = attributes.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private String trimToNull(String value) {
        return StringUtils.isBlank(value) ? null : value.trim();
    }

    private DataSource createExternalDriverDataSource(
            String jdbcUrl,
            PanelConnectionDefinition definition,
            String driverClassName,
            String driverJarPath) {
        try {
            URL driverUrl = Paths.get(driverJarPath).toUri().toURL();
            URLClassLoader classLoader = new URLClassLoader(new URL[]{driverUrl}, getClass().getClassLoader());
            Class<?> driverType = Class.forName(driverClassName, true, classLoader);
            Driver driver = (Driver) driverType.getDeclaredConstructor().newInstance();
            return new DriverBackedDataSource(jdbcUrl, definition.getUsername(), definition.getPassword(), driver);
        } catch (Exception exception) {
            throw new IllegalStateException("加载 JDBC 驱动失败: " + exception.getMessage(), exception);
        }
    }

    private static final class DriverBackedDataSource implements DataSource {

        private final String jdbcUrl;
        private final String username;
        private final String password;
        private final Driver driver;

        private DriverBackedDataSource(String jdbcUrl, String username, String password, Driver driver) {
            this.jdbcUrl = jdbcUrl;
            this.username = username;
            this.password = password;
            this.driver = driver;
        }

        @Override
        public Connection getConnection() throws SQLException {
            return getConnection(username, password);
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            Properties properties = new Properties();
            if (StringUtils.isNotBlank(username)) {
                properties.setProperty("user", username);
            }
            if (password != null) {
                properties.setProperty("password", password);
            }
            Connection connection = driver.connect(jdbcUrl, properties);
            if (connection == null) {
                throw new SQLException("驱动未接受 JDBC URL: " + jdbcUrl);
            }
            return connection;
        }

        @Override
        public PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(PrintWriter out) {
            // no-op
        }

        @Override
        public void setLoginTimeout(int seconds) {
            // no-op
        }

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            throw new SQLFeatureNotSupportedException("Not supported");
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            if (iface.isInstance(this)) {
                return iface.cast(this);
            }
            throw new SQLException("Unsupported unwrap type: " + iface);
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return iface.isInstance(this);
        }
    }
}
