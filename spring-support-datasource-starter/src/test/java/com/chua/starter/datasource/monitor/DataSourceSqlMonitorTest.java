package com.chua.starter.datasource.monitor;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SQL 监控链路单元测试
 *
 * @author CH
 * @since 4.0.0.42
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
}
