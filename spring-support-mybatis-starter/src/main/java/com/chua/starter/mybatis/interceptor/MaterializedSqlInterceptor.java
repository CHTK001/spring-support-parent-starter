package com.chua.starter.mybatis.interceptor;

import com.chua.common.support.data.materialized.MaterializedRouteDefinition;
import com.chua.common.support.data.materialized.MaterializedSqlCommandType;
import com.chua.common.support.data.materialized.MaterializedSqlDataSourceRouter;
import com.chua.common.support.data.materialized.MaterializedSqlRequest;
import com.chua.common.support.data.materialized.MaterializedSqlRoute;
import com.chua.starter.datasource.annotation.MaterializedRefresh;
import com.chua.starter.datasource.annotation.MaterializedRoute;
import com.chua.starter.datasource.properties.MaterializedRouteProperties;
import com.chua.starter.datasource.support.DataSourceContextSupport;
import com.chua.starter.datasource.materialized.MaterializedRefreshContext;
import com.chua.starter.datasource.materialized.MaterializedRouteContext;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.util.TablesNamesFinder;
import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.type.TypeHandlerRegistry;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.Ordered;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MyBatis SQL 物理化拦截器。
 *
 * @author CH
 * @since 2026/4/2
 */
@Intercepts({
        @Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class}),
        @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}),
        @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class, CacheKey.class, BoundSql.class}),
})
@Slf4j
public class MaterializedSqlInterceptor implements Interceptor {

    private static final MaterializedRouteDefinition NONE = new MaterializedRouteDefinition(-1L, null);

    private final MaterializedSqlDataSourceRouter materializedSqlRouter;
    private final MaterializedRouteProperties properties;
    private final Map<String, MaterializedRouteDefinition> definitionCache = new ConcurrentHashMap<>();
    private final Map<String, MaterializedRouteDefinition> refreshDefinitionCache = new ConcurrentHashMap<>();

    public MaterializedSqlInterceptor(MaterializedSqlDataSourceRouter materializedSqlRouter,
                                      MaterializedRouteProperties properties) {
        this.materializedSqlRouter = materializedSqlRouter;
        this.properties = properties;
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        if (!(invocation.getTarget() instanceof Executor)) {
            return invocation.proceed();
        }

        Object[] args = invocation.getArgs();
        if (args.length == 0 || !(args[0] instanceof MappedStatement mappedStatement)) {
            return invocation.proceed();
        }

        MaterializedRouteDefinition definition = resolveDefinition(mappedStatement);
        MaterializedRouteDefinition refreshDefinition = resolveRefreshDefinition(mappedStatement);
        if (definition == null && refreshDefinition == null) {
            return invocation.proceed();
        }

        Object parameter = args.length > 1 ? args[1] : null;
        BoundSql boundSql = resolveBoundSql(mappedStatement, args, parameter);
        MaterializedSqlRequest request = buildRequest(mappedStatement, boundSql, parameter, definition != null ? definition : refreshDefinition);
        if (request == null || CollectionUtils.isEmpty(request.getTables())) {
            return invocation.proceed();
        }

        if (definition != null && request.getCommandType() == MaterializedSqlCommandType.SELECT) {
            return interceptQuery(invocation, request);
        }

        Object result = invocation.proceed();
        if (definition != null && request.getCommandType() != MaterializedSqlCommandType.SELECT) {
            materializedSqlRouter.onWriteSuccess(request);
        }
        if (refreshDefinition != null) {
            materializedSqlRouter.onWriteSuccess(rebuildRequest(request, refreshDefinition, MaterializedSqlCommandType.UPDATE));
        }
        return result;
    }

