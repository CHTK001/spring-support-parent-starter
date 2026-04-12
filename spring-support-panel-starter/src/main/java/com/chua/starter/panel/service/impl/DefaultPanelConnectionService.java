package com.chua.starter.panel.service.impl;

import com.chua.common.support.core.utils.StringUtils;
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
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * 默认连接服务。
 */
public class DefaultPanelConnectionService implements PanelConnectionService {

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
        String jdbcUrl = definition.getProtocol();
        if (StringUtils.isBlank(jdbcUrl)) {
            jdbcUrl = buildJdbcUrl(definition);
        }
        try {
            DataSource dataSource = JdbcDataSourceFactory.createDataSource(
                    jdbcUrl,
                    definition.getUsername(),
                    definition.getPassword(),
                    null,
                    5
            );
            if (dataSource != null) {
                return dataSource;
            }
        } catch (Exception ignored) {
            // fallback to HikariDataSource for standalone panel runtime
        }
        try {
            return createHikariDataSource(jdbcUrl, definition);
        } catch (RuntimeException exception) {
            if (!shouldRetryWithoutDatabase(exception, definition)) {
                throw exception;
            }
            return createHikariDataSource(buildServerJdbcUrl(definition), definition);
        }
    }

    private DataSource createHikariDataSource(String jdbcUrl, PanelConnectionDefinition definition) {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(jdbcUrl);
        hikariConfig.setUsername(definition.getUsername());
        hikariConfig.setPassword(definition.getPassword());
        hikariConfig.setMaximumPoolSize(5);
        hikariConfig.setMinimumIdle(1);
        hikariConfig.setPoolName("panel-" + definition.getConnectionId());
        if (jdbcUrl.startsWith("jdbc:mysql:")) {
            hikariConfig.setDriverClassName("com.mysql.cj.jdbc.Driver");
        }
        return new HikariDataSource(hikariConfig);
    }

    private String buildJdbcUrl(PanelConnectionDefinition definition) {
        if (StringUtils.isBlank(definition.getHost()) || definition.getPort() == null) {
            throw new IllegalArgumentException("JDBC 连接缺少 host/port 或 protocol");
        }
        String databaseName = StringUtils.isBlank(definition.getDatabaseName()) ? "" : "/" + definition.getDatabaseName();
        return "jdbc:mysql://" + definition.getHost() + ":" + definition.getPort() + databaseName;
    }

    private String buildServerJdbcUrl(PanelConnectionDefinition definition) {
        return "jdbc:mysql://" + definition.getHost() + ":" + definition.getPort();
    }

    private boolean shouldRetryWithoutDatabase(RuntimeException exception, PanelConnectionDefinition definition) {
        if (StringUtils.isBlank(definition.getDatabaseName())) {
            return false;
        }
        Throwable current = exception;
        while (current != null) {
            if (current instanceof SQLException sqlException) {
                String message = sqlException.getMessage();
                if (message != null && message.contains("Unknown database")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }
}
