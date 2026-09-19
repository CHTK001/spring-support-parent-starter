package com.chua.report.client.starter.report;

import com.chua.report.client.starter.sync.MonitorTopics;
import com.chua.starter.oshi.support.OshiSystemMonitor;
import com.chua.starter.sync.support.client.SyncClient;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 设备指标上报器
 * <p>
 * 定时采集并上报系统指标（CPU、内存、磁盘、网卡等）
 * 支持配置要上报的指标类型
 * </p>
 * <ul>
 *   <li>CPU: 单独线程采集（因需停顿），缓存结果</li>
 *   <li>内存: 总大小、已使用大小、时间</li>
 *   <li>磁盘: 每个磁盘的名称、大小、用量、时间</li>
 *   <li>网卡: 每个网卡的名称、上行、下行、时间</li>
 * </ul>
 *
 * <p>采集能力通过 {@link OshiSystemMonitor} 统一封装，本类只负责调度与上报。</p>
 *
 * @版本 1.0.0
 * @since 2024/12/05
 * @author CH
 */
@Slf4j
public class DeviceMetricsReporter {

    /**
     * 单例实例
     */
    private static final DeviceMetricsReporter INSTANCE = new DeviceMetricsReporter();

    /**
     * CPU 采样间隔（毫秒）
     */
    private static final long CPU_SAMPLING_MILLIS = 1000L;

    /**
     * 启动延迟（秒）— 设备信息上报
     */
    private static final long INITIAL_DELAY_DEVICE_INFO_SECONDS = 20L;

    /**
     * 启动延迟（秒）— 指标上报
     */
    private static final long INITIAL_DELAY_METRICS_SECONDS = 10L;

    /**
     * CPU 采集启动延迟（秒）
     */
    private static final long INITIAL_DELAY_CPU_SECONDS = 5L;

    /**
     * 同步客户端 实例
     */
    @Setter
    /** 同步客户端 */
    private SyncClient syncClient;

    /**
     * 应用名称
     */
    @Setter
    /** APP名称 */
    private String appName;

    /**
     * 主机地址
     */
    @Setter
    /** 主机 */
    private String host;

    /**
     * 主调度器
     */
    private ScheduledExecutorService scheduler;

    /**
     * CPU 采集线程（单独维护）
     */
    private ScheduledExecutorService cpuScheduler;

    /**
     * 运行标志
     */
    private volatile boolean running = false;

    /**
     * 上报间隔（秒）
     */
    @Setter
    /** 间隔秒 */
    private long intervalSeconds = 30;

    /**
     * 要上报的指标类型（默认全部）
     */
    @Setter
    /** 是否启用指标 */
    private Set<MetricType> enabledMetrics = Set.of(MetricType.values());

    /**
     * 系统信息采集器（OSHI 统一入口）
     */
    private final OshiSystemMonitor monitor = new OshiSystemMonitor();

    /**
     * CPU 使用率缓存（由单独线程更新）
     */
    private final AtomicReference<Map<String, Object>> cpuCache = new AtomicReference<>(new HashMap<>());

    /** 创建 device指标reporter 实例 */
    private DeviceMetricsReporter() {
    }

    /**
     * 获取单例实例
     *
     * @return 单例
     */
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

