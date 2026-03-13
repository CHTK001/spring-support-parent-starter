package com.chua.starter.sync.data.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.starter.sync.data.support.entity.MonitorSyncStatistics;
import com.chua.starter.sync.data.support.entity.MonitorSyncTask;
import com.chua.starter.sync.data.support.mapper.MonitorSyncStatisticsMapper;
import com.chua.starter.sync.data.support.mapper.MonitorSyncTaskMapper;
import com.chua.starter.sync.data.support.service.SyncMonitorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 同步监控服务实现
 *
 * @author System
 * @since 2026/03/09
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SyncMonitorServiceImpl implements SyncMonitorService {

    private final MonitorSyncTaskMapper taskMapper;
    private final MonitorSyncStatisticsMapper statisticsMapper;
    
    // 实时监控数据缓存
    private final Map<Long, RealtimeData> realtimeCache = new ConcurrentHashMap<>();

    @Override
    public Map<String, Object> collectRealtimeData(Long taskId) {
        Map<String, Object> data = new HashMap<>();
        
        MonitorSyncTask task = taskMapper.selectById(taskId);
        if (task == null) {
            return data;
        }
        
        RealtimeData realtime = realtimeCache.get(taskId);
        if (realtime == null) {
            realtime = new RealtimeData();
        }

        data.put("taskId", taskId);
        data.put("taskName", task.getSyncTaskName());
        data.put("status", task.getSyncTaskStatus());
        data.put("processedRecords", realtime.processedRecords);
        data.put("totalRecords", realtime.totalRecords);
        data.put("progress", calculateProgress(realtime.processedRecords, realtime.totalRecords));
        data.put("throughput", realtime.throughput);
        data.put("startTime", task.getSyncTaskLastRunTime());
        data.put("elapsedTime", calculateElapsedTime(task.getSyncTaskLastRunTime()));
        
        return data;
    }

    @Override
    public Map<String, Object> calculatePerformanceMetrics(Long taskId, LocalDateTime startTime, LocalDateTime endTime) {
        Map<String, Object> metrics = new HashMap<>();
        
        LambdaQueryWrapper<MonitorSyncStatistics> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MonitorSyncStatistics::getSyncTaskId, taskId)
               .between(MonitorSyncStatistics::getCreateTime, startTime, endTime);
        
        List<MonitorSyncStatistics> statsList = statisticsMapper.selectList(wrapper);
        
        if (statsList.isEmpty()) {
            return metrics;
        }
        
        // 计算平均吞吐量
        BigDecimal avgThroughput = statsList.stream()
            .map(MonitorSyncStatistics::getAvgThroughput)
            .filter(t -> t != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal.valueOf(statsList.size()), 2, RoundingMode.HALF_UP);
        
        // 计算平均延迟
        BigDecimal avgLatency = statsList.stream()
            .map(MonitorSyncStatistics::getAvgLatency)
            .filter(l -> l != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal.valueOf(statsList.size()), 2, RoundingMode.HALF_UP);
        
        // 计算峰值内存
        Integer peakMemory = statsList.stream()
            .map(MonitorSyncStatistics::getPeakMemoryMb)
            .filter(m -> m != null)
            .max(Integer::compareTo)
            .orElse(0);
        
        // 计算总记录数
        long totalRecords = statsList.stream()
            .mapToLong(s -> s.getTotalRecords() != null ? s.getTotalRecords() : 0)
            .sum();
        
        long successRecords = statsList.stream()
            .mapToLong(s -> s.getSuccessRecords() != null ? s.getSuccessRecords() : 0)
            .sum();
        
        long failedRecords = statsList.stream()
            .mapToLong(s -> s.getFailedRecords() != null ? s.getFailedRecords() : 0)
            .sum();
        
        metrics.put("avgThroughput", avgThroughput);
        metrics.put("avgLatency", avgLatency);
        metrics.put("peakMemoryMb", peakMemory);
        metrics.put("totalRecords", totalRecords);
        metrics.put("successRecords", successRecords);
        metrics.put("failedRecords", failedRecords);
        metrics.put("successRate", calculateSuccessRate(successRecords, totalRecords));
        
        return metrics;
    }

    @Override
    public void storeMonitoringData(MonitorSyncStatistics statistics) {
        if (statistics.getCreateTime() == null) {
            statistics.setCreateTime(LocalDateTime.now());
        }
        if (statistics.getStatDate() == null) {
            statistics.setStatDate(LocalDate.now());
        }
        statisticsMapper.insert(statistics);
        log.info("存储监控数据: taskId={}, date={}", statistics.getSyncTaskId(), statistics.getStatDate());
    }

    @Override
    public void recordProgress(Long taskId, long processedRecords, long totalRecords, BigDecimal throughput) {
        RealtimeData data = realtimeCache.computeIfAbsent(taskId, k -> new RealtimeData());
        data.processedRecords = processedRecords;
        data.totalRecords = totalRecords;
        data.throughput = throughput;
        data.lastUpdateTime = LocalDateTime.now();
    }

    private BigDecimal calculateProgress(long processed, long total) {
        if (total == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(processed)
            .multiply(BigDecimal.valueOf(100))
            .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    }

    private long calculateElapsedTime(LocalDateTime startTime) {
        if (startTime == null) {
            return 0;
        }
        return java.time.Duration.between(startTime, LocalDateTime.now()).getSeconds();
    }

    private BigDecimal calculateSuccessRate(long success, long total) {
        if (total == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(success)
            .multiply(BigDecimal.valueOf(100))
            .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    }

    /**
     * 实时数据内部类
     */
    private static class RealtimeData {
        long processedRecords = 0;
        long totalRecords = 0;
        BigDecimal throughput = BigDecimal.ZERO;
        LocalDateTime lastUpdateTime = LocalDateTime.now();
    }
}
