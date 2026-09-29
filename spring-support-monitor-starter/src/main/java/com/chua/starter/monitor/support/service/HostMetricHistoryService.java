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
     * <p><b>越界即报错</b>：{@code hours} 不在 {@code 1..168} 内抛
     * {@link IllegalArgumentException}。历史上此处用
     * {@code Math.max(1, Math.min(hours, 168))} 夹取，越界入参被静默改写，
     * 调用方拿到的曲线与自己请求的区间不符却无从察觉。</p>
     *
     * @param hostId 主机台账标识
     * @param hours  回溯小时数，取值范围 {@code 1..168}（即最多 7 天）
     * @return 每行 {@code [时间戳ms, cpu, mem, disk, diskReadKbs, diskWriteKbs]}，缺失值为 null
     * @throws IllegalArgumentException hours 超出 {@code 1..168} 范围
     */
    List<List<Object>> list(Long hostId, int hours);

    /**
     * 删除保留期之前的历史。
     *
     * <p><b>失败不降级</b>：底层清理出错时本方法抛异常而不是返回 0。
     * 因为 {@code 0} 在引擎契约里已被占用为「后端不支持清理 / 本批无过期点」，
     * 用它表示故障会让调用方无法区分两者，保留期策略随之静默失效。</p>
     *
     * @param retentionDays 保留天数，非正数按 1 天处理
     * @return 删除行数；后端无法精确统计时返回 -1
     * @throws IllegalStateException 引擎不可用或清理失败
     */
    int cleanup(int retentionDays);
}
