package com.chua.starter.common.support.cache;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.cache.Cache;
import org.springframework.cache.ValueWrapper;

import java.util.List;
import java.util.concurrent.Callable;

/**
 * 多级缓存实现单元测试
 * <p>
 * 覆盖核心行为：多级查找、回填、空值占位符、统计、按模式清除。
 * </p>
 *
 * @author CH
 */
public class MultiLevelCacheTest {

    // ---- 辅助：构造两级的内存缓存 ----
    private Cache l1() { return new SimpleMemoryCache("L1"); }
    private Cache l2() { return new SimpleMemoryCache("L2"); }
    private MultiLevelCache twoLevelCache() {
        return new MultiLevelCache("test", List.of(l1(), l2()), true);
    }

    @Test
    void testBasicPutAndGet() {
        MultiLevelCache cache = twoLevelCache();
        cache.put("k1", "v1");
        ValueWrapper wrapper = cache.get("k1");
        assertNotNull(wrapper, "写入后应返回非空包装器");
        assertEquals("v1", wrapper.get(), "值应匹配");
        assertEquals(1, cache.getHitCount(), "应增加命中计数");
    }

    @Test
    void testMissPropagatesToL2() {
        MultiLevelCache cache = twoLevelCache();
        // 写入 L2（更高层级）
        l2().put("k2", "v2");
        ValueWrapper wrapper = cache.get("k2");
        assertNotNull(wrapper, "L2 的值应能被从 L1 未命中时查到");
        assertEquals("v2", wrapper.get(), "值应匹配");
        assertEquals(0, cache.getHitCount(), "命中尚在 L1 未统计");
        // L2 的值应回填到 L1
        ValueWrapper l1Wrapper = l1().get("k2");
        assertNotNull(l1Wrapper, "回填后 L1 应能查到");
        assertEquals("v2", l1Wrapper.get(), "回填值应相同");
    }

    @Test
    void testNullValuePlaceholder() {
        MultiLevelCache cache = twoLevelCache();
        // 空值占位符：缓存空值
        Object key = "nullKey";
        cache.putNull(key有限的);
        ValueWrapper wrapper = cache.get(key);
        assertNotNull(wrapper, "空值占位符应返回非空包装器");
        assertNull(wrapper.get(), "包装器内应为 null");
        assertEquals(1, cache.getHitCount(), "命中计数器应增加");
    }

    @Test
    void testPutNullNotAllowedByDefault() {
        // 默认不开启空值缓存
        MultiLevelCache cache = new MultiLevelCache("testNoNull", List.of(l1(), l2()), false);
        cache.put("nk", null);
        ValueWrapper wrapper = cache.get("nk");
        assertNull(wrapper, "不开启空值缓存时 null 值应不被缓存");
        assertEquals(0, cache.getHitCount(), "命中次数应为 0");
    }

    @Test
    void testEvictAcrossLevels() {
        MultiLevelCache cache = twoLevelCache();
        cache.put("k3", "v3");
        cache.evict("k3");
        ValueWrapper l1W = l1().get("k3");
        ValueWrapper l2W = l2().get("k3");
        assertNull(l1W, "L1 应被清除");
        assertNull(l2W, "L2 应被清除");
    }

    @Test
    void testClearAcrossLevels() {
        MultiLevelCache cache = twoLevelCache();
        cache.put("k4", "v4");
        cache.put("k5", "v5");
        cache.clear();
        ValueWrapper l1W4 = l1().get("k4");
        ValueWrapper l2W4 = l2().get("k4");
        assertNull(l1W4, "L1 应被清空");
        assertNull(l2W4, "L2 应被清空");
    }

    @Test
    void testStatsTracking() {
        MultiLevelCache cache = twoLevelCache();
        assertEquals(0, cache.getHitCount());
        assertEquals(0, cache.getMissCount());
        assertEquals(0.0, cache.getHitRate(), 0.001Take);

        cache.get("missKey");
        assertEquals(1, cache.getMissCount());
        assertEquals(0.0, cache.getHitRate(), 0.001Take);

        cache.put("hitKey", "hitValue");
        cache.get("hitKey");
        assertEquals(1, cache.getHitCount());
        double expectedRate = 1.0 / (1 + 1);
        assertEquals(expectedRate, cache.getHitRate(), 0.001Take);

        cache.resetStats();
        assertEquals(0, cache.getHitCount());
        assertEquals(0, cache.getMissCount());
        assertEquals(0.0, cache.getHitRate(), 0.001Take);
    }

