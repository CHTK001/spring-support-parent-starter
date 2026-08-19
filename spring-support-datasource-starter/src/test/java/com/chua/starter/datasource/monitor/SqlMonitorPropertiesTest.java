package com.chua.starter.datasource.monitor;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.StandardEnvironment;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SQL 监听配置与端点单元测试
 *
 * @since 4.0.0.42
 * @author CH
 */
class SqlMonitorPropertiesTest {

    /**
     * 测试未显式配置时 dev 环境自动启用
     */
    @Test
    void testAutoEnableInDevProfile() {
        SqlMonitorProperties properties = new SqlMonitorProperties();
        StandardEnvironment environment = new StandardEnvironment();
        environment.setActiveProfiles("dev");

        assertTrue(properties.resolveEnabled(environment));
    }

    /**
     * 测试未显式配置时 prod 环境自动关闭
     */
    @Test
    void testAutoDisableInProdProfile() {
        SqlMonitorProperties properties = new SqlMonitorProperties();
        StandardEnvironment environment = new StandardEnvironment();
        environment.setActiveProfiles("prod");

        assertFalse(properties.resolveEnabled(environment));
    }

    /**
     * 测试显式配置优先于环境
     */
    @Test
    void testExplicitConfigOverridesEnvironment() {
        SqlMonitorProperties properties = new SqlMonitorProperties();
        properties.setEnabled(false);
        StandardEnvironment environment = new StandardEnvironment();
        environment.setActiveProfiles("dev");

        assertFalse(properties.resolveEnabled(environment));
    }

    /**
     * 测试自定义自动启用环境列表
     */
    @Test
    void testCustomAutoEnableEnvironments() {
        SqlMonitorProperties properties = new SqlMonitorProperties();
        properties.setAutoEnableEnvironments(java.util.List.of("pre"));
        StandardEnvironment environment = new StandardEnvironment();
        environment.setActiveProfiles("pre");

        assertTrue(properties.resolveEnabled(environment));
    }

    /**
     * 测试无激活环境时关闭
     */
    @Test
    void testNoActiveProfileDisabled() {
        SqlMonitorProperties properties = new SqlMonitorProperties();
        StandardEnvironment environment = new StandardEnvironment();

        assertFalse(properties.resolveEnabled(environment));
    }

    /**
     * 测试 Actuator 端点统计快照
     */
    @Test
    void testEndpointStatistics() {
        org.springframework.context.ApplicationEventPublisher publisher = event -> {
        };
        SqlMonitorProperties properties = new SqlMonitorProperties();
        DataSourceSqlMonitor monitor = new DataSourceSqlMonitor(publisher, java.util.List.of(), properties);
        monitor.recordExecution("master", "select 1", 5, true, null);

        SqlMonitorEndpoint endpoint = new SqlMonitorEndpoint(monitor);
        Map<String, Object> statistics = endpoint.statistics();

        assertEquals(true, statistics.get("enabled"));
        @SuppressWarnings("unchecked")
        Map<String, Object> dataSources = (Map<String, Object>) statistics.get("dataSources");
        assertTrue(dataSources.containsKey("master"));
        @SuppressWarnings("unchecked")
        Map<String, Object> master = (Map<String, Object>) dataSources.get("master");
        assertEquals(1L, master.get("total"));
        assertEquals(5L, master.get("avgCostMs"));
    }

    /**
     * 测试 Actuator 端点重置
     */
    @Test
    void testEndpointReset() {
        org.springframework.context.ApplicationEventPublisher publisher = event -> {
        };
        SqlMonitorProperties properties = new SqlMonitorProperties();
        DataSourceSqlMonitor monitor = new DataSourceSqlMonitor(publisher, java.util.List.of(), properties);
        monitor.recordExecution("master", "select 1", 5, true, null);

        SqlMonitorEndpoint endpoint = new SqlMonitorEndpoint(monitor);
        endpoint.reset();

        assertEquals(0L, monitor.getStatistics("master").getTotalCount().get());
    }

    /**
     * 测试禁用时包装返回原数据源
     */
    @Test
    void testWrapDisabledReturnsOriginal() {
        org.springframework.context.ApplicationEventPublisher publisher = event -> {
        };
        SqlMonitorProperties properties = new SqlMonitorProperties();
        DataSourceSqlMonitor monitor = new DataSourceSqlMonitor(publisher, java.util.List.of(), properties);
        monitor.setEnabled(false);
        DataSourceSqlMonitor.initialize(monitor);

        javax.sql.DataSource dataSource = new com.zaxxer.hikari.HikariDataSource();
        javax.sql.DataSource result = DataSourceSqlMonitor.wrapIfEnabled("master", dataSource);

        assertEquals(dataSource, result);
    }

    /**
     * 测试统计跟踪数据源数量上限
     */
    @Test
    void testMaxTrackedDataSources() {
        org.springframework.context.ApplicationEventPublisher publisher = event -> {
        };
        SqlMonitorProperties properties = new SqlMonitorProperties();
        properties.setMaxTrackedDataSources(2);
        DataSourceSqlMonitor monitor = new DataSourceSqlMonitor(publisher, java.util.List.of(), properties);

        monitor.recordExecution("ds1", "select 1", 5, true, null);
        monitor.recordExecution("ds2", "select 1", 5, true, null);
        monitor.recordExecution("ds3", "select 1", 5, true, null);

        // 前两个数据源被跟踪，第三个超出上限不被跟踪
        assertNotNull(monitor.getStatistics("ds1"));
        assertNotNull(monitor.getStatistics("ds2"));
        assertEquals(null, monitor.getStatistics("ds3"));
        assertEquals(2, monitor.getAllStatistics().size());
    }

    /**
     * 测试数据源数量上限不阻塞事件发布
     */
    @Test
    void testMaxTrackedDataSourcesStillPublishesEvent() {
        org.springframework.context.ApplicationEventPublisher publisher = event -> {
        };
        SqlMonitorProperties properties = new SqlMonitorProperties();
        properties.setMaxTrackedDataSources(1);
        DataSourceSqlMonitor monitor = new DataSourceSqlMonitor(publisher, java.util.List.of(), properties);

        monitor.recordExecution("ds1", "select 1", 5, true, null);
        monitor.recordExecution("ds2", "select 1", 5, true, null);

        assertEquals(null, monitor.getStatistics("ds2"));
    }
}
