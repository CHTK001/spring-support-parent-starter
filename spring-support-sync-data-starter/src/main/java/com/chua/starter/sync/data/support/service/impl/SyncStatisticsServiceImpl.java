package com.chua.starter.sync.data.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.starter.sync.data.support.entity.MonitorSyncStatistics;
import com.chua.starter.sync.data.support.mapper.MonitorSyncStatisticsMapper;
import com.chua.starter.sync.data.support.service.SyncStatisticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 同步统计服务实现
 *
 * @author System
 * @since 2026/03/09
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SyncStatisticsServiceImpl implements SyncStatisticsService {

    private final MonitorSyncStatisticsMapper statisticsMapper;

    @Override
    public MonitorSyncStatistics generateDailyStatistics(Long taskId, LocalDate date) {
        LambdaQueryWrapper<MonitorSyncStatistics> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MonitorSyncStatistics::getSyncTaskId, taskId)
               .eq(MonitorSyncStatistics::getStatDate, date);
        
        MonitorSyncStatistics existing = statisticsMapper.selectOne(wrapper);
        if (existing != null) {
            return existing;
        }
        
        MonitorSyncStatistics statistics = new MonitorSyncStatistics();
        statistics.setSyncTaskId(taskId);
        statistics.setStatDate(date);
        statistics.setTotalRecords(0L);
        statistics.setSuccessRecords(0L);
        statistics.setFailedRecords(0L);
        statistics.setAvgThroughput(BigDecimal.ZERO);
        statistics.setAvgLatency(BigDecimal.ZERO);
        statistics.setPeakMemoryMb(0);
        statistics.setCreateTime(LocalDateTime.now());

        
        statisticsMapper.insert(statistics);
        log.info("生成日统计数据: taskId={}, date={}", taskId, date);
        return statistics;
    }

    @Override
    public List<MonitorSyncStatistics> queryStatistics(Long taskId, LocalDate startDate, LocalDate endDate) {
        LambdaQueryWrapper<MonitorSyncStatistics> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MonitorSyncStatistics::getSyncTaskId, taskId)
               .between(MonitorSyncStatistics::getStatDate, startDate, endDate)
               .orderByAsc(MonitorSyncStatistics::getStatDate);
        
        return statisticsMapper.selectList(wrapper);
    }

    @Override
    public Map<String, Object> analyzeTrend(Long taskId, int days) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(days - 1);
        
        List<MonitorSyncStatistics> statsList = queryStatistics(taskId, startDate, endDate);
        
        Map<String, Object> trend = new HashMap<>();
        
        if (statsList.isEmpty()) {
            trend.put("dates", Collections.emptyList());
            trend.put("throughput", Collections.emptyList());
            trend.put("latency", Collections.emptyList());
            trend.put("successRate", Collections.emptyList());
            return trend;
        }
        
        List<String> dates = statsList.stream()
            .map(s -> s.getStatDate().toString())
            .collect(Collectors.toList());
        
        List<BigDecimal> throughput = statsList.stream()
            .map(s -> s.getAvgThroughput() != null ? s.getAvgThroughput() : BigDecimal.ZERO)
            .collect(Collectors.toList());
        
        List<BigDecimal> latency = statsList.stream()
            .map(s -> s.getAvgLatency() != null ? s.getAvgLatency() : BigDecimal.ZERO)
            .collect(Collectors.toList());
        
        List<BigDecimal> successRate = statsList.stream()
            .map(this::calculateSuccessRate)
            .collect(Collectors.toList());
        
        trend.put("dates", dates);
        trend.put("throughput", throughput);
        trend.put("latency", latency);
        trend.put("successRate", successRate);
        trend.put("totalDays", days);
        trend.put("avgThroughput", calculateAverage(throughput));
        trend.put("avgLatency", calculateAverage(latency));
        trend.put("avgSuccessRate", calculateAverage(successRate));
        
        return trend;
    }

    private BigDecimal calculateSuccessRate(MonitorSyncStatistics stats) {
        long total = stats.getTotalRecords() != null ? stats.getTotalRecords() : 0;
        long success = stats.getSuccessRecords() != null ? stats.getSuccessRecords() : 0;
        
        if (total == 0) {
            return BigDecimal.ZERO;
        }
        
        return BigDecimal.valueOf(success)
            .multiply(BigDecimal.valueOf(100))
            .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateAverage(List<BigDecimal> values) {
        if (values.isEmpty()) {
            return BigDecimal.ZERO;
        }
        
        BigDecimal sum = values.stream()
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        return sum.divide(BigDecimal.valueOf(values.size()), 2, RoundingMode.HALF_UP);
    }
}
