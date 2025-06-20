package com.chua.starter.monitor.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 设备指标数据
 * @author CH
 * @since 2024/12/19
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("monitor_device_metrics")
public class MonitorDeviceMetrics {

    /**
     * 主键ID
     */
    @TableId(value = "monitor_device_metrics_id", type = IdType.AUTO)
    private Long monitorDeviceMetricsId;

    /**
     * 设备ID
     */
    @TableField("monitor_device_metrics_device_id")
    private String monitorDeviceMetricsDeviceId;

    /**
     * 设备名称
     */
    @TableField("monitor_device_metrics_device_name")
    private String monitorDeviceMetricsDeviceName;

    /**
     * 设备IP地址
     */
    @TableField("monitor_device_metrics_ip_address")
    private String monitorDeviceMetricsIpAddress;

    /**
     * 设备端口
     */
    @TableField("monitor_device_metrics_port")
    private Integer monitorDeviceMetricsPort;

    /**
     * 操作系统名称
     */
    @TableField("monitor_device_metrics_os_name")
    private String monitorDeviceMetricsOsName;

    /**
     * 操作系统版本
     */
    @TableField("monitor_device_metrics_os_version")
    private String monitorDeviceMetricsOsVersion;

    /**
     * 系统架构
     */
    @TableField("monitor_device_metrics_os_arch")
    private String monitorDeviceMetricsOsArch;

    /**
     * 主机名
     */
    @TableField("monitor_device_metrics_hostname")
    private String monitorDeviceMetricsHostname;

    /**
     * CPU使用率 (%)
     */
    @TableField("monitor_device_metrics_cpu_usage")
    private Double monitorDeviceMetricsCpuUsage;

    /**
     * CPU核心数
     */
    @TableField("monitor_device_metrics_cpu_cores")
    private Integer monitorDeviceMetricsCpuCores;

    /**
     * CPU频率 (MHz)
     */
    @TableField("monitor_device_metrics_cpu_frequency")
    private Long monitorDeviceMetricsCpuFrequency;

    /**
     * 内存使用率 (%)
     */
    @TableField("monitor_device_metrics_memory_usage")
    private Double monitorDeviceMetricsMemoryUsage;

    /**
     * 总内存 (bytes)
     */
    @TableField("monitor_device_metrics_total_memory")
    private Long monitorDeviceMetricsTotalMemory;

    /**
     * 已用内存 (bytes)
     */
    @TableField("monitor_device_metrics_used_memory")
    private Long monitorDeviceMetricsUsedMemory;

    /**
     * 可用内存 (bytes)
     */
    @TableField("monitor_device_metrics_available_memory")
    private Long monitorDeviceMetricsAvailableMemory;

    /**
     * 磁盘使用率 (%)
     */
    @TableField("monitor_device_metrics_disk_usage")
    private Double monitorDeviceMetricsDiskUsage;

    /**
     * 总磁盘空间 (bytes)
     */
    @TableField("monitor_device_metrics_total_disk")
    private Long monitorDeviceMetricsTotalDisk;

    /**
     * 已用磁盘空间 (bytes)
     */
    @TableField("monitor_device_metrics_used_disk")
    private Long monitorDeviceMetricsUsedDisk;

    /**
     * 可用磁盘空间 (bytes)
     */
    @TableField("monitor_device_metrics_available_disk")
    private Long monitorDeviceMetricsAvailableDisk;

    /**
     * 网络入流量 (bytes/s)
     */
    @TableField("monitor_device_metrics_network_in_bytes")
    private Long monitorDeviceMetricsNetworkInBytes;

    /**
     * 网络出流量 (bytes/s)
     */
    @TableField("monitor_device_metrics_network_out_bytes")
    private Long monitorDeviceMetricsNetworkOutBytes;

    /**
     * 网络入包数 (packets/s)
     */
    @TableField("monitor_device_metrics_network_in_packets")
    private Long monitorDeviceMetricsNetworkInPackets;

    /**
     * 网络出包数 (packets/s)
     */
    @TableField("monitor_device_metrics_network_out_packets")
    private Long monitorDeviceMetricsNetworkOutPackets;

    /**
     * 系统负载平均值
     */
    @TableField("monitor_device_metrics_load_average")
    private String monitorDeviceMetricsLoadAverage;

    /**
     * 系统运行时间 (秒)
     */
    @TableField("monitor_device_metrics_uptime")
    private Long monitorDeviceMetricsUptime;

    /**
     * 进程数量
     */
    @TableField("monitor_device_metrics_process_count")
    private Integer monitorDeviceMetricsProcessCount;

    /**
     * 线程数量
     */
    @TableField("monitor_device_metrics_thread_count")
    private Integer monitorDeviceMetricsThreadCount;

    /**
     * 温度 (摄氏度)
     */
    @TableField("monitor_device_metrics_temperature")
    private Double monitorDeviceMetricsTemperature;

    /**
     * 是否在线
     */
    @TableField("monitor_device_metrics_online")
    private Boolean monitorDeviceMetricsOnline;

    /**
     * 数据收集时间
     */
    @TableField("monitor_device_metrics_collect_time")
    private LocalDateTime monitorDeviceMetricsCollectTime;

    /**
     * 扩展属性
     */
    @TableField("monitor_device_metrics_extra_data")
    private String monitorDeviceMetricsExtraData;

    /**
     * 创建时间
     */
    @TableField("monitor_device_metrics_create_time")
    private LocalDateTime monitorDeviceMetricsCreateTime;

    /**
     * 更新时间
     */
    @TableField("monitor_device_metrics_update_time")
    private LocalDateTime monitorDeviceMetricsUpdateTime;
}
