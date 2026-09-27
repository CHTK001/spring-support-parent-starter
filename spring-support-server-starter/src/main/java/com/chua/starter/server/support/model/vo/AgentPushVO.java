package com.chua.starter.server.support.model.vo;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Agent 上报负载
 * <p>
 * 探针按 Agent键 主动推送的硬件与实时指标，字段名与历史 Map 载荷保持完全一致：
 * {@code cpuUsage} 兼容旧别名 {@code cpu}，{@code memUsage} 兼容旧别名 {@code memUsedPct}。
 * 未知字段忽略，缺省字段为 {@code null} 表示不上报该项。
 * </p>
 *
 * @param cpuCores  CPU 核心数
 * @param memoryMb  内存容量（MB）
 * @param diskGb    磁盘容量（GB）
 * @param os        操作系统平台（OSHI family，如 Windows / Linux / macOS）
 * @param osVersion 操作系统版本
 * @param osArch    操作系统架构
 * @param gpuCount  GPU 数量
 * @param gpuModel  GPU 型号
 * @param networks  网卡清单（JSON 字符串）
 * @param cpuUsage  CPU 使用率（%，别名 cpu）
 * @param memUsage  内存使用率（%，别名 memUsedPct）
 * @param diskUsage 磁盘使用率（%）
 * @param publicIp  公网 IP
 * @param privateIp 内网 IP
 * @author CH
 * @since 4.0.0.42
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AgentPushVO(
        Integer cpuCores,
        Long memoryMb,
        Long diskGb,
        String os,
        String osVersion,
        String osArch,
        Integer gpuCount,
        String gpuModel,
        String networks,
        @JsonAlias("cpu") Double cpuUsage,
        @JsonAlias("memUsedPct") Double memUsage,
        Double diskUsage,
        String publicIp,
        String privateIp
) {
}