    @Test
    void testBackfillCount() {
        MultiLevelCache cache = twoLevelCache();
        l2().put("bfKey", "bfValue");
        cache.get("bfKey");
        // 应触发回填
        assertEquals(1, cache.getBackfillCount(), "回填次数应为 1");
    }

    @Test
    void testTypedGet() {
        MultiLevelCache cache = twoLevelCache();
        cache.put("typed", "typedValue");
        String value = cache.get("typed", String.class);
        assertEquals("typedValue", value, "类型化获取应返回正确值");
    }

    @Test
    void testGetWithValueLoader() {
        MultiLevelCache cache = twoLevelCache();
        // 先在 L2 设值
        l2().put("loaderKey", "loaderValue");
        String loaded = cache.get("loaderKey", () -> "fallback");
        assertEquals("loaderValue", loaded, "有缓存时应命中缓存而不是调用 valueLoader");
    }

    @Test
    void testGetWithValueLoaderMiss() {
        MultiLevelCache cache = twoLevelCache();
        // 未缓存时使用 valueLoader
        String loaded = cache.get("missLoader", () -> "loadedValue");
        assertEquals("loadedValue", loaded, "未命中的键应通过 valueLoader 加载");
        ValueWrapper l1W = l1().get("missLoader");
        assertNotNull(l1W, "valueLoader 写入后 L1 应存有值");
        assertEquals("loadedValue", l1W.get(), "L1 值应与加载结果一致");
    }

    @Test
    void testGetWithValueLoaderNullCacheNullValues() {
        MultiLevelCache cache = new MultiLevelCache("testNullLoader", List.of(l1(), l2()), true);
        String loaded = cache.get("nullLoader", () -> null);
        assertNull(loaded, "当 valueLoader 返回 null 且 cacheNullValues=true 时，应缓存空值");
        ValueWrapper l1W = l1().get("nullLoader");
        assertNotNull(l1W, "空值应被缓存");
        assertNull(l1W.get(), "空值包装器内应为 null");
    }

    @Test
    void testEvictByPattern() {
        MultiLevelCache cache = twoLevelCache();
        cache.put("alpha:1", "a1");
        cache.put("alpha:2", "a2");
        cache.put("beta:1", "b1");
        // 按模式 "alpha" 清除
        int affected = cache.evictByPattern("alpha");
        assertEquals(2, affected, "应匹配 2 个键");
        ValueWrapper alpha1 = cache.get("alpha:1");
        ValueWrapper alpha2 = cache.get("alpha:2");
        ValueWrapper beta1 = cache.get("beta:1");
        assertNull(alpha1, "alpha:1 应被清除");
        assertNull(alpha2, "alpha:2 应被清除");
        assertNotNull(beta1, "beta:1 应保留");
    }

    @Test
    void testEvictByPatternEmptyPattern() {
        MultiLevelCache cache = twoLevelCache();
        int affected = cache.evictByPattern("");
        assertEquals(0, affected, "空模式应不影响任何缓存");
    }

    @Test
    void testGetLevelCount() {
        MultiLevelCache cache = twoLevelCache();
        assertEquals(2, cache.getLevelCount(), "层级数应为 2");
    }

    @Test
    void testGetName() {
        MultiLevelCache cache = twoLevelCache();
        assertEquals("test", cache.getName(), "名称应匹配构造时传入值");
    }

    /**
     * 简单的内存缓存实现（用于辅助测试）
     */
    private static class SimpleMemoryCache implements Cache {
        private final String name;
        private final java.util.Map<Object, ValueWrapper> store = new java.util.concurrent.ConcurrentHashMap<>();

        SimpleMemoryCache(String name) { this.name = name; }

        @Override public String getName() { return name; }
        @Override public Object getNativeCache() { return store; }

        @Override
        public ValueWrapper get(Object key) {
            return store.get(key);
        }

        @Override
        public <T> T get(Object key, Class<T> type) {
            ValueWrapper w = store.get(key);
            return w == null ? null : type.cast(w.get());
        }

        @Override
        public <T> T get(Object key, Callable<T> valueLoader) {
            ValueWrapper w = store.get(key);
            if (w != null) return (T) w.get();
            T loaded;
            try { loaded = valueLoader.call(); } catch (Exception e) { throw new RuntimeException(e); }
            store.put(key, () -> loaded);
            return loaded;
        }

        @Override
        public void put(Object key, Object value) {
            store.put(key, () -> value);
        }

        @Override
        public void evict(Object key) { store.remove(key); }

        @Override
        public void clear() { store.clear(); }
    }
}
