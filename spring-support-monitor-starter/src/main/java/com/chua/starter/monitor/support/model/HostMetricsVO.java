package com.chua.starter.monitor.support.model;

import lombok.Data;

/**
 * 主机实时指标视图。
 *
 * <p>Agent {@code /metrics} 与本机采集共用的一份瞬时资源快照，供画像概览与自动采集写库使用。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
public class HostMetricsVO {

    /**
     * CPU 使用率（0-100）
     */
    private double cpuPercent;

    /**
     * 内存总量，单位 MB
     */
    private long memoryTotalMb;

    /**
     * 内存已用，单位 MB
     */
    private long memoryUsedMb;

    /**
     * 内存使用率（0-100）
     */
    private double memoryPercent;

    /**
     * 磁盘总容量，单位 GB
     */
    private double diskTotalGb;

    /**
     * 磁盘已用容量，单位 GB
     */
    private double diskUsedGb;

    /**
     * 磁盘使用率（0-100）
     */
    private double diskPercent;

    /**
     * 磁盘读取速率，单位 KB/s
     */
    private double diskReadKbs;

    /**
     * 磁盘写入速率，单位 KB/s
     */
    private double diskWriteKbs;

    /**
     * 进程数
     */
    private int processCount;

    /**
     * 线程数
     */
    private int threadCount;

    /**
     * 句柄数：Windows 为系统句柄总数，Linux/macOS 为打开文件描述符数。
     *
     * <p>取不到（通道不支持、权限不足）时保持 -1，前端据此显示「—」，
     * 不能以 0 冒充「没有句柄」。</p>
     */
    private long handleCount = -1L;

    /**
     * 开机时长，单位秒
     */
    private long uptimeSec;

    /**
     * 网络接收速率，单位 KB/s
     */
    private double networkRxKb;

    /**
     * 网络发送速率，单位 KB/s
     */
    private double networkTxKb;

    /**
     * 网络带宽使用率（0-100）
     */
    private double networkPercent;

    /**
     * 采集时间文本
     */
    private String collectTime;
}
