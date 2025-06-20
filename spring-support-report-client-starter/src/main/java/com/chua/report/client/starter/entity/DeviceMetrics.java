package com.chua.report.client.starter.entity;

import lombok.Data;

import java.time.LocalDateTime;

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
     * 设备端口
     */
    private Integer port;

    /**
     * 操作系统名称
     */
    private String osName;

    /**
     * 操作系统版本
     */
    private String osVersion;

    /**
     * 系统架构
     */
    private String osArch;

    /**
     * 主机名
     */
    private String hostname;

    /**
     * CPU使用率 (%)
     */
    private Double cpuUsage;

    /**
     * CPU核心数
     */
    private Integer cpuCores;

    /**
     * CPU频率 (MHz)
     */
    private Long cpuFrequency;

    /**
     * 内存使用率 (%)
     */
    private Double memoryUsage;

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
     * 磁盘使用率 (%)
     */
    private Double diskUsage;

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
     * 网络入流量 (bytes/s)
     */
    private Long networkInBytes;

    /**
     * 网络出流量 (bytes/s)
     */
    private Long networkOutBytes;

    /**
     * 网络入包数 (packets/s)
     */
    private Long networkInPackets;

    /**
     * 网络出包数 (packets/s)
     */
    private Long networkOutPackets;

    /**
     * 系统负载平均值
     */
    private String loadAverage;

    /**
     * 系统运行时间 (秒)
     */
    private Long uptime;

    /**
     * 进程数量
     */
    private Integer processCount;

    /**
     * 线程数量
     */
    private Integer threadCount;

    /**
     * 温度 (摄氏度)
     */
    private Double temperature;

    /**
     * 是否在线
     */
    private Boolean online;

    /**
     * 数据收集时间
     */
    private LocalDateTime collectTime;

    /**
     * 扩展属性
     */
    private String extraData;
}