    private Object interceptQuery(Invocation invocation, MaterializedSqlRequest request) throws Throwable {
        MaterializedSqlRoute route = materializedSqlRouter.route(request);
        if (!route.isRouted()) {
            return invocation.proceed();
        }
        DataSource routedDataSource = materializedSqlRouter.resolveDataSource(route);
        if (routedDataSource == null) {
            return invocation.proceed();
        }

        String originalDataSource = DataSourceContextSupport.getDbType();
        try {
            DataSourceContextSupport.addDatasource(route.getDataSource(), routedDataSource);
            DataSourceContextSupport.setDbType(route.getDataSource());
            return invocation.proceed();
        } catch (Throwable ex) {
            log.debug("[MyBatis][Materialized] 内存副本执行失败，回退源库: {}", ex.getMessage());
            restoreDataSource(originalDataSource);
            return invocation.proceed();
        } finally {
            restoreDataSource(originalDataSource);
        }
    }

    private void restoreDataSource(String originalDataSource) {
        if (StringUtils.hasText(originalDataSource)) {
            DataSourceContextSupport.setDbType(originalDataSource);
            return;
        }
        DataSourceContextSupport.clearDbType();
    }

    private MaterializedSqlRequest buildRequest(MappedStatement mappedStatement,
                                                BoundSql boundSql,
                                                Object parameter,
                                                MaterializedRouteDefinition definition) {
        String sql = boundSql.getSql();
        if (!StringUtils.hasText(sql)) {
            return null;
        }

        MaterializedSqlCommandType commandType = switch (mappedStatement.getSqlCommandType()) {
            case SELECT -> MaterializedSqlCommandType.SELECT;
            case INSERT -> MaterializedSqlCommandType.INSERT;
            case UPDATE -> MaterializedSqlCommandType.UPDATE;
            case DELETE -> MaterializedSqlCommandType.DELETE;
            default -> MaterializedSqlCommandType.OTHER;
        };

        List<String> tables = parseTables(sql);
        if (tables.isEmpty()) {
            return null;
        }

        MaterializedSqlRequest.MaterializedSqlRequestBuilder builder = MaterializedSqlRequest.builder()
                .statementId(mappedStatement.getId())
                .sourceDataSource(DataSourceContextSupport.getDbType())
                .sql(sql)
                .commandType(commandType)
                .definition(definition);

        for (String table : tables) {
            builder.table(table);
        }
        for (Object item : resolveParameters(mappedStatement.getConfiguration(), boundSql, parameter)) {
            builder.parameter(item);
        }
        return builder.build();
    }

    private BoundSql resolveBoundSql(MappedStatement mappedStatement, Object[] args, Object parameter) {
        if (args.length == 6 && args[5] instanceof BoundSql boundSql) {
            return boundSql;
        }
        return mappedStatement.getBoundSql(parameter);
    }

    private MaterializedSqlRequest rebuildRequest(MaterializedSqlRequest request,
                                                  MaterializedRouteDefinition definition,
                                                  MaterializedSqlCommandType commandType) {
        MaterializedSqlRequest.MaterializedSqlRequestBuilder builder = MaterializedSqlRequest.builder()
                .statementId(request.getStatementId())
                .sourceDataSource(request.getSourceDataSource())
                .sql(request.getSql())
                .commandType(commandType)
                .definition(definition);
        for (String table : request.getTables()) {
            builder.table(table);
        }
        for (Object parameter : request.getParameters()) {
            builder.parameter(parameter);
        }
        return builder.build();
    }

    private List<Object> resolveParameters(Configuration configuration, BoundSql boundSql, Object parameterObject) {
        List<Object> values = new ArrayList<>();
        List<ParameterMapping> parameterMappings = boundSql.getParameterMappings();
        if (CollectionUtils.isEmpty(parameterMappings) || parameterObject == null) {
            return values;
        }

        TypeHandlerRegistry typeHandlerRegistry = configuration.getTypeHandlerRegistry();
        if (typeHandlerRegistry.hasTypeHandler(parameterObject.getClass())) {
            values.add(parameterObject);
            return values;
        }

        MetaObject metaObject = configuration.newMetaObject(parameterObject);
        for (ParameterMapping parameterMapping : parameterMappings) {
            String propertyName = parameterMapping.getProperty();
            if (metaObject.hasGetter(propertyName)) {
                values.add(metaObject.getValue(propertyName));
                continue;
            }
            if (boundSql.hasAdditionalParameter(propertyName)) {
                values.add(boundSql.getAdditionalParameter(propertyName));
                continue;
            }
            values.add(null);
        }
        return values;
    }

