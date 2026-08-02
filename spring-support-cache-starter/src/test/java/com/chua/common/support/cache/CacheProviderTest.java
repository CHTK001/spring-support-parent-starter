package com.chua.common.support.cache;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 缓存提供者单元测试
 * <p>
 * 覆盖 MemoryCacheProvider 的基本行为：写入、读取、过期、移除、清空、统计重置。
 * </p>
 *
 * @author CH
 */
public class CacheProviderTest {

    @Test
    void testAutoProviderIsMemory() {
        CacheProvider provider = CacheProvider.auto();
        assertNotNull(provider, "auto() 应返回非空实例");
        assertTrue(provider instanceof CacheProvider.MemoryCacheProvider,
                "auto() 应返回 MemoryCacheProvider");
    }

    @Test
    void testMemoryPutAndGet() {
        CacheProvider provider = CacheProvider.auto();
        provider.put("key1", "value1");
        Object value = provider.get("key1", String.class);
        assertEquals("value1", value, "写入后应能读取同一值");
    }

    @Test
    void testMemoryTTLExpiry() throws InterruptedException {
        CacheProvider provider = CacheProvider.auto();
        // TTL 1 秒
        provider.put("key2", "value2", 1);
        // 立即读取应命中
        Object immediate = provider.get("key2", String.class);
        assertEquals("value2", immediate, "刚写入 1 秒 TTL 的键应立即命中");
        // 等待 1.5 秒后过期
        Thread.sleep(1500);
        Object expired = provider.get("key2", String.class);
        assertNull(expired, "1 秒 TTL 的键应在 1.5 秒后过期");
    }

    @Test
    void testMemoryRemove() {
        CacheProvider provider = CacheProvider.auto();
        provider.put("key3", "value3");
        provider.remove("key3");
        Object value = provider.get("key3", String.class);
        assertNull(value, "移除后应返回 null");
    }

    @Test
    void testMemoryClear() {
        CacheProvider provider = CacheProvider.auto();
        provider.put("key4", "value4");
        provider.put("key5", "value5");
        provider.clear();
        Object v4 = provider.get("key4", String.class);
        Object v5 = provider.get("key5", String.class);
        assertNull(v4, "清空后 key4 应返回 null");
        assertNull(v5, "清空后 key5 应返回 null");
    }

    @Test
    void testMemoryGetHitRate() {
        CacheProvider provider = CacheProvider.auto();
        assertEquals(0.0, provider.getHitRate(), "初始命中率应为 0");
        provider.put("hit", "hitValue");
        provider.get("hit", String.class);
        // MemoryCacheProvider 未实现统计，默认返回 0
        assertEquals(0.0, provider.getHitRate(), "MemoryCacheProvider 应始终返回 0");
    }

    @Test
    void testMemoryResetStats() {
        CacheProvider provider = CacheProvider.auto();
        provider.resetStats();
        // 无副作用，仅验证不抛异常
        assertDoesNotThrow(() -> provider.resetStats());
    }

    @Test
    void testPutWithNullKey() {
        CacheProvider provider = CacheProvider.auto();
        assertDoesNotThrow(() -> provider.put(null, "value"));
        Object value = provider.get(null, String.class);
        assertNull(value, "null 键写入后读取应返回 null");
    }
}
