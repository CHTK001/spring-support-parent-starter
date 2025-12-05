package com.chua.report.client.starter.report;

import com.chua.report.client.starter.sync.MonitorTopics;
import com.chua.sync.support.client.SyncClient;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 设备指标上报器
 * <p>
 * 定时采集并上报系统指标（CPU、内存、磁盘等）
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/12/05
 */
@Slf4j
public class DeviceMetricsReporter {

    private static final DeviceMetricsReporter INSTANCE = new DeviceMetricsReporter();

    private SyncClient syncClient;
    private String appName;
    private ScheduledExecutorService scheduler;
    private volatile boolean running = false;

    // 上报间隔（秒）
    private long intervalSeconds = 30;

    private DeviceMetricsReporter() {
    }

    public static DeviceMetricsReporter getInstance() {
        return INSTANCE;
    }

    public void setSyncClient(SyncClient syncClient) {
        this.syncClient = syncClient;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public void setIntervalSeconds(long intervalSeconds) {
        this.intervalSeconds = intervalSeconds;
    }

    /**
     * 启动定时上报
     */
    public synchronized void start() {
        if (running) {
            return;
        }

        if (syncClient == null) {
            log.warn("[DeviceReporter] SyncClient 未设置，无法启动上报");
            return;
        }

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "device-reporter");
            t.setDaemon(true);
            return t;
        });

        // 启动时上报一次设备信息
        scheduler.schedule(this::reportDeviceInfo, 1, TimeUnit.SECONDS);

        // 定时上报指标
        scheduler.scheduleAtFixedRate(this::reportMetrics, 5, intervalSeconds, TimeUnit.SECONDS);

        running = true;
        log.info("[DeviceReporter] 启动定时上报，指标间隔: {}秒", intervalSeconds);
    }

    /**
     * 停止上报
     */
    public synchronized void stop() {
        if (!running) {
            return;
        }

        if (scheduler != null) {
            scheduler.shutdown();
            scheduler = null;
        }
        running = false;
        log.info("[DeviceReporter] 停止定时上报");
    }

    /**
     * 采集并上报指标
     */
    private void reportMetrics() {
        try {
            Map<String, Object> metrics = collectMetrics();
            syncClient.publish(MonitorTopics.DEVICE_METRICS, metrics);
            log.debug("[DeviceReporter] 上报设备指标: {}", metrics.get("ipAddress"));
        } catch (Exception e) {
            log.error("[DeviceReporter] 上报指标失败", e);
        }
    }

    /**
     * 上报设备基本信息
     */
    private void reportDeviceInfo() {
        try {
            Map<String, Object> info = collectDeviceInfo();
            syncClient.publish(MonitorTopics.DEVICE_INFO, info);
            log.info("[DeviceReporter] 上报设备信息: {}", info.get("ipAddress"));
        } catch (Exception e) {
            log.error("[DeviceReporter] 上报设备信息失败", e);
        }
    }

    /**
     * 采集设备基本信息（只在启动时上报一次）
     */
    private Map<String, Object> collectDeviceInfo() {
        Map<String, Object> info = new HashMap<>();
        try {
            InetAddress localHost = InetAddress.getLocalHost();
            info.put("deviceId", localHost.getHostName() + "_" + appName);
            info.put("deviceName", appName);
            info.put("ipAddress", localHost.getHostAddress());
            info.put("hostname", localHost.getHostName());
            info.put("appName", appName);

            OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
            info.put("osName", osBean.getName());
            info.put("osVersion", osBean.getVersion());
            info.put("osArch", osBean.getArch());
            info.put("cpuCores", osBean.getAvailableProcessors());

            Runtime runtime = Runtime.getRuntime();
            info.put("maxMemory", runtime.maxMemory());

            info.put("online", true);
            info.put("startTime", System.currentTimeMillis());
        } catch (Exception e) {
            log.error("[DeviceReporter] 采集设备信息失败", e);
        }
        return info;
    }

    /**
     * 采集系统指标
     */
    private Map<String, Object> collectMetrics() {
        Map<String, Object> metrics = new HashMap<>();

        try {
            // 基本信息
            InetAddress localHost = InetAddress.getLocalHost();
            metrics.put("deviceId", localHost.getHostName() + "_" + appName);
            metrics.put("deviceName", appName);
            metrics.put("ipAddress", localHost.getHostAddress());
            metrics.put("hostname", localHost.getHostName());
            metrics.put("appName", appName);

            // 操作系统信息
            OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
            metrics.put("osName", osBean.getName());
            metrics.put("osVersion", osBean.getVersion());
            metrics.put("osArch", osBean.getArch());
            metrics.put("cpuCores", osBean.getAvailableProcessors());

            // CPU 使用率
            double cpuLoad = osBean.getSystemLoadAverage();
            if (cpuLoad >= 0) {
                metrics.put("cpuUsage", Math.round(cpuLoad / osBean.getAvailableProcessors() * 100 * 100.0) / 100.0);
                metrics.put("loadAverage", String.format("%.2f", cpuLoad));
            } else {
                // Windows 不支持 getSystemLoadAverage，尝试其他方式
                if (osBean instanceof com.sun.management.OperatingSystemMXBean) {
                    com.sun.management.OperatingSystemMXBean sunOsBean = (com.sun.management.OperatingSystemMXBean) osBean;
                    double cpuUsage = sunOsBean.getCpuLoad() * 100;
                    metrics.put("cpuUsage", Math.round(cpuUsage * 100.0) / 100.0);
                }
            }

            // 内存信息
            MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
            long heapUsed = memoryBean.getHeapMemoryUsage().getUsed();
            long heapMax = memoryBean.getHeapMemoryUsage().getMax();
            long nonHeapUsed = memoryBean.getNonHeapMemoryUsage().getUsed();

            // 系统内存
            if (osBean instanceof com.sun.management.OperatingSystemMXBean) {
                com.sun.management.OperatingSystemMXBean sunOsBean = (com.sun.management.OperatingSystemMXBean) osBean;
                long totalMemory = sunOsBean.getTotalMemorySize();
                long freeMemory = sunOsBean.getFreeMemorySize();
                long usedMemory = totalMemory - freeMemory;

                metrics.put("totalMemory", totalMemory);
                metrics.put("usedMemory", usedMemory);
                metrics.put("availableMemory", freeMemory);
                metrics.put("memoryUsage", Math.round((double) usedMemory / totalMemory * 100 * 100.0) / 100.0);
            } else {
                // 回退到 JVM 内存
                Runtime runtime = Runtime.getRuntime();
                metrics.put("totalMemory", runtime.maxMemory());
                metrics.put("usedMemory", runtime.totalMemory() - runtime.freeMemory());
                metrics.put("availableMemory", runtime.freeMemory());
            }

            // 磁盘信息
            File[] roots = File.listRoots();
            long totalDisk = 0;
            long freeDisk = 0;
            for (File root : roots) {
                totalDisk += root.getTotalSpace();
                freeDisk += root.getFreeSpace();
            }
            long usedDisk = totalDisk - freeDisk;
            metrics.put("totalDisk", totalDisk);
            metrics.put("usedDisk", usedDisk);
            metrics.put("availableDisk", freeDisk);
            if (totalDisk > 0) {
                metrics.put("diskUsage", Math.round((double) usedDisk / totalDisk * 100 * 100.0) / 100.0);
            }

            // 运行时间
            RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
            metrics.put("uptime", runtimeBean.getUptime() / 1000);

            // 线程数
            metrics.put("threadCount", Thread.activeCount());

            // 进程数（JVM 不直接支持，设为 -1）
            metrics.put("processCount", -1);

            // 在线状态
            metrics.put("online", true);

            // 收集时间
            metrics.put("collectTime", System.currentTimeMillis());

        } catch (Exception e) {
            log.error("[DeviceReporter] 采集指标失败", e);
        }

        return metrics;
    }
}