    private List<String> parseTables(String sql) {
        try {
            Statement statement = CCJSqlParserUtil.parse(sql);
            TablesNamesFinder finder = new TablesNamesFinder();
            return new ArrayList<>(new LinkedHashSet<>(finder.getTableList(statement)));
        } catch (Exception e) {
            log.debug("[MyBatis][Materialized] SQL 解析失败，跳过物理化: {}", e.getMessage());
            return List.of();
        }
    }

    private MaterializedRouteDefinition resolveDefinition(MappedStatement mappedStatement) {
        MaterializedRouteDefinition current = MaterializedRouteContext.get();
        if (current != null) {
            return current;
        }

        MaterializedRouteDefinition cached = definitionCache.computeIfAbsent(mappedStatement.getId(), this::resolveDefinitionFromMapper);
        return cached == NONE ? null : cached;
    }

    private MaterializedRouteDefinition resolveRefreshDefinition(MappedStatement mappedStatement) {
        MaterializedRouteDefinition current = MaterializedRefreshContext.get();
        if (current != null) {
            return current;
        }

        MaterializedRouteDefinition cached = refreshDefinitionCache.computeIfAbsent(mappedStatement.getId(), this::resolveRefreshDefinitionFromMapper);
        return cached == NONE ? null : cached;
    }

    private MaterializedRouteDefinition resolveDefinitionFromMapper(String statementId) {
        int lastDot = statementId.lastIndexOf('.');
        if (lastDot < 0) {
            return NONE;
        }

        String className = statementId.substring(0, lastDot);
        String methodName = statementId.substring(lastDot + 1);
        try {
            Class<?> mapperType = Class.forName(className);
            for (Method method : mapperType.getMethods()) {
                if (!method.getName().equals(methodName)) {
                    continue;
                }
                MaterializedRoute route = AnnotatedElementUtils.findMergedAnnotation(method, MaterializedRoute.class);
                if (route != null) {
                    return new MaterializedRouteDefinition(
                            route.threshold() > 0 ? route.threshold() : properties.getDefaultThreshold(),
                            route.dataSource());
                }
            }

            MaterializedRoute route = AnnotatedElementUtils.findMergedAnnotation(mapperType, MaterializedRoute.class);
            if (route != null) {
                return new MaterializedRouteDefinition(
                        route.threshold() > 0 ? route.threshold() : properties.getDefaultThreshold(),
                        route.dataSource());
            }
        } catch (ClassNotFoundException e) {
            log.debug("[MyBatis][Materialized] 未找到 Mapper 类型: {}", className);
        }
        return NONE;
    }

    private MaterializedRouteDefinition resolveRefreshDefinitionFromMapper(String statementId) {
        int lastDot = statementId.lastIndexOf('.');
        if (lastDot < 0) {
            return NONE;
        }

        String className = statementId.substring(0, lastDot);
        String methodName = statementId.substring(lastDot + 1);
        try {
            Class<?> mapperType = Class.forName(className);
            for (Method method : mapperType.getMethods()) {
                if (!method.getName().equals(methodName)) {
                    continue;
                }
                MaterializedRefresh refresh = AnnotatedElementUtils.findMergedAnnotation(method, MaterializedRefresh.class);
                if (refresh != null) {
                    return new MaterializedRouteDefinition(
                            refresh.threshold() > 0 ? refresh.threshold() : properties.getDefaultThreshold(),
                            refresh.dataSource());
                }
            }

            MaterializedRefresh refresh = AnnotatedElementUtils.findMergedAnnotation(mapperType, MaterializedRefresh.class);
            if (refresh != null) {
                return new MaterializedRouteDefinition(
                        refresh.threshold() > 0 ? refresh.threshold() : properties.getDefaultThreshold(),
                        refresh.dataSource());
            }
        } catch (ClassNotFoundException e) {
            log.debug("[MyBatis][Materialized] 未找到 Mapper 类型: {}", className);
        }
        return NONE;
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {
    }
}
