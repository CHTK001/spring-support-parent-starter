package com.chua.starter.sync.support.datasync;

import com.chua.starter.sync.support.datasync.SyncDataSyncConfiguration.DataSyncMessageBridge;
import com.chua.starter.sync.support.datasync.SyncDataSyncConfiguration.DataSyncMessagePublisher;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DataSync 桥接配置单元测试
 *
 * @author CH
 * @since 2026/01/15
 */
public class SyncDataSyncConfigurationTest {

    /**
     * 测试 DataSyncMessageBridge 对 Map 数据的透传
     */
    @Test
    void testBridge_mapInput_returnsSameContent() {
        DataSyncMessageBridge bridge = new DataSyncMessageBridge();
        Map<String, Object> input = new HashMap<>();
        input.put("name", "test");
        input.put("value", 123);

        Object result = bridge.apply(input);

        assertNotNull(result);
        assertEquals(input, result);
    }

    /**
     * 测试 DataSyncMessageBridge 对 null 输入的处理
     */
    @Test
    void testBridge_nullInput_returnsNull() {
        DataSyncMessageBridge bridge = new DataSyncMessageBridge();
        Object result = bridge.apply(null);
        assertNull(result);
    }

    /**
     * 测试 DataSyncMessageBridge 对非 Map 输入的原样返回
     */
    @Test
    void testBridge_nonMapInput_returnsSame() {
        DataSyncMessageBridge bridge = new DataSyncMessageBridge();
        String input = "plain string";
        Object result = bridge.apply(input);
        assertEquals(input, result);
    }
}
