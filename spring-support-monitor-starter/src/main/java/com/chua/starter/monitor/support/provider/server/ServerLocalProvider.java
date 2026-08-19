package com.chua.starter.monitor.support.provider.server;

import com.chua.metrics.support.CpuCore;
import com.chua.metrics.support.DiskInfo;
import com.chua.metrics.support.DiskIo;
import com.chua.metrics.support.MemorySlot;
import com.chua.metrics.support.MetricsService;
import com.chua.metrics.support.MetricsSnapshot;
import com.chua.metrics.support.NetworkInterface;
import com.chua.metrics.support.SystemLoad;
import com.chua.starter.monitor.support.enums.ProviderType;
import com.chua.starter.monitor.support.enums.ServiceType;
import com.chua.starter.monitor.support.service.MonitorConfigService;
import com.chua.starter.monitor.support.spi.CollectContext;
import com.chua.starter.monitor.support.spi.CollectResult;
import com.chua.starter.monitor.support.spi.MetricPoint;
import com.chua.starter.monitor.support.spi.MetricProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 服务器本地采集 Provider
 * <p>
 * 基于 utils-support-metrics-starter 直采本机 CPU/内存/磁盘/网络/负载等指标,
 * macOS 下 native 库不支持, 通过配置项跳过。
 * </p>
 *
 * @since 2026/8/4
 * @author CH
 */
@Slf4j
@Component
public class ServerLocalProvider implements MetricProvider {

    /**
     * macOS 跳过配置项
     */
    private static final String CONFIG_KEY_SKIP_MACOS = "monitor.local.skip.macos";

    /**
     * MetricsService 提供者(可能未启用)
     */
    private final ObjectProvider<MetricsService> metricsServiceProvider;

    /**
     * 全局配置 Service
     */
    private final MonitorConfigService configService;

    /**
     * 构造方法
     *
     * @param metricsServiceProvider MetricsService 提供者
     * @param configService          全局配置 Service
     */
    @Autowired
    public ServerLocalProvider(ObjectProvider<MetricsService> metricsServiceProvider,
                               MonitorConfigService configService) {
        this.metricsServiceProvider = metricsServiceProvider;
        this.configService = configService;
    }

    @Override
    public ProviderType providerType() {
        return ProviderType.LOCAL;
    }

    @Override
    public Set<ServiceType> supportServiceTypes() {
        return Set.of(ServiceType.SERVER);
    }

