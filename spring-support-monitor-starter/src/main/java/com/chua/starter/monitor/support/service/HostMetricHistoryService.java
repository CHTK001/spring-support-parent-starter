package com.chua.starter.monitor.support.service;

import java.util.List;

/**
 * 主机内置指标历史服务。
 *
 * <p>主机定时采集（本机 OSHI / OpenSSH / Agent）每次完成后，把 CPU / 内存 / 磁盘
 * 使用率写一行历史，不依赖监控项；供主机监控详情页「近 1 小时基础数据」与
 * 图表历史弹窗回溯。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public interface HostMetricHistoryService {

    /**
     * 写入一次主机指标历史（三率可部分为空，全空则不写）。
     *
     * @param hostId    主机台账标识
     * @param cpuUsage  CPU 使用率，可为空
     * @param memUsage  内存使用率，可为空
     * @param diskUsage 磁盘使用率，可为空
     */
    default void record(Long hostId, Double cpuUsage, Double memUsage, Double diskUsage) {
        record(hostId, cpuUsage, memUsage, diskUsage, null, null);
    }

    /**
     * 写入一次主机指标历史（含磁盘读写速率，三率可部分为空，全空则不写）。
     *
     * @param hostId       主机台账标识
     * @param cpuUsage     CPU 使用率，可为空
     * @param memUsage     内存使用率，可为空
     * @param diskUsage    磁盘使用率，可为空
     * @param diskReadKbs  磁盘读取速率 KB/s，可为空
     * @param diskWriteKbs 磁盘写入速率 KB/s，可为空
     */
    void record(Long hostId, Double cpuUsage, Double memUsage, Double diskUsage,
                Double diskReadKbs, Double diskWriteKbs);

    /**
     * 查询主机近 N 小时的指标历史，升序。
     *
     * @param hostId 主机台账标识
     * @param hours  回溯小时数（1..168）
     * @return 每行 {@code [时间戳ms, cpu, mem, disk, diskReadKbs, diskWriteKbs]}，缺失值为 null
     */
    List<List<Object>> list(Long hostId, int hours);

    /**
     * 删除保留期之前的历史。
     *
     * @param retentionDays 保留天数
     * @return 删除行数
     */
    int cleanup(int retentionDays);
}
