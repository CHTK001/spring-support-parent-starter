package com.chua.tenant.support.configuration;

import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.chua.common.support.datasource.dialect.Dialect;
import com.chua.common.support.datasource.dialect.DialectFactory;
import com.chua.common.support.datasource.meta.ColumnMetadata;
import com.chua.common.support.datasource.meta.TableMetadata;
import com.chua.common.support.datasource.type.JavaType;
import com.chua.common.support.lang.engine.Engine;
import com.chua.common.support.lang.engine.JdbcEngine;
import com.chua.common.support.lang.engine.datasource.JdbcEngineDataSource;
import com.chua.common.support.lang.engine.ddl.ActionType;
import com.chua.common.support.lang.engine.ddl.EngineTable;
import com.chua.common.support.utils.CollectionUtils;
import com.chua.starter.common.support.utils.RequestUtils;
import com.chua.tenant.support.properties.TenantProperties;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * 租户配置�?
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/9/11
 */
@Slf4j
@EnableConfigurationProperties(TenantProperties.class)
public class TenantConfiguration implements EnvironmentAware, BeanClassLoaderAware {

    private ClassLoader classLoader;
    private TenantProperties tenantProperties;

    /**
     * 创建租户拦截�?
     * 自动在SQL中添加租户条�?
     *
     * @param tenantProperties 租户配置属�?
     * @return 租户拦截�?
     */
    @Bean
    @ConditionalOnProperty(prefix = TenantProperties.PRE, name = "enable", havingValue = "true")
    public TenantLineInnerInterceptor tenantLineInnerInterceptor(TenantProperties tenantProperties) {
        log.info("[租户插件] 初始化租户拦截器，租户字�? {}", tenantProperties.getTenantId());
        return new TenantLineInnerInterceptor(new TenantLineHandler() {
            @Override
            public Expression getTenantId() {
                String tenantId = RequestUtils.getTenantId();
                if (null == tenantId) {
                    log.debug("[租户插件] 未获取到租户ID，使用默认�?-1");
                    return new LongValue(-1);
                }
                log.debug("[租户插件] 当前租户ID: {}", tenantId);
                return new LongValue(tenantId);
            }

            @Override
            public String getTenantIdColumn() {
                return tenantProperties.getTenantId();
            }

            @Override
            public boolean ignoreTable(String tableName) {
                RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
                if (requestAttributes == null) {
                    log.debug("[租户插件] 非Web请求上下文，忽略�? {}", tableName);
                    return true;
                }
                Set<String> ignoreTable = tenantProperties.getIgnoreTable();
                boolean ignored = CollectionUtils.containsIgnoreCase(ignoreTable, tableName);
                if (ignored) {
                    log.debug("[租户插件] �?{} 在忽略列表中", tableName);
                }
                return ignored;
            }
        });
    }

