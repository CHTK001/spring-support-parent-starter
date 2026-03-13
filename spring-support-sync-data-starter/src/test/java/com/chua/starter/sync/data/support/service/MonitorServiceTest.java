package com.chua.starter.sync.data.support.service;

import com.chua.starter.sync.data.support.service.impl.SyncMonitorServiceImpl;
import com.chua.starter.sync.data.support.service.impl.SyncStatisticsServiceImpl;
import com.chua.starter.sync.data.support.service.impl.AlertServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 监控服务单元测试
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
    void testMonitorServiceInitialization() {
        SyncMonitorServiceImpl service = new SyncMonitorServiceImpl();
        assertNotNull(service);
    }
    
    @Test
    void testStatisticsServiceInitialization() {
        SyncStatisticsServiceImpl service = new SyncStatisticsServiceImpl();
        assertNotNull(service);
    }
    
    @Test
    void testAlertServiceInitialization() {
        AlertServiceImpl service = new AlertServiceImpl();
        assertNotNull(service);
    }
    
    @Test
    void testGetRealtimeMonitorData() {
        Long taskId = 1L;
        
        when(monitorService.getRealtimeMonitorData(taskId))
            .thenReturn(createMockMonitorData());
        
        Map<String, Object> data = monitorService.getRealtimeMonitorData(taskId);
        
        assertNotNull(data);
        assertTrue(data.containsKey("taskId"));
        assertTrue(data.containsKey("status"));
        assertTrue(data.containsKey("progress"));
        
        verify(monitorService, times(1)).getRealtimeMonitorData(taskId);
    }
    
    @Test
    void testGetPerformanceMetrics() {
        Long taskId = 1L;
        
        when(monitorService.getPerformanceMetrics(taskId))
            .thenReturn(createMockMetrics());
        
        Map<String, Object> metrics = monitorService.getPerformanceMetrics(taskId);
        
        assertNotNull(metrics);
        assertTrue(metrics.containsKey("throughput"));
        assertTrue(metrics.containsKey("latency"));
        assertTrue(metrics.containsKey("errorRate"));
        
        verify(monitorService, times(1)).getPerformanceMetrics(taskId);
    }
    
    @Test
    void testGetDailyStatistics() {
        LocalDateTime date = LocalDateTime.now();
        
        when(statisticsService.getDailyStatistics(date))
            .thenReturn(createMockStatistics());
        
        Map<String, Object> stats = statisticsService.getDailyStatistics(date);
        
        assertNotNull(stats);
        assertTrue(stats.containsKey("totalRecords"));
        assertTrue(stats.containsKey("successCount"));
        assertTrue(stats.containsKey("failureCount"));
        
        verify(statisticsService, times(1)).getDailyStatistics(date);
    }
    
    @Test
    void testCreateAlert() {
        Long taskId = 1L;
        String alertType = "ERROR";
        String message = "同步失败";
        
        doNothing().when(alertService).createAlert(taskId, alertType, message);
        
        alertService.createAlert(taskId, alertType, message);
        
        verify(alertService, times(1)).createAlert(taskId, alertType, message);
    }
    
    @Test
    void testResolveAlert() {
        Long alertId = 1L;
        
        doNothing().when(alertService).resolveAlert(alertId);
        
        alertService.resolveAlert(alertId);
        
        verify(alertService, times(1)).resolveAlert(alertId);
    }
    
    @Test
    void testGetActiveAlerts() {
        Long taskId = 1L;
        
        when(alertService.getActiveAlerts(taskId))
            .thenReturn(java.util.Collections.emptyList());
        
        var alerts = alertService.getActiveAlerts(taskId);
        
        assertNotNull(alerts);
        
        verify(alertService, times(1)).getActiveAlerts(taskId);
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
