package com.chua.starter.datasource.monitor;

import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
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
 * @since 4.0.0.42
 * @author CH
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
     * 连接关闭时的回调（用于连接数统计递减），可为 null
     */
    private final Runnable onClose;

    /**
     * 监控连接构造器
     *
     * @param delegate       目标连接
     * @param dataSourceName 数据源名称
     * @param monitor        监控门面
     */
    public MonitoringConnection(Connection delegate, String dataSourceName, DataSourceSqlMonitor monitor) {
        this(delegate, dataSourceName, monitor, null);
    }

    /**
     * 监控连接构造器（带关闭回调）
     *
     * @param delegate       目标连接
     * @param dataSourceName 数据源名称
     * @param monitor        监控门面
     * @param onClose        连接关闭回调，可为 null
     */
    public MonitoringConnection(Connection delegate, String dataSourceName, DataSourceSqlMonitor monitor, Runnable onClose) {
        this.delegate = delegate;
        this.dataSourceName = dataSourceName;
        this.monitor = monitor;
        this.onClose = onClose;
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

        // 关闭连接：透传并触发关闭回调（幂等，多次 close 仅回调一次）
        if ("close".equals(name)) {
            try {
                return invokeTarget(method, args);
            } finally {
                if (onClose != null) {
                    onClose.run();
                }
            }
        }

        // 创建 Statement 的入口：统一反射分派，避免手工解析重载参数导致
        // (String, String[]) / (String, int[]) 等重载被误强转为 int 抛 ClassCastException
        if ("createStatement".equals(name) || "prepareStatement".equals(name) || "prepareCall".equals(name)) {
            Object result = invokeTarget(method, args);
            if (result instanceof Statement statement) {
                // 提取 SQL 文本：仅当第一个参数为 String（execute(sql) 形式）时携带
                String sql = args != null && args.length > 0 && args[0] instanceof String sqlText ? sqlText : null;
                return MonitoringStatement.wrap(statement, dataSourceName, monitor, sql);
            }
            return result;
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
