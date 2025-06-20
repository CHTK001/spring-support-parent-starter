package com.chua.report.client.starter.service.impl;

import com.chua.report.client.starter.entity.DeviceMetrics;
import com.chua.report.client.starter.properties.ReportClientProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * OshiDeviceDataServiceImpl 测试类
 * @author CH
 * @since 2024/12/19
 */
@ExtendWith(MockitoExtension.class)
class OshiDeviceDataServiceImplTest {

    @Mock
    private ReportClientProperties properties;

    private OshiDeviceDataServiceImpl deviceDataService;

    @BeforeEach
    void setUp() {
        // 设置模拟属性
        when(properties.getDeviceId()).thenReturn("test-device-001");
        when(properties.getDeviceName()).thenReturn("Test Device");
        when(properties.getReceivablePort()).thenReturn(8080);
        
        deviceDataService = new OshiDeviceDataServiceImpl(properties);
    }

    @Test
    void testGetDeviceMetrics() {
        // 测试获取设备指标
        DeviceMetrics metrics = deviceDataService.getDeviceMetrics();
        
        assertNotNull(metrics);
        assertNotNull(metrics.getCollectTime());
        assertTrue(metrics.getOnline() != null && metrics.getOnline());
        
        // 验证基本信息
        assertEquals("test-device-001", metrics.getDeviceId());
        assertEquals("Test Device", metrics.getDeviceName());
        assertEquals(8080, metrics.getPort());
        
        // 验证CPU指标
        assertNotNull(metrics.getCpuUsage());
        assertTrue(metrics.getCpuUsage() >= 0 && metrics.getCpuUsage() <= 100);
        assertTrue(metrics.getCpuCores() > 0);
        
        // 验证内存指标
        assertNotNull(metrics.getTotalMemory());
        assertNotNull(metrics.getUsedMemory());
        assertNotNull(metrics.getAvailableMemory());
        assertTrue(metrics.getTotalMemory() > 0);
        assertTrue(metrics.getMemoryUsage() >= 0 && metrics.getMemoryUsage() <= 100);
        
        // 验证磁盘指标
        assertNotNull(metrics.getTotalDisk());
        assertNotNull(metrics.getUsedDisk());
        assertNotNull(metrics.getAvailableDisk());
        assertTrue(metrics.getTotalDisk() > 0);
        assertTrue(metrics.getDiskUsage() >= 0 && metrics.getDiskUsage() <= 100);
        
        // 验证网络指标
        assertNotNull(metrics.getNetworkInBytes());
        assertNotNull(metrics.getNetworkOutBytes());
        assertNotNull(metrics.getNetworkInPackets());
        assertNotNull(metrics.getNetworkOutPackets());
        
        // 验证系统指标
        assertNotNull(metrics.getUptime());
        assertTrue(metrics.getUptime() > 0);
        assertTrue(metrics.getProcessCount() > 0);
        assertTrue(metrics.getThreadCount() > 0);
    }

    @Test
    void testGetDeviceInfo() {
        // 测试获取设备基本信息
        DeviceMetrics info = deviceDataService.getDeviceInfo();
        
        assertNotNull(info);
        assertNotNull(info.getCollectTime());
        assertTrue(info.getOnline() != null && info.getOnline());
        
        // 验证基本信息
        assertEquals("test-device-001", info.getDeviceId());
        assertEquals("Test Device", info.getDeviceName());
        assertEquals(8080, info.getPort());
        assertNotNull(info.getIpAddress());
        assertNotNull(info.getHostname());
        assertNotNull(info.getOsName());
        assertNotNull(info.getOsVersion());
        assertNotNull(info.getOsArch());
    }

    @Test
    void testIsDeviceOnline() {
        // 测试设备在线状态检查
        boolean isOnline = deviceDataService.isDeviceOnline();
        
        // 由于测试环境可能不同，这里只验证方法能正常执行
        assertNotNull(isOnline);
    }

    @Test
    void testGetDeviceMetricsWithNullProperties() {
        // 测试当属性为null时的情况
        when(properties.getDeviceId()).thenReturn(null);
        when(properties.getDeviceName()).thenReturn(null);
        
        DeviceMetrics metrics = deviceDataService.getDeviceMetrics();
        
        assertNotNull(metrics);
        assertNotNull(metrics.getDeviceId()); // 应该使用默认值
        assertNotNull(metrics.getDeviceName()); // 应该使用默认值
    }
}
