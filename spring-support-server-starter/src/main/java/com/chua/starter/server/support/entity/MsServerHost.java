package com.chua.starter.server.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.starter.mybatis.pojo.SysBase;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 服务器主机实体
 * <p>
 * 对应表 {@code ms_server_host}，存储服务器资产台账主数据。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "服务器主机")
@TableName("ms_server_host")
@JsonIgnoreProperties(ignoreUnknown = true)
public class MsServerHost extends SysBase {

    /**
     * 串行版本UID
    */
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    @Schema(description = "主键", example = "1")
    private Long id; // 标识

    /**
     * 主机名称
     */
    @TableField("ms_server_host_name")
    @Schema(description = "主机名称", example = "应用服务器-01")
    private String serverHostName; // 服务端主机名称

    /**
     * 主机编码（唯一标识）
     */
    @TableField("ms_server_host_code")
    @Schema(description = "主机编码", example = "app-01")
    private String serverHostCode; // 服务端主机编码

    /**
     * 主机地址
     */
    @TableField("ms_server_host_address")
    @Schema(description = "主机地址", example = "124.221.230.112")
    private String serverHostAddress; // 服务端主机地址

    /**
     * SSH 端口
     */
    @TableField("ms_server_host_port")
    @Schema(description = "SSH端口", example = "22")
    private Integer serverHostPort; // 服务端主机端口

    /**
     * 操作系统类型
     */
    @TableField("ms_server_host_os_type")
    @Schema(description = "操作系统类型: LINUX / WINDOWS / MACOS", example = "LINUX")
    private String serverHostOsType; // 服务端主机os类型

    /**
     * 操作系统架构
     */
    @TableField("ms_server_host_os_arch")
    @Schema(description = "操作系统架构", example = "x86_64")
    private String serverHostOsArch; // 服务端主机osArch Linux Linux Linux

    /**
     * 是否开启 SSH（发布/命令执行等基于 OpenSSH 的能力依赖）
     * <p>默认值：Linux/macOS 为 true，Windows 为 false（Windows 需自行启用 OpenSSH）。</p>
     */
    @TableField("ms_server_host_ssh_enabled")
    @Schema(description = "是否开启SSH 1是 0否（Linux默认开，Windows默认关）", example = "1")
    private Integer serverHostSshEnabled; // 服务端主机是否开启ssh

    /**
     * 所属分组 标识
     */
    @TableField("ms_server_group_id")
    @Schema(description = "所属分组ID", example = "1")
    private Long msServerGroupId; // ms服务端群体标识

    /**
     * 标签集合（JSON 数组字符串）
     */
    @TableField("ms_server_host_tags")
    @Schema(description = "标签集合(JSON数组)", example = "[\"生产\",\"核心\"]")
    private String serverHostTags; // 服务端主机标签

    /**
     * 启用状态
     */
    @TableField("ms_server_host_status")
    @Schema(description = "启用状态 1启用 0停用", example = "1")
    private Integer serverHostStatus; // 服务端主机状态

    /**
     * 在线状态
     */
    @TableField("ms_server_host_online")
    @Schema(description = "在线状态 1在线 0离线 -1未知", example = "-1")
    private Integer serverHostOnline; // 服务端主机online

    /**
     * 最近在线时间
     */
    @TableField("ms_server_host_online_time")
    @Schema(description = "最近在线时间")
    private java.time.LocalDateTime serverHostOnlineTime; // 服务端主机online时间

    /**
     * 探针标识（为后续 Agent 上报预留，服务端生成）
     */
    @TableField("ms_server_host_agent_key")
    @Schema(description = "探针标识(32位)", example = "8f14e45fceea167a5a36dedd4bea2543")
    private String serverHostAgentKey; // 服务端主机Agent键

    /**
     * 描述
     */
    @TableField("ms_server_host_description")
    @Schema(description = "描述", example = "核心业务服务器")
    private String serverHostDescription; // 服务端主机description

    /**
     * CPU 核心数
     */
    @TableField("ms_server_host_cpu_cores")
    @Schema(description = "CPU核心数", example = "8")
    private Integer serverHostCpuCores; // 服务端主机cpu核心

