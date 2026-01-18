package com.chua.report.client.starter.report;

import com.chua.oshi.support.*;
import com.chua.report.client.starter.sync.MonitorTopics;
import com.chua.sync.support.client.SyncClient;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.*;
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
 * @author CH
 * @version 1.0.0
 * @since 2024/12/05
 */
@Slf4j
public class DeviceMetricsReporter {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DeviceMetricsReporter.class);

    private static final DeviceMetricsReporter INSTANCE = new DeviceMetricsReporter();

    @Setter
    private SyncClient syncClient;

    /**
     * 设置 SyncClient
     *
     * @param syncClient SyncClient 实例
     */
    public void setSyncClient(SyncClient syncClient) {
        this.syncClient = syncClient;
    }

    @Setter
    private String appName;

    /**
     * 设置应用名称
     *
     * @param appName 应用名称
     */
    public void setAppName(String appName) {
        this.appName = appName;
    }

    @Setter
    private String host;

    /**
     * 设置主机地址
     *
     * @param host 主机地址
     */
    public void setHost(String host) {
        this.host = host;
    }

    /**
     * 主调度器
     */
    private ScheduledExecutorService scheduler;

    /**
     * CPU 采集线程（单独维护）
     */
    private ScheduledExecutorService cpuScheduler;

    private volatile boolean running = false;

    /**
     * 上报间隔（秒）
     */
    @Setter
    private long intervalSeconds = 30;

    /**
     * 设置上报间隔
     *
     * @param intervalSeconds 上报间隔（秒）
     */
    public void setIntervalSeconds(long intervalSeconds) {
        this.intervalSeconds = intervalSeconds;
    }

    /**
     * 要上报的指标类型（默认全部）
     */
    @Setter
    private Set<MetricType> enabledMetrics = new HashSet<>(Arrays.asList(MetricType.values()));

    /**
     * CPU 使用率缓存（由单独线程更新）
     */
    private final AtomicReference<Map<String, Object>> cpuCache = new AtomicReference<>(new HashMap<>());

    private DeviceMetricsReporter() {
    }

    public static DeviceMetricsReporter getInstance() {
        return INSTANCE;
    }

    /**
     * 启动定时上报
     *
     * @author CH
     * @since 1.0.0
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
            cpuScheduler.scheduleAtFixedRate(this::collectCpu, 5, intervalSeconds, TimeUnit.SECONDS);
        }

        // 启动时上报一次设备信息
        scheduler.schedule(this::reportDeviceInfo, 20, TimeUnit.SECONDS);

        // 定时上报指标
        scheduler.scheduleAtFixedRate(this::reportMetrics, 10, intervalSeconds, TimeUnit.SECONDS);

        running = true;
        log.info("[DeviceReporter] 启动定时上报，间隔: {}秒，上报指标: {}", intervalSeconds, enabledMetrics);
    }

    /**
     * 停止上报
     *
     * @author CH
     * @since 1.0.0
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
     *
     * @author CH
     * @since 1.0.0
     */
    private void collectCpu() {
        try {
            Cpu cpu = Oshi.newCpu(1000);
            Map<String, Object> cpuData = new HashMap<>();
            cpuData.put("cpuUsage", 100 - cpu.getFree());
            cpuData.put("cpuUser", cpu.getUser());
            cpuData.put("cpuSystem", cpu.getSys());
            cpuData.put("cpuWait", cpu.getWait());
            cpuData.put("cpuTime", System.currentTimeMillis());
            cpuCache.set(cpuData);
            log.trace("[DeviceReporter] CPU采集完成: usage={}%", cpuData.get("cpuUsage"));
        } catch (Exception e) {
            log.error("[DeviceReporter] CPU采集失败", e);
        }
    }

    /**
     * 采集并上报指标
     *
     * @author CH
     * @since 1.0.0
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
     *
     * @param types 要采集的指标类型
     * @return 指标数据
     * @author CH
     * @since 1.0.0
     */
    private Map<String, Object> collectMetrics(Set<MetricType> types) {
        Map<String, Object> metrics = new HashMap<>();

        try {
            // IP 地址（用于匹配服务器，始终采集）
            Sys sys = Oshi.newSys();
            metrics.put("ipAddress", StringUtils.defaultIfBlank(host, sys.getComputerIp()));

            // CPU（从缓存获取，由单独线程更新）
            if (types.contains(MetricType.CPU)) {
                Map<String, Object> cpuData = cpuCache.get();
                if (cpuData != null && !cpuData.isEmpty()) {
                    metrics.putAll(cpuData);
                }
            }

            // 内存: 总大小、已使用大小、可用大小、时间
            if (types.contains(MetricType.MEMORY)) {
                Mem mem = Oshi.newMem();
                Map<String, Object> memoryData = new HashMap<>();
                memoryData.put("memoryTotal", mem.getTotal());
                memoryData.put("memoryUsed", mem.getUsed());
                memoryData.put("memoryFree", mem.getFree());
                memoryData.put("memoryUsage", mem.getUsage());
                memoryData.put("memoryTime", System.currentTimeMillis());
                metrics.put("memory", memoryData);
            }

            // 磁盘: 每个磁盘的名称、大小、用量、时间
            if (types.contains(MetricType.DISK)) {
                List<SysFile> sysFiles = Oshi.newSysFile();
                List<Map<String, Object>> diskList = new ArrayList<>();
                long collectTime = System.currentTimeMillis();
                long totalDiskSum = 0;
                long usedDiskSum = 0;
                long freeDiskSum = 0;

                for (SysFile sf : sysFiles) {
                    Map<String, Object> diskData = new HashMap<>();
                    diskData.put("name", sf.getDirName());
                    diskData.put("total", sf.getTotal());
                    diskData.put("used", sf.getUsed());
                    diskData.put("free", sf.getFree());
                    diskData.put("usage", sf.getUsage());
                    diskData.put("type", sf.getTypeName());
                    diskData.put("time", collectTime);
                    diskList.add(diskData);

                    totalDiskSum += sf.getTotal();
                    usedDiskSum += sf.getUsed();
                    freeDiskSum += sf.getFree();
                }
                metrics.put("disks", diskList);
                // 汇总数据
                metrics.put("diskTotal", totalDiskSum);
                metrics.put("diskUsed", usedDiskSum);
                metrics.put("diskFree", freeDiskSum);
            }

            // 网卡: 每个网卡的名称、上行、下行、时间
            if (types.contains(MetricType.NETWORK)) {
                List<Network> networks = Oshi.newNetwork();
                List<Map<String, Object>> networkList = new ArrayList<>();
                long collectTime = System.currentTimeMillis();

                for (Network net : networks) {
                    Map<String, Object> netData = new HashMap<>();
                    netData.put("name", net.getName());
                    netData.put("displayName", net.getDisplayName());
                    netData.put("mac", net.getMac());
                    netData.put("receiveBytes", net.getReceiveBytes());
                    netData.put("transmitBytes", net.getTransmitBytes());
                    netData.put("time", collectTime);
                    networkList.add(netData);
                }
                metrics.put("networks", networkList);
            }

        } catch (Exception e) {
            log.error("[DeviceReporter] 采集指标失败", e);
        }

        return metrics;
    }
}
