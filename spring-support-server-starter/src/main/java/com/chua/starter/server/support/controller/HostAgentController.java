package com.chua.starter.server.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.lang.datasource.series.SeriesEngine;
import com.chua.starter.server.support.constants.ServerConstants;
import com.chua.starter.server.support.entity.MsServerHost;
import com.chua.starter.server.support.enums.HostCollectType;
import com.chua.starter.server.support.mapper.MsServerHostMapper;
import com.chua.starter.server.support.model.vo.AgentPushVO;
import com.chua.starter.server.support.spi.HostCollectContext;
import com.chua.starter.server.support.spi.HostCollectProvider;
import com.chua.starter.server.support.spi.HostCollectProviderRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;


/**
 * 服务器资产 Agent 上报接口
 * <p>
 * 目标主机上的 Agent 按 Agent键 主动推送硬件/状态数据，
 * 由 {@link HostCollectProviderRegistry} 解析出的 AGENT 采集实现处理回写。用于 NAT / 防火墙下
 * 服务端无法直连的主机采集。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Tag(name = "服务器资产-Agent上报", description = "Agent 主动推送采集数据")
@RestController
@RequestMapping(ServerConstants.API_PREFIX + "/host/agent")
@RequiredArgsConstructor
public class HostAgentController {

    /**
     * 采集提供者注册中心
    */
    private final HostCollectProviderRegistry collectRegistry;

    /**
     * 主机 映射器
    */
    private final MsServerHostMapper hostMapper;

    /**
     * 时序引擎（可选，未配置时为 null）
    */
    @Autowired(required = false)
    private SeriesEngine seriesEngine;

    /**
     * Agent 推送数据（按 Agent键 定位主机）
     *
     * @param agentKey 探针标识
     * @param payload  上报负载（cpu核心/内存mb/diskgb/os版本/公共ip/私募ip 等）
     * @return 处理结果
     */
    @Operation(summary = "Agent推送采集数据")
    @PostMapping("/{agentKey}/push")
    public Mono<ReturnResult<String>> push(@PathVariable String agentKey, @RequestBody AgentPushVO payload) {
        if (agentKey == null || agentKey.isBlank()) {
            return Mono.just(ReturnResult.error("agentKey 不能为空"));
        }
        MsServerHost host = hostMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<MsServerHost>()
                        .eq(MsServerHost::getServerHostAgentKey, agentKey.trim()));
        if (host == null) {
            return Mono.just(ReturnResult.error("未找到 agentKey 对应的主机: " + agentKey));
        }
        // 校验采集方式：只有 AGENT 模式才接受 agent 数据
        if (!"AGENT".equals(host.getServerHostCollectMode())) {
            return Mono.just(ReturnResult.error("主机采集方式不是 AGENT，拒绝接收 agent 数据: " + host.getServerHostCollectMode()));
        }
        try {
            if (payload != null) {
                applyPayload(host, payload);
                // 写历史时序指标（CPU/内存/磁盘）
                writeHistoryMetrics(host, payload);
            }
            HostCollectContext context = HostCollectContext.builder()
                    .mode(HostCollectType.AGENT)
                    .host(host)
                    .build();
            HostCollectProvider provider = collectRegistry.resolve(HostCollectType.AGENT)
                    .orElseThrow(() -> new IllegalStateException("AGENT 采集实现未注册"));
            return provider.collect(context)
                    .map(outcome -> outcome.isSuccess()
                            ? ReturnResult.ok(outcome.getMessage())
                            : ReturnResult.error(outcome.getMessage()));
        } catch (Exception e) {
            return Mono.just(ReturnResult.error("Agent 数据回写失败: " + e.getMessage()));
        }
    }

    /**
     * 将上报负载写入主机字段
     *
     * @param host    主机
     * @param payload 上报数据
     */
    private void applyPayload(MsServerHost host, AgentPushVO payload) {
        if (payload.cpuCores() != null) {
            host.setServerHostCpuCores(payload.cpuCores());
        }
        if (payload.memoryMb() != null) {
            host.setServerHostMemoryMb(payload.memoryMb());
        }
        if (payload.diskGb() != null) {
            host.setServerHostDiskGb(payload.diskGb());
        }
        if (payload.osVersion() != null) {
            host.setServerHostOsVersion(payload.osVersion());
        }
        if (payload.osArch() != null) {
            host.setServerHostOsArch(payload.osArch());
        }
        if (payload.gpuCount() != null) {
            host.setServerHostGpuCount(payload.gpuCount());
        }
        if (payload.gpuModel() != null) {
            host.setServerHostGpuModel(payload.gpuModel());
        }
        if (payload.networks() != null) {
            host.setServerHostNetworks(payload.networks());
        }
        if (payload.cpuUsage() != null) {
            host.setServerHostCpuUsage(payload.cpuUsage());
        }
        if (payload.memUsage() != null) {
            host.setServerHostMemUsage(payload.memUsage());
        }
        if (payload.diskUsage() != null) {
            host.setServerHostDiskUsage(payload.diskUsage());
        }
        if (payload.publicIp() != null) {
            host.setServerHostPublicIp(payload.publicIp());
        }
        if (payload.privateIp() != null) {
            host.setServerHostPrivateIp(payload.privateIp());
        }
        hostMapper.updateById(host);
    }

    /**
     * 写历史时序指标（CPU/内存/磁盘）到 SeriesEngine。
     *
     * @param host    主机
     * @param payload 上报负载
     */
    private void writeHistoryMetrics(MsServerHost host, AgentPushVO payload) {
        if (seriesEngine == null || !seriesEngine.isAvailable()) {
            return;
        }
        Long monitorId = host.getId();
        long now = System.currentTimeMillis();
        try {
            // CPU
            if (payload.cpuUsage() != null) {
                seriesEngine.writePoint("ms_monitor_metric_cpu", monitorId, "cpu_usage", payload.cpuUsage(), now);
            }
            // 内存
            if (payload.memUsage() != null) {
                seriesEngine.writePoint("ms_monitor_metric_memory", monitorId, "usage_percent", payload.memUsage(), now);
            }
            // 磁盘
            if (payload.diskUsage() != null) {
                seriesEngine.writePoint("ms_monitor_metric_disk", monitorId, "usage_percent", payload.diskUsage(), now);
            }
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(HostAgentController.class)
                    .warn("[HostAgent] 时序历史写入失败 hostId={}: {}", monitorId, e.getMessage());
        }
    }
}