    /**
     * 内存容量(MB)
     */
    @TableField("ms_server_host_memory_mb")
    @Schema(description = "内存容量(MB)", example = "16384")
    private Long serverHostMemoryMb; // 服务端主机内存mb

    /**
     * 磁盘容量(GB)
     */
    @TableField("ms_server_host_disk_gb")
    @Schema(description = "磁盘容量(GB)", example = "100")
    private Long serverHostDiskGb; // 服务端主机diskgb

    /**
     * GPU 卡数（探测结果）
     */
    @TableField("ms_server_host_gpu_count")
    @Schema(description = "GPU卡数(探测结果)", example = "2")
    private Integer serverHostGpuCount; // 服务端主机gpu计数

    /**
     * GPU 型号（探测结果）
     */
    @TableField("ms_server_host_gpu_model")
    @Schema(description = "GPU型号(探测结果)", example = "NVIDIA GeForce RTX 4090")
    private String serverHostGpuModel; // 服务端主机gpu型号

    /**
     * 网卡信息（JSON 数组字符串：[{"名称":"eth0","ip":"10.0.0.3","mac":"aa:bb:cc:dd:ee:ff"}]）
     */
    @TableField("ms_server_host_networks")
    @Schema(description = "网卡信息(JSON数组)", example = "[{\"name\":\"eth0\",\"ip\":\"10.0.0.3\"}]")
    private String serverHostNetworks; // 服务端主机网卡

    /**
     * 主网卡 MAC 地址（探测结果，画像刷新回写）
     */
    @TableField("ms_server_host_mac")
    @Schema(description = "主网卡MAC(探测结果)", example = "00:25:41:03:10:00")
    private String serverHostMac;

    /**
     * 操作系统上报的真实主机名（探测结果，区别于用户填写的资产名称）
     */
    @TableField("ms_server_host_os_hostname")
    @Schema(description = "OS真实主机名(探测结果)", example = "DESKTOP-N2BE5N0")
    private String serverHostOsHostname;

    /**
     * Java 运行时版本（探测结果）
     */
    @TableField("ms_server_host_java_version")
    @Schema(description = "Java版本(探测结果)", example = "1.8.0_491")
    private String serverHostJavaVersion;

    /**
     * 开机时长（秒，探测结果；配合最近采集时间可换算）
     */
    @TableField("ms_server_host_uptime_sec")
    @Schema(description = "开机时长秒(探测结果)", example = "276000")
    private Long serverHostUptimeSec;

    /**
     * CPU 使用率（%，探测结果）
     */
    @TableField("ms_server_host_cpu_usage")
    @Schema(description = "CPU使用率%(探测结果)", example = "12.50")
    private Double serverHostCpuUsage; // 服务端主机cpu使用率

    /**
     * 内存使用率（%，探测结果）
     */
    @TableField("ms_server_host_mem_usage")
    @Schema(description = "内存使用率%(探测结果)", example = "45.30")
    private Double serverHostMemUsage; // 服务端主机内存使用率

    /**
     * 磁盘使用率（%，探测结果）
     */
    @TableField("ms_server_host_disk_usage")
    @Schema(description = "磁盘使用率%(探测结果)", example = "68.00")
    private Double serverHostDiskUsage; // 服务端主机磁盘使用率

    /**
     * 磁盘读取速率（KB/s，探测结果）
     */
    @TableField("ms_server_host_disk_read_kbs")
    @Schema(description = "磁盘读取速率KB/s(探测结果)", example = "128.50")
    private Double serverHostDiskReadKbs; // 服务端主机磁盘读取速率

    /**
     * 磁盘写入速率（KB/s，执行结果）
     */
    @TableField("ms_server_host_disk_write_kbs")
    @Schema(description = "磁盘写入速率KB/s(探测结果)", example = "64.00")
    private Double serverHostDiskWriteKbs; // 服务端主机磁盘写入速率

    /**
     * 主机上运行的软件实例（JSON 数组，探测结果）
     */
    @TableField("ms_server_host_software")
    @Schema(description = "运行软件实例JSON(探测结果)")
    private String serverHostSoftware; // 服务端主机软件实例

    /**
     * 系统版本
     */
    @TableField("ms_server_host_os_version")
    @Schema(description = "系统版本", example = "Ubuntu 24.04 LTS")
    private String serverHostOsVersion; // 服务端主机os版本

