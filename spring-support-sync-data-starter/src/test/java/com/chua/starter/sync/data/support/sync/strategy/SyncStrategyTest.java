package com.chua.starter.sync.data.support.sync.strategy;

import com.chua.starter.sync.data.support.adapter.DataSourceAdapter;
import com.chua.starter.sync.data.support.adapter.DataSourceConfig;
import com.chua.starter.sync.data.support.adapter.DataSourceMetadata;
import com.chua.starter.sync.data.support.adapter.ReadConfig;
import com.chua.starter.sync.data.support.adapter.WriteConfig;
import com.chua.starter.sync.data.support.sync.strategy.impl.FullSyncStrategy;
import com.chua.starter.sync.data.support.sync.strategy.impl.IncrementalSyncStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 同步策略单元测试
 */
class SyncStrategyTest {

    private SyncContext context;

    @BeforeEach
    void setUp() {
        context = new SyncContext();
        context.setReadConfig(new ReadConfig());
        context.setWriteConfig(new WriteConfig());
        context.setBatchSize(2);
        context.setIncrementalField("updatedAt");
    }

    @Test
    void testFullSyncStrategyMode() {
        FullSyncStrategy strategy = new FullSyncStrategy();
        assertEquals(SyncMode.FULL, strategy.getMode());
    }

    @Test
    void testIncrementalSyncStrategyModeAndState() {
        IncrementalSyncStrategy strategy = new IncrementalSyncStrategy();
        assertEquals(SyncMode.INCREMENTAL, strategy.getMode());
        strategy.setLastIncrementalValue(10L);
        assertEquals(10L, strategy.getLastIncrementalValue());
    }

    @Test
    void testFullSyncExecute() {
        FullSyncStrategy strategy = new FullSyncStrategy();
        InMemoryAdapter source = new InMemoryAdapter(List.of(
            Map.of("id", 1, "updatedAt", 1L),
            Map.of("id", 2, "updatedAt", 2L)
        ));
        InMemoryAdapter target = new InMemoryAdapter(new ArrayList<>());

        SyncResult result = strategy.execute(source, target, context);
        assertTrue(result.isSuccess());
        assertEquals(2L, result.getTotalRecords());
        assertEquals(2L, result.getSuccessRecords());
    }

    @Test
    void testIncrementalSyncExecuteAndTrackValue() {
        IncrementalSyncStrategy strategy = new IncrementalSyncStrategy();
        InMemoryAdapter source = new InMemoryAdapter(List.of(
            Map.of("id", 1, "updatedAt", 100L),
            Map.of("id", 2, "updatedAt", 200L)
        ));
        InMemoryAdapter target = new InMemoryAdapter(new ArrayList<>());

        SyncResult result = strategy.execute(source, target, context);
        assertTrue(result.isSuccess());
        assertEquals(2L, result.getTotalRecords());
        assertEquals(200L, strategy.getLastIncrementalValue());
    }

    private static final class InMemoryAdapter implements DataSourceAdapter {
        private final List<Map<String, Object>> sourceRecords;
        private final List<Map<String, Object>> writtenRecords = new ArrayList<>();

        private InMemoryAdapter(List<Map<String, Object>> sourceRecords) {
            this.sourceRecords = sourceRecords;
        }

        @Override
        public void connect(DataSourceConfig config) {
            // no-op
        }

        @Override
        public Stream<Map<String, Object>> read(ReadConfig config) {
            return sourceRecords.stream().map(HashMap::new);
        }

        @Override
        public void write(List<Map<String, Object>> records, WriteConfig config) {
            writtenRecords.addAll(records);
        }

        @Override
        public boolean testConnection() {
            return true;
        }

        @Override
        public void close() {
            // no-op
        }

        @Override
        public DataSourceMetadata getMetadata() {
            return new DataSourceMetadata();
        }
    }
}
