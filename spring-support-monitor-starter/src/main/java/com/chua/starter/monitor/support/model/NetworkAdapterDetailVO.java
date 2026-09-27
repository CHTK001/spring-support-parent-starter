package com.chua.starter.monitor.support.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 网卡详情（参考 Windows「网络连接 → 状态/属性」）。
 *
 * <p>由服务层经当前采集通道（本机 / agent / SSH / WinRM）下发 PowerShell
 * 读取 {@code Get-NetAdapter}、{@code Get-NetIPConfiguration} 等信息后组装。</p>
 *
 * @author CH
 * @version 1.0.0
 */
@Data
@Schema(description = "网卡详情")
public class NetworkAdapterDetailVO {

    /**
     * 网卡名称（接口别名，如 Ethernet0）
     */
    @Schema(description = "网卡名称")
    private String name;

    /**
     * 接口索引（下发配置时用于定位）
     */
    @Schema(description = "接口索引")
    private Integer ifIndex;

    /**
     * 网卡描述（厂商/型号）
     */
    @Schema(description = "网卡描述")
    private String description;

    /**
     * 媒体连接状态（Up / Disconnected）
     */
    @Schema(description = "连接状态")
    private String status;

    /**
     * 物理地址
     */
    @Schema(description = "MAC 地址")
    private String macAddress;

    /**
     * 链路速率（原文，如 1 Gbps）
     */
    @Schema(description = "链路速率")
    private String linkSpeed;

    /**
     * 接口 MTU
     */
    @Schema(description = "MTU")
    private Integer mtu;

    /**
     * 是否 DHCP 自动获取
     */
    @Schema(description = "是否DHCP")
    private Boolean dhcpEnabled;

    /**
     * IPv4 地址（含前缀长度，如 192.168.200.33/24）
     */
    @Schema(description = "IPv4 地址列表")
    private List<String> ipv4Addresses = new ArrayList<>();

    /**
     * IPv6 地址
     */
    @Schema(description = "IPv6 地址列表")
    private List<String> ipv6Addresses = new ArrayList<>();

    /**
     * 默认网关
     */
    @Schema(description = "默认网关")
    private List<String> gateways = new ArrayList<>();

    /**
     * DNS 服务器
     */
    @Schema(description = "DNS 服务器")
    private List<String> dnsServers = new ArrayList<>();
}