    /**
     * 公网 IP
     */
    @TableField("ms_server_host_public_ip")
    @Schema(description = "公网IP", example = "124.221.230.112")
    private String serverHostPublicIp; // 服务端主机公共ip

    /**
     * 内网 IP
     */
    @TableField("ms_server_host_private_ip")
    @Schema(description = "内网IP", example = "10.0.0.3")
    private String serverHostPrivateIp; // 服务端主机私募ip

    /**
     * 在离线检测开关（主机级）
     */
    @TableField("ms_server_host_online_enabled")
    @Schema(description = "在离线检测开关(主机级)", example = "1")
    private Integer serverHostOnlineEnabled; // 服务端主机online启用

    /**
     * 在离线检测间隔(秒)（主机级）
     */
    @TableField("ms_server_host_online_interval")
    @Schema(description = "在离线检测间隔秒(主机级)", example = "60")
    private Integer serverHostOnlineInterval; // 服务端主机online间隔

    /**
     * 硬件采集开关（主机级）
     */
    @TableField("ms_server_host_collect_enabled")
    @Schema(description = "硬件采集开关(主机级)", example = "0")
    private Integer serverHostCollectEnabled; // 服务端主机collect启用

    /**
     * 采集方式（主机级）
     */
    @TableField("ms_server_host_collect_mode")
    @Schema(description = "采集方式(主机级): SCHEDULED/AGENT/HTTP/PROMETHEUS", example = "SCHEDULED")
    private String serverHostCollectMode; // 服务端主机collectmode

    /**
     * 采集间隔(秒)（主机级）
     */
    @TableField("ms_server_host_collect_interval")
    @Schema(description = "采集间隔秒(主机级)", example = "3600")
    private Integer serverHostCollectInterval; // 服务端主机collect间隔

    /**
     * 最近采集时间（主机级，采集节奏控制）
     */
    @TableField("ms_server_host_last_collect_time")
    @Schema(description = "最近采集时间(主机级)")
    private java.time.LocalDateTime serverHostLastCollectTime; // 服务端主机最后一个collect时间

    /**
     * Agent 上报开关（主机级）
     */
    @TableField("ms_server_host_agent_enabled")
    @Schema(description = "Agent上报开关(主机级)", example = "0")
    private Integer serverHostAgentEnabled; // 服务端主机Agent启用
    /**
     * Agent 是否已安装（0未安装 1已安装）
     */
    @TableField("ms_server_host_agent_installed")
    @Schema(description = "Agent是否已安装(0未安装 1已安装)", example = "0")
    private Integer serverHostAgentInstalled; // 服务端主机Agent已安装

    /**
     * Agent HTTP 端口（画像与部署生命周期都以它为准，不再各处写死 19876）
     */
    @TableField("ms_server_host_agent_port")
    @Schema(description = "Agent HTTP端口", example = "19876")
    private Integer serverHostAgentPort; // 服务端主机Agent端口

    /**
     * Agent 版本号（由 /health 回读，升级是否生效靠它判定）
     */
    @TableField("ms_server_host_agent_version")
    @Schema(description = "Agent版本号", example = "4.0.0.42")
    private String serverHostAgentVersion; // 服务端主机Agent版本

    /**
     * 最近一次 Agent 采集成功时间
     */
    @TableField("ms_server_host_agent_time")
    @Schema(description = "最近一次Agent采集成功时间")
    private java.time.LocalDateTime serverHostAgentTime; // 服务端主机Agent时间

    /**
     * 最近一次 Agent 访问失败原因（画像页据此说明「为什么没数据」）
     */
    @TableField("ms_server_host_agent_error")
    @Schema(description = "最近一次Agent失败原因")
    private String serverHostAgentError; // 服务端主机Agent错误

    /**
     * 远控开关（主机级）
     */
    @TableField("ms_server_host_remote_enabled")
    @Schema(description = "远控开关(主机级)", example = "1")
    private Integer serverHostRemoteEnabled; // 服务端主机远程启用

