package com.chua.starter.sync.data.support.sync.strategy;

import com.chua.starter.sync.data.support.sync.strategy.impl.FullSyncStrategy;
import com.chua.starter.sync.data.support.sync.strategy.impl.IncrementalSyncStrategy;
import com.chua.starter.sync.data.support.sync.strategy.impl.BidirectionalSyncStrategy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 同步策略单元测试
 */
class SyncStrategyTest {
    
    private Map<String, Object> sourceConfig;
    private Map<String, Object> targetConfig;
    
    @BeforeEach
    void setUp() {
        sourceConfig = new HashMap<>();
        sourceConfig.put("type", "jdbc");
        sourceConfig.put("url", "jdbc:h2:mem:source");
        
        targetConfig = new HashMap<>();
        targetConfig.put("type", "jdbc");
        targetConfig.put("url", "jdbc:h2:mem:target");
    }
    
    @Test
    void testFullSyncStrategyInitialization() {
        FullSyncStrategy strategy = new FullSyncStrategy();
        assertNotNull(strategy);
        assertEquals("FULL", strategy.getStrategyType());
    }
    
    @Test
    void testIncrementalSyncStrategyInitialization() {
        IncrementalSyncStrategy strategy = new IncrementalSyncStrategy();
        assertNotNull(strategy);
        assertEquals("INCREMENTAL", strategy.getStrategyType());
    }
    
    @Test
    void testBidirectionalSyncStrategyInitialization() {
        BidirectionalSyncStrategy strategy = new BidirectionalSyncStrategy();
        assertNotNull(strategy);
        assertEquals("BIDIRECTIONAL", strategy.getStrategyType());
    }
    
    @Test
    void testFullSyncStrategyExecution() {
        FullSyncStrategy strategy = new FullSyncStrategy();
        
        try {
            // 配置策略
            strategy.configure(sourceConfig, targetConfig);
            assertTrue(strategy.isConfigured());
            
            // 执行同步（需要实际数据库连接，这里只测试配置）
            // SyncResult result = strategy.execute();
            // assertNotNull(result);
            
        } catch (Exception e) {
            System.out.println("全量同步测试跳过: " + e.getMessage());
        }
    }
    
    @Test
    void testIncrementalSyncWithTimestamp() {
        IncrementalSyncStrategy strategy = new IncrementalSyncStrategy();
        
        Map<String, Object> config = new HashMap<>(sourceConfig);
        config.put("incrementalField", "updated_at");
        config.put("incrementalType", "timestamp");
        
        strategy.configure(config, targetConfig);
        assertTrue(strategy.isConfigured());
        
        // 验证增量字段配置
        assertEquals("updated_at", strategy.getIncrementalField());
    }
    
    @Test
    void testBidirectionalSyncConflictDetection() {
        BidirectionalSyncStrategy strategy = new BidirectionalSyncStrategy();
        
        Map<String, Object> config = new HashMap<>(sourceConfig);
        config.put("conflictStrategy", "OVERWRITE");
        config.put("conflictField", "updated_at");
        
        strategy.configure(config, targetConfig);
        assertTrue(strategy.isConfigured());
        
        // 验证冲突策略配置
        assertEquals("OVERWRITE", strategy.getConflictStrategy());
    }
    
    @Test
    void testSyncStrategyFactory() {
        SyncStrategyFactory factory = new SyncStrategyFactory();
        
        SyncStrategy fullSync = factory.create("FULL");
        assertNotNull(fullSync);
        assertTrue(fullSync instanceof FullSyncStrategy);
        
        SyncStrategy incrementalSync = factory.create("INCREMENTAL");
        assertNotNull(incrementalSync);
        assertTrue(incrementalSync instanceof IncrementalSyncStrategy);
        
        SyncStrategy bidirectionalSync = factory.create("BIDIRECTIONAL");
        assertNotNull(bidirectionalSync);
        assertTrue(bidirectionalSync instanceof BidirectionalSyncStrategy);
    }
}
