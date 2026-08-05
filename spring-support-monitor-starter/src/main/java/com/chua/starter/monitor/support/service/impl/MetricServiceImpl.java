package com.chua.starter.monitor.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.utils.IdUtils;
import com.chua.starter.monitor.support.entity.*;
import com.chua.starter.monitor.support.mapper.*;
import com.chua.starter.monitor.support.service.MetricService;
import com.chua.starter.monitor.support.spi.CollectResult;
import com.chua.starter.monitor.support.spi.MetricPoint;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 监控指标 Service 实现
 *
 * @author CH
 * @since 2026/8/4
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MetricServiceImpl implements MetricService {

    private final MsMonitorMetricMapper metricMapper;
    private final MsMonitorMetricStatisticsMapper statisticsMapper;
    private final MsMonitorMetricCpuMapper cpuMapper;
    private final MsMonitorMetricMemoryMapper memoryMapper;
    private final MsMonitorMetricDiskMapper diskMapper;
    private final MsMonitorMetricNetworkMapper networkMapper;
    private final MsMonitorMetricJvmMapper jvmMapper;
    private final MsMonitorRedisMetricMapper redisMetricMapper;
    private final MsMonitorRedisMetricStatisticsMapper redisMetricStatsMapper;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public ReturnResult<Void> writeCollectResult(CollectResult result) {
        try {
            if (result == null || result.getPoints() == null || result.getPoints().isEmpty()) {
                return ReturnResult.ok();
            }
            Long monitorId = result.getMonitor() != null ? result.getMonitor().getId() : null;
            LocalDateTime collectTime = result.getCollectTime() != null ? result.getCollectTime() : LocalDateTime.now();
            for (MetricPoint point : result.getPoints()) {
                MsMonitorMetric metric = new MsMonitorMetric();
                metric.setId(IdUtils.createSnowflakeId());
                metric.setMonitorId(monitorId);
                metric.setMetricName(point.getMetricName());
                metric.setMetricValue(point.getValue());
                if (point.getTags() != null && !point.getTags().isEmpty()) {
                    metric.setMetricTags(objectMapper.writeValueAsString(point.getTags()));
                }
                metric.setCollectTime(collectTime);
                metricMapper.insert(metric);
            }
            return ReturnResult.ok();
        } catch (Exception e) {
            log.error("写入采集结果失败", e);
            return ReturnResult.error(e.getMessage());
        }
    }

    @Override
    public ReturnResult<Void> writePoints(Long monitorId, List<MetricPoint> points, LocalDateTime collectTime) {
        try {
            if (points == null || points.isEmpty()) {
                return ReturnResult.ok();
            }
            LocalDateTime ct = collectTime != null ? collectTime : LocalDateTime.now();
            for (MetricPoint point : points) {
                MsMonitorMetric metric = new MsMonitorMetric();
                metric.setId(IdUtils.createSnowflakeId());
                metric.setMonitorId(monitorId);
                metric.setMetricName(point.getMetricName());
                metric.setMetricValue(point.getValue());
                if (point.getTags() != null && !point.getTags().isEmpty()) {
                    metric.setMetricTags(objectMapper.writeValueAsString(point.getTags()));
                }
                metric.setCollectTime(ct);
                metricMapper.insert(metric);
            }
            return ReturnResult.ok();
        } catch (Exception e) {
            log.error("写入指标点失败", e);
            return ReturnResult.error(e.getMessage());
        }
    }

    @Override
    public ReturnResult<Void> aggregateHourly() {
        try {
            LocalDateTime hourEnd = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS);
            LocalDateTime hourStart = hourEnd.minusHours(1);
            String sql = "INSERT INTO ms_monitor_metric_statistics(monitor_id, metric_name, hour_bucket, avg_value, min_value, max_value, count_value) " +
                    "SELECT monitor_id, metric_name, ?, AVG(metric_value), MIN(metric_value), MAX(metric_value), COUNT(*) " +
                    "FROM ms_monitor_metric " +
                    "WHERE collect_time >= ? AND collect_time < ? " +
                    "GROUP BY monitor_id, metric_name " +
                    "ON DUPLICATE KEY UPDATE avg_value=VALUES(avg_value), min_value=VALUES(min_value), max_value=VALUES(max_value), count_value=VALUES(count_value)";
            int updated = jdbcTemplate.update(sql, hourStart, hourStart, hourEnd);
            log.info("小时聚合完成, 影响 {} 行, 时段 {} ~ {}", updated, hourStart, hourEnd);
            return ReturnResult.ok();
        } catch (Exception e) {
            log.error("小时聚合失败", e);
            return ReturnResult.error(e.getMessage());
        }
    }

    @Override
    public ReturnResult<MsMonitorMetricCpu> getLatestCpu(Long monitorId) {
        return ReturnResult.ok(cpuMapper.selectOne(
                new LambdaQueryWrapper<MsMonitorMetricCpu>()
                        .eq(MsMonitorMetricCpu::getMonitorId, monitorId)
                        .orderByDesc(MsMonitorMetricCpu::getCollectTime)
                        .last("LIMIT 1")));
    }

    @Override
    public ReturnResult<MsMonitorMetricMemory> getLatestMemory(Long monitorId) {
        return ReturnResult.ok(memoryMapper.selectOne(
                new LambdaQueryWrapper<MsMonitorMetricMemory>()
                        .eq(MsMonitorMetricMemory::getMonitorId, monitorId)
                        .orderByDesc(MsMonitorMetricMemory::getCollectTime)
                        .last("LIMIT 1")));
    }

    @Override
    public ReturnResult<List<MsMonitorMetricDisk>> getLatestDisk(Long monitorId) {
        return ReturnResult.ok(diskMapper.selectList(
                new LambdaQueryWrapper<MsMonitorMetricDisk>()
                        .eq(MsMonitorMetricDisk::getMonitorId, monitorId)
                        .orderByDesc(MsMonitorMetricDisk::getCollectTime)
                        .last("LIMIT 5")));
    }

    @Override
    public ReturnResult<MsMonitorMetricNetwork> getLatestNetwork(Long monitorId) {
        return ReturnResult.ok(networkMapper.selectOne(
                new LambdaQueryWrapper<MsMonitorMetricNetwork>()
                        .eq(MsMonitorMetricNetwork::getMonitorId, monitorId)
                        .orderByDesc(MsMonitorMetricNetwork::getCollectTime)
                        .last("LIMIT 1")));
    }

    @Override
    public ReturnResult<MsMonitorMetricJvm> getLatestJvm(Long monitorId) {
        return ReturnResult.ok(jvmMapper.selectOne(
                new LambdaQueryWrapper<MsMonitorMetricJvm>()
                        .eq(MsMonitorMetricJvm::getMonitorId, monitorId)
                        .orderByDesc(MsMonitorMetricJvm::getCollectTime)
                        .last("LIMIT 1")));
    }

    @Override
    public ReturnResult<List<MsMonitorMetricCpu>> getCpuHistory(Long monitorId, LocalDateTime startTime, LocalDateTime endTime) {
        return ReturnResult.ok(cpuMapper.selectList(
                new LambdaQueryWrapper<MsMonitorMetricCpu>()
                        .eq(MsMonitorMetricCpu::getMonitorId, monitorId)
                        .ge(startTime != null, MsMonitorMetricCpu::getCollectTime, startTime)
                        .le(endTime != null, MsMonitorMetricCpu::getCollectTime, endTime)
                        .orderByAsc(MsMonitorMetricCpu::getCollectTime)
                        .last("LIMIT 200")));
    }

    @Override
    public ReturnResult<List<MsMonitorMetricMemory>> getMemoryHistory(Long monitorId, LocalDateTime startTime, LocalDateTime endTime) {
        return ReturnResult.ok(memoryMapper.selectList(
                new LambdaQueryWrapper<MsMonitorMetricMemory>()
                        .eq(MsMonitorMetricMemory::getMonitorId, monitorId)
                        .ge(startTime != null, MsMonitorMetricMemory::getCollectTime, startTime)
                        .le(endTime != null, MsMonitorMetricMemory::getCollectTime, endTime)
                        .orderByAsc(MsMonitorMetricMemory::getCollectTime)
                        .last("LIMIT 200")));
    }

    @Override
    public ReturnResult<List<MsMonitorMetricJvm>> getJvmHistory(Long monitorId, LocalDateTime startTime, LocalDateTime endTime) {
        return ReturnResult.ok(jvmMapper.selectList(
                new LambdaQueryWrapper<MsMonitorMetricJvm>()
                        .eq(MsMonitorMetricJvm::getMonitorId, monitorId)
                        .ge(startTime != null, MsMonitorMetricJvm::getCollectTime, startTime)
                        .le(endTime != null, MsMonitorMetricJvm::getCollectTime, endTime)
                        .orderByAsc(MsMonitorMetricJvm::getCollectTime)
                        .last("LIMIT 200")));
    }

    @Override
    public ReturnResult<List<MsMonitorMetricNetwork>> getNetworkHistory(Long monitorId, LocalDateTime startTime, LocalDateTime endTime) {
        return ReturnResult.ok(networkMapper.selectList(
                new LambdaQueryWrapper<MsMonitorMetricNetwork>()
                        .eq(MsMonitorMetricNetwork::getMonitorId, monitorId)
                        .ge(startTime != null, MsMonitorMetricNetwork::getCollectTime, startTime)
                        .le(endTime != null, MsMonitorMetricNetwork::getCollectTime, endTime)
                        .orderByAsc(MsMonitorMetricNetwork::getCollectTime)
                        .last("LIMIT 200")));
    }

    @Override
    public ReturnResult<MsMonitorRedisMetric> getLatestRedisMetric(Long taskId) {
        return ReturnResult.ok(redisMetricMapper.selectOne(
                new LambdaQueryWrapper<MsMonitorRedisMetric>()
                        .eq(MsMonitorRedisMetric::getMonitorId, taskId)
                        .orderByDesc(MsMonitorRedisMetric::getCollectTime)
                        .last("LIMIT 1")));
    }

    @Override
    public ReturnResult<List<MsMonitorRedisMetric>> getRedisMetricHistory(Long taskId, LocalDateTime startTime, LocalDateTime endTime) {
        return ReturnResult.ok(redisMetricMapper.selectList(
                new LambdaQueryWrapper<MsMonitorRedisMetric>()
                        .eq(MsMonitorRedisMetric::getMonitorId, taskId)
                        .ge(startTime != null, MsMonitorRedisMetric::getCollectTime, startTime)
                        .le(endTime != null, MsMonitorRedisMetric::getCollectTime, endTime)
                        .orderByAsc(MsMonitorRedisMetric::getCollectTime)
                        .last("LIMIT 200")));
    }
}