    /**
     * 远控协议（主机级，为空时按操作系统类型推断）
     */
    @TableField("ms_server_host_remote_protocol")
    @Schema(description = "远控协议: SSH/RDP/VNC(空则按系统类型推断)", example = "SSH")
    private String serverHostRemoteProtocol; // 服务端主机远程protocol

    /**
     * 远控端口（主机级，为空时取协议默认端口）
     */
    @TableField("ms_server_host_remote_port")
    @Schema(description = "远控端口(空则取协议默认端口)", example = "22")
    private Integer serverHostRemotePort; // 服务端主机远程端口

    /**
     * 远控模式（GUACD 经 guacd 中继 / DIRECT 平台原生客户端直连）
     */
    @TableField("ms_server_host_remote_mode")
    @Schema(description = "远控模式: GUACD/DIRECT", example = "GUACD")
    private String serverHostRemoteMode; // 服务端主机远程mode

    /**
     * VNC 口令（AES 加密落库，仅主机详情接口解密回显）
     */
    @TableField("ms_server_host_vnc_password")
    @Schema(description = "VNC口令(加密存储)", example = "vnc-secret")
    private String serverHostVncPassword; // 服务端主机vnc密码

    /**
     * 包管理器（硬件探测结果：apt/dnf/yum/brew/winget，供软件仓库 SPI 路由）
     */
    @TableField("ms_server_host_package_manager")
    @Schema(description = "包管理器(探测结果): apt/dnf/yum/brew/winget", example = "apt")
    private String serverHostPackageManager; // 服务端主机包管理器

    /**
     * 延迟检测开关（主机级）
     */
    @TableField("ms_server_host_delay_enabled")
    @Schema(description = "延迟检测开关(主机级)", example = "0")
    private Integer serverHostDelayEnabled; // 服务端主机延迟启用

    /**
     * 延迟检测间隔(秒)（主机级）
     */
    @TableField("ms_server_host_delay_interval")
    @Schema(description = "延迟检测间隔秒(主机级)", example = "60")
    private Integer serverHostDelayInterval; // 服务端主机延迟间隔

    /**
     * 最近延迟毫秒（检测结果回写）
     */
    @TableField("ms_server_host_delay_ms")
    @Schema(description = "最近延迟毫秒(检测结果)", example = "23")
    private Long serverHostDelayMs; // 服务端主机延迟ms

    /**
     * 端口扫描结果（JSON 数组字符串：[{"端口":22,"协议":"tcp","服务":"ssh","状态":"打开"}]）
     */
    @TableField("ms_server_host_ports")
    @Schema(description = "端口扫描结果(JSON数组)", example = "[{\"port\":22,\"protocol\":\"tcp\",\"service\":\"ssh\"}]")
    private String serverHostPorts; // 服务端主机端口

    /**
     * 最近端口扫描时间
     */
    @TableField("ms_server_host_last_port_scan_time")
    @Schema(description = "最近端口扫描时间")
    private java.time.LocalDateTime serverHostLastPortScanTime; // 服务端主机最后一个端口扫描时间

    /**
     * 最近延迟检测时间
     */
    @TableField("ms_server_host_last_delay_time")
    @Schema(description = "最近延迟检测时间")
    private java.time.LocalDateTime serverHostLastDelayTime; // 服务端主机最后一个延迟时间

    /**
     * 登录账号（瞬态，新增表单直接提交，不落库）
     */
    @TableField(exist = false)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Schema(description = "登录账号(瞬态)")
    private String serverHostAccount; // 服务端主机账户

    /**
     * 登录密码（瞬态，新增表单直接提交，不落库）
     */
    @TableField(exist = false)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Schema(description = "登录密码(瞬态)")
    private String serverHostPassword; // 服务端主机密码

    /**
     * PEM 私钥内容（瞬态，新增表单直接提交，不落库）
     */
    @TableField(exist = false)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Schema(description = "PEM私钥(瞬态)")
    private String serverHostPrivateKey; // 服务端主机私募键

    /**
     * 认证类型标记（瞬态，不落库）：password / key / none（免密登录）。
     * 新增/编辑主机表单提交，供 {@code autoCreateIdentity} 决定自动创建的身份类型。
     */
    @TableField(exist = false)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Schema(description = "认证类型标记(瞬态,password/key/none)")
    private String serverHostAuthType; // 服务端主机认证类型标记
}
