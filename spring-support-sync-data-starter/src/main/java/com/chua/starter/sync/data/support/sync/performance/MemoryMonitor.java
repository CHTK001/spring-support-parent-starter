package com.chua.starter.sync.data.support.sync.performance;

import com.chua.starter.sync.data.support.properties.SyncProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 内存监控器
 * 监控JVM内存使用情况，提供内存可用性检查
 *
 * @author System
 * @since 2026/03/09
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MemoryMonitor {
    
    private final SyncProperties syncProperties;
    
    /**
     * 检查内存是否可用
     * 
     * @return true 如果内存使用率低于阈值
     */
    public boolean isMemoryAvailable() {
        double usage = getCurrentMemoryUsage();
        double threshold = syncProperties.getMemoryThreshold();
        
        if (usage > threshold) {
            log.warn("内存使用率过高: {:.2f}%, 阈值: {:.2f}%", usage * 100, threshold * 100);
            return false;
        }
        
        return true;
    }
    
    /**
     * 获取当前内存使用率
     * 
     * @return 内存使用率 (0.0-1.0)
     */
    public double getCurrentMemoryUsage() {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        
        return (double) usedMemory / maxMemory;
    }
    
    /**
     * 获取内存信息
     * 
     * @return 内存信息
     */
    public MemoryInfo getMemoryInfo() {
        Runtime runtime = Runtime.getRuntime();
        
        MemoryInfo info = new MemoryInfo();
        info.maxMemory = runtime.maxMemory();
        info.totalMemory = runtime.totalMemory();
        info.freeMemory = runtime.freeMemory();
        info.usedMemory = info.totalMemory - info.freeMemory;
        info.usageRate = (double) info.usedMemory / info.maxMemory;
        
        return info;
    }
    
    /**
     * 建议执行GC
     * 当内存使用率接近阈值时建议执行GC
     * 
     * @return true 如果建议执行GC
     */
    public boolean shouldGc() {
        double usage = getCurrentMemoryUsage();
        double threshold = syncProperties.getMemoryThreshold();
        
        // 当使用率超过阈值的90%时建议GC
        return usage > (threshold * 0.9);
    }
    
    /**
     * 执行GC建议
     * 注意：这只是建议JVM执行GC，不保证立即执行
     */
    public void suggestGc() {
        if (shouldGc()) {
            log.info("内存使用率较高，建议执行GC");
            System.gc();
        }
    }
    
    /**
     * 内存信息
     */
    public static class MemoryInfo {
        public long maxMemory;      // 最大内存
        public long totalMemory;    // 已分配内存
        public long freeMemory;     // 空闲内存
        public long usedMemory;     // 已使用内存
        public double usageRate;    // 使用率
        
        @Override
        public String toString() {
            return String.format("Memory[max=%dMB, total=%dMB, used=%dMB, free=%dMB, usage=%.2f%%]",
                    maxMemory / 1024 / 1024,
                    totalMemory / 1024 / 1024,
                    usedMemory / 1024 / 1024,
                    freeMemory / 1024 / 1024,
                    usageRate * 100);
        }
    }
}
