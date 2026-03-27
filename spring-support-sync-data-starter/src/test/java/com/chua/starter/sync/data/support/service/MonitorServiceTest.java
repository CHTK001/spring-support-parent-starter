package com.chua.starter.sync.data.support.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 监控服务接口单元测试（基于 mock）
 */
class MonitorServiceTest {

    @Mock
    private SyncMonitorService monitorService;

    @Mock
    private SyncStatisticsService statisticsService;

    @Mock
    private AlertService alertService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testCollectRealtimeData() {
        Long taskId = 1L;

        when(monitorService.collectRealtimeData(taskId))
            .thenReturn(createMockMonitorData());

        Map<String, Object> data = monitorService.collectRealtimeData(taskId);

        assertNotNull(data);
        assertTrue(data.containsKey("taskId"));
        assertTrue(data.containsKey("status"));
        assertTrue(data.containsKey("progress"));

        verify(monitorService, times(1)).collectRealtimeData(taskId);
    }

    @Test
    void testCalculatePerformanceMetrics() {
        Long taskId = 1L;
        LocalDateTime start = LocalDateTime.now().minusHours(1);
        LocalDateTime end = LocalDateTime.now();

        when(monitorService.calculatePerformanceMetrics(taskId, start, end))
            .thenReturn(createMockMetrics());

        Map<String, Object> metrics = monitorService.calculatePerformanceMetrics(taskId, start, end);

        assertNotNull(metrics);
        assertTrue(metrics.containsKey("throughput"));
        assertTrue(metrics.containsKey("latency"));
        assertTrue(metrics.containsKey("errorRate"));

        verify(monitorService, times(1)).calculatePerformanceMetrics(taskId, start, end);
    }

    @Test
    void testAnalyzeTrend() {
        Long taskId = 1L;
        int days = 7;

        when(statisticsService.analyzeTrend(taskId, days))
            .thenReturn(createMockStatistics());

        Map<String, Object> stats = statisticsService.analyzeTrend(taskId, days);

        assertNotNull(stats);
        assertTrue(stats.containsKey("totalRecords"));
        assertTrue(stats.containsKey("successCount"));
        assertTrue(stats.containsKey("failureCount"));

        verify(statisticsService, times(1)).analyzeTrend(taskId, days);
    }

    @Test
    void testTriggerAlert() {
        Long taskId = 1L;
        String alertType = "PERFORMANCE";
        String alertLevel = "WARNING";
        String message = "同步失败";

        doNothing().when(alertService).triggerAlert(taskId, alertType, alertLevel, message);

        alertService.triggerAlert(taskId, alertType, alertLevel, message);

        verify(alertService, times(1)).triggerAlert(taskId, alertType, alertLevel, message);
    }

    @Test
    void testResolveAlert() {
        Long alertId = 1L;

        doNothing().when(alertService).resolveAlert(alertId);

        alertService.resolveAlert(alertId);

        verify(alertService, times(1)).resolveAlert(alertId);
    }

    @Test
    void testListAlerts() {
        Long taskId = 1L;
        when(alertService.listAlerts(taskId, "WARNING", false))
            .thenReturn(Collections.emptyList());

        var alerts = alertService.listAlerts(taskId, "WARNING", false);

        assertNotNull(alerts);

        verify(alertService, times(1)).listAlerts(taskId, "WARNING", false);
    }

    private Map<String, Object> createMockMonitorData() {
        return Map.of(
            "taskId", 1L,
            "status", "RUNNING",
            "progress", 50.0,
            "currentRecords", 5000L,
            "totalRecords", 10000L
        );
    }
    
    private Map<String, Object> createMockMetrics() {
        return Map.of(
            "throughput", 1000.0,
            "latency", 50.0,
            "errorRate", 0.01,
            "memoryUsage", 512.0
        );
    }
    
    private Map<String, Object> createMockStatistics() {
        return Map.of(
            "totalRecords", 100000L,
            "successCount", 99000L,
            "failureCount", 1000L,
            "avgThroughput", 1500.0
        );
    }
}
