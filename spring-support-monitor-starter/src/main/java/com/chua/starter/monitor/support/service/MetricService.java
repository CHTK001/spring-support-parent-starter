package com.chua.starter.monitor.support.service;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.monitor.support.entity.*;
import com.chua.starter.monitor.support.spi.CollectResult;
import com.chua.starter.monitor.support.spi.MetricPoint;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 监控指标 Service
 *
 * @author CH
 * @since 2026/8/4
 */
public interface MetricService {

    /**
     * 写入采集结果
     *
     * @param result 采集结果
     * @return 操作结果
     */
    ReturnResult<Void> writeCollectResult(CollectResult result);

    /**
     * 写入指标点
     *
     * @param monitorId   监控 ID
     * @param points      指标点列表
     * @param collectTime 采集时间
     * @return 操作结果
     */
    ReturnResult<Void> writePoints(Long monitorId, List<MetricPoint> points, LocalDateTime collectTime);

    /**
     * 执行小时级聚合
     *
     * @return 操作结果
     */
    ReturnResult<Void> aggregateHourly();

    /**
     * 清理过期数据
     *
     * @param retentionDays 保留天数
     * @return 操作结果
     */
    ReturnResult<Void> cleanupOldData(int retentionDays);

    // ===== 查询方法 =====

    /**
     * 获取最新 CPU 指标
     *
     * @param monitorId 监控 ID
     * @return CPU 指标
     */
    ReturnResult<MsMonitorMetricCpu> getLatestCpu(Long monitorId);

    /**
     * 获取最新内存指标
     *
     * @param monitorId 监控 ID
     * @return 内存指标
     */
    ReturnResult<MsMonitorMetricMemory> getLatestMemory(Long monitorId);

    /**
     * 获取最新磁盘指标
     *
     * @param monitorId 监控 ID
     * @return 磁盘指标列表
     */
    ReturnResult<List<MsMonitorMetricDisk>> getLatestDisk(Long monitorId);

    /**
     * 获取最新网络指标
     *
     * @param monitorId 监控 ID
     * @return 网络指标
     */
    ReturnResult<MsMonitorMetricNetwork> getLatestNetwork(Long monitorId);

    /**
     * 获取最新 JVM 指标
     *
     * @param monitorId 监控 ID
     * @return JVM 指标
     */
    ReturnResult<MsMonitorMetricJvm> getLatestJvm(Long monitorId);

    /**
     * 获取 CPU 历史指标
     *
     * @param monitorId 监控 ID
     * @param startTime 起始时间
     * @param endTime   结束时间
     * @return CPU 历史指标列表
     */
    ReturnResult<List<MsMonitorMetricCpu>> getCpuHistory(Long monitorId, LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 获取内存历史指标
     *
     * @param monitorId 监控 ID
     * @param startTime 起始时间
     * @param endTime   结束时间
     * @return 内存历史指标列表
     */
    ReturnResult<List<MsMonitorMetricMemory>> getMemoryHistory(Long monitorId, LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 获取 JVM 历史指标
     *
     * @param monitorId 监控 ID
     * @param startTime 起始时间
     * @param endTime   结束时间
     * @return JVM 历史指标列表
     */
    ReturnResult<List<MsMonitorMetricJvm>> getJvmHistory(Long monitorId, LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 获取网络历史指标
     *
     * @param monitorId 监控 ID
     * @param startTime 起始时间
     * @param endTime   结束时间
     * @return 网络历史指标列表
     */
    ReturnResult<List<MsMonitorMetricNetwork>> getNetworkHistory(Long monitorId, LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 获取磁盘历史指标
     *
     * @param monitorId 监控 ID
     * @param startTime 起始时间
     * @param endTime   结束时间
     * @return 磁盘历史指标列表
     */
    ReturnResult<List<MsMonitorMetricDisk>> getDiskHistory(Long monitorId, LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 获取最新 Redis 指标
     *
     * @param monitorId 监控 ID
     * @return Redis 指标
     */
    ReturnResult<MsMonitorRedisMetric> getLatestRedisMetric(Long monitorId);

    /**
     * 获取 Redis 历史指标
     *
     * @param monitorId 监控 ID
     * @param startTime 起始时间
     * @param endTime   结束时间
     * @return Redis 历史指标列表
     */
    ReturnResult<List<MsMonitorRedisMetric>> getRedisMetricHistory(Long monitorId, LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 获取通用指标历史
     *
     * @param monitorId  监控 ID
     * @param metricName 指标名（可选）
     * @param startTime  起始时间
     * @param endTime    结束时间
     * @return 通用指标列表
     */
    ReturnResult<List<MsMonitorMetric>> getGenericMetricHistory(Long monitorId, String metricName, LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 获取通用指标小时聚合统计
     *
     * @param monitorId  监控 ID
     * @param metricName 指标名（可选）
     * @param startTime  起始时间
     * @param endTime    结束时间
     * @return 聚合统计列表
     */
    ReturnResult<List<MsMonitorMetricStatistics>> getMetricStatistics(Long monitorId, String metricName, LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 获取 Redis 指标小时聚合统计
     *
     * @param monitorId  监控 ID
     * @param metricName 指标名（可选）
     * @param startTime  起始时间
     * @param endTime    结束时间
     * @return Redis 聚合统计列表
     */
    ReturnResult<List<MsMonitorRedisMetricStatistics>> getRedisMetricStatistics(Long monitorId, String metricName, LocalDateTime startTime, LocalDateTime endTime);
}