package com.chua.starter.monitor.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.common.support.lang.json.Json;
import com.chua.starter.monitor.support.entity.MsMonitorMetric;
import com.chua.starter.monitor.support.entity.MsMonitorMetricCpu;
import com.chua.starter.monitor.support.entity.MsMonitorMetricDisk;
import com.chua.starter.monitor.support.entity.MsMonitorMetricJvm;
import com.chua.starter.monitor.support.entity.MsMonitorMetricMemory;
import com.chua.starter.monitor.support.entity.MsMonitorMetricNetwork;
import com.chua.starter.monitor.support.entity.MsMonitorMetricStatistics;
import com.chua.starter.monitor.support.mapper.MsMonitorMetricCpuMapper;
import com.chua.starter.monitor.support.mapper.MsMonitorMetricDiskMapper;
import com.chua.starter.monitor.support.mapper.MsMonitorMetricJvmMapper;
import com.chua.starter.monitor.support.mapper.MsMonitorMetricMapper;
import com.chua.starter.monitor.support.mapper.MsMonitorMetricMemoryMapper;
import com.chua.starter.monitor.support.mapper.MsMonitorMetricNetworkMapper;
import com.chua.starter.monitor.support.mapper.MsMonitorMetricStatisticsMapper;
import com.chua.starter.monitor.support.service.MetricService;
import com.chua.starter.monitor.support.spi.CollectResult;
import com.chua.starter.monitor.support.spi.MetricPoint;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.DoubleSummaryStatistics;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 监控指标 Service 实现
 *
 * @author CH
 * @since 2026/8/4
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MetricServiceImpl implements MetricService {

    private final MsMonitorMetricMapper metricMapper;
    private final MsMonitorMetricStatisticsMapper statisticsMapper;
    private final MsMonitorMetricCpuMapper cpuMapper;
    private final MsMonitorMetricMemoryMapper memoryMapper;
    private final MsMonitorMetricDiskMapper diskMapper;
    private final MsMonitorMetricNetworkMapper networkMapper;
    private final MsMonitorMetricJvmMapper jvmMapper;

    @Override
    public Mono<Void> writeCollectResult(CollectResult result) {
        if (result == null || result.getMonitor() == null) {
            return Mono.empty();
        }
        List<MetricPoint> points = result.getPoints();
        if (points == null || points.isEmpty()) {
            return Mono.empty();
        }
        Long monitorId = result.getMonitor().getId();
        LocalDateTime ts = result.getCollectTime() == null ? LocalDateTime.now() : result.getCollectTime();
        return writePoints(monitorId, points, ts);
    }

    @Override
    public Mono<Void> writePoints(Long monitorId, List<MetricPoint> points, LocalDateTime collectTime) {
        if (points == null || points.isEmpty()) {
            return Mono.empty();
        }
        final LocalDateTime ts = collectTime == null ? LocalDateTime.now() : collectTime;
        return Mono.fromRunnable(() -> doWrite(monitorId, points, ts))
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    /**
     * 同步写入
     *
     * @param monitorId 主机 id
     * @param points    指标点
     * @param ts        时间戳
     */
    private void doWrite(Long monitorId, List<MetricPoint> points, LocalDateTime ts) {
        List<MsMonitorMetric> general = new ArrayList<>();
        List<MsMonitorMetricCpu> cpus = new ArrayList<>();
        List<MsMonitorMetricMemory> memories = new ArrayList<>();
        List<MsMonitorMetricDisk> disks = new ArrayList<>();
        List<MsMonitorMetricNetwork> nets = new ArrayList<>();
        List<MsMonitorMetricJvm> jvms = new ArrayList<>();

        for (MetricPoint p : points) {
            String name = p.getMetricName();
            if (name == null) {
                continue;
            }
            String tags = Json.toJson(p.getTags());
            String table = p.getTargetTable();

            // 按 target_table 路由到结构化子表
            if ("ms_monitor_metric_cpu".equals(table)) {
                MsMonitorMetricCpu cpu = new MsMonitorMetricCpu();
                cpu.setMonitorId(monitorId);
                cpu.setCollectTime(ts);
                cpu.setCpuUsage(p.getValue());
                if (p.getTags() != null) {
                    cpu.setCpuModel(p.getTags().get("model"));
                    cpu.setCpuCores(parseInt(p.getTags().get("cores")));
                }
                cpus.add(cpu);
                continue;
            }
            if ("ms_monitor_metric_memory".equals(table)) {
                MsMonitorMetricMemory mem = new MsMonitorMetricMemory();
                mem.setMonitorId(monitorId);
                mem.setCollectTime(ts);
                mem.setUsagePercent(p.getValue());
                if (p.getTags() != null) {
                    mem.setTotalBytes(parseLong(p.getTags().get("total_bytes")));
                    mem.setUsedBytes(parseLong(p.getTags().get("used_bytes")));
                    mem.setFreeBytes(parseLong(p.getTags().get("free_bytes")));
                }
                memories.add(mem);
                continue;
            }
            if ("ms_monitor_metric_disk".equals(table)) {
                MsMonitorMetricDisk disk = new MsMonitorMetricDisk();
                disk.setMonitorId(monitorId);
                disk.setCollectTime(ts);
                disk.setUsagePercent(p.getValue());
                if (p.getTags() != null) {
                    disk.setMountPoint(p.getTags().get("mount"));
                    disk.setFileSystem(p.getTags().get("fs"));
                    disk.setTotalBytes(parseLong(p.getTags().get("total_bytes")));
                    disk.setUsedBytes(parseLong(p.getTags().get("used_bytes")));
                }
                disks.add(disk);
                continue;
            }
            if ("ms_monitor_metric_network".equals(table)) {
                MsMonitorMetricNetwork net = new MsMonitorMetricNetwork();
                net.setMonitorId(monitorId);
                net.setCollectTime(ts);
                if (p.getTags() != null) {
                    net.setInterfaceName(p.getTags().get("iface"));
                    net.setRxBytes(parseLong(p.getTags().get("rx_bytes")));
                    net.setTxBytes(parseLong(p.getTags().get("tx_bytes")));
                    net.setSpeedBps(parseLong(p.getTags().get("speed_bps")));
                }
                nets.add(net);
                continue;
            }
            if ("ms_monitor_metric_jvm".equals(table)) {
                MsMonitorMetricJvm jvm = new MsMonitorMetricJvm();
                jvm.setMonitorId(monitorId);
                jvm.setCollectTime(ts);
                jvm.setHeapUsagePercent(p.getValue());
                if (p.getTags() != null) {
                    jvm.setHeapUsedBytes(parseLong(p.getTags().get("heap_used_bytes")));
                    jvm.setHeapMaxBytes(parseLong(p.getTags().get("heap_max_bytes")));
                    jvm.setYoungGcCount(parseLong(p.getTags().get("young_gc_count")));
                    jvm.setFullGcCount(parseLong(p.getTags().get("full_gc_count")));
                    jvm.setThreads(parseInt(p.getTags().get("threads")));
                    jvm.setDaemonThreads(parseInt(p.getTags().get("daemon_threads")));
                }
                jvms.add(jvm);
                continue;
            }

            // 默认入通用时序
            MsMonitorMetric metric = new MsMonitorMetric();
            metric.setMonitorId(monitorId);
            metric.setMetricName(name);
            metric.setMetricValue(p.getValue());
            metric.setMetricTags(tags);
            metric.setCollectTime(ts);
            general.add(metric);
        }

        if (!general.isEmpty()) {
            general.forEach(metricMapper::insert);
        }
        if (!cpus.isEmpty()) {
            cpus.forEach(cpuMapper::insert);
        }
        if (!memories.isEmpty()) {
            memories.forEach(memoryMapper::insert);
        }
        if (!disks.isEmpty()) {
            disks.forEach(diskMapper::insert);
        }
        if (!nets.isEmpty()) {
            nets.forEach(networkMapper::insert);
        }
        if (!jvms.isEmpty()) {
            jvms.forEach(jvmMapper::insert);
        }
    }

    @Override
    public Mono<Void> aggregateHourly() {
        return Mono.fromRunnable(this::doAggregate)
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    /**
     * 小时聚合
     */
    private void doAggregate() {
        LocalDateTime bucketEnd = LocalDateTime.now().withMinute(0).withSecond(0).withNano(0);
        LocalDateTime bucketStart = bucketEnd.minusHours(1);
        LambdaQueryWrapper<MsMonitorMetric> wrapper = new LambdaQueryWrapper<>();
        wrapper.between(MsMonitorMetric::getCollectTime, bucketStart, bucketEnd);
        List<MsMonitorMetric> rows = metricMapper.selectList(wrapper);
        if (rows.isEmpty()) {
            return;
        }
        Map<String, List<MsMonitorMetric>> groups = rows.stream()
                .collect(Collectors.groupingBy(r -> r.getMonitorId() + ":" + r.getMetricName()));
        for (Map.Entry<String, List<MsMonitorMetric>> entry : groups.entrySet()) {
            String[] parts = entry.getKey().split(":");
            Long monitorId = Long.parseLong(parts[0]);
            String metricName = parts[1];
            DoubleSummaryStatistics stat = entry.getValue().stream()
                    .collect(Collectors.summarizingDouble(MsMonitorMetric::getMetricValue));
            MsMonitorMetricStatistics s = new MsMonitorMetricStatistics();
            s.setMonitorId(monitorId);
            s.setMetricName(metricName);
            s.setHourBucket(bucketEnd);
            s.setAvgValue(stat.getAverage());
            s.setMinValue(stat.getMin());
            s.setMaxValue(stat.getMax());
            s.setCountValue(stat.getCount());
            statisticsMapper.insert(s);
        }
        log.info("[MetricService] 小时聚合完成 bucket={} rows={}", bucketEnd, rows.size());
    }

    private static Long parseLong(String s) {
        if (s == null) {
            return null;
        }
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer parseInt(String s) {
        if (s == null) {
            return null;
        }
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}