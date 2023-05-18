package com.chua.starter.datasource.datasource;

import com.chua.starter.datasource.support.DynamicDataSource;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import lombok.SneakyThrows;
import org.apache.calcite.adapter.jdbc.JdbcSchema;
import org.apache.calcite.jdbc.CalciteConnection;
import org.apache.calcite.schema.SchemaPlus;
import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.PooledObjectFactory;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.springframework.context.ApplicationContext;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * 多数据源
 *
 * @author CH
 */
public class MultiDataSource implements DataSource {
    private final List<DataSource> dataSources;
    private final DynamicDataSource dynamicDataSource;
    private ApplicationContext applicationContext;

    private static final ThreadLocal<GenericObjectPool<Connection>> THREAD_LOCAL = new InheritableThreadLocal<>();
    private final Multimap<String, String> tables = HashMultimap.create();

    private final Map<SchemaPlus, SchemaPlus> cache = new ConcurrentHashMap<>();

    private PrintWriter printWriter = new PrintWriter(System.out);

    private final Logger logger = Logger.getLogger(DataSource.class.getName());

    final Properties info = new Properties();
    private GenericObjectPool<Connection> pool;
    private int seconds;

    {
        info.put("lex", "MYSQL");
        info.put("druidFetch", "1000");
    }

    public MultiDataSource(List<DataSource> dataSources, ApplicationContext applicationContext) {
        this.dataSources = dataSources;
        this.applicationContext = applicationContext;
        this.dynamicDataSource = applicationContext.getBean(DynamicDataSource.class);
        initialDataSource();
    }


    public static void returnObject(Connection connection) {
        if (null == connection) {
            return;
        }

        GenericObjectPool<Connection> pool1 = THREAD_LOCAL.get();
        if (null == pool1) {
            return;
        }
        pool1.returnObject(connection);
    }

    /**
     * 创建连接池
     */
    private void refreshDataSource(SchemaPlus schemaPlus) {
        for (DataSource dataSource : dataSources) {
            String catalog;
            try (Connection connection = dataSource.getConnection()) {
                catalog = connection.getCatalog();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            JdbcSchema jdbcSchema = JdbcSchema.create(schemaPlus, catalog, dataSource, catalog, null);
            schemaPlus.add(catalog, jdbcSchema);
        }
    }

    /**
     * 连接池配置
     *
     * @return 配置
     */
    private GenericObjectPoolConfig<Connection> createConfig() {
        GenericObjectPoolConfig<Connection> poolConfig = new GenericObjectPoolConfig();
        // 最大空闲数
        poolConfig.setMaxIdle(5);
        // 最小空闲数, 池中只有一个空闲对象的时候，池会在创建一个对象，并借出一个对象，从而保证池中最小空闲数为1
        poolConfig.setMinIdle(1);
        // 最大池对象总数
        poolConfig.setMaxTotal(20);
        // 逐出连接的最小空闲时间 默认1800000毫秒(30分钟)
        poolConfig.setMinEvictableIdleTime(Duration.ofMillis(1800000));
        // 逐出扫描的时间间隔(毫秒) 如果为负数,则不运行逐出线程, 默认-1
        poolConfig.setTimeBetweenEvictionRuns(Duration.ofMillis(1800000 * 2L));
        // 在获取对象的时候检查有效性, 默认false
        poolConfig.setTestOnBorrow(true);
        // 在归还对象的时候检查有效性, 默认false
        poolConfig.setTestOnReturn(false);
        // 在空闲时检查有效性, 默认false
        poolConfig.setTestWhileIdle(false);
        // 最大等待时间， 默认的值为-1，表示无限等待。
        poolConfig.setMaxWait(Duration.ofMillis(5000));
        // 是否启用后进先出, 默认true
        poolConfig.setLifo(true);
        // 连接耗尽时是否阻塞, false报异常,ture阻塞直到超时, 默认true
        poolConfig.setBlockWhenExhausted(true);
        // 每次逐出检查时 逐出的最大数目 默认3
        poolConfig.setNumTestsPerEvictionRun(3);

        return poolConfig;
    }

    /**
     * 初始化数据库
     */
    private void initialDataSource() {
        PooledObjectFactory<Connection> factory = new BasePooledObjectFactory<Connection>() {
            @Override
            public Connection create() throws Exception {
                try {
                    Connection connection = DriverManager.getConnection("jdbc:calcite:", info).unwrap(CalciteConnection.class);
                    CalciteConnection calciteConnection = connection.unwrap(CalciteConnection.class);
                    refreshDataSource(calciteConnection.getRootSchema());
                    return calciteConnection;
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public PooledObject<Connection> wrap(Connection obj) {
                return new DefaultPooledObject<>(obj);
            }
        };
        this.pool = new GenericObjectPool<>(factory, createConfig());
    }

    @SneakyThrows
    @Override
    public Connection getConnection() throws SQLException {
        Connection connection = pool.borrowObject();
        THREAD_LOCAL.remove();
        THREAD_LOCAL.set(pool);
        dynamicDataSource.bindResource(connection);
        return connection;
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return getConnection();
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        return null;
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return false;
    }

    @Override
    public PrintWriter getLogWriter() throws SQLException {
        return printWriter;
    }

    @Override
    public void setLogWriter(PrintWriter out) throws SQLException {
        this.printWriter = out;
    }

    @Override
    public void setLoginTimeout(int seconds) throws SQLException {
        this.seconds = seconds;
    }

    @Override
    public int getLoginTimeout() throws SQLException {
        return seconds;
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        return logger;
    }


}
