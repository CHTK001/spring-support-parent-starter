package com.chua.starter.monitor.core.client;

import com.chua.starter.monitor.core.spi.MetricPoint;
import lombok.extern.slf4j.Slf4j;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.software.os.OSFileStore;
import oshi.software.os.OperatingSystem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 基于 OSHI 的本机指标采集器（客户端默认实现）。
 *
 * <p>指标口径与服务端本机采集（ServerLocalProvider）对齐：
 * cpu_usage / memory_usage / disk_usage，单位 %，目标子表
 * ms_monitor_metric_cpu / _memory / _disk，保证服务端曲线与告警规则直接可用。</p>
 *
 * <p>OSHI 涉及原生库加载，初始化失败（如缺 dll）不应拖垮客户端进程：
 * 首次采集时懒加载并锁定失败状态，失败后仅返回空列表并告警一次。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Slf4j
public class OshiMetricCollector implements MonitorMetricCollector {

    /**
     * CPU 指标落库子表
     */
    private static final String TABLE_CPU = "ms_monitor_metric_cpu";

    /**
     * 内存指标落库子表
     */
    private static final String TABLE_MEMORY = "ms_monitor_metric_memory";

    /**
     * 磁盘指标落库子表
     */
    private static final String TABLE_DISK = "ms_monitor_metric_disk";

    /**
     * OSHI 是否已初始化成功
     */
    private volatile boolean initialized;

    /**
     * OSHI 是否已初始化失败（失败后不再重试，避免每轮刷异常）
     */
    private volatile boolean initFailed;

    private CentralProcessor processor;
    private GlobalMemory memory;
    private OperatingSystem os;

    /**
     * 上一次 CPU tick 采样（用于差值计算使用率）
     */
    private long[] prevTicks;

    @Override
    public List<MetricPoint> collect() {
        List<MetricPoint> points = new ArrayList<>();
        if (!ensureInit()) {
            return points;
        }
        try {
            collectCpu(points);
        } catch (Throwable e) {
            log.warn("[monitor-client] CPU 采集失败: {}", e.getMessage());
        }
        try {
            collectMemory(points);
        } catch (Throwable e) {
            log.warn("[monitor-client] 内存采集失败: {}", e.getMessage());
        }
        try {
            collectDisk(points);
        } catch (Throwable e) {
            log.warn("[monitor-client] 磁盘采集失败: {}", e.getMessage());
        }
        return points;
    }

    /**
     * 懒加载 OSHI（原生库失败不致命）。
     *
     * @return 是否可用
     */
    private synchronized boolean ensureInit() {
        if (initialized) {
            return true;
        }
        if (initFailed) {
            return false;
        }
        try {
            SystemInfo systemInfo = new SystemInfo();
            HardwareAbstractionLayer hardware = systemInfo.getHardware();
            processor = hardware.getProcessor();
            memory = hardware.getMemory();
            os = systemInfo.getOperatingSystem();
            prevTicks = processor.getSystemCpuLoadTicks();
            initialized = true;
            log.info("[monitor-client] OSHI 本机采集器就绪: os={} {}, cores={}",
                    os.getManufacturer(),
                    os.getFamily(),
                    processor.getLogicalProcessorCount());
            return true;
        } catch (Throwable e) {
            initFailed = true;
            log.warn("[monitor-client] OSHI 初始化失败，本机指标采集不可用（心跳仍将继续）: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 采集 CPU 平均使用率（口径同 ServerLocalProvider：cores/model tags）。
     *
     * @param points 输出列表
     */
    private void collectCpu(List<MetricPoint> points) {
        long[] ticks = processor.getSystemCpuLoadTicks();
        double load = processor.getSystemCpuLoadBetweenTicks(prevTicks) * 100.0;
        prevTicks = ticks;
        if (load < 0.0) {
            load = 0.0;
        }
        if (load > 100.0) {
            load = 100.0;
        }
        Map<String, String> tags = new HashMap<>();
        tags.put("cores", String.valueOf(processor.getLogicalProcessorCount()));
        String model = processor.getProcessorIdentifier() == null ? null : processor.getProcessorIdentifier().getName();
        tags.put("model", model == null || model.isBlank() ? "unknown" : model);
        points.add(MetricPoint.builder()
                .metricName("cpu_usage")
                .value(round2(load))
                .unit("%")
                .tags(tags)
                .targetTable(TABLE_CPU)
                .build());
    }

    /**
     * 采集内存使用率（口径同 ServerLocalProvider：total/used/free bytes tags）。
     *
     * @param points 输出列表
     */
    private void collectMemory(List<MetricPoint> points) {
        long total = memory.getTotal();
        if (total <= 0) {
            return;
        }
        long free = memory.getAvailable();
        long used = total - free;
        double percent = used * 100.0 / total;
        Map<String, String> tags = new HashMap<>();
        tags.put("total_bytes", String.valueOf(total));
        tags.put("used_bytes", String.valueOf(used));
        tags.put("free_bytes", String.valueOf(free));
        points.add(MetricPoint.builder()
                .metricName("memory_usage")
                .value(round2(percent))
                .unit("%")
                .tags(tags)
                .targetTable(TABLE_MEMORY)
                .build());
    }

    /**
     * 采集各挂载点磁盘使用率（口径同 ServerLocalProvider：mount/fs/bytes tags）。
     *
     * @param points 输出列表
     */
    private void collectDisk(List<MetricPoint> points) {
        List<OSFileStore> stores = os.getFileSystem().getFileStores();
        if (stores == null) {
            return;
        }
        for (OSFileStore store : stores) {
            if (store == null || store.getMount() == null) {
                continue;
            }
            try {
                long total = store.getTotalSpace();
                if (total <= 0) {
                    continue;
                }
                long free = store.getUsableSpace();
                long used = total - free;
                double percent = used * 100.0 / total;
                Map<String, String> tags = new HashMap<>();
                tags.put("mount", store.getMount());
                tags.put("fs", store.getType() == null ? "" : store.getType());
                tags.put("total_bytes", String.valueOf(total));
                tags.put("used_bytes", String.valueOf(used));
                tags.put("free_bytes", String.valueOf(free));
                points.add(MetricPoint.builder()
                        .metricName("disk_usage")
                        .value(round2(percent))
                        .unit("%")
                        .tags(tags)
                        .targetTable(TABLE_DISK)
                        .build());
            } catch (Throwable e) {
                log.debug("[monitor-client] 磁盘分区采集失败 mount={}: {}", store.getMount(), e.getMessage());
            }
        }
    }

    /**
     * 保留两位小数，避免浮点噪声入库。
     *
     * @param value 原始值
     * @return 两位小数值
     */
    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
