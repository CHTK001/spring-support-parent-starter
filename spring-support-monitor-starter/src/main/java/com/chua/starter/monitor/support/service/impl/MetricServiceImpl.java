package com.chua.starter.monitor.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.utils.IdUtils;
import com.chua.common.support.utils.NumberUtils;
import com.chua.starter.monitor.support.entity.*;
import com.chua.starter.monitor.support.event.MetricCollectedEvent;
import com.chua.starter.monitor.support.mapper.*;
import com.chua.starter.monitor.support.service.MetricService;
import com.chua.starter.monitor.support.service.alarm.AlarmEngineService;
import com.chua.starter.monitor.support.spi.CollectResult;
import com.chua.starter.monitor.support.spi.MetricPoint;
import com.chua.starter.monitor.support.spi.impl.MetricTimeSeriesWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 监控指标 服务 实现
 *
 * @since 2026/8/4
 * @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MetricServiceImpl implements MetricService {

    /**
     * 通用指标 映射器
     */
    private final MsMonitorMetricMapper metricMapper;
    /**
     * 通用指标统计 映射器
     */
    private final MsMonitorMetricStatisticsMapper statisticsMapper;
    /**
     * CPU 指标 映射器
     */
    private final MsMonitorMetricCpuMapper cpuMapper;
    /**
     * 内存指标 映射器
     */
    private final MsMonitorMetricMemoryMapper memoryMapper;
    /**
     * 磁盘指标 映射器
     */
    private final MsMonitorMetricDiskMapper diskMapper;
    /**
     * 网络指标 映射器
     */
    private final MsMonitorMetricNetworkMapper networkMapper;
    /**
     * JVM 指标 映射器
     */
    private final MsMonitorMetricJvmMapper jvmMapper;
    /**
     * Redis 指标 映射器
     */
    private final MsMonitorRedisMetricMapper redisMetricMapper;
    /**
     * Redis 指标统计 映射器
     */
    private final MsMonitorRedisMetricStatisticsMapper redisMetricStatsMapper;
    /**
     * JDBC 模板
     */
    private final JdbcTemplate jdbcTemplate;
    /**
     * JSON 序列化器
     */
    private final ObjectMapper objectMapper;
    /**
     * 时序存储门面（influxdb / Redis / 无，按配置选择）
     */
    private final MetricTimeSeriesWriter timeSeriesWriter;
    /**
     * 告警引擎（写入链路尾部评估）
     */
    private final AlarmEngineService alarmEngineService;

    /**
     * Spring 事件发布器（采集入库后发布，解耦告警评估与实时推送）
     */
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 默认批量插入大小
     */
    private static final int BATCH_SIZE = 200;

    @Override
    @Transactional(rollbackFor = Exception.class)
    @org.springframework.cache.annotation.Caching(evict = {
            @org.springframework.cache.annotation.CacheEvict(cacheNames = "monitor:metric:latest", key = "'cpu:' + #result.monitor.id", condition = "#result != null && #result.monitor != null && #result.monitor.id != null"),
            @org.springframework.cache.annotation.CacheEvict(cacheNames = "monitor:metric:latest", key = "'memory:' + #result.monitor.id", condition = "#result != null && #result.monitor != null && #result.monitor.id != null"),
            @org.springframework.cache.annotation.CacheEvict(cacheNames = "monitor:metric:latest", key = "'disk:' + #result.monitor.id", condition = "#result != null && #result.monitor != null && #result.monitor.id != null"),
            @org.springframework.cache.annotation.CacheEvict(cacheNames = "monitor:metric:latest", key = "'network:' + #result.monitor.id", condition = "#result != null && #result.monitor != null && #result.monitor.id != null"),
            @org.springframework.cache.annotation.CacheEvict(cacheNames = "monitor:metric:latest", key = "'jvm:' + #result.monitor.id", condition = "#result != null && #result.monitor != null && #result.monitor.id != null")
    })
    /**
     * 写入collect结果
     *
     * @param result 结果
     * @return 写入collect结果的结果
     */
    public ReturnResult<Void> writeCollectResult(CollectResult result) {
        try {
            if (result == null || result.getPoints() == null || result.getPoints().isEmpty()) {
                return ReturnResult.ok();
            }
            Long monitorId = result.getMonitor() != null ? result.getMonitor().getId() : null;
            LocalDateTime collectTime = result.getCollectTime() != null ? result.getCollectTime() : LocalDateTime.now();
            List<MsMonitorMetric> genericBatch = new ArrayList<>();
            List<MsMonitorMetricCpu> cpuBatch = new ArrayList<>();
            List<MsMonitorMetricMemory> memBatch = new ArrayList<>();
            List<MsMonitorMetricDisk> diskBatch = new ArrayList<>();
            List<MsMonitorMetricNetwork> netBatch = new ArrayList<>();
            List<MsMonitorMetricJvm> jvmBatch = new ArrayList<>();
            List<MsMonitorRedisMetric> redisBatch = new ArrayList<>();

            for (MetricPoint point : result.getPoints()) {
                String target = point.getTargetTable();
                if (target == null || target.isEmpty() || "ms_monitor_metric".equals(target)) {
                    genericBatch.add(buildGenericMetric(monitorId, point, collectTime));
                } else if ("ms_monitor_metric_cpu".equals(target)) {
                    cpuBatch.add(buildCpuMetric(monitorId, point, collectTime));
                } else if ("ms_monitor_metric_memory".equals(target)) {
                    memBatch.add(buildMemoryMetric(monitorId, point, collectTime));
                } else if ("ms_monitor_metric_disk".equals(target)) {
                    diskBatch.add(buildDiskMetric(monitorId, point, collectTime));
                } else if ("ms_monitor_metric_network".equals(target)) {
                    netBatch.add(buildNetworkMetric(monitorId, point, collectTime));
                } else if ("ms_monitor_metric_jvm".equals(target)) {
                    jvmBatch.add(buildJvmMetric(monitorId, point, collectTime));
                } else if ("ms_monitor_redis_metric".equals(target)) {
                    redisBatch.add(buildRedisMetric(monitorId, point, collectTime));
                } else {
                    genericBatch.add(buildGenericMetric(monitorId, point, collectTime));
                }
            }
            /**
             * 批量插入generic。
             * @param genericBatch genericbatch
             */
            batchInsertGeneric(genericBatch);
            /**
             * 批量插入cpu。
             * @param cpuBatch cpubatch
             */
            batchInsertCpu(cpuBatch);
            /**
             * 批量插入内存。
             * @param memBatch membatch
             */
            batchInsertMemory(memBatch);
            /**
             * 批量插入disk。
             * @param diskBatch diskbatch
             */
            batchInsertDisk(diskBatch);
            /**
             * 批量插入network。
             * @param netBatch netbatch
             */
            batchInsertNetwork(netBatch);
            /**
             * 批量插入jvm。
             * @param jvmBatch jvmbatch
             */
            batchInsertJvm(jvmBatch);
            /**
             * 批量插入Redis。
             * @param redisBatch redisbatch
             */
            batchInsertRedis(redisBatch);
 // 时序明细同步写入（按 监控.timeseries.engine 选择 influxdb/Redis，未配置时跳过），失败不阻断主链路
            timeSeriesWriter.write(result);
 // 写入链路尾部：发布采集完成事件，解耦告警评估 / 实时推送（见 指标collected事件监听器）
            if (monitorId != null) {
                try {
                    eventPublisher.publishEvent(new MetricCollectedEvent(result));
                } catch (Exception e) {
                    log.debug("[event] 采集事件发布跳过: {}", e.getMessage());
                }
            }
            return ReturnResult.ok();
        } catch (Exception e) {
            log.error("写入采集结果失败", e);
            return ReturnResult.error(e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 写入Points
     *
     * @param monitorId 监控标识
     * @param points points
     * @param collectTime collect时间
     * @return 写入points的结果
     */
    public ReturnResult<Void> writePoints(Long monitorId, List<MetricPoint> points, LocalDateTime collectTime) {
        try {
            if (points == null || points.isEmpty()) {
                return ReturnResult.ok();
            }
            LocalDateTime ct = collectTime != null ? collectTime : LocalDateTime.now();
            List<MsMonitorMetric> genericBatch = new ArrayList<>();
            for (MetricPoint point : points) {
                genericBatch.add(buildGenericMetric(monitorId, point, ct));
            }
            /**
             * 批量插入generic。
             * @param genericBatch genericbatch
             */
            batchInsertGeneric(genericBatch);
            return ReturnResult.ok();
        } catch (Exception e) {
            log.error("写入指标点失败", e);
            return ReturnResult.error(e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * aggregate时薪
     *
     * @return aggregate时薪的结果
     */
    public ReturnResult<Void> aggregateHourly() {
        try {
            LocalDateTime hourEnd = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS);
            LocalDateTime hourStart = hourEnd.minusHours(1);
            log.info("开始小时聚合, 时段 {} ~ {}", hourStart, hourEnd);

            // ---- 聚合通用指标 ----
            List<MsMonitorMetric> metricList = metricMapper.selectList(
                    new LambdaQueryWrapper<MsMonitorMetric>()
                            .ge(MsMonitorMetric::getCollectTime, hourStart)
                            .lt(MsMonitorMetric::getCollectTime, hourEnd));

            List<MsMonitorMetricStatistics> existingStats = statisticsMapper.selectList(
                    new LambdaQueryWrapper<MsMonitorMetricStatistics>()
                            .eq(MsMonitorMetricStatistics::getHourBucket, hourStart));

            Map<StatisticsKey, MsMonitorMetricStatistics> statMap = existingStats.stream()
                    .collect(Collectors.toMap(
                            s -> new StatisticsKey(s.getMonitorId(), s.getMetricName()),
                            s -> s));

            Map<StatisticsKey, List<MsMonitorMetric>> grouped = metricList.stream()
                    .collect(Collectors.groupingBy(
                            m -> new StatisticsKey(m.getMonitorId(), m.getMetricName())));

            List<MsMonitorMetricStatistics> statsToSave = new ArrayList<>();
            for (Map.Entry<StatisticsKey, List<MsMonitorMetric>> entry : grouped.entrySet()) {
                StatisticsKey key = entry.getKey();
                List<MsMonitorMetric> items = entry.getValue();
                if (items.isEmpty()) {
                    continue;
                }
                MsMonitorMetricStatistics stat = statMap.getOrDefault(key, new MsMonitorMetricStatistics());
                stat.setMonitorId(key.monitorId);
                stat.setMetricName(key.metricName);
                stat.setHourBucket(hourStart);
                stat.setAvgValue(items.stream().mapToDouble(MsMonitorMetric::getMetricValue).average().orElse(0.0));
                stat.setMinValue(items.stream().mapToDouble(MsMonitorMetric::getMetricValue).min().orElse(0.0));
                stat.setMaxValue(items.stream().mapToDouble(MsMonitorMetric::getMetricValue).max().orElse(0.0));
                stat.setCountValue((long) items.size());
                statsToSave.add(stat);
            }
            if (statsToSave.isEmpty()) {
                log.info("小时聚合完成, 无通用指标数据");
            } else {
                for (MsMonitorMetricStatistics stat : statsToSave) {
                    if (stat.getId() == null) {
                        statisticsMapper.insert(stat);
                    } else {
                        statisticsMapper.updateById(stat);
                    }
                }
                log.info("小时聚合完成, 通用指标 {} 行, 时段 {} ~ {}", statsToSave.size(), hourStart, hourEnd);
            }

            // ---- 聚合 Redis 指标 ----
            List<MsMonitorRedisMetric> redisList = redisMetricMapper.selectList(
                    new LambdaQueryWrapper<MsMonitorRedisMetric>()
                            .ge(MsMonitorRedisMetric::getCollectTime, hourStart)
                            .lt(MsMonitorRedisMetric::getCollectTime, hourEnd));

            List<MsMonitorRedisMetricStatistics> existingRedisStats = redisMetricStatsMapper.selectList(
                    new LambdaQueryWrapper<MsMonitorRedisMetricStatistics>()
                            .eq(MsMonitorRedisMetricStatistics::getHourBucket, hourStart));

            Map<StatisticsKey, MsMonitorRedisMetricStatistics> redisStatMap = existingRedisStats.stream()
                    .collect(Collectors.toMap(
                            s -> new StatisticsKey(s.getMonitorId(), s.getMetricName()),
                            s -> s));

            Map<StatisticsKey, List<MsMonitorRedisMetric>> redisGrouped = redisList.stream()
                    .collect(Collectors.groupingBy(
                            m -> new StatisticsKey(m.getMonitorId(), m.getMetricName())));

            int redisCount = 0;
            for (Map.Entry<StatisticsKey, List<MsMonitorRedisMetric>> entry : redisGrouped.entrySet()) {
                StatisticsKey key = entry.getKey();
                List<MsMonitorRedisMetric> items = entry.getValue();
                if (items.isEmpty()) {
                    continue;
                }
                MsMonitorRedisMetricStatistics stat = redisStatMap.getOrDefault(key, new MsMonitorRedisMetricStatistics());
                stat.setMonitorId(key.monitorId);
                stat.setMetricName(key.metricName);
                stat.setHourBucket(hourStart);
                stat.setAvgValue(items.stream().mapToDouble(MsMonitorRedisMetric::getMetricValue).average().orElse(0.0));
                stat.setMinValue(items.stream().mapToDouble(MsMonitorRedisMetric::getMetricValue).min().orElse(0.0));
                stat.setMaxValue(items.stream().mapToDouble(MsMonitorRedisMetric::getMetricValue).max().orElse(0.0));
                stat.setCountValue((long) items.size());
                if (stat.getId() == null) {
                    redisMetricStatsMapper.insert(stat);
                } else {
                    redisMetricStatsMapper.updateById(stat);
                }
                redisCount++;
            }
            log.info("小时聚合完成, Redis 指标 {} 行, 时段 {} ~ {}", redisCount, hourStart, hourEnd);

            return ReturnResult.ok();
        } catch (Exception e) {
            log.error("小时聚合失败", e);
            return ReturnResult.error(e.getMessage());
        }
    }

    /**
     * 统计聚合唯一键（监控标识 + 指标名称）
     * @param monitorId 监控标识
     * @param metricName 指标名称
     * @return statistics键的结果
     */
    private record StatisticsKey(Long monitorId, String metricName) {
    }

    @Override
    /** cleanup旧数据 */
    public ReturnResult<Void> cleanupOldData(int retentionDays) {
        try {
            LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
            int deleted = jdbcTemplate.update("DELETE FROM ms_monitor_metric WHERE ms_monitor_metric_collect_time < ?", cutoff);
            jdbcTemplate.update("DELETE FROM ms_monitor_metric_cpu WHERE ms_monitor_metric_cpu_collect_time < ?", cutoff);
            jdbcTemplate.update("DELETE FROM ms_monitor_metric_memory WHERE ms_monitor_metric_memory_collect_time < ?", cutoff);
            jdbcTemplate.update("DELETE FROM ms_monitor_metric_disk WHERE ms_monitor_metric_disk_collect_time < ?", cutoff);
            jdbcTemplate.update("DELETE FROM ms_monitor_metric_network WHERE ms_monitor_metric_network_collect_time < ?", cutoff);
            jdbcTemplate.update("DELETE FROM ms_monitor_metric_jvm WHERE ms_monitor_metric_jvm_collect_time < ?", cutoff);
            jdbcTemplate.update("DELETE FROM ms_monitor_redis_metric WHERE ms_monitor_redis_metric_collect_time < ?", cutoff);
            int deletedStats = jdbcTemplate.update("DELETE FROM ms_monitor_metric_statistics WHERE ms_monitor_metric_statistics_hour_bucket < ?", cutoff);
            int deletedRedisStats = jdbcTemplate.update("DELETE FROM ms_monitor_redis_metric_statistics WHERE ms_monitor_redis_metric_statistics_hour_bucket < ?", cutoff);
            log.info("数据清理完成, 删除 {} 条指标, {} 条统计", deleted, deletedStats + deletedRedisStats);
            return ReturnResult.ok();
        } catch (Exception e) {
            log.error("数据清理失败", e);
            return ReturnResult.error(e.getMessage());
        }
    }

    @Override
    @Cacheable(cacheNames = "monitor:metric:latest", key = "'cpu:' + #monitorId", unless = "#result == null || #result.data == null")
    /**
    * 获取latestcpu
    *
    * @param monitorId 监控标识
    * @return 获取latestcpu的结果
    */
    public ReturnResult<MsMonitorMetricCpu> getLatestCpu(Long monitorId) {
        return ReturnResult.ok(cpuMapper.selectOne(
                new LambdaQueryWrapper<MsMonitorMetricCpu>()
                        .eq(MsMonitorMetricCpu::getMonitorId, monitorId)
                        .orderByDesc(MsMonitorMetricCpu::getCollectTime)
                        .last("LIMIT 1")));
    }

    @Override
    @Cacheable(cacheNames = "monitor:metric:latest", key = "'memory:' + #monitorId", unless = "#result == null || #result.data == null")
    /**
     * 获取latest内存
     *
     * @param monitorId 监控标识
     * @return 获取latest内存的结果
     */
    public ReturnResult<MsMonitorMetricMemory> getLatestMemory(Long monitorId) {
        return ReturnResult.ok(memoryMapper.selectOne(
                new LambdaQueryWrapper<MsMonitorMetricMemory>()
                        .eq(MsMonitorMetricMemory::getMonitorId, monitorId)
                        .orderByDesc(MsMonitorMetricMemory::getCollectTime)
                        .last("LIMIT 1")));
    }

    @Override
    @Cacheable(cacheNames = "monitor:metric:latest", key = "'disk:' + #monitorId", unless = "#result == null || #result.data == null || #result.data.isEmpty()")
    /**
     * 获取latestdisk
     *
     * @param monitorId 监控标识
     * @return 获取latestdisk的结果
     */
    public ReturnResult<List<MsMonitorMetricDisk>> getLatestDisk(Long monitorId) {
        return ReturnResult.ok(diskMapper.selectList(
                new LambdaQueryWrapper<MsMonitorMetricDisk>()
                        .eq(MsMonitorMetricDisk::getMonitorId, monitorId)
                        .orderByDesc(MsMonitorMetricDisk::getCollectTime)
                        .last("LIMIT 5")));
    }

    @Override
    @Cacheable(cacheNames = "monitor:metric:latest", key = "'network:' + #monitorId", unless = "#result == null || #result.data == null")
    /**
     * 获取latestnetwork
     *
     * @param monitorId 监控标识
     * @return 获取latestnetwork的结果
     */
    public ReturnResult<MsMonitorMetricNetwork> getLatestNetwork(Long monitorId) {
        return ReturnResult.ok(networkMapper.selectOne(
                new LambdaQueryWrapper<MsMonitorMetricNetwork>()
                        .eq(MsMonitorMetricNetwork::getMonitorId, monitorId)
                        .orderByDesc(MsMonitorMetricNetwork::getCollectTime)
                        .last("LIMIT 1")));
    }

    @Override
    @Cacheable(cacheNames = "monitor:metric:latest", key = "'jvm:' + #monitorId", unless = "#result == null || #result.data == null")
    /**
     * 获取latestjvm
     *
     * @param monitorId 监控标识
     * @return 获取latestjvm的结果
     */
    public ReturnResult<MsMonitorMetricJvm> getLatestJvm(Long monitorId) {
        return ReturnResult.ok(jvmMapper.selectOne(
                new LambdaQueryWrapper<MsMonitorMetricJvm>()
                        .eq(MsMonitorMetricJvm::getMonitorId, monitorId)
                        .orderByDesc(MsMonitorMetricJvm::getCollectTime)
                        .last("LIMIT 1")));
    }

    @Override
    /** 获取cpu历史 */
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
    /** 获取内存历史 */
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
    /** 获取jvm历史 */
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
    /** 获取network历史 */
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
    /** 获取disk历史 */
    public ReturnResult<List<MsMonitorMetricDisk>> getDiskHistory(Long monitorId, LocalDateTime startTime, LocalDateTime endTime) {
        return ReturnResult.ok(diskMapper.selectList(
                new LambdaQueryWrapper<MsMonitorMetricDisk>()
                        .eq(MsMonitorMetricDisk::getMonitorId, monitorId)
                        .ge(startTime != null, MsMonitorMetricDisk::getCollectTime, startTime)
                        .le(endTime != null, MsMonitorMetricDisk::getCollectTime, endTime)
                        .orderByAsc(MsMonitorMetricDisk::getCollectTime)
                        .last("LIMIT 200")));
    }

    @Override
    /** 获取latestredis指标 */
    public ReturnResult<MsMonitorRedisMetric> getLatestRedisMetric(Long monitorId) {
        return ReturnResult.ok(redisMetricMapper.selectOne(
                new LambdaQueryWrapper<MsMonitorRedisMetric>()
                        .eq(MsMonitorRedisMetric::getMonitorId, monitorId)
                        .orderByDesc(MsMonitorRedisMetric::getCollectTime)
                        .last("LIMIT 1")));
    }

    @Override
    /** 获取Redis指标历史 */
    public ReturnResult<List<MsMonitorRedisMetric>> getRedisMetricHistory(Long monitorId, LocalDateTime startTime, LocalDateTime endTime) {
        return ReturnResult.ok(redisMetricMapper.selectList(
                new LambdaQueryWrapper<MsMonitorRedisMetric>()
                        .eq(MsMonitorRedisMetric::getMonitorId, monitorId)
                        .ge(startTime != null, MsMonitorRedisMetric::getCollectTime, startTime)
                        .le(endTime != null, MsMonitorRedisMetric::getCollectTime, endTime)
                        .orderByAsc(MsMonitorRedisMetric::getCollectTime)
                        .last("LIMIT 200")));
    }

    @Override
    /** 获取generic指标历史 */
    public ReturnResult<List<MsMonitorMetric>> getGenericMetricHistory(Long monitorId, String metricName, LocalDateTime startTime, LocalDateTime endTime) {
        return ReturnResult.ok(metricMapper.selectList(
                new LambdaQueryWrapper<MsMonitorMetric>()
                        .eq(MsMonitorMetric::getMonitorId, monitorId)
                        .eq(metricName != null && !metricName.isEmpty(), MsMonitorMetric::getMetricName, metricName)
                        .ge(startTime != null, MsMonitorMetric::getCollectTime, startTime)
                        .le(endTime != null, MsMonitorMetric::getCollectTime, endTime)
                        .orderByAsc(MsMonitorMetric::getCollectTime)
                        .last("LIMIT 200")));
    }

    @Override
    /** 获取指标statistics */
    public ReturnResult<List<MsMonitorMetricStatistics>> getMetricStatistics(Long monitorId, String metricName, LocalDateTime startTime, LocalDateTime endTime) {
        return ReturnResult.ok(statisticsMapper.selectList(
                new LambdaQueryWrapper<MsMonitorMetricStatistics>()
                        .eq(MsMonitorMetricStatistics::getMonitorId, monitorId)
                        .eq(metricName != null && !metricName.isEmpty(), MsMonitorMetricStatistics::getMetricName, metricName)
                        .ge(startTime != null, MsMonitorMetricStatistics::getHourBucket, startTime)
                        .le(endTime != null, MsMonitorMetricStatistics::getHourBucket, endTime)
                        .orderByAsc(MsMonitorMetricStatistics::getHourBucket)));
    }

@Override
    /** 获取 Redis 指标小时聚合统计 */
    public ReturnResult<List<MsMonitorRedisMetricStatistics>> getRedisMetricStatistics(Long monitorId, String metricName, LocalDateTime startTime, LocalDateTime endTime) {
        return ReturnResult.ok(redisMetricStatsMapper.selectList(
                new LambdaQueryWrapper<MsMonitorRedisMetricStatistics>()
                        .eq(MsMonitorRedisMetricStatistics::getMonitorId, monitorId)
                        .eq(metricName != null && !metricName.isEmpty(), MsMonitorRedisMetricStatistics::getMetricName, metricName)
                        .ge(startTime != null, MsMonitorRedisMetricStatistics::getHourBucket, startTime)
                        .le(endTime != null, MsMonitorRedisMetricStatistics::getHourBucket, endTime)
                        .orderByAsc(MsMonitorRedisMetricStatistics::getHourBucket)));
    }

    @Override
    /** 导出指标历史数据 */
    public ReturnResult<String> exportMetricHistory(Long monitorId, String metricName, LocalDateTime startTime, LocalDateTime endTime) {
        try {
 // 1. 查询通用指标 (ms_监控_指标)
            LambdaQueryWrapper<MsMonitorMetric> wrapper = new LambdaQueryWrapper<MsMonitorMetric>()
                    .eq(monitorId != null, MsMonitorMetric::getMonitorId, monitorId)
                    .ge(startTime != null, MsMonitorMetric::getCollectTime, startTime)
                    .le(endTime != null, MsMonitorMetric::getCollectTime, endTime);
            if (metricName != null && !metricName.isEmpty()) {
                wrapper.eq(MsMonitorMetric::getMetricName, metricName);
            }
            List<MsMonitorMetric> metrics = metricMapper.selectList(wrapper);

 // 2. 查询 Redis 指标 (ms_监控_redis_指标)
            LambdaQueryWrapper<MsMonitorRedisMetric> redisWrapper = new LambdaQueryWrapper<MsMonitorRedisMetric>()
                    .eq(monitorId != null, MsMonitorRedisMetric::getMonitorId, monitorId)
                    .ge(startTime != null, MsMonitorRedisMetric::getCollectTime, startTime)
                    .le(endTime != null, MsMonitorRedisMetric::getCollectTime, endTime);
            if (metricName != null && !metricName.isEmpty()) {
                redisWrapper.eq(MsMonitorRedisMetric::getMetricName, metricName);
            }
            List<MsMonitorRedisMetric> redisMetrics = redisMetricMapper.selectList(redisWrapper);

            // 3. 生成 CSV：通用指标与 Redis 指标共用一套固定列，避免行长度不一致
            StringBuilder csv = new StringBuilder();
            csv.append("source,monitorId,metricName,metricValue,collectTime,metricTags\n");
            for (MsMonitorMetric m : metrics) {
                appendCsvRow(csv, "GENERIC", m.getMonitorId(), m.getMetricName(),
                        m.getMetricValue(), m.getCollectTime(), m.getMetricTags());
            }
            for (MsMonitorRedisMetric r : redisMetrics) {
                appendCsvRow(csv, "REDIS", r.getMonitorId(), r.getMetricName(),
                        r.getMetricValue(), r.getCollectTime(), r.getMetricTags());
            }
            return ReturnResult.ok(csv.toString());
        } catch (Exception e) {
            log.error("指标导出失败", e);
            return ReturnResult.error(e.getMessage());
        }
    }

    /**
     * 追加一行 CSV（固定 6 列）
     *
     * @param csv        目标缓冲
     * @param source     指标来源: GENERIC / REDIS
     * @param monitorId  监控资产 ID
     * @param metricName 指标名
     * @param value      指标值
     * @param collectTime 采集时间
     * @param tags       标签 JSON
     */
    private void appendCsvRow(StringBuilder csv, String source, Long monitorId, String metricName,
                              Object value, LocalDateTime collectTime, String tags) {
        csv.append(source).append(',')
                .append(monitorId == null ? "" : monitorId).append(',')
                .append(escapeCsv(metricName)).append(',')
                .append(escapeCsv(value == null ? "" : String.valueOf(value))).append(',')
                .append(collectTime == null ? "" : collectTime).append(',')
                .append(escapeCsv(tags)).append('\n');
    }

    /**
     * CSV 字段转义（RFC4180：含引号/逗号/换行时用双引号包裹，内部引号翻倍）
     *
     * @param raw 原始值
     * @return 转义后的值
     */
    private String escapeCsv(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        if (raw.indexOf('"') < 0 && raw.indexOf(',') < 0
                && raw.indexOf('\n') < 0 && raw.indexOf('\r') < 0) {
            return raw;
        }
        return '"' + raw.replace("\"", "\"\"") + '"';
    }

    /**
     * 批量插入通用指标
     *
     * @param batch 指标列表
     */
    private void batchInsertGeneric(List<MsMonitorMetric> batch) {
        if (batch.isEmpty()) {
            return;
        }
        for (int i = 0; i < batch.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, batch.size());
            batch.subList(i, end).forEach(metricMapper::insert);
        }
    }

    /**
     * 批量插入 CPU 指标
     *
     * @param batch CPU 指标列表
     */
    private void batchInsertCpu(List<MsMonitorMetricCpu> batch) {
        if (batch.isEmpty()) {
            return;
        }
        for (MsMonitorMetricCpu cpu : batch) {
            cpuMapper.insert(cpu);
        }
    }

    /**
     * 批量插入内存指标
     *
     * @param batch 内存指标列表
     */
    private void batchInsertMemory(List<MsMonitorMetricMemory> batch) {
        if (batch.isEmpty()) {
            return;
        }
        for (MsMonitorMetricMemory mem : batch) {
            memoryMapper.insert(mem);
        }
    }

    /**
     * 批量插入磁盘指标
     *
     * @param batch 磁盘指标列表
     */
    private void batchInsertDisk(List<MsMonitorMetricDisk> batch) {
        if (batch.isEmpty()) {
            return;
        }
        for (MsMonitorMetricDisk disk : batch) {
            diskMapper.insert(disk);
        }
    }

    /**
     * 批量插入网络指标
     *
     * @param batch 网络指标列表
     */
    private void batchInsertNetwork(List<MsMonitorMetricNetwork> batch) {
        if (batch.isEmpty()) {
            return;
        }
        for (MsMonitorMetricNetwork net : batch) {
            networkMapper.insert(net);
        }
    }

    /**
     * 批量插入 JVM 指标
     *
     * @param batch JVM 指标列表
     */
    private void batchInsertJvm(List<MsMonitorMetricJvm> batch) {
        if (batch.isEmpty()) {
            return;
        }
        for (MsMonitorMetricJvm jvm : batch) {
            jvmMapper.insert(jvm);
        }
    }

    /**
     * 批量插入 Redis 指标
     *
     * @param batch Redis 指标列表
     */
    private void batchInsertRedis(List<MsMonitorRedisMetric> batch) {
        if (batch.isEmpty()) {
            return;
        }
        for (MsMonitorRedisMetric r : batch) {
            redisMetricMapper.insert(r);
        }
    }

    /**
     * 构建通用指标实体
     *
     * @param monitorId   监控主机 标识
     * @param point       指标点
     * @param collectTime 采集时间
     * @return 通用指标实体
     */
    private MsMonitorMetric buildGenericMetric(Long monitorId, MetricPoint point, LocalDateTime collectTime) {
        MsMonitorMetric metric = new MsMonitorMetric();
        metric.setId(IdUtils.createSnowflakeId());
        metric.setMonitorId(monitorId);
        metric.setMetricName(point.getMetricName());
        metric.setMetricValue(point.getValue());
        if (point.getTags() != null && !point.getTags().isEmpty()) {
            try {
                metric.setMetricTags(objectMapper.writeValueAsString(point.getTags()));
            } catch (Exception ignored) {
            }
        }
        metric.setCollectTime(collectTime);
        return metric;
    }

    /**
     * 构建 CPU 指标实体, 从 指标point 的 标签 中提取 模型/核心 字段
     *
     * @param monitorId   监控主机 标识
     * @param point       指标点
     * @param collectTime 采集时间
     * @return CPU 指标实体
     */
    private MsMonitorMetricCpu buildCpuMetric(Long monitorId, MetricPoint point, LocalDateTime collectTime) {
        MsMonitorMetricCpu cpu = new MsMonitorMetricCpu();
        cpu.setId(IdUtils.createSnowflakeId());
        cpu.setMonitorId(monitorId);
        Map<String, String> tags = point.getTags();
        cpu.setCpuModel(tags != null ? tags.get("model") : null);
        cpu.setCpuCores(tags != null ? NumberUtils.toInt(tags.get("cores"), 0) : 0);
        cpu.setCpuUsage(point.getValue());
        cpu.setLoad1(tags != null && tags.containsKey("load1") ? Double.parseDouble(tags.get("load1")) : null);
        cpu.setLoad5(tags != null && tags.containsKey("load5") ? Double.parseDouble(tags.get("load5")) : null);
        cpu.setLoad15(tags != null && tags.containsKey("load15") ? Double.parseDouble(tags.get("load15")) : null);
        cpu.setCollectTime(collectTime);
        return cpu;
    }

    /**
     * 构建内存指标实体, 从 指标point 的 标签 中提取 total_bytes/used_bytes/free_bytes 字段
     *
     * @param monitorId   监控主机 标识
     * @param point       指标点
     * @param collectTime 采集时间
     * @return 内存指标实体
     */
    private MsMonitorMetricMemory buildMemoryMetric(Long monitorId, MetricPoint point, LocalDateTime collectTime) {
        MsMonitorMetricMemory mem = new MsMonitorMetricMemory();
        mem.setId(IdUtils.createSnowflakeId());
        mem.setMonitorId(monitorId);
        Map<String, String> tags = point.getTags();
        mem.setTotalBytes(tags != null ? NumberUtils.toLong(tags.get("total_bytes"), 0L) : 0L);
        mem.setUsedBytes(tags != null ? NumberUtils.toLong(tags.get("used_bytes"), 0L) : 0L);
        mem.setFreeBytes(tags != null ? NumberUtils.toLong(tags.get("free_bytes"), 0L) : 0L);
        mem.setUsagePercent(point.getValue());
        mem.setCollectTime(collectTime);
        return mem;
    }

    /**
     * 构建磁盘指标实体, 从 指标point 的 标签 中提取 mount/fs/total_bytes/used_bytes 字段
     *
     * @param monitorId   监控主机 标识
     * @param point       指标点
     * @param collectTime 采集时间
     * @return 磁盘指标实体
     */
    private MsMonitorMetricDisk buildDiskMetric(Long monitorId, MetricPoint point, LocalDateTime collectTime) {
        MsMonitorMetricDisk disk = new MsMonitorMetricDisk();
        disk.setId(IdUtils.createSnowflakeId());
        disk.setMonitorId(monitorId);
        Map<String, String> tags = point.getTags();
        disk.setMountPoint(tags != null ? tags.get("mount") : null);
        disk.setFileSystem(tags != null ? tags.get("fs") : null);
        disk.setTotalBytes(tags != null ? NumberUtils.toLong(tags.get("total_bytes"), 0L) : 0L);
        disk.setUsedBytes(tags != null ? NumberUtils.toLong(tags.get("used_bytes"), 0L) : 0L);
        disk.setUsagePercent(point.getValue());
        disk.setCollectTime(collectTime);
        return disk;
    }

    /**
     * 构建网络指标实体, 从 指标point 的 标签 中提取 iface/rx_bytes/tx_bytes 字段,
     * 并根据前一次采集记录计算网卡速率 速度bps
     *
     * @param monitorId   监控主机 标识
     * @param point       指标点
     * @param collectTime 采集时间
     * @return 网络指标实体
     */
    private MsMonitorMetricNetwork buildNetworkMetric(Long monitorId, MetricPoint point, LocalDateTime collectTime) {
        MsMonitorMetricNetwork net = new MsMonitorMetricNetwork();
        net.setId(IdUtils.createSnowflakeId());
        net.setMonitorId(monitorId);
        Map<String, String> tags = point.getTags();
        String iface = tags != null ? tags.get("iface") : null;
        net.setInterfaceName(iface);
        long rxBytes = tags != null ? NumberUtils.toLong(tags.get("rx_bytes"), 0L) : 0L;
        long txBytes = tags != null ? NumberUtils.toLong(tags.get("tx_bytes"), 0L) : 0L;
        net.setRxBytes(rxBytes);
        net.setTxBytes(txBytes);
        // 查询前一次采集记录，计算速率增量
        long speedBps = 0L;
        if (iface != null) {
            MsMonitorMetricNetwork prev = networkMapper.selectOne(
                    new LambdaQueryWrapper<MsMonitorMetricNetwork>()
                            .eq(MsMonitorMetricNetwork::getMonitorId, monitorId)
                            .eq(MsMonitorMetricNetwork::getInterfaceName, iface)
                            .lt(MsMonitorMetricNetwork::getCollectTime, collectTime)
                            .orderByDesc(MsMonitorMetricNetwork::getCollectTime)
                            .last("LIMIT 1"));
            if (prev != null && prev.getRxBytes() != null && prev.getCollectTime() != null) {
                long seconds = ChronoUnit.SECONDS.between(prev.getCollectTime(), collectTime);
                if (seconds > 0) {
                    long rxDelta = rxBytes - prev.getRxBytes();
                    long txDelta = txBytes - (prev.getTxBytes() != null ? prev.getTxBytes() : 0L);
                    // 速率 = (增量字节 * 8) / 间隔秒数 (bps)
                    speedBps = Math.max(0L, Math.max(rxDelta, txDelta) * 8 / seconds);
                }
            }
        }
        net.setSpeedBps(speedBps);
        net.setCollectTime(collectTime);
        return net;
    }

    /**
     * 构建 JVM 指标实体, 从 指标point 的 标签 中提取 heap_used/heap_最大/young_gc/完整_gc/threads 字段
     *
     * @param monitorId   监控主机 标识
     * @param point       指标点
     * @param collectTime 采集时间
     * @return JVM 指标实体
     */
    private MsMonitorMetricJvm buildJvmMetric(Long monitorId, MetricPoint point, LocalDateTime collectTime) {
        MsMonitorMetricJvm jvm = new MsMonitorMetricJvm();
        jvm.setId(IdUtils.createSnowflakeId());
        jvm.setMonitorId(monitorId);
        Map<String, String> tags = point.getTags();
        jvm.setHeapUsedBytes(tags != null ? NumberUtils.toLong(tags.get("heap_used"), 0L) : 0L);
        jvm.setHeapMaxBytes(tags != null ? NumberUtils.toLong(tags.get("heap_max"), 0L) : 0L);
        jvm.setHeapUsagePercent(point.getValue());
        jvm.setYoungGcCount(tags != null ? NumberUtils.toLong(tags.get("young_gc"), 0L) : 0L);
        jvm.setFullGcCount(tags != null ? NumberUtils.toLong(tags.get("full_gc"), 0L) : 0L);
        jvm.setThreads(tags != null ? NumberUtils.toInt(tags.get("threads"), 0) : 0);
        jvm.setDaemonThreads(tags != null ? NumberUtils.toInt(tags.get("daemon_threads"), 0) : 0);
        jvm.setCollectTime(collectTime);
        return jvm;
    }

    /**
     * 构建 Redis 指标实体
     *
     * @param monitorId   监控主机 标识
     * @param point       指标点
     * @param collectTime 采集时间
     * @return Redis 指标实体
     */
    private MsMonitorRedisMetric buildRedisMetric(Long monitorId, MetricPoint point, LocalDateTime collectTime) {
        MsMonitorRedisMetric redis = new MsMonitorRedisMetric();
        redis.setId(IdUtils.createSnowflakeId());
        redis.setMonitorId(monitorId);
        redis.setMetricName(point.getMetricName());
        redis.setMetricValue(point.getValue());
        if (point.getTags() != null && !point.getTags().isEmpty()) {
            try {
                redis.setMetricTags(objectMapper.writeValueAsString(point.getTags()));
            } catch (Exception ignored) {
            }
        }
        redis.setCollectTime(collectTime);
        return redis;
    }
}
