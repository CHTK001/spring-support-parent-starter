package com.chua.report.client.starter.pojo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 设备指标数据
 * @author CH
 * @since 2024/12/19
 */
@Data
public class DeviceMetrics {

    /**
     * 设备ID
     */
    private String deviceId;

    /**
     * 设备名称
     */
    private String deviceName;

    /**
     * 设备IP地址
     */
    private String ipAddress;

    /**
     * 操作系统
     */
    private String operatingSystem;

    /**
     * 系统架构
     */
    private String architecture;

    /**
     * 系统版本
     */
    private String systemVersion;

    /**
     * 主机名
     */
    private String hostname;

    /**
     * 运行时间 (毫秒)
     */
    private Long uptime;

    /**
     * 启动时间
     */
    private LocalDateTime bootTime;

    /**
     * CPU 核心数
     */
    private Integer cpuCores;

    /**
     * CPU 使用率 (百分比)
     */
    private Double cpuUsage;

    /**
     * CPU 温度 (摄氏度)
     */
    private Double cpuTemperature;

    /**
     * 总内存 (bytes)
     */
    private Long totalMemory;

    /**
     * 已用内存 (bytes)
     */
    private Long usedMemory;

    /**
     * 可用内存 (bytes)
     */
    private Long availableMemory;

    /**
     * 内存使用率 (百分比)
     */
    private Double memoryUsage;

    /**
     * 总磁盘空间 (bytes)
     */
    private Long totalDisk;

    /**
     * 已用磁盘空间 (bytes)
     */
    private Long usedDisk;

    /**
     * 可用磁盘空间 (bytes)
     */
    private Long availableDisk;

    /**
     * 磁盘使用率 (百分比)
     */
    private Double diskUsage;

    /**
     * 磁盘分区信息列表
     */
    private List<DiskPartitionInfo> diskPartitions;

    /**
     * 网络接收字节数
     */
    private Long networkBytesReceived;

    /**
     * 网络发送字节数
     */
    private Long networkBytesSent;

    /**
     * 网络接收包数
     */
    private Long networkPacketsReceived;

    /**
     * 网络发送包数
     */
    private Long networkPacketsSent;

    /**
     * 进程数量
     */
    private Integer processCount;

    /**
     * 线程数量
     */
    private Integer threadCount;

    /**
     * 负载平均值 (1分钟)
     */
    private Double loadAverage1m;

    /**
     * 负载平均值 (5分钟)
     */
    private Double loadAverage5m;

    /**
     * 负载平均值 (15分钟)
     */
    private Double loadAverage15m;

    /**
     * 采集时间
     */
    private LocalDateTime collectTime;

    /**
     * 扩展属性
     */
    private String extendedProperties;

    /**
     * 是否在线
     */
    private Boolean online;

    /**
     * 最后在线时间
     */
    private LocalDateTime lastOnlineTime;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 数据版本
     */
    private String version;

    /**
     * 标签
     */
    private String tags;

    /**
     * 地理位置
     */
    private String location;

    /**
     * 环境信息
     */
    private String environment;

    /**
     * 备注
     */
    private String remarks;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
