package com.chua.starter.monitor.support.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 主机基础信息视图。
 *
 * <p>四类采集通道（本机 OSHI / Agent HTTP / SSH / WinRM）的字段并集，通道给不出的字段为
 * {@code null}，前端按同一份契约渲染，无需区分来源。数值型字段统一在 Provider 侧完成量纲与
 * 类型收敛（如 SSH 把 {@code cpuCores} 解析成整数），不再以字符串下发。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
public class HostInfoVO {

    /**
     * 主机名
     */
    private String hostname;

    /**
     * 操作系统族（Windows/Linux/macOS），仅 Agent 通道可得
     */
    private String os;

    /**
     * 操作系统名称
     */
    private String osName;

    /**
     * 操作系统版本
     */
    private String osVersion;

    /**
     * 操作系统构建号或代号
     */
    private String osBuild;

    /**
     * CPU 架构，仅 Agent 通道可得
     */
    private String arch;

    /**
     * 逻辑核心数
     */
    private Integer cpuCores;

    /**
     * CPU 型号
     */
    private String cpuName;

    /**
     * 物理内存总量，单位 MB
     */
    private Long memoryMb;

    /**
     * 物理内存总量，单位 MB（本机通道字段）
     */
    private Long memoryTotalMb;

    /**
     * 已用内存，单位 MB（本机通道字段）
     */
    private Long memoryUsedMb;

    /**
     * 内存使用率（0-100，本机通道字段）
     */
    private Double memoryUsage;

    /**
     * 已用内存，单位 MB（SSH/WinRM 通道字段）
     */
    private Long memoryUsed;

    /**
     * CPU 使用率（0-100，SSH/WinRM 通道字段）
     */
    private Double cpuPercent;

    /**
     * 磁盘总容量，单位 GB
     */
    private Double diskTotalGb;

    /**
     * 公网地址
     */
    private String publicIp;

    /**
     * 内网地址
     */
    private String privateIp;

    /**
     * 网卡 MAC 地址
     */
    private String mac;

    /**
     * IPv4 默认网关
     */
    private String ipv4DefaultGateway;

    /**
     * Java 运行时版本，仅 Agent 通道可得
     */
    private String javaVersion;

    /**
     * Agent 版本，仅 Agent 通道可得
     */
    private String agentVersion;

    /**
     * Agent HTTP 端口，仅 Agent 通道可得
     */
    private Integer agentPort;

    /**
     * Agent 自身进程号，仅 Agent 通道可得
     */
    private Integer pid;

    /**
     * 开机时长，单位秒，仅 Agent 通道可得
     */
    private Long uptimeSec;

    /**
     * 采集时间文本，仅 Agent 通道可得
     */
    private String collectTime;

    /**
     * 可用的包管理器标识
     */
    private String packageManager;

    /**
     * GPU 型号描述，仅 SSH/WinRM 通道可得
     */
    private String gpu;

    /**
     * 本机网卡列表，仅本机通道内嵌
     */
    private List<NetworkInterfaceRowVO> networkInterfaces = new ArrayList<>();
}
