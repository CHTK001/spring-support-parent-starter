package com.chua.starter.datasource.monitor;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SQL 监控链路单元测试
 *
 * @since 4.0.0.42
 * @author CH
 */
class DataSourceSqlMonitorTest {

    /**
     * 监控实例
     */
    private DataSourceSqlMonitor monitor;

    /**
     * 事件记录
     */
    private final List<SqlExecutionEvent> events = new ArrayList<>();

    /**
     * 初始化
     */
    @BeforeEach
    void setUp() {
        ApplicationEventPublisher publisher = event -> events.add((SqlExecutionEvent) event);
        SqlMonitorProperties properties = new SqlMonitorProperties();
        properties.setEnabled(true);
        properties.setSlowSqlThresholdMs(100);
        monitor = new DataSourceSqlMonitor(publisher, List.of(), properties);
    }

    /**
     * 清理
     */
    @AfterEach
    void tearDown() {
        events.clear();
    }

    /**
     * 测试 SQL 执行计数与事件发布
     */
    @Test
    void testRecordExecutionPublishesEvent() {
        monitor.recordExecution("master", "select * from t", 10, true, null);

        SqlStatistics statistics = monitor.getStatistics("master");
        assertNotNull(statistics);
        assertEquals(1, statistics.getTotalCount().get());
        assertEquals(1, statistics.getSuccessCount().get());
        assertEquals(10, statistics.getAvgCostMs());
        assertEquals(1, events.size());
        assertEquals("select", events.get(0).getSqlType());
    }

    /**
     * 测试慢 SQL 标记
     */
    @Test
    void testSlowSqlFlag() {
        monitor.recordExecution("master", "select * from t", 200, true, null);

        SqlStatistics statistics = monitor.getStatistics("master");
        assertEquals(1, statistics.getSlowSqlCount().get());
        assertTrue(events.get(0).isSlowSql());
    }

    /**
     * 测试失败记录
     */
    @Test
    void testFailRecord() {
        monitor.recordExecution("master", "insert into t values(1)", 5, false, "duplicate key");

        SqlStatistics statistics = monitor.getStatistics("master");
        assertEquals(1, statistics.getFailCount().get());
        assertEquals("insert", events.get(0).getSqlType());
        assertEquals("duplicate key", events.get(0).getErrorMessage());
    }

    /**
     * 测试包装幂等：同一数据源只包装一次
     */
    @Test
    void testWrapIdempotent() {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1");

        DataSource wrapped1 = monitor.wrap(dataSource, "master");
        DataSource wrapped2 = monitor.wrap(dataSource, "master");

        assertSame(wrapped1, wrapped2);
        assertTrue(DataSourceSqlMonitor.isMonitored(wrapped1));
        assertTrue(wrapped1 instanceof MonitoringDataSource);
    }

    /**
     * 测试真实 JDBC 链路：执行 SQL 触发监控
     */
    @Test
    void testRealExecution() throws Exception {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1");
        dataSource.setDriverClassName("org.h2.Driver");

        DataSource wrapped = monitor.wrap(dataSource, "master");
        try (Connection connection = wrapped.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("create table if not exists monitor_t(id int primary key, name varchar(50))");
            statement.execute("insert into monitor_t values(1, 'a')");
            ResultSet rs = statement.executeQuery("select count(*) from monitor_t");
            rs.next();
            assertEquals(1, rs.getInt(1));
        }

        SqlStatistics statistics = monitor.getStatistics("master");
        assertNotNull(statistics);
        assertEquals(3, statistics.getTotalCount().get());
        assertEquals(3, events.size());
    }

    /**
     * 测试 unwrap 透传
     */
    @Test
    void testUnwrap() throws Exception {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1");

        DataSource wrapped = monitor.wrap(dataSource, "master");
        assertTrue(wrapped.isWrapperFor(HikariDataSource.class));
        assertSame(dataSource, wrapped.unwrap(HikariDataSource.class));
    }

    /**
     * 测试重置统计
     */
    @Test
    void testReset() {
        monitor.recordExecution("master", "select 1", 1, true, null);
        monitor.resetStatistics();
        assertEquals(0, monitor.getStatistics("master").getTotalCount().get());
    }

