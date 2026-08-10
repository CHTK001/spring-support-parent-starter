package com.chua.starter.datasource.monitor;

import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.Statement;

/**
 * SQL 监控 Statement
 *
 * <p>基于 JDK 动态代理包装 {@link Statement}，对 execute/executeQuery/executeUpdate/
 * executeLargeUpdate/executeBatch 等执行方法计时并上报到 {@link DataSourceSqlMonitor}。
 * 其余方法全部透传，包括批量添加、参数设置等，保证 SQL 语义不变。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class MonitoringStatement implements InvocationHandler {

    /**
     * 执行方法集合
     */
    private static final String[] EXECUTE_METHODS = {
            "execute", "executeQuery", "executeUpdate", "executeLargeUpdate", "executeBatch"
    };

    /**
     * 目标 Statement
     */
    private final Statement delegate;

    /**
     * 数据源名称
     */
    private final String dataSourceName;

    /**
     * 监控门面
     */
    private final DataSourceSqlMonitor monitor;

    /**
     * SQL 文本（PreparedStatement 时为模板 SQL，无法拿到绑定参数）
     */
    private final String sql;

    /**
     * 构造器
     *
     * @param delegate       目标 Statement
     * @param dataSourceName 数据源名称
     * @param monitor        监控门面
     * @param sql            SQL 文本
     */
    private MonitoringStatement(Statement delegate, String dataSourceName, DataSourceSqlMonitor monitor, String sql) {
        this.delegate = delegate;
        this.dataSourceName = dataSourceName;
        this.monitor = monitor;
        this.sql = sql;
    }

    /**
     * 包装 Statement
     *
     * @param statement      目标 Statement
     * @param dataSourceName 数据源名称
     * @param monitor        监控门面
     * @return 代理后的 Statement
     */
    public static Statement wrap(Statement statement, String dataSourceName, DataSourceSqlMonitor monitor) {
        return wrap(statement, dataSourceName, monitor, null);
    }

    /**
     * 包装 Statement（携带 SQL 文本）
     *
     * @param statement      目标 Statement
     * @param dataSourceName 数据源名称
     * @param monitor        监控门面
     * @param sql            SQL 文本
     * @return 代理后的 Statement
     */
    public static Statement wrap(Statement statement, String dataSourceName, DataSourceSqlMonitor monitor, String sql) {
        if (statement == null) {
            return null;
        }
        Class<?>[] interfaces;
        if (statement instanceof CallableStatement) {
            interfaces = new Class[]{Statement.class, PreparedStatement.class, CallableStatement.class};
        } else if (statement instanceof PreparedStatement) {
            interfaces = new Class[]{Statement.class, PreparedStatement.class};
        } else {
            interfaces = new Class[]{Statement.class};
        }
        return (Statement) Proxy.newProxyInstance(
                statement.getClass().getClassLoader(),
                interfaces,
                new MonitoringStatement(statement, dataSourceName, monitor, sql));
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String name = method.getName();

        // 拦截执行方法，计时上报
        for (String execMethod : EXECUTE_METHODS) {
            if (execMethod.equals(name)) {
                long start = System.currentTimeMillis();
                try {
                    Object result = invokeTarget(method, args);
                    long costMs = System.currentTimeMillis() - start;
                    monitor.recordExecution(dataSourceName, resolveSql(args), costMs, true, null);
                    return result;
                } catch (Throwable e) {
                    long costMs = System.currentTimeMillis() - start;
                    monitor.recordExecution(dataSourceName, resolveSql(args), costMs, false, e.getMessage());
                    throw e;
                }
            }
        }

        // Object 基础方法透传
        if ("toString".equals(name)) {
            return "MonitoringStatement[" + delegate + "]";
        }
        if ("hashCode".equals(name)) {
            return System.identityHashCode(proxy);
        }
        if ("equals".equals(name)) {
            return proxy == args[0];
        }
        if ("unwrap".equals(name)) {
            Class<?> iface = (Class<?>) args[0];
            if (iface.isInstance(proxy)) {
                return iface.cast(proxy);
            }
            return invokeTarget(method, args);
        }
        if ("isWrapperFor".equals(name)) {
            Class<?> iface = (Class<?>) args[0];
            return iface.isInstance(proxy) || (Boolean) invokeTarget(method, args);
        }

        return invokeTarget(method, args);
    }

    /**
     * 解析本次执行的 SQL 文本
     *
     * @param args 方法参数
     * @return SQL 文本
     */
    private String resolveSql(Object[] args) {
        // 显式传入的 SQL（execute(sql) / executeQuery(sql) / executeUpdate(sql)）
        if (args != null && args.length > 0 && args[0] instanceof String s) {
            return s;
        }
        // PreparedStatement 模板 SQL
        return sql;
    }

    /**
     * 调用目标 Statement 对应方法
     *
     * @param method 方法
     * @param args   参数
     * @return 方法返回值
     * @throws Throwable 目标 Statement 抛出的异常
     */
    private Object invokeTarget(Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(delegate, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }
}
