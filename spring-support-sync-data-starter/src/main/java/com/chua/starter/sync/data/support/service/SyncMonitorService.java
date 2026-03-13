package com.chua.starter.sync.data.support.service;

import com.chua.starter.sync.data.support.entity.MonitorSyncStatistics;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 同步监控服务接口
 *
 * @author System
 * @since 2026/03/09
 */
public interface SyncMonitorService {

    /**
     * 采集实时监控数据
     *
     * @param taskId 任务ID
     * @return 实时监控数据
     */
    Map<String, Object> collectRealtimeData(Long taskId);

    /**
     * 计算性能指标
     *
     * @param taskId 任务ID
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 性能指标
     */
    Map<String, Object> calculatePerformanceMetrics(Long taskId, LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 存储监控数据
     *
     * @param statistics 统计数据
     */
    void storeMonitoringData(MonitorSyncStatistics statistics);

    /**
     * 记录同步进度
     *
     * @param taskId 任务ID
     * @param processedRecords 已处理记录数
     * @param totalRecords 总记录数
     * @param throughput 吞吐量
     */
    void recordProgress(Long taskId, long processedRecords, long totalRecords, BigDecimal throughput);
}
