package com.chua.starter.datasync.mapping;

import com.chua.starter.datasync.config.DefaultDataSyncConfigDefinition;
import com.chua.starter.datasync.model.DataSyncMapping;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 映射管理器单元测试。
 *
 * <p>覆盖映射添加/查询/删除/创建。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
class DefaultDataSyncMappingManagerTest {

    /**
     * 被测映射管理器
     */
    private DataSyncMappingManager manager;

    @BeforeEach
    void setUp() {
        manager = new DefaultDataSyncMappingManager();
    }

    @Test
    void addAndGetMappings() {
        manager.addMapping(createMapping("m1", "input-a", "output-x"));
        manager.addMapping(createMapping("m2", "input-b", "output-y"));

        assertEquals(2, manager.getMappings().size());
        assertEquals("m1", manager.getMappings().get(0).mappingId());
    }

    @Test
    void getMappingsByInputId() {
        manager.addMapping(createMapping("m1", "input-a", "output-x"));
        manager.addMapping(createMapping("m2", "input-a", "output-y"));

        List<DataSyncMapping> result = manager.getMappingsByInputId("input-a");
        assertEquals(2, result.size());
    }

    @Test
    void getMappingsByOutputId() {
        manager.addMapping(createMapping("m1", "input-a", "output-x"));
        manager.addMapping(createMapping("m2", "input-b", "output-x"));

        List<DataSyncMapping> result = manager.getMappingsByOutputId("output-x");
        assertEquals(2, result.size());
    }

    @Test
    void removeMapping() {
        manager.addMapping(createMapping("m1", "input-a", "output-x"));
        manager.removeMapping("m1");
        assertEquals(0, manager.getMappings().size());
    }

    @Test
    void createFromConfig() {
        DefaultDataSyncConfigDefinition config = new DefaultDataSyncConfigDefinition(
                "m1", "input-a", "source-a", "output-x", "sink-a",
                List.of(), 100, "fixed", "0/5 * * * * ?", Map.of());

        DataSyncMapping mapping = manager.createFromConfig("m1", config);
        assertNotNull(mapping);
        assertEquals("m1", mapping.mappingId());
        assertEquals("input-a", mapping.inputId());
        assertEquals(100, mapping.batch());
    }

    /**
     * 构造测试映射。
     */
    private DataSyncMapping createMapping(String id, String inputId, String outputId) {
        return new DefaultDataSyncMapping(
                id, inputId, "source-" + id, outputId, "sink-" + id,
                null, List.of(), 100, "fixed", "0/5 * * * * ?",
                Map.of(), null);
    }
}
