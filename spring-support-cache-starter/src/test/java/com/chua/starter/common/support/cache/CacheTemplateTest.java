package com.chua.starter.common.support.cache;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;

/**
 * CacheTemplate 单元测试
 *
 * @author CH
 */
@DisplayName("CacheTemplate 测试")
class CacheTemplateTest {

    private MultiLevelCacheManager cacheManager;
    private TenantedKeyGenerator keyGenerator;
    private CacheTemplate cacheTemplate;

    @BeforeEach
    void setUp() throws Exception {
        cacheManager = mock(MultiLevelCacheManager.class);
        keyGenerator = mock(TenantedKeyGenerator.class);
        cacheTemplate = new CacheTemplate(cacheManager, keyGenerator);

        // 模拟 key 生成器：buildKey("someKey") -> "cache:test-app:test-platform:tenant-0:someKey"
        when(keyGenerator.generate(null, null, "user:123")).thenReturn("cache:test-app:test-platform:tenant-0:user:123");
        when(keyGenerator.generate(null, null, "user:456")).thenReturn("cache:test-app:test-platform:tenant-0:user:456");
    }

    @Test
    @DisplayName("buildKey 应返回完整的缓存键")
    void shouldBuildFullKey() {
        String key = cacheTemplate.buildKey("user:123");
        assertEquals("cache:test-app:test-platform:tenant-0:user:123", key);
    }

    @Test
    @DisplayName("put 应使用构建后的完整键写入缓存")
    void shouldPutWithFullKey() {
        Cache cache = mock(Cache.class);
        when(cacheManager.getCache("users")).thenReturn(cache);

        cacheTemplate.put("users", "user:123", "someValue");

        verify(cache).put("cache:test-app:test-platform:tenant-0:user:123", "someValue");
    }

    @Test
    @DisplayName("get 应使用构建后的完整键读取缓存")
    void shouldGetWithFullKey() {
        Cache cache = mock(Cache.class);
        when(cacheManager.getCache("users")).thenReturn(cache);
        when(cache.get("cache:test-app:test-platform:tenant-0:user:123"))
                .thenReturn(() -> "storedValue");

        String result = cacheTemplate.get("users", "user:123", String.class);

        assertEquals("storedValue", result);
    }

    @Test
    @DisplayName("get 未命中时应返回 null")
    void shouldReturnNullOnMiss() {
        Cache cache = mock(Cache.class);
        when(cacheManager.getCache("users")).thenReturn(cache);
        when(cache.get("cache:test-app:test-platform:tenant-0:user:456")).thenReturn(null);

        String result = cacheTemplate.get("users", "user:456", String.class);

        assertNull(result);
    }

    @Test
    @DisplayName("evict 应使用构建后的完整键删除缓存")
    void shouldEvictWithFullKey() {
        Cache cache = mock(Cache.class);
        when(cacheManager.getCache("users")).thenReturn(cache);

        cacheTemplate.evict("users", "user:123");

        verify(cache).evict("cache:test-app:test-platform:tenant-0:user:123");
    }

    @Test
    @DisplayName("evictByPattern 应委托给 MultiLevelCache")
    void shouldEvictByPattern() {
        Cache cache = mock(MultiLevelCache.class);
        when(cacheManager.getCache("users")).thenReturn(cache);
        when(((MultiLevelCache) cache).evictByPattern("user:*")).thenReturn(2);

        boolean result = cacheTemplate.evictByPattern("users", "user:*");

        assertTrue(result);
        verify((MultiLevelCache) cache).evictByPattern("user:*");
    }

    @Test
    @DisplayName("evictByPattern 对非 MultiLevelCache 应 fallback 到 clear")
    void shouldFallbackToClearForNonMultiLevelCache() {
        Cache cache = mock(Cache.class);
        when(cacheManager.getCache("users")).thenReturn(cache);

        boolean result = cacheTemplate.evictByPattern("users", "user:*");

        assertTrue(result);
        verify(cache).clear();
    }

    @Test
    @DisplayName("clear 应委托给底层缓存")
    void shouldClearCache() {
        Cache cache = mock(Cache.class);
        when(cacheManager.getCache("users")).thenReturn(cache);

        cacheTemplate.clear("users");

        verify(cache).clear();
    }

    @Test
    @DisplayName("clear 缓存不存在时应不抛异常")
    void shouldNotThrowWhenCacheNotFound() {
        when(cacheManager.getCache("nonexistent")).thenReturn(null);

        assertDoesNotThrow(() -> cacheTemplate.clear("nonexistent"));
        assertDoesNotThrow(() -> cacheTemplate.put("nonexistent", "k", "v"));
        assertDoesNotThrow(() -> cacheTemplate.get("nonexistent", "k", String.class));
        assertDoesNotThrow(() -> cacheTemplate.evict("nonexistent", "k"));
    }

    @Test
    @DisplayName("clearAll 应委托给 cacheManager")
    void shouldClearAll() {
        cacheTemplate.clearAll();
        verify(cacheManager).clearAll();
    }

    @Test
    @DisplayName("getStats 应委托给 cacheManager")
    void shouldGetStats() {
        cacheTemplate.getStats();
        verify(cacheManager).getStats();
    }

    @Test
    @DisplayName("getCacheManager 应返回底层管理器")
    void shouldReturnCacheManager() {
        assertSame(cacheManager, cacheTemplate.getCacheManager());
    }

    @Test
    @DisplayName("getKeyGenerator 应返回底层键生成器")
    void shouldReturnKeyGenerator() {
        assertSame(keyGenerator, cacheTemplate.getKeyGenerator());
    }
}