    /**
     * 测试自定义监听器回调
     */
    @Test
    void testListenerCallback() {
        AtomicInteger callbackCount = new AtomicInteger();
        SqlExecutionListener listener = event -> callbackCount.incrementAndGet();
        ApplicationEventPublisher publisher = event -> {
        };
        DataSourceSqlMonitor listenerMonitor = new DataSourceSqlMonitor(publisher, List.of(listener), new SqlMonitorProperties());

        listenerMonitor.recordExecution("master", "delete from t", 3, true, null);

        assertEquals(1, callbackCount.get());
        assertEquals(1, listenerMonitor.getStatistics("master").getTotalCount().get());
    }

    /**
     * 测试并发包装幂等：同一数据源并发 wrap 只生成一个包装实例
     */
    @Test
    void testConcurrentWrapIdempotent() throws Exception {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1");

        List<DataSource> results = java.util.Collections.synchronizedList(new ArrayList<>());
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            Thread thread = new Thread(() -> results.add(monitor.wrap(dataSource, "master")));
            threads.add(thread);
            thread.start();
        }
        for (Thread thread : threads) {
            thread.join();
        }

        assertEquals(8, results.size());
        assertSame(results.get(0), results.get(1));
        assertEquals(1, results.stream().distinct().count());
    }

    /**
     * 测试活跃连接数随 close 增减且重复 close 只递减一次
     */
    @Test
    void testActiveConnectionsTracking() throws Exception {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1");

        DataSource wrapped = monitor.wrap(dataSource, "master");
        MonitoringDataSource monitoringDataSource = (MonitoringDataSource) wrapped;
        assertEquals(0, monitoringDataSource.getActiveConnections());

        Connection connection = wrapped.getConnection();
        assertEquals(1, monitoringDataSource.getActiveConnections());

        connection.close();
        assertEquals(0, monitoringDataSource.getActiveConnections());

        // 重复 close 不再递减
        connection.close();
        assertEquals(0, monitoringDataSource.getActiveConnections());
    }

    /**
     * 测试 prepareStatement 全重载参数透传（String[] 生成主键回填路径不抛 CCE）
     */
    @Test
    void testPrepareStatementOverloads() throws Exception {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1");
        dataSource.setDriverClassName("org.h2.Driver");

        DataSource wrapped = monitor.wrap(dataSource, "master");
        try (Connection connection = wrapped.getConnection()) {
            Statement statement = connection.createStatement();
            statement.execute("create table if not exists prep_t(id int auto_increment primary key, name varchar(50))");
            statement.close();

            // String[] 列名回填重载（MyBatis 生成主键路径）
            try (PreparedStatement ps = connection.prepareStatement(
                    "insert into prep_t(name) values(?)", new String[]{"id"})) {
                ps.setString(1, "x");
                assertEquals(1, ps.executeUpdate());
            }
            // int[] 列索引回填重载
            try (PreparedStatement ps = connection.prepareStatement(
                    "insert into prep_t(name) values(?)", new int[]{1})) {
                ps.setString(1, "y");
                assertEquals(1, ps.executeUpdate());
            }
            // 四参 resultSetType/concurrency/holdability
            try (PreparedStatement ps = connection.prepareStatement(
                    "select * from prep_t", ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY, ResultSet.CLOSE_CURSORS_AT_COMMIT)) {
                assertNotNull(ps.executeQuery());
            }
        }

        assertTrue(monitor.getStatistics("master").getTotalCount().get() >= 4);
    }

    /**
     * 测试统计淘汰同步清理：容量满触发淘汰时 statisticsMap 同步收缩
     */
    @Test
    void testStatisticsEvictionSynchronized() {
        SqlMonitorProperties properties = new SqlMonitorProperties();
        properties.setEnabled(true);
        properties.setMaxTrackedDataSources(3);
        properties.setOverflowPolicy(com.chua.common.support.collection.OverflowPolicy.EVICT_ELDEST);
        ApplicationEventPublisher publisher = event -> {
        };
        DataSourceSqlMonitor boundedMonitor = new DataSourceSqlMonitor(publisher, List.of(), properties);

        boundedMonitor.recordExecution("ds1", "select 1", 1, true, null);
        boundedMonitor.recordExecution("ds2", "select 1", 1, true, null);
        boundedMonitor.recordExecution("ds3", "select 1", 1, true, null);
        // 第 4 个触发淘汰 ds1
        boundedMonitor.recordExecution("ds4", "select 1", 1, true, null);

        assertEquals(3, boundedMonitor.getAllStatistics().size());
        assertNull(boundedMonitor.getStatistics("ds1"));
        assertNotNull(boundedMonitor.getStatistics("ds2"));
        assertNotNull(boundedMonitor.getStatistics("ds3"));
        assertNotNull(boundedMonitor.getStatistics("ds4"));
    }
}
