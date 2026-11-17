package com.chua.starter.mybatis.interceptor;

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
import org.slf4j.MDC;

import java.sql.Statement;

/**
 * 映射器追踪拦截器。
 *
 * <p>把当前执行的 Mapper 方法名放进 MDC 的 {@code mapper} 键，
 * 便于日志与链路追踪按 Mapper 方法聚合。</p>
 *
 * @author CH
 * @since 4.0.0
 */
@Intercepts({
        @Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class}),
        @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}),
        @Signature(type = StatementHandler.class, method = "batch", args = {Statement.class}),
        @Signature(type = StatementHandler.class, method = "parameterize", args = {Statement.class}),
        @Signature(type = ResultSetHandler.class, method = "handleResultSets", args = {Statement.class})
})
public class MapperTracingInterceptor implements Interceptor {

    /**
     * 拦截 Executor / StatementHandler / ResultSetHandler 的调用。
     *
     * <p>首个参数是 {@link MappedStatement} 时，把 Mapper 方法名写入 MDC，
     * 调用结束后由 {@link MDC.MDCCloseable} 自动清理。</p>
     *
     * @param invocation MyBatis 调用上下文
     * @return 原方法返回值
     * @throws Throwable 原方法抛出的异常原样上抛
     */
    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        Object arg = invocation.getArgs()[0];
        if (arg instanceof MappedStatement ms) {
            String mapperMethod = ms.getId();
            try (MDC.MDCCloseable closeable = MDC.putCloseable("mapper", mapperMethod)) {
                return invocation.proceed();
            }
        }

        return invocation.proceed();
    }
}
