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
* Druid、Hikari、Seata 代理 等场景。</p>
*
* @since 4.0.0.42
* @author CH
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
    /** 获取Connection */
    public Connection getConnection() throws SQLException {
        Connection connection = delegate.getConnection();
        activeConnections.incrementAndGet();
        /**
        * 监控connection。
        * @param connection connection
        * @param dataSourceName 数据源名称
        * @param monitor 监控
        * @param closeCallback( 关闭callback(
        * @return 监控connection的结果
         */
        return new MonitoringConnection(connection, dataSourceName, monitor, closeCallback()).proxy();
    }

    @Override
    /** 获取Connection */
    public Connection getConnection(String username, String password) throws SQLException {
        Connection connection = delegate.getConnection(username, password);
        activeConnections.incrementAndGet();
        /**
        * 监控connection。
        * @param connection connection
        * @param dataSourceName 数据源名称
        * @param monitor 监控
        * @param closeCallback( 关闭callback(
        * @return 监控connection的结果
         */
        return new MonitoringConnection(connection, dataSourceName, monitor, closeCallback()).proxy();
    }

    @Override
    /** Unwrap */
    public <T> T unwrap(Class<T> iface) throws SQLException {
        if (iface.isInstance(this)) {
            return iface.cast(this);
        }
        return delegate.unwrap(iface);
    }

    @Override
    /** 是否包装器for */
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return iface.isInstance(this) || delegate.isWrapperFor(iface);
    }

    @Override
    /** 获取记录日志Writer */
    public PrintWriter getLogWriter() throws SQLException {
        return delegate.getLogWriter();
    }

    @Override
    /** 设置记录日志Writer */
    public void setLogWriter(PrintWriter out) throws SQLException {
        delegate.setLogWriter(out);
    }

    @Override
    /** 设置login超时 */
    public void setLoginTimeout(int seconds) throws SQLException {
        delegate.setLoginTimeout(seconds);
    }

    @Override
    /** 获取login超时 */
    public int getLoginTimeout() throws SQLException {
        return delegate.getLoginTimeout();
    }

    @Override
    /** 获取父日志记录器 */
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
    * 构建连接关闭回调（幂等：连接被重复 关闭 时仅递减一次）
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
    /** 转为字符串 */
    public String toString() {
        return "MonitoringDataSource{" +
                "name='" + dataSourceName + '\'' +
                ", delegate=" + delegate +
                '}';
    }
}
