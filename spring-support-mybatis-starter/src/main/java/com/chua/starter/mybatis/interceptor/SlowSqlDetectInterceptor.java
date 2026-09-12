package com.chua.starter.mybatis.interceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.chua.starter.mybatis.event.MybatisSlowSqlEvent;
import com.chua.starter.mybatis.properties.MybatisSlowSqlProperties;
import lombok.extern.slf4j.Slf4j;
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
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.util.CollectionUtils;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;

import static com.chua.starter.common.support.constant.MdcConstant.TRACE_ID;

/**
 * MyBatis 慢 SQL 检测拦截器。
 *
 * <p>拦截 Executor 的 query/update，统计耗时并在超过阈值时发布 {@link MybatisSlowSqlEvent}。</p>
 *
 * <p>注意：事件中是否携带 SQL/参数由 {@link MybatisSlowSqlProperties} 控制，避免敏感信息泄露。</p>
 *
 * @since 2026/4/27
 * @author CH
 */
@Intercepts({
        @Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class}),
        @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}),
        @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class, CacheKey.class, BoundSql.class}),
})
public class SlowSqlDetectInterceptor implements Interceptor {
    /** 日志 */
    private static final Logger log = LoggerFactory.getLogger(SlowSqlDetectInterceptor.class);

    /**
     * 日期格式化器（仅用于参数格式化）。
     */
    private static final SimpleDateFormat FORMATTER = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    /** 事件publisher */
    private final ApplicationEventPublisher eventPublisher;
    /** 属性 */
    private final MybatisSlowSqlProperties properties;

    /**
     * 构造慢 SQL 检测拦截器。
     *
     * @param eventPublisher Spring 事件发布器
     * @param properties     慢 SQL 配置
     */
    public SlowSqlDetectInterceptor(ApplicationEventPublisher eventPublisher,
                                   MybatisSlowSqlProperties properties) {
        this.eventPublisher = eventPublisher;
        this.properties = properties;
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        long start = System.currentTimeMillis();
        Throwable error = null;
        try {
            return invocation.proceed();
        } catch (Throwable ex) {
            error = ex;
            throw ex;
        } finally {
            long elapsed = System.currentTimeMillis() - start;
            if (properties.isEnabled() && elapsed >= Math.max(0, properties.getThresholdMs())) {
                try {
                    publishSlowSqlEvent(invocation, elapsed, error);
                } catch (Exception e) {
                    // 监控逻辑不可影响业务
                    log.debug("[MyBatis][SlowSQL] 发布事件失败: {}", e.getMessage());
                }
            }
        }
    }

    /**
     * 发布slowsql事件。
     * @param invocation invocation
     * @param elapsedMs elapsedms
     * @param error 错误
     */
    private void publishSlowSqlEvent(Invocation invocation, long elapsedMs, Throwable error) {
        Object[] args = invocation.getArgs();
        if (args == null || args.length == 0 || !(args[0] instanceof MappedStatement ms)) {
            return;
        }

        Object parameterObject = args.length > 1 ? args[1] : null;
        BoundSql boundSql = resolveBoundSql(ms, args, parameterObject);
        Configuration configuration = ms.getConfiguration();

        String sql = null;
        List<String> parameters = null;

        if (properties.isIncludeSql() || properties.isIncludeParameters()) {
            SqlResolveResult resolved = resolveSql(configuration, boundSql);
            if (properties.isIncludeSql()) {
                sql = truncate(resolved.sql, properties.getMaxSqlLength());
            }
            if (properties.isIncludeParameters()) {
                parameters = resolved.parameters;
            }
        }

        String traceId = MDC.get(TRACE_ID);
        String commandType = ms.getSqlCommandType() != null ? ms.getSqlCommandType().name() : "";

        eventPublisher.publishEvent(new MybatisSlowSqlEvent(
                this,
                ms.getId(),
                commandType,
                elapsedMs,
                traceId,
                sql,
                parameters
        ));

        if (properties.isPrint()) {
            String errMsg = error == null ? "" : ("，异常=" + error.getClass().getSimpleName());
            log.warn("[MyBatis][慢SQL] 耗时={}ms，阈值={}ms，方法={}，类型={}，traceId={}{}{}",
                    elapsedMs,
                    properties.getThresholdMs(),
                    ms.getId(),
                    commandType,
                    traceId,
                    errMsg,
                    sql == null ? "" : ("\nSQL: " + sql),
                    parameters == null ? "" : ("\n参数: " + String.join(", ", parameters)));
        }
    }