        // 主调度器
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "device-reporter");
            t.setDaemon(true);
            return t;
        });

        // CPU 单独线程（采集需要停顿）
        if (enabledMetrics.contains(MetricType.CPU)) {
            cpuScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "cpu-collector");
                t.setDaemon(true);
                return t;
            });
            // CPU 采集间隔与上报间隔一致
            cpuScheduler.scheduleAtFixedRate(
                    this::collectCpu,
                    INITIAL_DELAY_CPU_SECONDS,
                    intervalSeconds,
                    TimeUnit.SECONDS);
        }

        // 启动时上报一次设备信息
        scheduler.schedule(this::reportDeviceInfo, INITIAL_DELAY_DEVICE_INFO_SECONDS, TimeUnit.SECONDS);

        // 定时上报指标
        scheduler.scheduleAtFixedRate(
                this::reportMetrics,
                INITIAL_DELAY_METRICS_SECONDS,
                intervalSeconds,
                TimeUnit.SECONDS);

        running = true;
        log.info("[DeviceReporter] 启动定时上报，间隔: {}秒，上报指标: {}", intervalSeconds, enabledMetrics);
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
        if (cpuScheduler != null) {
            cpuScheduler.shutdown();
            cpuScheduler = null;
        }
        running = false;
        log.info("[DeviceReporter] 停止定时上报");
    }

    /**
     * CPU 采集（单独线程执行，因需停顿）
     */
    private void collectCpu() {
        try {
            Map<String, Object> cpuData = monitor.cpuSnapshot(CPU_SAMPLING_MILLIS);
            cpuCache.set(cpuData);
            log.trace("[DeviceReporter] CPU采集完成: usage={}%", cpuData.get("cpuUsage"));
        } catch (Exception e) {
            log.error("[DeviceReporter] CPU采集失败", e);
        }
    }

    /**
     * 采集并上报指标
     */
    private void reportMetrics() {
        try {
            // 采集启用的指标
            Map<String, Object> metrics = collectMetrics(enabledMetrics);

            // 添加上报的指标类型列表（供服务端识别）
            List<String> reportedTypes = new ArrayList<>();
            for (MetricType type : enabledMetrics) {
                reportedTypes.add(type.getCode());
            }
            metrics.put("reportedMetrics", reportedTypes);
            metrics.put("reportTime", System.currentTimeMillis());

            syncClient.publish(MonitorTopics.DEVICE_METRICS, metrics);
            log.debug("[DeviceReporter] 上报设备指标: ip={}, metrics={}",
                    metrics.get("ipAddress"), reportedTypes);
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
     *
     * @return 设备信息
     */
    private Map<String, Object> collectDeviceInfo() {
        Map<String, Object> info = new HashMap<>();
        try {
            Map<String, Object> sysSnapshot = monitor.systemSnapshot();
            info.put("ipAddress", StringUtils.defaultIfBlank(host, (String) sysSnapshot.get("ipAddress")));
            info.put("osName", sysSnapshot.get("osName"));
            info.put("osVersion", sysSnapshot.get("osVersion"));
            info.put("osArch", sysSnapshot.get("osArch"));
        } catch (Exception e) {
            log.error("[DeviceReporter] 采集设备信息失败", e);
        }
        return info;
    }

    /**
     * 采集系统指标
     *
     * @param types 要采集的指标类型
     * @return 指标数据
     */
    private Map<String, Object> collectMetrics(Set<MetricType> types) {
        Map<String, Object> metrics = new HashMap<>();

        try {
            // IP 地址（用于匹配服务器，始终采集）
            Map<String, Object> sysSnapshot = monitor.systemSnapshot();
            metrics.put("ipAddress", StringUtils.defaultIfBlank(host, (String) sysSnapshot.get("ipAddress")));

            // CPU（从缓存获取，由单独线程更新）
            if (types.contains(MetricType.CPU)) {
                Map<String, Object> cpuData = cpuCache.get();
                if (cpuData != null && !cpuData.isEmpty()) {
                    metrics.putAll(cpuData);
                }
            }

            // 内存: 总大小、已使用大小、可用大小、时间
            if (types.contains(MetricType.MEMORY)) {
                metrics.put("memory", monitor.memorySnapshot());
            }

            // 磁盘: 每个磁盘的名称、大小、用量、时间 + 汇总数据
            if (types.contains(MetricType.DISK)) {
                List<Map<String, Object>> diskList = monitor.diskSnapshot();
                metrics.put("disks", diskList);

                long totalDiskSum = 0;
                long usedDiskSum = 0;
                long freeDiskSum = 0;
                for (Map<String, Object> disk : diskList) {
                    totalDiskSum += (Long) disk.get("total");
                    usedDiskSum += (Long) disk.get("used");
                    freeDiskSum += (Long) disk.get("free");
                }
                metrics.put("diskTotal", totalDiskSum);
                metrics.put("diskUsed", usedDiskSum);
                metrics.put("diskFree", freeDiskSum);
            }

            // 网卡: 每个网卡的名称、上行、下行、时间
            if (types.contains(MetricType.NETWORK)) {
                metrics.put("networks", monitor.networkSnapshot());
            }

        } catch (Exception e) {
            log.error("[DeviceReporter] 采集指标失败", e);
        }

        return metrics;
    }
}
