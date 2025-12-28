package com.chua.starter.datasource.support;

import com.chua.starter.datasource.datasource.MultiDataSource;
import com.google.common.base.Strings;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.jdbc.datasource.SimpleConnectionHandle;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.util.ClassUtils;

import javax.sql.DataSource;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.util.Collection;
import java.util.Map;

import static com.chua.starter.common.support.logger.ModuleLog.highlight;

/**
 * 动态数据源
 *
 * @author CH
 */
@Slf4j
public class DynamicDataSource extends AbstractRoutingDataSource {

    private static final String MASTER = "master";
    protected static final Map<Object, Object> TARGET_DATA_SOURCES = DataSourceContextSupport.DATA_SOURCE_MAP;
    private final Object defaultDataSource;
    private static final String MYBATIS = "org.springframework.transaction.support.TransactionSynchronizationManager";
    private boolean binder;

    private DynmaicSimpleConnectionHolder connectionHolder = null;

    public DynamicDataSource() {
        this.setTargetDataSources(TARGET_DATA_SOURCES);
        this.defaultDataSource = createDefaultDataSource();
        this.setDefaultTargetDataSource(defaultDataSource);
    }

    @Override
    public void afterPropertiesSet() {
        super.afterPropertiesSet();
        this.setTargetDataSources(TARGET_DATA_SOURCES);

    }

    /**
     * 绑定事务
     *
     * @param value 值
     * @throws IllegalStateException ex
     */
    public void bindResource(Connection value) throws IllegalStateException {
        if (null == connectionHolder) {
            synchronized (this) {
                if (null == connectionHolder) {
                    connectionHolder = new DynmaicSimpleConnectionHolder(new DynmaicConnectionHandle(value));
                }
            }
        }

        if (ClassUtils.isPresent(MYBATIS, Thread.currentThread().getContextClassLoader())) {
            connectionHolder.setConnection(value);
            try {
                Method method = ClassUtils.getMethod(Class.forName(MYBATIS), "bindResource", Object.class, Object.class);
                com.chua.common.support.utils.ClassUtils.setAccessible(method);
                method.invoke(null, this, connectionHolder);
                this.binder = true;
            } catch (Exception ignored) {
            }
        }

    }

    /**
     * 绑定事务
     *
     * @throws IllegalStateException ex
     */
    public void unbindResource() throws IllegalStateException {
        if (!binder) {
            return;
        }

        if (ClassUtils.isPresent(MYBATIS, Thread.currentThread().getContextClassLoader())) {
            try {
                connectionHolder.released();
                this.binder = false;
            } catch (Exception ignored) {
            }
        }

    }


    @Override
    protected DataSource determineTargetDataSource() {
        unbindResource();
        Object lookupKey = determineCurrentLookupKey();
        if (null == lookupKey) {
            return (DataSource) this.defaultDataSource;
        }

        DataSource dataSource = (DataSource) TARGET_DATA_SOURCES.get(lookupKey);
        if (dataSource == null) {
            dataSource = (DataSource) this.defaultDataSource;
        }
        if (dataSource == null) {
            throw new IllegalStateException("Cannot determine target DataSource for lookup key [" + lookupKey + "]");
        }
        return dataSource;
    }

    public DataSource getDataSource(String name) {
        if (Strings.isNullOrEmpty(name)) {
            return determineTargetDataSource();
        }
        return (DataSource) TARGET_DATA_SOURCES.get(name);
    }

    /**
     * 构建默认数据源
     *
     * @return 数据源
     */
    private Object createDefaultDataSource() {
        if (TARGET_DATA_SOURCES.containsKey(MASTER)) {
            return TARGET_DATA_SOURCES.get(MASTER);
        }

        Collection<Object> values = TARGET_DATA_SOURCES.values();
        if (values.isEmpty()) {
            return null;
        }
        return values.iterator().next();
    }

    @Override
    protected Object determineCurrentLookupKey() {
        String dbType = DataSourceContextSupport.getDbType();
        if (null != dbType && log.isInfoEnabled()) {
            log.info("[Datasource] 当前数据源: {}", highlight(dbType));
        }
        return dbType;
    }


    class DynmaicConnectionHandle extends SimpleConnectionHandle {

        private Connection connection;

        /**
         * Create a new SimpleConnectionHandle for the given Connection.
         *
         * @param connection the JDBC Connection
         */
        public DynmaicConnectionHandle(Connection connection) {
            super(connection);
            this.connection = connection;
        }

        public void setConnection(Connection connection) {
            this.connection = connection;
        }

        @Override
        public Connection getConnection() {
            return connection;
        }
    }

    class DynmaicSimpleConnectionHolder extends ConnectionHolder {


        private final DynmaicConnectionHandle connectionHandle;

        public DynmaicSimpleConnectionHolder(DynmaicConnectionHandle connectionHandle) {
            super(connectionHandle);
            this.connectionHandle = connectionHandle;
        }

        public void setConnection(Connection connection) {
            connectionHandle.setConnection(connection);
        }

        @Override
        public Connection getConnection() {
            return connectionHandle.getConnection();
        }

        @Override
        protected boolean hasConnection() {
            return null != getConnection();
        }

        @Override
        public void released() {
            MultiDataSource.returnObject(getConnection());
            this.setConnection(null);
            super.released();
        }
    }
}