    @Override
    public void setEnvironment(Environment environment) {
        tenantProperties = Binder.get(environment).bindOrCreate(TenantProperties.PRE, TenantProperties.class);
        if (!tenantProperties.isEnable()) {
            log.info("[租户插件] 租户功能未启�?);
            return;
        }

        log.info("[租户插件] 租户功能已启用，租户字段: {}", tenantProperties.getTenantId());

        if (!tenantProperties.isAutoAddColumn()) {
            log.info("[租户插件] 自动添加租户字段功能未启�?);
            return;
        }

        try {
            DataSourceProperties dataSourceProperties = Binder.get(environment).bindOrCreate("spring.datasource",
                    DataSourceProperties.class);
            DataSource dataSource = DataSourceBuilder.create()
                    .type(dataSourceProperties.getType())
                    .driverClassName(dataSourceProperties.getDriverClassName())
                    .url(dataSourceProperties.getUrl())
                    .username(dataSourceProperties.getUsername())
                    .password(dataSourceProperties.getPassword())
                    .build();

            log.info("[租户插件] 开始自动检测并添加租户字段");
            autoTenantColumn(dataSource, dataSourceProperties.determineUrl());
            log.info("[租户插件] 租户字段检测完�?);
        } catch (Exception e) {
            log.error("[租户插件] 自动添加租户字段失败", e);
        }
    }

    /**
     * 自动为数据库表添加租户字�?
     *
     * @param dataSource 数据�?
     * @param url        数据库连接URL
     */
    public void autoTenantColumn(DataSource dataSource, String url) {
        Map<String, List<String>> columnNames = new HashMap<>();
        Dialect dialect;
        try (Connection connection = dataSource.getConnection()) {
            dialect = DialectFactory.create(connection);
            log.debug("[租户插件] 数据库方言: {}", dialect.getClass().getSimpleName());
        } catch (SQLException e) {
            log.error("[租户插件] 获取数据库连接失�?, e);
            throw new RuntimeException("获取数据库连接失�?, e);
        }

        Engine engine = new JdbcEngine();
        engine.addDataSource(JdbcEngineDataSource.builder()
                .dataSource(dataSource)
                .build());

        String databaseName;
        try (Connection connection = dataSource.getConnection()) {
            databaseName = dialect.getDatabaseName(url);
            log.info("[租户插件] 数据库名�? {}", databaseName);
            DatabaseMetaData metaData = connection.getMetaData();
            registerColumn(columnNames, metaData, databaseName);
            log.info("[租户插件] 共扫描到 {} 张表", columnNames.size());
        } catch (Exception e) {
            log.error("[租户插件] 读取数据库元数据失败", e);
            throw new RuntimeException("读取数据库元数据失败", e);
        }

        int addedCount = 0;
        for (Map.Entry<String, List<String>> entry : columnNames.entrySet()) {
            String tableName = entry.getKey();
            boolean hasTenantColumn = check(entry.getValue());
            if (!hasTenantColumn) {
                if (shouldIgnoreTable(tableName)) {
                    log.debug("[租户插件] �?{} 在忽略列表中，跳过添加租户字�?, tableName);
                    continue;
                }
                log.info("[租户插件] �?{} 缺少租户字段，开始添�?, tableName);
                updateTable(engine, databaseName, tableName);
                addedCount++;
            } else {
                log.debug("[租户插件] �?{} 已存在租户字�?, tableName);
            }
        }
        log.info("[租户插件] 共为 {} 张表添加了租户字�?, addedCount);
    }

    /**
     * 更新表结构，添加租户字段
     *
     * @param engine       数据库引�?
     * @param databaseName 数据库名�?
     * @param tableName    表名
     */
    private void updateTable(Engine engine, String databaseName, String tableName) {
        if (engine instanceof EngineTable engineTable) {
            try {
                TableMetadata table = new TableMetadata();
                table.setTableName(tableName);
                table.setCatalog(databaseName);

                ColumnMetadata column = new ColumnMetadata();
                column.setColumnName(tenantProperties.getTenantId());
                column.setJavaType(new JavaType(Integer.class, null));
                column.setComment("租户ID");
                column.setNullable(true);
                column.setIndex(true);
                column.setUpdatable(false);

                table.addColumn(column);
                engineTable.createTable(null, "hibernate")
                        .doIt(table, ActionType.UPDATE);
                log.info("[租户插件] 成功为表 {} 添加租户字段 {}", tableName, tenantProperties.getTenantId());
            } catch (Exception e) {
                log.error("[租户插件] 为表 {} 添加租户字段失败", tableName, e);
            }
        } else {
            log.warn("[租户插件] 引擎不支持表操作，无法添加租户字�?);
        }
    }

    /**
     * 检查列列表中是否包含租户字�?
     *
     * @param columnList 列名列表
     * @return 是否包含租户字段
     */
    private boolean check(List<String> columnList) {
        return CollectionUtils.containsIgnoreCase(columnList, tenantProperties.getTenantId());
    }

    /**
     * 判断表是否应该被忽略
     *
     * @param tableName 表名
     * @return 是否忽略
     */
    private boolean shouldIgnoreTable(String tableName) {
        Set<String> ignoreTable = tenantProperties.getIgnoreTable();
        return CollectionUtils.containsIgnoreCase(ignoreTable, tableName);
    }

    /**
     * 注册数据库列信息
     * 该方法用于从数据库的元数据中提取特定数据库名称的列信息，并将其存储在映射�?
     * 映射的键是表名，值是该表的所有列名列�?
     *
     * @param columnNames  存储列名的映射，其中键为表名，值为该表的列名列�?
     * @param metaData     数据库元数据，用于获取列信息
     * @param databaseName 要注册其列信息的数据库名�?
     * @throws Exception 读取元数据异�?
     */
    private void registerColumn(Map<String, List<String>> columnNames, DatabaseMetaData metaData, String databaseName)
            throws Exception {
        try (ResultSet resultSet = metaData.getColumns(databaseName, null, "%", "%")) {
            while (resultSet.next()) {
                String tableName = resultSet.getString("TABLE_NAME");
                String columnName = resultSet.getString("COLUMN_NAME");
                columnNames.computeIfAbsent(tableName, it -> new LinkedList<>())
                        .add(columnName);
            }
        }
    }

    // 租户同步功能已迁移到 spring-support-sync-starter
    // 通过 SPI 机制加载 TenantSyncMessageHandler 处理租户相关主题
    // 配置请参�? plugin.sync.* �?plugin.mybatis-plus.tenant.tenant-sync.*

    @Override
    public void setBeanClassLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }
}
