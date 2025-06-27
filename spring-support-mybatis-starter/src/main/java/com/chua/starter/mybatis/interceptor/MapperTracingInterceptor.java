package com.chua.starter.mybatis.interceptor;

import com.chua.starter.mybatis.p6spy.MybatisP6SpyLogger;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.resultset.ResultSetHandler;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

import java.sql.Statement;

/**
 * @author CH
 * @since 2025/6/27 15:54
 */
@Intercepts({
        @Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class}),
        @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}),
        @Signature(type = StatementHandler.class, method = "batch", args = {Statement.class}),
        @Signature(type = StatementHandler.class, method = "parameterize", args = {Statement.class}),
        @Signature(type = ResultSetHandler.class, method = "handleResultSets", args = {Statement.class})
})
public class MapperTracingInterceptor implements Interceptor {

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        MappedStatement ms = (MappedStatement) invocation.getArgs()[0];
        String mapperMethod = ms.getId();

        MybatisP6SpyLogger.pushMethod(mapperMethod);
        try {
            return invocation.proceed();
        } finally {
            MybatisP6SpyLogger.popMethod();
        }
    }


}