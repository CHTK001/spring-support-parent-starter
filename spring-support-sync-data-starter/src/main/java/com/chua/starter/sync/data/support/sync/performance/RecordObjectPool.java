package com.chua.starter.sync.data.support.sync.performance;

import com.chua.starter.sync.data.support.properties.SyncProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.HashMap;
import java.util.Map;

/**
 * 记录对象池
 * 复用Map对象以减少GC压力
 *
 * @author System
 * @since 2026/03/09
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "plugin.sync", name = "object-pool-enabled", havingValue = "true", matchIfMissing = true)
public class RecordObjectPool {
    
    private final SyncProperties syncProperties;
    private GenericObjectPool<Map<String, Object>> pool;
    
    @PostConstruct
    public void init() {
        GenericObjectPoolConfig<Map<String, Object>> config = new GenericObjectPoolConfig<>();
        config.setMaxTotal(syncProperties.getObjectPoolMaxTotal());
        config.setMaxIdle(syncProperties.getObjectPoolMaxIdle());
        config.setMinIdle(10);
        config.setTestOnBorrow(false);
        config.setTestOnReturn(false);
        config.setTestWhileIdle(false);
        
        this.pool = new GenericObjectPool<>(new RecordFactory(), config);
        
        log.info("记录对象池初始化完成 - maxTotal: {}, maxIdle: {}", 
                config.getMaxTotal(), config.getMaxIdle());
    }
    
    /**
     * 借用对象
     * 
     * @return Map对象
     */
    public Map<String, Object> borrowObject() {
        try {
            return pool.borrowObject();
        } catch (Exception e) {
            log.warn("从对象池借用对象失败，创建新对象: {}", e.getMessage());
            return new HashMap<>();
        }
    }
    
    /**
     * 归还对象
     * 
     * @param record Map对象
     */
    public void returnObject(Map<String, Object> record) {
        if (record == null) {
            return;
        }
        
        try {
            record.clear(); // 清空数据
            pool.returnObject(record);
        } catch (Exception e) {
            log.warn("归还对象到对象池失败: {}", e.getMessage());
        }
    }
    
    /**
     * 获取对象池统计信息
     * 
     * @return 统计信息
     */
    public PoolStats getStats() {
        PoolStats stats = new PoolStats();
        stats.numActive = pool.getNumActive();
        stats.numIdle = pool.getNumIdle();
        stats.numWaiters = pool.getNumWaiters();
        stats.createdCount = pool.getCreatedCount();
        stats.borrowedCount = pool.getBorrowedCount();
        stats.returnedCount = pool.getReturnedCount();
        stats.destroyedCount = pool.getDestroyedCount();
        return stats;
    }
    
    @PreDestroy
    public void destroy() {
        if (pool != null) {
            pool.close();
            log.info("记录对象池已关闭");
        }
    }
    
    /**
     * 记录工厂
     */
    private static class RecordFactory extends BasePooledObjectFactory<Map<String, Object>> {
        
        @Override
        public Map<String, Object> create() {
            return new HashMap<>();
        }
        
        @Override
        public PooledObject<Map<String, Object>> wrap(Map<String, Object> obj) {
            return new DefaultPooledObject<>(obj);
        }
        
        @Override
        public void passivateObject(PooledObject<Map<String, Object>> p) {
            // 归还前清空
            p.getObject().clear();
        }
    }
    
    /**
     * 对象池统计信息
     */
    public static class PoolStats {
        public int numActive;       // 活跃对象数
        public int numIdle;         // 空闲对象数
        public int numWaiters;      // 等待对象的线程数
        public long createdCount;   // 创建对象总数
        public long borrowedCount;  // 借用对象总数
        public long returnedCount;  // 归还对象总数
        public long destroyedCount; // 销毁对象总数
        
        @Override
        public String toString() {
            return String.format("PoolStats[active=%d, idle=%d, waiters=%d, created=%d, borrowed=%d, returned=%d, destroyed=%d]",
                    numActive, numIdle, numWaiters, createdCount, borrowedCount, returnedCount, destroyedCount);
        }
    }
}
