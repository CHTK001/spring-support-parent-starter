package com.chua.starter.server.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.server.support.constants.ServerConstants;
import com.chua.starter.server.support.entity.MsServerHost;
import com.chua.starter.server.support.enums.HostCollectType;
import com.chua.starter.server.support.mapper.MsServerHostMapper;
import com.chua.starter.server.support.spi.AgentHostCollectProvider;
import com.chua.starter.server.support.spi.HostCollectContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * 服务器资产 Agent 上报接口
 * <p>
 * 目标主机上的 Agent 按 agentKey 主动推送硬件/状态数据，
 * 由 {@link AgentHostCollectProvider} 处理回写。用于 NAT / 防火墙下
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

    /** Agent 采集 Provider */
    private final AgentHostCollectProvider agentProvider;

    /** 主机 Mapper */
    private final MsServerHostMapper hostMapper;

    /**
     * Agent 推送数据（按 agentKey 定位主机）
     *
     * @param agentKey 探针标识
     * @param payload  上报数据（cpuCores/memoryMb/diskGb/osVersion/publicIp/privateIp 等）
     * @return 处理结果
     */
    @Operation(summary = "Agent推送采集数据")
    @PostMapping("/{agentKey}/push")
    public Mono<ReturnResult<String>> push(@PathVariable String agentKey, @RequestBody Map<String, Object> payload) {
        if (agentKey == null || agentKey.isBlank()) {
            return Mono.just(ReturnResult.error("agentKey 不能为空"));
        }
        MsServerHost host = hostMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<MsServerHost>()
                        .eq(MsServerHost::getServerHostAgentKey, agentKey.trim()));
        if (host == null) {
            return Mono.just(ReturnResult.error("未找到 agentKey 对应的主机: " + agentKey));
        }
        try {
            if (payload != null) {
                applyPayload(host, payload);
            }
            HostCollectContext context = HostCollectContext.builder()
                    .mode(HostCollectType.AGENT)
                    .host(host)
                    .build();
            return agentProvider.collect(context)
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
    private void applyPayload(MsServerHost host, Map<String, Object> payload) {
        Integer cpuCores = toInteger(payload.get("cpuCores"));
        if (cpuCores != null) {
            host.setServerHostCpuCores(cpuCores);
        }
        Long memoryMb = toLong(payload.get("memoryMb"));
        if (memoryMb != null) {
            host.setServerHostMemoryMb(memoryMb);
        }
        Long diskGb = toLong(payload.get("diskGb"));
        if (diskGb != null) {
            host.setServerHostDiskGb(diskGb);
        }
        if (payload.get("osVersion") != null) {
            host.setServerHostOsVersion(String.valueOf(payload.get("osVersion")));
        }
        if (payload.get("osArch") != null) {
            host.setServerHostOsArch(String.valueOf(payload.get("osArch")));
        }
        if (payload.get("publicIp") != null) {
            host.setServerHostPublicIp(String.valueOf(payload.get("publicIp")));
        }
        if (payload.get("privateIp") != null) {
            host.setServerHostPrivateIp(String.valueOf(payload.get("privateIp")));
        }
        hostMapper.updateById(host);
    }

    /**
     * 安全转为 Integer（null/非数字返回 null）
     *
     * @param value 原始值
     * @return Integer 或 null
     */
    private Integer toInteger(Object value) {
        if (value == null) {
            return null;
        }
        try {
            if (value instanceof Number n) {
                return n.intValue();
            }
            String s = String.valueOf(value).trim();
            return s.isEmpty() ? null : Integer.valueOf(s);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 安全转为 Long（null/非数字返回 null）
     *
     * @param value 原始值
     * @return Long 或 null
     */
    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        try {
            if (value instanceof Number n) {
                return n.longValue();
            }
            String s = String.valueOf(value).trim();
            return s.isEmpty() ? null : Long.valueOf(s);
        } catch (Exception e) {
            return null;
        }
    }
}
            return defValue;
        }
    }
}
