package com.chua.report.client.starter.report;

import com.chua.oshi.support.*;
import com.chua.report.client.starter.sync.MonitorTopics;
import com.chua.sync.support.client.SyncClient;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.List;
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

    @Setter
    private SyncClient syncClient;
    @Setter
    private String appName;
    @Setter
    private String host;
    private ScheduledExecutorService scheduler;
    private volatile boolean running = false;

    // 上报间隔（秒）
    @Setter
    private long intervalSeconds = 30;

    private DeviceMetricsReporter() {
    }

    public static DeviceMetricsReporter getInstance() {
        return INSTANCE;
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
        scheduler.schedule(this::reportDeviceInfo, 20, TimeUnit.SECONDS);

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
            Sys sys = Oshi.newSys();
            info.put("ipAddress", StringUtils.defaultIfBlank(host, sys.getComputerIp()));
            info.put("osName", sys.getOsName());
            info.put("osVersion", System.getProperty("os.version"));
            info.put("osArch", sys.getOsArch());
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
            // IP 地址（用于匹配服务器）
            Sys sys = Oshi.newSys();
            metrics.put("ipAddress", sys.getComputerIp());

            // CPU 使用率
            Cpu cpu = Oshi.newCpu(500);
            metrics.put("cpuUsage", 100 - cpu.getFree());

            // 内存使用率
            Mem mem = Oshi.newMem();
            metrics.put("memoryUsage", mem.getUsage());

            // 磁盘使用率
            List<SysFile> sysFiles = Oshi.newSysFile();
            long totalDisk = 0;
            long usedDisk = 0;
            for (SysFile sf : sysFiles) {
                totalDisk += sf.getTotal();
                usedDisk += sf.getUsed();
            }
            if (totalDisk > 0) {
                metrics.put("diskUsage", Math.round((double) usedDisk / totalDisk * 100 * 100.0) / 100.0);
            }

            // 网络流量
            List<Network> networks = Oshi.newNetwork();
            long networkIn = 0;
            long networkOut = 0;
            for (Network net : networks) {
                networkIn += net.getReceiveBytes();
                networkOut += net.getTransmitBytes();
            }
            metrics.put("networkInBytes", networkIn);
            metrics.put("networkOutBytes", networkOut);

        } catch (Exception e) {
            log.error("[DeviceReporter] 采集指标失败", e);
        }

        return metrics;
    }
}
