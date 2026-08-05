package com.chua.starter.monitor.support.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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

    ReturnResult<Void> writeCollectResult(CollectResult result);

    ReturnResult<Void> writePoints(Long monitorId, List<MetricPoint> points, LocalDateTime collectTime);

    ReturnResult<Void> aggregateHourly();

    // ===== 查询 =====
    ReturnResult<MsMonitorMetricCpu> getLatestCpu(Long monitorId);
    ReturnResult<MsMonitorMetricMemory> getLatestMemory(Long monitorId);
    ReturnResult<List<MsMonitorMetricDisk>> getLatestDisk(Long monitorId);
    ReturnResult<MsMonitorMetricNetwork> getLatestNetwork(Long monitorId);
    ReturnResult<MsMonitorMetricJvm> getLatestJvm(Long monitorId);

    ReturnResult<List<MsMonitorMetricCpu>> getCpuHistory(Long monitorId, LocalDateTime startTime, LocalDateTime endTime);
    ReturnResult<List<MsMonitorMetricMemory>> getMemoryHistory(Long monitorId, LocalDateTime startTime, LocalDateTime endTime);
    ReturnResult<List<MsMonitorMetricJvm>> getJvmHistory(Long monitorId, LocalDateTime startTime, LocalDateTime endTime);
    ReturnResult<List<MsMonitorMetricNetwork>> getNetworkHistory(Long monitorId, LocalDateTime startTime, LocalDateTime endTime);

    ReturnResult<MsMonitorRedisMetric> getLatestRedisMetric(Long monitorId);
    ReturnResult<List<MsMonitorRedisMetric>> getRedisMetricHistory(Long monitorId, LocalDateTime startTime, LocalDateTime endTime);
}