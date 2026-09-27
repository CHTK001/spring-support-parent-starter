package com.chua.starter.server.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.server.support.constants.ServerConstants;
import com.chua.starter.server.support.entity.MsServerHost;
import com.chua.starter.server.support.enums.HostCollectType;
import com.chua.starter.server.support.mapper.MsServerHostMapper;
import com.chua.starter.server.support.model.vo.AgentPushVO;
import com.chua.starter.server.support.spi.HostAgentHistoryWriter;
import com.chua.starter.server.support.spi.HostCollectContext;
import com.chua.starter.server.support.spi.HostCollectProvider;
import com.chua.starter.server.support.spi.HostCollectProviderRegistry;
import com.chua.starter.server.support.util.HostPlatforms;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.HashSet;
import java.util.Set;


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
@Slf4j
@RestController
@RequestMapping(ServerConstants.API_PREFIX + "/host/agent")
@RequiredArgsConstructor
public class HostAgentController {

    /**
     * 历史指标写入扩展点（监控模块注册实现；未接入监控模块时为空）
    */
    private static final ServiceProvider<HostAgentHistoryWriter> HISTORY_WRITERS =
            ServiceProvider.of(HostAgentHistoryWriter.class);

    /**
     * 采集提供者注册中心
    */
    private final HostCollectProviderRegistry collectRegistry;

    /**
     * 主机 映射器
    */
    private final MsServerHostMapper hostMapper;

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
        String reportedOs = HostPlatforms.normalize(payload.os());
        if (!reportedOs.isEmpty()) {
            host.setServerHostOsType(reportedOs);
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
     * 广播给监控域的历史写入实现。
     *
     * @param host    已回写台账的主机
     * @param payload 上报负载
     */
    private void writeHistoryMetrics(MsServerHost host, AgentPushVO payload) {
        try {
            Set<Class<?>> seen = new HashSet<>();
            for (HostAgentHistoryWriter writer : HISTORY_WRITERS.collect()) {
                // 同一实现类的多个别名（扩展点登记名 + @Spi value）会各自建实例，只执行一次
                if (writer == null || !seen.add(writer.getClass())) {
                    continue;
                }
                writer.write(host, payload);
            }
        } catch (Exception e) {
            log.warn("[HostAgent] 历史指标写入扩展点执行失败 hostId={}: {}", host.getId(), e.getMessage());
        }
    }
}
