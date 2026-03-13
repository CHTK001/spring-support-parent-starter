package com.chua.starter.sync.data.support.sync.performance;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 动态批次大小计算器
 * 根据内存使用情况动态调整批次大小
 *
 * @author System
 * @since 2026/03/09
 */
@Slf4j
@Component
public class AdaptiveBatchSizeCalculator {

    private static final double HIGH_MEMORY_THRESHOLD = 0.8;
    private static final double LOW_MEMORY_THRESHOLD = 0.5;

    /**
     * 根据内存使用情况动态调整批次大小
     *
     * @param currentMemoryUsage 当前内存使用率（0.0-1.0）
     * @param defaultBatchSize 默认批次大小
     * @return 调整后的批次大小
     */
    public int calculateBatchSize(double currentMemoryUsage, int defaultBatchSize) {
        if (currentMemoryUsage > HIGH_MEMORY_THRESHOLD) {
            int newSize = defaultBatchSize / 2;
            log.debug("内存使用率过高({}%), 批次大小减半: {} -> {}", 
                    currentMemoryUsage * 100, defaultBatchSize, newSize);
            return Math.max(newSize, 100); // 最小100
        } else if (currentMemoryUsage < LOW_MEMORY_THRESHOLD) {
            int newSize = defaultBatchSize * 2;
            log.debug("内存充足({}%), 批次大小加倍: {} -> {}", 
                    currentMemoryUsage * 100, defaultBatchSize, newSize);
            return Math.min(newSize, 10000); // 最大10000
        }
        return defaultBatchSize;
    }

    /**
     * 获取当前内存使用率
     *
     * @return 内存使用率（0.0-1.0）
     */
    public double getCurrentMemoryUsage() {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        return (double) usedMemory / maxMemory;
    }
}
