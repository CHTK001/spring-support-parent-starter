package com.chua.starter.mybatis.interceptor;

import com.chua.starter.datasource.annotation.DS;
import com.chua.starter.datasource.properties.MultiDataSourceSettingProperties;
import com.chua.starter.datasource.support.DataSourceContextSupport;
import com.chua.starter.datasource.support.DynamicDataSourceAspect;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.*;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 支持在 MyBatis Mapper 上解析 @DS。
 *
 * @author CH
 * @since 2026/4/2
 */
@Intercepts({
        @Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class}),
        @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, org.apache.ibatis.session.RowBounds.class, org.apache.ibatis.session.ResultHandler.class}),
        @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, org.apache.ibatis.session.RowBounds.class, org.apache.ibatis.session.ResultHandler.class, org.apache.ibatis.cache.CacheKey.class, org.apache.ibatis.mapping.BoundSql.class}),
})
@Slf4j
public class MapperDataSourceInterceptor implements Interceptor {

    private final ApplicationContext applicationContext;
    private final MultiDataSourceSettingProperties properties;
    private final Map<String, String> mapperDataSourceCache = new ConcurrentHashMap<>();

    public MapperDataSourceInterceptor(ApplicationContext applicationContext,
                                       MultiDataSourceSettingProperties properties) {
        this.applicationContext = applicationContext;
        this.properties = properties;
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        Object[] args = invocation.getArgs();
        if (args.length == 0 || !(args[0] instanceof MappedStatement mappedStatement)) {
            return invocation.proceed();
        }

        String mapperDataSource = mapperDataSourceCache.computeIfAbsent(mappedStatement.getId(), this::resolveMapperDataSource);
        if (!StringUtils.hasText(mapperDataSource)) {
            return invocation.proceed();
        }

        String originalDataSource = DataSourceContextSupport.getDbType();
        DynamicDataSourceAspect aspect = new DynamicDataSourceAspect();
        aspect.setApplicationContext(applicationContext);
        try {
            aspect.switchTo(mapperDataSource, properties);
            return invocation.proceed();
        } catch (Throwable ex) {
            log.debug("[MyBatis][DS] Mapper 数据源切换失败: {}", ex.getMessage());
            throw ex;
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
        DataSourceContextSupport.setDbType(DataSourceContextSupport.DEFAULT_DATASOURCE);
    }

    private String resolveMapperDataSource(String statementId) {
        int lastDot = statementId.lastIndexOf('.');
        if (lastDot < 0) {
            return "";
        }

        String className = statementId.substring(0, lastDot);
        String methodName = statementId.substring(lastDot + 1);
        try {
            Class<?> mapperType = Class.forName(className);
            for (Method method : mapperType.getMethods()) {
                if (!method.getName().equals(methodName)) {
                    continue;
                }
                DS ds = AnnotatedElementUtils.findMergedAnnotation(method, DS.class);
                if (ds != null && StringUtils.hasText(ds.value())) {
                    return ds.value();
                }
            }

            DS ds = AnnotatedElementUtils.findMergedAnnotation(mapperType, DS.class);
            if (ds != null && StringUtils.hasText(ds.value())) {
                return ds.value();
            }
        } catch (ClassNotFoundException e) {
            log.debug("[MyBatis][DS] 未找到 Mapper 类型: {}", className);
        }
        return "";
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {
    }
}
