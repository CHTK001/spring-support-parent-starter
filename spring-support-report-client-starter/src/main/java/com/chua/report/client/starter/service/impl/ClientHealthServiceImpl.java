package com.chua.report.client.starter.service.impl;

import com.chua.report.client.starter.pojo.ClientHealthInfo;
import com.chua.report.client.starter.pojo.ClientHealthReport;
import com.chua.report.client.starter.properties.FileManagementProperties;
import com.chua.report.client.starter.service.ClientHealthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.net.InetAddress;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 客户端健康状态服务实现
 * @author CH
 * @since 2024/12/19
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClientHealthServiceImpl implements ClientHealthService {

    private final FileManagementProperties properties;
    
    private final AtomicReference<ClientHealthInfo> healthInfo = new AtomicReference<>();
    
    private volatile LocalDateTime startTime = LocalDateTime.now();

    @Override
    public void updateHealthStatus(String deviceId, String deviceName) {
        try {
            long validityPeriod = properties.getHealthValidityPeriod();
            
            ClientHealthInfo currentHealth = healthInfo.get();
            if (currentHealth == null) {
                // 首次创建健康信息
                ClientHealthInfo newHealth = createHealthInfo(deviceId, deviceName, validityPeriod);
                healthInfo.set(newHealth);
                log.info("客户端健康状态初始化: deviceId={}, deviceName={}, validityPeriod={}s", 
                    deviceId, deviceName, validityPeriod);
            } else {
                // 更新现有健康信息
                currentHealth.setDeviceId(deviceId);
                currentHealth.setDeviceName(deviceName);
                currentHealth.updateHealth(validityPeriod);
                log.debug("客户端健康状态更新: deviceId={}, deviceName={}, expireTime={}", 
                    deviceId, deviceName, currentHealth.getExpireTime());
            }
            
        } catch (Exception e) {
            log.error("更新客户端健康状态失败: deviceId={}, deviceName={}", deviceId, deviceName, e);
        }
    }

    @Override
    public boolean isHealthy() {
        ClientHealthInfo current = healthInfo.get();
        if (current == null) {
            return false;
        }
        
        boolean healthy = current.getHealthy() != null && current.getHealthy() && !current.isExpired();
        
        if (!healthy && current.getHealthy() != null && current.getHealthy()) {
            // 健康状态过期，标记为不健康
            current.markUnhealthy();
            log.warn("客户端健康状态过期: deviceId={}, expireTime={}", 
                current.getDeviceId(), current.getExpireTime());
        }
        
        return healthy;
    }

    @Override
    public ClientHealthInfo getHealthInfo() {
        ClientHealthInfo current = healthInfo.get();
        if (current == null) {
            return ClientHealthInfo.builder()
                .healthy(false)
                .lastUpdateTime(LocalDateTime.now())
                .build();
        }

        // 更新运行时长和性能指标
        current.setUptime(java.time.Duration.between(startTime, LocalDateTime.now()).getSeconds());
        collectSystemMetrics(current);

        return current;
    }

    @Override
    public long getLastUpdateTime() {
        ClientHealthInfo current = healthInfo.get();
        if (current == null || current.getLastUpdateTime() == null) {
            return 0;
        }
        
        return current.getLastUpdateTime()
            .atZone(java.time.ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli();
    }

    @Override
    public long getHealthValidityPeriod() {
        return properties.getHealthValidityPeriod();
    }

    @Override
    public boolean isHealthExpired() {
        ClientHealthInfo current = healthInfo.get();
        return current == null || current.isExpired();
    }

    @Override
    public void resetHealth() {
        ClientHealthInfo current = healthInfo.get();
        if (current != null) {
            current.markUnhealthy();
            log.info("客户端健康状态已重置: deviceId={}", current.getDeviceId());
        }
        healthInfo.set(null);
    }

    /**
     * 获取健康状态上报对象
     * @return 健康状态上报对象，如果没有健康信息则返回null
     */
    public ClientHealthReport getHealthReport() {
        ClientHealthInfo current = getHealthInfo();
        if (current == null || current.getClientIp() == null) {
            return null;
        }

        return current.toReport();
    }

    /**
     * 创建健康信息
     */
    private ClientHealthInfo createHealthInfo(String deviceId, String deviceName, long validityPeriod) {
        try {
            ClientHealthInfo health = ClientHealthInfo.create(deviceId, deviceName, validityPeriod);

            // 设置客户端信息
            health.setClientVersion(getClass().getPackage().getImplementationVersion());
            health.setClientIp(getLocalIpAddress());
            health.setOsInfo(System.getProperty("os.name") + " " + System.getProperty("os.version"));
            health.setJavaVersion(System.getProperty("java.version"));
            health.setStartTime(startTime);
            health.setUptime(java.time.Duration.between(startTime, LocalDateTime.now()).getSeconds());

            // 设置主机名
            try {
                health.setHostname(InetAddress.getLocalHost().getHostName());
            } catch (Exception e) {
                health.setHostname("unknown");
            }

            // 收集系统性能指标
            collectSystemMetrics(health);

            return health;

        } catch (Exception e) {
            log.warn("创建健康信息时获取系统信息失败", e);
            return ClientHealthInfo.create(deviceId, deviceName, validityPeriod);
        }
    }

    /**
     * 获取本地IP地址
     */
    private String getLocalIpAddress() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "unknown";
        }
    }

    /**
     * 收集系统性能指标
     */
    private void collectSystemMetrics(ClientHealthInfo health) {
        try {
            // 获取操作系统MXBean
            OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();

            // CPU使用率
            if (osBean instanceof com.sun.management.OperatingSystemMXBean) {
                com.sun.management.OperatingSystemMXBean sunOsBean =
                    (com.sun.management.OperatingSystemMXBean) osBean;
                double cpuUsage = sunOsBean.getProcessCpuLoad() * 100;
                if (cpuUsage >= 0) {
                    health.setCpuUsage(Math.round(cpuUsage * 100.0) / 100.0);
                }
            }

            // 内存使用率
            MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
            long usedMemory = memoryBean.getHeapMemoryUsage().getUsed();
            long maxMemory = memoryBean.getHeapMemoryUsage().getMax();
            if (maxMemory > 0) {
                double memoryUsage = (double) usedMemory / maxMemory * 100;
                health.setMemoryUsage(Math.round(memoryUsage * 100.0) / 100.0);
            }

            // 磁盘使用率
            File[] roots = File.listRoots();
            if (roots.length > 0) {
                File root = roots[0]; // 使用第一个根目录
                long totalSpace = root.getTotalSpace();
                long freeSpace = root.getFreeSpace();
                if (totalSpace > 0) {
                    double diskUsage = (double) (totalSpace - freeSpace) / totalSpace * 100;
                    health.setDiskUsage(Math.round(diskUsage * 100.0) / 100.0);
                }
            }

            // 网络延迟（简单的本地回环测试）
            long startTime = System.currentTimeMillis();
            try {
                InetAddress.getByName("127.0.0.1").isReachable(1000);
                long latency = System.currentTimeMillis() - startTime;
                health.setNetworkLatency(latency);
            } catch (Exception e) {
                health.setNetworkLatency(999L); // 默认延迟
            }

        } catch (Exception e) {
            log.warn("收集系统性能指标失败", e);
        }
    }
}
