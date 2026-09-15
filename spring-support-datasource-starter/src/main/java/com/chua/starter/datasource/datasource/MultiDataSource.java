package com.chua.starter.datasource.datasource;

import com.chua.datasource.support.partition.PartitionAwareTableFactory;
import com.chua.datasource.support.partition.PartitionRoutingTable;
import com.chua.starter.datasource.support.DynamicDataSource;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
* 多数据源
*
* @author CH
* @since 4.0.0
 */
@Slf4j
public class MultiDataSource implements DataSource {
    /** 数据源 */
    private final List<DataSource> dataSources;
    /** Dynamic数据来源 */
    private final DynamicDataSource dynamicDataSource;
    /** Application上下文 */
    private ApplicationContext applicationContext;

    /** Thread_本地 */
    private static final ThreadLocal<GenericObjectPool<Connection>> THREAD_LOCAL = new InheritableThreadLocal<>();
    private final Multimap<String, String> tables = HashMultimap.create(); // tables

    private final Map<SchemaPlus, SchemaPlus> cache = new ConcurrentHashMap<>(); // 缓存

    /** Print写入器 */
    private PrintWriter printWriter = new PrintWriter(System.out);

    final Properties info = new Properties(); // 信息
    /** 池 */
    private GenericObjectPool<Connection> pool;
    /** 秒 */
    private int seconds;

    {
        info.put("lex", "MYSQL");
        info.put("druidFetch", "1000");
    }

    /**
    * 创建 multi数据源 实例
    * @param dataSources 数据源
    * @param applicationContext application上下文
    * @param applicationContext application上下文
     */
    public MultiDataSource(List<DataSource> dataSources, ApplicationContext applicationContext) {
        this.dataSources = dataSources;
        this.applicationContext = applicationContext;
        this.dynamicDataSource = applicationContext.getBean(DynamicDataSource.class);
        /**
        * initial数据源。
         */
        initialDataSource();
    }


    /**
    * 申报对象
    *
    * @param connection connection
     */
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
    * @param schemaPlus 模式plus
     */
    private void refreshDataSource(SchemaPlus schemaPlus) {
        for (DataSource dataSource : dataSources) {
            String catalog;
            /**
            * 尝试。
            * @param dataSource.getConnection( 数据源.getconnection(
             */
            try (Connection connection = dataSource.getConnection()) {
                catalog = connection.getCatalog();
            } catch (SQLException e) {
                /**
                * runtime异常。
                * @param e e
                * @return runtime异常的结果
                 */
                throw new RuntimeException(e);
            }
            JdbcSchema jdbcSchema = JdbcSchema.create(schemaPlus, catalog, dataSource, catalog, null);
            schemaPlus.add(catalog, jdbcSchema);
        }

        // 注册分区感知路由表，使 Calcite 查询自动跨源表 + 分区表
        /**
        * 注册分区routingtables。
        * @param schemaPlus 模式plus
         */
        registerPartitionRoutingTables(schemaPlus);
    }

    /**
    * 注册分区感知路由表，使 Calcite 查询自动跨源表 + 分区表。
    * @param schemaPlus 模式plus
     */
    private void registerPartitionRoutingTables(SchemaPlus schemaPlus) {
        try {
            if (dataSources.isEmpty()) {
                return;
            }
            DataSource primaryDs = dataSources.getFirst();
            PartitionAwareTableFactory factory = new PartitionAwareTableFactory(primaryDs);
            Map<String, PartitionRoutingTable> routingTables = factory.buildAllRoutingTables();
            if (routingTables.isEmpty()) {
                return;
            }

            for (String catalog : getCatalogs()) {
                SchemaPlus catalogSchema = schemaPlus.getSubSchema(catalog);
                if (catalogSchema == null) {
                    continue;
                }
                for (Map.Entry<String, PartitionRoutingTable> entry : routingTables.entrySet()) {
                    try {
                        catalogSchema.add(entry.getKey(), entry.getValue());
                        log.info("[分区路由] 表 {} 已注册为分区感知视图", entry.getKey());
                    } catch (Exception e) {
                        log.warn("[分区路由] 注册表 {} 失败: {}", entry.getKey(), e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[分区路由] 初始化跳过 ({}), 不影响正常运行", e.getMessage());
        }
    }

    /**
    * 获取Catalogs
    *
    * @return 获取catalogs的结果
     */
    private List<String> getCatalogs() {
        List<String> catalogs = new ArrayList<>(dataSources.size());
        for (DataSource ds : dataSources) {
            /**
            * 尝试。
            * @param ds.getConnection( ds.获取connection(
             */
            try (Connection conn = ds.getConnection()) {
                catalogs.add(conn.getCatalog());
            } catch (SQLException ignored) {
                // 跳过异常数据源
            }
        }
        return catalogs;
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
            /** 创建 */
            public Connection create() throws Exception {
                try {
                    Connection connection = DriverManager.getConnection("jdbc:calcite:", info).unwrap(CalciteConnection.class);
                    CalciteConnection calciteConnection = connection.unwrap(CalciteConnection.class);
                    /**
                    * refresh数据源。
                    * @param calciteConnection.getRootSchema( calciteconnection.获取根模式(
                     */
                    refreshDataSource(calciteConnection.getRootSchema());
                    return calciteConnection;
                } catch (SQLException e) {
                    /**
                    * runtime异常。
                    * @param e e
                    * @return runtime异常的结果
                     */
                    throw new RuntimeException(e);
                }
            }

            @Override
            /** Wrap */
            public PooledObject<Connection> wrap(Connection obj) {
                return new DefaultPooledObject<>(obj);
            }
        };
        this.pool = new GenericObjectPool<>(factory, createConfig());
    }

    @Override
    /** 获取Connection */
    public Connection getConnection() throws SQLException {
        try {
            Connection connection = pool.borrowObject();
            THREAD_LOCAL.remove();
            THREAD_LOCAL.set(pool);
            dynamicDataSource.bindResource(connection);
            return connection;
        } catch (Exception e) {
            /**
            * SQL异常。
            * @param "获取连接失败" "获取连接失败"
            * @param e e
            * @return sql异常的结果
             */
            throw new SQLException("获取连接失败", e);
        }
    }

    @Override
    /** 获取Connection */
    public Connection getConnection(String username, String password) throws SQLException {
        return getConnection();
    }

    @Override
    /** Unwrap */
    public <T> T unwrap(Class<T> iface) throws SQLException {
        return null;
    }

    @Override
    /** 是否包装器for */
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return false;
    }

    @Override
    /** 获取记录日志Writer */
    public PrintWriter getLogWriter() throws SQLException {
        return printWriter;
    }

    @Override
    /** 设置记录日志Writer */
    public void setLogWriter(PrintWriter out) throws SQLException {
        this.printWriter = out;
    }

    @Override
    /** 设置login超时 */
    public void setLoginTimeout(int seconds) throws SQLException {
        this.seconds = seconds;
    }

    @Override
    /** 获取login超时 */
    public int getLoginTimeout() throws SQLException {
        return seconds;
    }

    @Override
    /** 获取父日志记录器 */
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
 // 数据源接口要求返回Java.util.日志.日志记录器，但实际使用SLF4J
 // 返回空表示不支持，由SLF4J桥接处理
        return null;
    }


}
