package com.chua.starter.monitor.support.spi.impl;


import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.monitor.support.entity.MsMonitor;
import com.chua.starter.monitor.support.service.MetricService;
import com.chua.starter.monitor.support.service.MonitorService;
import com.chua.starter.monitor.support.spi.CollectResult;
import com.chua.starter.monitor.support.spi.MetricPoint;
import com.chua.starter.server.support.entity.MsServerHost;
import com.chua.starter.server.support.model.vo.AgentPushVO;
import com.chua.starter.server.support.spi.HostAgentHistoryWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Agent 上报历史写入（主机域 → 监控域）。
 *
 * <p>实现服务端启动器的 {@link HostAgentHistoryWriter} 扩展点：把 {@code /ms/服务端/主机/Agent/推送}
 * 上报的 CPU/内存/磁盘使用率，落成归属监控项的历史时序点，走 {@link MetricService#writeCollectResult}
 * 主链路（明细表 + 时序引擎 + 告警评估 + 实时推送），与定时采集同一套读取口径。</p>
 *
 * <p>历史点只认监控 标识：主机 标识 与监控 标识 属不同序列，混用会把上报点写进别人的曲线，
 * 因此解析不到归属监控项时跳过并告警，绝不以主机 标识 顶替。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Spi(value = "agentHostHistory", order = 100)
public class AgentHostHistoryWriter implements HostAgentHistoryWriter {

    /**
     * 内存容量换算：MB 转 字节
    */
    private static final long MB = 1024L * 1024L;

    /**
     * 磁盘容量换算：GB 转 字节
    */
    private static final long GB = 1024L * MB;

    /**
     * 监控 服务，解析主机归属的监控项
    */
    @Autowired
    private MonitorService monitorService;

    /**
     * 指标 服务，历史点入库主链路
    */
    @Autowired
    private MetricService metricService;

    @Override
    /**
     * 写入。
     * @param host 主机
     * @param payload 上报负载
     */
    public void write(MsServerHost host, AgentPushVO payload) {
        if (host == null || host.getId() == null || payload == null) {
            return;
        }
        MsMonitor monitor = resolveMonitor(host.getId());
        if (monitor == null) {
            log.warn("[agent-history] 主机 {}({}) 无归属监控项，跳过历史写入", host.getId(), host.getServerHostName());
            return;
        }
        CollectResult result = new CollectResult();
        result.setMonitor(monitor);
        List<MetricPoint> points = result.getPoints();
        int added = 0;
        Map<String, String> cpuTags = new HashMap<>(2);
        if (payload.cpuCores() != null) {
            cpuTags.put("cores", String.valueOf(payload.cpuCores()));
        }
        added += addPoint(points, "cpu_usage", payload.cpuUsage(), "ms_monitor_metric_cpu", cpuTags);
        added += addPoint(points, "memory_usage", payload.memUsage(), "ms_monitor_metric_memory",
                capacityTags(payload.memoryMb() == null ? null : payload.memoryMb() * MB, payload.memUsage(), true));
        added += addPoint(points, "disk_usage", payload.diskUsage(), "ms_monitor_metric_disk",
                capacityTags(payload.diskGb() == null ? null : payload.diskGb() * GB, payload.diskUsage(), false));
        if (added == 0) {
            return;
        }
        ReturnResult<Void> written = metricService.writeCollectResult(result);
        if (!written.isOk()) {
            log.warn("[agent-history] 主机 {} 历史写入失败 monitorId={}: {}",
                    host.getId(), monitor.getId(), written.getMsg());
        }
    }

    /**
     * 由容量与使用率推导明细行的已用/可用字节。
     *
     * <p>Agent 协议只上报容量与百分数，不落 used_bytes 会让同一行的使用率与已用量自相矛盾。</p>
     *
     * @param totalBytes 总容量字节，可为 空
     * @param usagePercent 使用率，可为 空
     * @param withFree   是否附带 free_bytes
     * @return 标签集合，容量为空时返回空集合
     */
    private Map<String, String> capacityTags(Long totalBytes, Double usagePercent, boolean withFree) {
        Map<String, String> tags = new HashMap<>(4);
        if (totalBytes == null) {
            return tags;
        }
        tags.put("total_bytes", String.valueOf(totalBytes));
        // [P3C A20 豁免]
        long used = usagePercent == null ? 0L : Math.min(totalBytes, Math.round(totalBytes * usagePercent / 100.0));
        tags.put("used_bytes", String.valueOf(used));
        if (withFree) {
            tags.put("free_bytes", String.valueOf(totalBytes - used));
        }
        return tags;
    }

    /**
     * 解析主机归属的监控项，多个时取首个（同一台机器的多个监控项共享同一份上报值）
     *
     * @param hostId 主机 标识
     * @return 监控项，无归属返回 空
     */
    private MsMonitor resolveMonitor(Long hostId) {
        List<MsMonitor> monitors = monitorService.listByServerHost(hostId).getData();
        return monitors == null || monitors.isEmpty() ? null : monitors.get(0);
    }

    /**
     * 追加一个指标点，使用率为空时忽略该项。
     *
     * @param points 指标点集合
     * @param name   指标名
     * @param value  使用率，可为 空
     * @param target 目标明细表
     * @param tags   附加标签，可为空集合
     * @return 实际追加数量（0/1）
     */
    private int addPoint(List<MetricPoint> points, String name, Double value, String target,
                         Map<String, String> tags) {
        if (value == null) {
            return 0;
        }
        points.add(MetricPoint.builder()
                .metricName(name)
                .value(value)
                .unit("%")
                .tags(tags)
                .targetTable(target)
                .build());
        return 1;
    }
}
