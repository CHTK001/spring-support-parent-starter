package com.chua.starter.datasource.monitor;

import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * SQL 监控数据源
 *
 * <p>对目标数据源做透明代理，仅拦截 {@code getConnection()} 返回的连接并包装为
 * {@link MonitoringConnection}，其余方法全部透传。连接池内部连接管理不受影响。</p>
 *
 * <p>提供 {@code unwrap} 透传，保证 {@code instanceof} / {@code isWrapperFor} 判断兼容
 * Druid、Hikari、Seata Proxy 等场景。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class MonitoringDataSource implements DataSource {

    /**
     * 目标数据源
     */
    private final DataSource delegate;

    /**
     * 数据源名称
     */
    private final String dataSourceName;

    /**
     * 监控门面
     */
    private final DataSourceSqlMonitor monitor;

    /**
     * 当前活跃连接数（统计用）
     */
    private final AtomicInteger activeConnections = new AtomicInteger();

    /**
     * 监控数据源构造器
     *
     * @param delegate       目标数据源
     * @param dataSourceName 数据源名称
     * @param monitor        监控门面
     */
    public MonitoringDataSource(DataSource delegate, String dataSourceName, DataSourceSqlMonitor monitor) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.dataSourceName = dataSourceName;
        this.monitor = monitor;
    }

    @Override
    public Connection getConnection() throws SQLException {
        Connection connection = delegate.getConnection();
        activeConnections.incrementAndGet();
        return new MonitoringConnection(connection, dataSourceName, monitor, closeCallback()).proxy();
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        Connection connection = delegate.getConnection(username, password);
        activeConnections.incrementAndGet();
        return new MonitoringConnection(connection, dataSourceName, monitor, closeCallback()).proxy();
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        if (iface.isInstance(this)) {
            return iface.cast(this);
        }
        return delegate.unwrap(iface);
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return iface.isInstance(this) || delegate.isWrapperFor(iface);
    }

    @Override
    public PrintWriter getLogWriter() throws SQLException {
        return delegate.getLogWriter();
    }

    @Override
    public void setLogWriter(PrintWriter out) throws SQLException {
        delegate.setLogWriter(out);
    }

    @Override
    public void setLoginTimeout(int seconds) throws SQLException {
        delegate.setLoginTimeout(seconds);
    }

    @Override
    public int getLoginTimeout() throws SQLException {
        return delegate.getLoginTimeout();
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        return delegate.getParentLogger();
    }

    /**
     * 获取目标数据源
     *
     * @return 目标数据源
     */
    public DataSource getTargetDataSource() {
        return delegate;
    }

    /**
     * 获取数据源名称
     *
     * @return 数据源名称
     */
    public String getDataSourceName() {
        return dataSourceName;
    }

    /**
     * 获取当前活跃连接数
     *
     * @return 活跃连接数
     */
    public int getActiveConnections() {
        return activeConnections.get();
    }

    /**
     * 构建连接关闭回调（幂等：连接被重复 close 时仅递减一次）
     *
     * @return 关闭回调
     */
    private Runnable closeCallback() {
        AtomicBoolean closed = new AtomicBoolean(false);
        return () -> {
            if (closed.compareAndSet(false, true)) {
                activeConnections.decrementAndGet();
            }
        };
    }

    @Override
    public String toString() {
        return "MonitoringDataSource{" +
                "name='" + dataSourceName + '\'' +
                ", delegate=" + delegate +
                '}';
    }
}
