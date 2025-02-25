package com.chua.tenant.support.configuration;

import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.chua.common.support.datasource.dialect.Dialect;
import com.chua.common.support.datasource.dialect.DialectFactory;
import com.chua.common.support.datasource.meta.Column;
import com.chua.common.support.datasource.meta.Table;
import com.chua.common.support.datasource.type.JavaType;
import com.chua.common.support.lang.engine.Engine;
import com.chua.common.support.lang.engine.JdbcEngine;
import com.chua.common.support.lang.engine.datasource.JdbcEngineDataSource;
import com.chua.common.support.lang.engine.ddl.ActionType;
import com.chua.common.support.lang.engine.ddl.EngineTable;
import com.chua.common.support.utils.CollectionUtils;
import com.chua.starter.common.support.utils.RequestUtils;
import com.chua.tenant.support.properties.TenantProperties;
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
 * 租户配置类
 * @author CH
 * @since 2024/9/11
 */
@EnableConfigurationProperties(TenantProperties.class)
public class TenantConfiguration implements EnvironmentAware, BeanClassLoaderAware {

    private ClassLoader classLoader;
    List<DataSource> dataSourceList = new LinkedList<>();
    private TenantProperties tenantProperties;

    @Bean
    @ConditionalOnProperty(prefix = TenantProperties.PRE, name = "enable", havingValue = "true")
    public TenantLineInnerInterceptor tenantLineInnerInterceptor(TenantProperties tenantProperties) {
        return new TenantLineInnerInterceptor(new TenantLineHandler() {
            @Override
            public Expression getTenantId() {
                String tenantId = RequestUtils.getTenantId();
                if(null == tenantId) {
                    return new LongValue(-1);
                }

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
                    return true;
                }
                Set<String> ignoreTable = tenantProperties.getIgnoreTable();
                return CollectionUtils.containsIgnoreCase(ignoreTable, tableName);
            }
        });
    }

    @Override
    public void setEnvironment(Environment environment) {
        tenantProperties = Binder.get(environment).bindOrCreate(TenantProperties.PRE, TenantProperties.class);
        if(!tenantProperties.isEnable()) {
            return;
        }
        DataSourceProperties dataSourceProperties = Binder.get(environment).bindOrCreate("spring.datasource", DataSourceProperties.class);
        DataSource dataSource = DataSourceBuilder.create()
                .type(dataSourceProperties.getType())
                .driverClassName(dataSourceProperties.getDriverClassName())
                .url(dataSourceProperties.getUrl())
                .username(dataSourceProperties.getUsername())
                .password(dataSourceProperties.getPassword())
                .build();

        autoTenantColumn(dataSource, dataSourceProperties.determineUrl());
    }

    public void autoTenantColumn(DataSource dataSource, String url) {
        Map<String, List<String>> columnNames = new HashMap<>();
        Dialect dialect = null;
        try (Connection connection = dataSource.getConnection()) {
            dialect = DialectFactory.create(connection);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        Engine engine = new JdbcEngine();
        engine.addDataSource(JdbcEngineDataSource.builder()
                        .dataSource(dataSource)
                .build()
        );
        String databaseName = null;
        try (Connection connection = dataSource.getConnection()){
            databaseName = dialect.getDatabaseName(url);
            DatabaseMetaData metaData = connection.getMetaData();
            registerColumn(columnNames, metaData, databaseName);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        for (Map.Entry<String, List<String>> entry : columnNames.entrySet()) {
            boolean hasTenantColumn = check(entry.getValue());
            if(!hasTenantColumn) {
                updateTable(engine, databaseName, entry.getKey());
            }
        }
    }

    private void updateTable(Engine engine, String databaseName, String tableName) {
        if(engine instanceof EngineTable engineTable) {
            Table table = new Table();
            table.setTableName(tableName);
            table.setCatalog(databaseName);
            Column column = new Column();
            column.setNodeId(tenantProperties.getTenantId());
            column.setJavaType(new JavaType(Integer.class, null));
            column.setComment("租户ID");
            column.setNullable(true);
            column.setIndex(true);
            column.setUpdatable(false);

            table.addColumn(column);
            engineTable.createTable(null, "hibernate")
                    .doIt(table, ActionType.UPDATE);
        }
    }

    private boolean check(List<String> value) {
        return CollectionUtils.containsIgnoreCase(value, tenantProperties.getTenantId());
    }

    /**
     * 注册数据库列信息
     * 该方法用于从数据库的元数据中提取特定数据库名称的列信息，并将其存储在映射中
     * 映射的键是表名，值是该表的所有列名列表
     *
     * @param columnNames   存储列名的映射，其中键为表名，值为该表的列名列表
     * @param metaData      数据库元数据，用于获取列信息
     * @param databaseName  要注册其列信息的数据库名称
     * 注意：该方法的具体实现应处理异常情况，如元数据不可用或数据库名称无效
     */
    private void registerColumn(Map<String, List<String>> columnNames, DatabaseMetaData metaData, String databaseName)throws Exception  {
        try (ResultSet resultSet = metaData.getColumns(databaseName, null, "%", "%")) {
            while (resultSet.next()) {
                while (resultSet.next()) {
                    columnNames.computeIfAbsent(resultSet.getString("TABLE_NAME"), it -> new LinkedList<>())
                            .add(resultSet.getString("COLUMN_NAME"));
                }
            }
        }
    }


    private void registerTable(List<String> tableNames, DatabaseMetaData metaData, String databaseName) throws Exception {
        try (ResultSet resultSet = metaData.getTables(databaseName, null, "%", new String[]{"TABLE"})) {
            while (resultSet.next()) {
                tableNames.add(resultSet.getString("TABLE_NAME"));
            }
        }
    }


    @Override
    public void setBeanClassLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }
}
