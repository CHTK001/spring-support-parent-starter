package com.chua.starter.sync.data.support.sync.performance;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * 批量处理性能监控器
 * 监控批量处理的性能指标，包括吞吐量、延迟、批次大小等
 *
 * @author System
 * @since 2026/03/09
 */
@Slf4j
@Component
public class BatchPerformanceMonitor {
    
    /**
     * 任务性能指标映射
     */
    private final ConcurrentHashMap<Long, TaskMetrics> taskMetricsMap = new ConcurrentHashMap<>();
    
    /**
     * 记录批次开始
     *
     * @param taskId 任务ID
     * @param batchSize 批次大小
     * @return 批次ID
     */
    public long recordBatchStart(Long taskId, int batchSize) {
        TaskMetrics metrics = taskMetricsMap.computeIfAbsent(taskId, k -> new TaskMetrics());
        long batchId = metrics.batchCounter.incrementAndGet();
        
        BatchInfo batchInfo = new BatchInfo();
        batchInfo.batchId = batchId;
        batchInfo.batchSize = batchSize;
        batchInfo.startTime = System.currentTimeMillis();
        
        metrics.currentBatch.put(batchId, batchInfo);
        
        return batchId;
    }
    
    /**
     * 记录批次完成
     *
     * @param taskId 任务ID
     * @param batchId 批次ID
     * @param successCount 成功记录数
     * @param failCount 失败记录数
     */
    public void recordBatchComplete(Long taskId, long batchId, int successCount, int failCount) {
        TaskMetrics metrics = taskMetricsMap.get(taskId);
        if (metrics == null) {
            return;
        }
        
        BatchInfo batchInfo = metrics.currentBatch.remove(batchId);
        if (batchInfo == null) {
            return;
        }
        
        long endTime = System.currentTimeMillis();
        long duration = endTime - batchInfo.startTime;
        
        // 更新统计指标
        metrics.totalBatches.increment();
        metrics.totalRecords.add(successCount + failCount);
        metrics.successRecords.add(successCount);
        metrics.failRecords.add(failCount);
        metrics.totalDuration.add(duration);
        
        // 更新最大/最小批次大小
        updateMinMax(metrics.minBatchSize, batchInfo.batchSize, true);
        updateMinMax(metrics.maxBatchSize, batchInfo.batchSize, false);
        
        // 更新最大/最小延迟
        updateMinMax(metrics.minLatency, duration, true);
        updateMinMax(metrics.maxLatency, duration, false);
        
        // 计算吞吐量（记录数/秒）
        if (duration > 0) {
            double throughput = (successCount + failCount) * 1000.0 / duration;
            metrics.lastThroughput.set((long) throughput);
        }
        
        log.debug("批次完成 - taskId: {}, batchId: {}, size: {}, success: {}, fail: {}, duration: {}ms",
                taskId, batchId, batchInfo.batchSize, successCount, failCount, duration);
    }
    
    /**
     * 记录批次失败
     *
     * @param taskId 任务ID
     * @param batchId 批次ID
     * @param error 错误信息
     */
    public void recordBatchError(Long taskId, long batchId, String error) {
        TaskMetrics metrics = taskMetricsMap.get(taskId);
        if (metrics == null) {
            return;
        }
        
        BatchInfo batchInfo = metrics.currentBatch.remove(batchId);
        if (batchInfo == null) {
            return;
        }
        
        metrics.errorBatches.increment();
        
        log.warn("批次失败 - taskId: {}, batchId: {}, error: {}", taskId, batchId, error);
    }
    
    /**
     * 获取任务性能指标
     *
     * @param taskId 任务ID
     * @return 性能指标
     */
    public PerformanceMetrics getMetrics(Long taskId) {
        TaskMetrics metrics = taskMetricsMap.get(taskId);
        if (metrics == null) {
            return null;
        }
        
        PerformanceMetrics result = new PerformanceMetrics();
        result.taskId = taskId;
        result.totalBatches = metrics.totalBatches.sum();
        result.errorBatches = metrics.errorBatches.sum();
        result.totalRecords = metrics.totalRecords.sum();
        result.successRecords = metrics.successRecords.sum();
        result.failRecords = metrics.failRecords.sum();
        result.totalDuration = metrics.totalDuration.sum();
        
        // 计算平均值
        if (result.totalBatches > 0) {
            result.avgBatchSize = (int) (result.totalRecords / result.totalBatches);
            result.avgLatency = result.totalDuration / result.totalBatches;
            result.avgThroughput = result.totalRecords * 1000.0 / result.totalDuration;
        }
        
        result.minBatchSize = metrics.minBatchSize.get();
        result.maxBatchSize = metrics.maxBatchSize.get();
        result.minLatency = metrics.minLatency.get();
        result.maxLatency = metrics.maxLatency.get();
        result.lastThroughput = metrics.lastThroughput.get();
        
        // 计算成功率
        if (result.totalRecords > 0) {
            result.successRate = result.successRecords * 100.0 / result.totalRecords;
        }
        
        return result;
    }
    
    /**
     * 重置任务指标
     *
     * @param taskId 任务ID
     */
    public void resetMetrics(Long taskId) {
        taskMetricsMap.remove(taskId);
        log.info("重置任务性能指标 - taskId: {}", taskId);
    }
    
    /**
     * 更新最小/最大值
     */
    private void updateMinMax(AtomicLong target, long value, boolean isMin) {
        target.updateAndGet(current -> {
            if (current == 0) {
                return value;
            }
            return isMin ? Math.min(current, value) : Math.max(current, value);
        });
    }
    
    /**
     * 任务性能指标
     */
    private static class TaskMetrics {
        final AtomicLong batchCounter = new AtomicLong(0);
        final ConcurrentHashMap<Long, BatchInfo> currentBatch = new ConcurrentHashMap<>();
        
        final LongAdder totalBatches = new LongAdder();
        final LongAdder errorBatches = new LongAdder();
        final LongAdder totalRecords = new LongAdder();
        final LongAdder successRecords = new LongAdder();
        final LongAdder failRecords = new LongAdder();
        final LongAdder totalDuration = new LongAdder();
        
        final AtomicLong minBatchSize = new AtomicLong(0);
        final AtomicLong maxBatchSize = new AtomicLong(0);
        final AtomicLong minLatency = new AtomicLong(0);
        final AtomicLong maxLatency = new AtomicLong(0);
        final AtomicLong lastThroughput = new AtomicLong(0);
    }
    
    /**
     * 批次信息
     */
    private static class BatchInfo {
        long batchId;
        int batchSize;
        long startTime;
    }
    
    /**
     * 性能指标
     */
    @Data
    public static class PerformanceMetrics {
        private Long taskId;
        private long totalBatches;
        private long errorBatches;
        private long totalRecords;
        private long successRecords;
        private long failRecords;
        private long totalDuration;
        
        private int avgBatchSize;
        private long avgLatency;
        private double avgThroughput;
        
        private long minBatchSize;
        private long maxBatchSize;
        private long minLatency;
        private long maxLatency;
        private long lastThroughput;
        
        private double successRate;
    }
}
