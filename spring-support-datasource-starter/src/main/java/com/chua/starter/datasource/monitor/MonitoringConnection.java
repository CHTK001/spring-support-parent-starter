package com.chua.starter.datasource.monitor;

import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;

/**
 * SQL 监控连接
 *
 * <p>基于 JDK 动态代理包装 {@link Connection}，透明透传全部方法（含 commit/rollback/close
 * 等事务方法），仅拦截创建 Statement 的入口返回 {@link MonitoringStatement}，从而对执行的
 * SQL 进行计时与上报，不影响连接池与事务语义。</p>
 *
 * <p>若底层连接本身已是代理（如 Seata ConnectionProxy），内部再创建 Statement 时
 * {@link MonitoringStatement} 透传包装的 statement，监控层位于最外层，功能不受影响。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class MonitoringConnection implements InvocationHandler {

    /**
     * 目标连接
     */
    private final Connection delegate;

    /**
     * 数据源名称
     */
    private final String dataSourceName;

    /**
     * 监控门面
     */
    private final DataSourceSqlMonitor monitor;

    /**
     * 监控连接构造器
     *
     * @param delegate       目标连接
     * @param dataSourceName 数据源名称
     * @param monitor        监控门面
     */
    public MonitoringConnection(Connection delegate, String dataSourceName, DataSourceSqlMonitor monitor) {
        this.delegate = delegate;
        this.dataSourceName = dataSourceName;
        this.monitor = monitor;
    }

    /**
     * 创建代理连接
     *
     * @return 代理后的连接
     */
    public Connection proxy() {
        return (Connection) Proxy.newProxyInstance(
                delegate.getClass().getClassLoader(),
                new Class[]{Connection.class},
                this);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String name = method.getName();

        // 创建 Statement 的入口：返回监控 Statement
        if ("createStatement".equals(name)) {
            Statement statement;
            if (args == null || args.length == 0) {
                statement = delegate.createStatement();
            } else if (args.length == 2) {
                statement = delegate.createStatement((int) args[0], (int) args[1]);
            } else {
                statement = delegate.createStatement((int) args[0], (int) args[1], (int) args[2]);
            }
            return MonitoringStatement.wrap(statement, dataSourceName, monitor);
        }
        if ("prepareStatement".equals(name)) {
            PreparedStatement statement;
            String sql = (String) args[0];
            if (args.length == 1) {
                statement = delegate.prepareStatement(sql);
            } else if (args.length == 2) {
                statement = delegate.prepareStatement(sql, (int) args[1]);
            } else if (args.length == 3 && args[1] instanceof Integer) {
                statement = delegate.prepareStatement(sql, (int) args[1], (int) args[2]);
            } else {
                statement = delegate.prepareStatement(sql, (int[]) args[1]);
            }
            return MonitoringStatement.wrap(statement, dataSourceName, monitor, sql);
        }
        if ("prepareCall".equals(name)) {
            CallableStatement statement = delegate.prepareCall((String) args[0]);
            return MonitoringStatement.wrap(statement, dataSourceName, monitor, (String) args[0]);
        }

        // Object 基础方法透传
        if ("toString".equals(name)) {
            return "MonitoringConnection[" + delegate + "]";
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
     * 调用目标连接对应方法（透传）
     *
     * @param method 方法
     * @param args   参数
     * @return 方法返回值
     * @throws Throwable 目标连接抛出的异常
     */
    private Object invokeTarget(Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(delegate, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }
}