    @Override
    public Mono<CollectResult> collect(CollectContext context) {
        return Mono.fromCallable(() -> doCollect(context))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 同步执行本地采集
     *
     * @param context 采集上下文
     * @return 采集结果
     */

    private CollectResult doCollect(CollectContext context) {
        CollectResult result = new CollectResult();
        result.setTask(context.getTask());
        result.setMonitor(context.getMonitor());

        MetricsService metricsService = metricsServiceProvider.getIfAvailable();
        if (metricsService == null) {
            return result.fail("utils-support-metrics-starter 未启用");
        }

        if (isMacOs()) {
            boolean skip = configService.getBoolean(CONFIG_KEY_SKIP_MACOS, true);
            if (skip) {
                log.debug("[ServerLocalProvider] macOS 已配置跳过 task={}", context.getTask().getId());
                return result.fail("macOS 平台已配置跳过");
            }
        }

        try {
            MetricsSnapshot snapshot = metricsService.getCurrentSnapshot();
            if (snapshot == null) {
                return result.fail("无指标数据");
            }

            collectCpu(snapshot, result);
            collectMemory(snapshot, result);
            collectDisk(snapshot, result);
            collectDiskIo(snapshot, result);
            collectNetwork(snapshot, result);
            collectSystemLoad(snapshot, result);

            result.setSuccess(true);
        } catch (Throwable e) {
            log.error("[ServerLocalProvider] 采集异常", e);
            result.fail(e.getMessage());
        }
        return result;
    }

    /**
     * 采集 CPU
     *
     * @param snapshot 指标快照
     * @param result   采集结果
     */
    private void collectCpu(MetricsSnapshot snapshot, CollectResult result) {
        List<CpuCore> cpuCores = snapshot.getCpuCores();
        if (cpuCores == null || cpuCores.isEmpty()) {
            return;
        }

        double totalUsage = 0.0;
        String model = null;
        for (CpuCore core : cpuCores) {
            totalUsage += core.getUsage();
            if (model == null && core.getName() != null) {
                model = core.getName();
            }
        }
        int cores = cpuCores.size();
        double avgUsage = totalUsage / cores;

        Map<String, String> tags = new HashMap<>();
        tags.put("cores", String.valueOf(cores));
        tags.put("model", model == null ? "native_metrics" : model);

        MetricPoint pt = MetricPoint.builder()
                .metricName("cpu_usage")
                .value(avgUsage)
                .unit("%")
                .tags(tags)
                .targetTable("ms_monitor_metric_cpu")
                .build();
        result.addPoint(pt);
    }

    /**
     * 采集内存
     *
     * @param snapshot 指标快照
     * @param result   采集结果
     */
    private void collectMemory(MetricsSnapshot snapshot, CollectResult result) {
        long total = snapshot.getSwapTotal();
        long used = snapshot.getSwapUsed();

        List<MemorySlot> slots = snapshot.getMemorySlots();
        if (slots != null) {
            for (MemorySlot slot : slots) {
                total += slot.getTotal();
                used += slot.getUsed();
            }
        }

        long free = total > 0 ? total - used : 0L;
        double usagePercent = total > 0 ? used * 100.0 / total : 0.0;

        Map<String, String> tags = new HashMap<>();
        tags.put("total_bytes", String.valueOf(total));
        tags.put("used_bytes", String.valueOf(used));
        tags.put("free_bytes", String.valueOf(free));

        MetricPoint pt = MetricPoint.builder()
                .metricName("memory_usage")
                .value(usagePercent)
                .unit("%")
                .tags(tags)
                .targetTable("ms_monitor_metric_memory")
                .build();
        result.addPoint(pt);
    }

    /**
     * 采集磁盘
     *
     * @param snapshot 指标快照
     * @param result   采集结果
     */
    private void collectDisk(MetricsSnapshot snapshot, CollectResult result) {
        List<DiskInfo> disks = snapshot.getDisks();
        if (disks == null || disks.isEmpty()) {
            return;
        }

        for (DiskInfo disk : disks) {
            if (disk == null || disk.getMountPoint() == null) {
                continue;
            }

            long total = disk.getTotal();
            long used = disk.getUsed();
            long free = total > 0 ? total - used : 0L;
            double usagePercent = total > 0 ? used * 100.0 / total : 0.0;

            Map<String, String> tags = new HashMap<>();
            tags.put("mount", disk.getMountPoint());
            tags.put("fs", disk.getFileSystem());
            tags.put("total_bytes", String.valueOf(total));
            tags.put("used_bytes", String.valueOf(used));
            tags.put("free_bytes", String.valueOf(free));

            MetricPoint pt = MetricPoint.builder()
                    .metricName("disk_usage")
                    .value(usagePercent)
                    .unit("%")
                    .tags(tags)
                    .targetTable("ms_monitor_metric_disk")
                    .build();
            result.addPoint(pt);
        }
    }

    /**
     * 采集磁盘 IO(累计值, 由上层按差值计算速率)
     *
     * @param snapshot 指标快照
     * @param result   采集结果
     */
    private void collectDiskIo(MetricsSnapshot snapshot, CollectResult result) {
        List<DiskIo> ioList = snapshot.getDiskIo();
        if (ioList == null || ioList.isEmpty()) {
            return;
        }

        for (DiskIo io : ioList) {
            if (io == null || io.getName() == null) {
                continue;
            }

            Map<String, String> tags = new HashMap<>();
            tags.put("device", io.getName());
            tags.put("read_bytes", String.valueOf(io.getReadBytes()));
            tags.put("write_bytes", String.valueOf(io.getWriteBytes()));

            MetricPoint pt = MetricPoint.builder()
                    .metricName("disk_io_write_bytes")
                    .value((double) io.getWriteBytes())
                    .unit("bytes")
                    .tags(tags)
                    .targetTable("ms_monitor_metric_disk")
                    .build();
            result.addPoint(pt);
        }
    }

    /**
     * 采集网络(累计值)
     *
     * @param snapshot 指标快照
     * @param result   采集结果
     */
    private void collectNetwork(MetricsSnapshot snapshot, CollectResult result) {
        List<NetworkInterface> nets = snapshot.getNetworks();
        if (nets == null || nets.isEmpty()) {
            return;
        }

        for (NetworkInterface net : nets) {
            if (net == null || net.getName() == null || net.getName().startsWith("lo")) {
                continue;
            }

            Map<String, String> tags = new HashMap<>();
            tags.put("iface", net.getName());
            tags.put("rx_bytes", String.valueOf(net.getReceivedBytes()));
            tags.put("tx_bytes", String.valueOf(net.getTransmittedBytes()));

            MetricPoint pt = MetricPoint.builder()
                    .metricName("network_rx_bytes")
                    .value((double) net.getReceivedBytes())
                    .unit("bytes")
                    .tags(tags)
                    .targetTable("ms_monitor_metric_network")
                    .build();
            result.addPoint(pt);
        }
    }

    /**
     * 采集系统负载
     *
     * @param snapshot 指标快照
     * @param result   采集结果
     */
    private void collectSystemLoad(MetricsSnapshot snapshot, CollectResult result) {
        SystemLoad load = snapshot.getLoad();
        if (load == null) {
            return;
        }

        Map<String, String> tag1 = new HashMap<>();
        tag1.put("type", "1min");
        result.addPoint(MetricPoint.builder()
                .metricName("system_load_1m")
                .value(load.getLoad1())
                .unit("avg")
                .tags(tag1)
                .targetTable("ms_monitor_metric")
                .build());

        Map<String, String> tag5 = new HashMap<>();
        tag5.put("type", "5min");
        result.addPoint(MetricPoint.builder()
                .metricName("system_load_5m")
                .value(load.getLoad5())
                .unit("avg")
                .tags(tag5)
                .targetTable("ms_monitor_metric")
                .build());

        Map<String, String> tag15 = new HashMap<>();
        tag15.put("type", "15min");
        result.addPoint(MetricPoint.builder()
                .metricName("system_load_15m")
                .value(load.getLoad15())
                .unit("avg")
                .tags(tag15)
                .targetTable("ms_monitor_metric")
                .build());
    }

    /**
     * 检测是否为 macOS
     *
     * @return 是否
     */
    private boolean isMacOs() {
        String os = System.getProperty("os.name");
        return os != null && os.toLowerCase().contains("mac");
    }
}