    /**
      * resolveboundsql。
     * @param ms ms
     * @param args 参数
     * @param parameter 参数
     * @return resolveBoundSql的结果
     */
    private BoundSql resolveBoundSql(MappedStatement ms, Object[] args, Object parameter) {
 // MyBatis 的 查询 重载中，boundsql 可能已在参数中传入
    /**
     * sqlresolve结果类。
     *
     * @author CH
     * @since 4.0.0
     */
        if (args.length >= 6 && args[5] instanceof BoundSql bs) {
            return bs;
        }
        return ms.getBoundSql(parameter);
    }

    private static class SqlResolveResult {
        /** SQL */
        private final String sql;
        /**
         * 参数
         *
         /**
          * sqlresolve结果。
          * @param sql SQL
          * @param parameters 参数
          */
         * @param obj obj
         * @return 获取参数值的结果
         * @param value 值
         * @param maxLen 最大len
         */
        private final List<String> parameters;
/**
   * resolvesql。
 * @param configuration 配置
 * @param boundSql boundsql
 * @return resolveSql的结果
 * @param obj obj
 * @param value 值
 /**
   * sqlresolve结果。
   * @param sql sql
   * @param parameters 参数
  */
 * @param maxLen 最大len
 */

        private SqlResolveResult(String sql, List<String> parameters) {
            this.sql = sql;
            this.parameters = parameters;
        /**
         * resolveSql。
         * @param configuration 配置
         * @param boundSql boundSql
         * @return resolveSql的结果
         */
        }
    }

    private SqlResolveResult resolveSql(Configuration configuration, BoundSql boundSql) {
        Object parameterObject = boundSql.getParameterObject();
        List<ParameterMapping> mappings = boundSql.getParameterMappings();
        String sql = boundSql.getSql().replaceAll("[\\s]+", " ");
        List<String> list = new ArrayList<>();

        if (!CollectionUtils.isEmpty(mappings) && parameterObject != null) {
            TypeHandlerRegistry typeHandlerRegistry = configuration.getTypeHandlerRegistry();
            if (typeHandlerRegistry.hasTypeHandler(parameterObject.getClass())) {
                sql = sql.replaceFirst("\\?", Matcher.quoteReplacement(getParameterValue(parameterObject)));
                list.add(parameterObject + "(" + parameterObject.getClass().getSimpleName() + ")");
            } else {
                MetaObject metaObject = configuration.newMetaObject(parameterObject);
                for (ParameterMapping parameterMapping : mappings) {
                    String propertyName = parameterMapping.getProperty();
                    if (metaObject.hasGetter(propertyName)) {
                        Object obj = metaObject.getValue(propertyName);
                        sql = sql.replaceFirst("\\?", Matcher.quoteReplacement(getParameterValue(obj)));
                        list.add(propertyName + "=" + (obj == null ? "null" : (obj + "(" + obj.getClass().getSimpleName() + ")")));
                    } else if (boundSql.hasAdditionalParameter(propertyName)) {
                        Object obj = boundSql.getAdditionalParameter(propertyName);
                        sql = sql.replaceFirst("\\?", Matcher.quoteReplacement(getParameterValue(obj)));
                        list.add(propertyName + "=" + (obj == null ? "null" : (obj + "(" + obj.getClass().getSimpleName() + ")")));
                    } else {
                        sql = sql.replaceFirst("\\?", "缺失");
                        list.add("缺失");
                    }
                }
            }
        }

        return new SqlResolveResult(sql, list);
    }

    private String truncate(String value, int maxLen) {
        if (value == null) {
            return null;
        }
        int safeMax = Math.max(0, maxLen);
        if (safeMax == 0) {
            return "";
        }
        if (value.length() <= safeMax) {
            return value;
        }
        return value.substring(0, safeMax) + "...(truncated)";
    }

    private String getParameterValue(Object obj) {
        if (obj == null) {
            return "null";
        }
        if (obj instanceof String) {
            return "'" + obj + "'";
        }
        if (obj instanceof Date) {
            return "'" + FORMATTER.format(obj) + "'";
        }
        return String.valueOf(obj);
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {
        // ignore
    }
}

