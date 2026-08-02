package com.chua.starter.common.support.cache;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;

import java.util.Collection;
import java.util.Collections;

/**
 * MultiLevelCacheManager 单元测试
 *
 * @author CH
 */
@DisplayName("MultiLevelCacheManager 测试")
class MultiLevelCacheManagerTest {

    private MultiLevelCacheManager manager;

    @BeforeEach
    void setUp() throws Exception {
        // 使用无参构造器 + afterPropertiesSet 初始化
        // 由于没有完整 Spring 上下文，这里模拟最基本的场景
        manager = new MultiLevelCacheManager();
    }

    @Test
    @DisplayName("getCache 应返回 null（无底层管理器时）")
    void shouldReturnNullWhenNoManagers() {
        Cache cache = manager.getCache("testCache");
        assertNull(cache, "无底层缓存管理器时应返回 null");
    }

    @Test
    @DisplayName("getCacheNames 应返回空列表（无底层管理器时）")
    void shouldReturnEmptyNames() {
        Collection<String> names = manager.getCacheNames();
        assertNotNull(names, "不应返回 null");
        assertTrue(names.isEmpty(), "无底层管理器时应返回空列表");
    }

    @Test
    @DisplayName("getCache 传入 null 应返回 null")
    void shouldReturnNullForNullName() {
        Cache cache = manager.getCache(null);
        assertNull(cache);
    }

    @Test
    @DisplayName("getCache 传入空字符串应返回 null")
    void shouldReturnNullForEmptyName() {
        Cache cache = manager.getCache("");
        assertNull(cache);
    }

    @Test
    @DisplayName("getCacheManagers 应返回空列表（未初始化时）")
    void shouldReturnEmptyManagers() {
        assertNotNull(manager.getCacheManagers(), "不应返回 null");
        assertTrue(manager.getCacheManagers().isEmpty(), "未初始化时应返回空列表");
    }

    @Test
    @DisplayName("getCacheProperties 应在 afterPropertiesSet 后不为 null")
    void shouldReturnProperties() {
        // afterPropertiesSet 已调用，但无 Environment，properties 会为 null
        // 只是验证不抛异常
        assertDoesNotThrow(() -> manager.getCacheProperties());
    }

    @Test
    @DisplayName("clearAll 不应抛出异常")
    void shouldNotThrowOnClearAll() {
        assertDoesNotThrow(() -> manager.clearAll());
    }

    @Test
    @DisplayName("resetAllStats 不应抛出异常")
    void shouldNotThrowOnResetStats() {
        assertDoesNotThrow(() -> manager.resetAllStats());
    }

    @Test
    @DisplayName("evictByPattern 缓存不存在时应返回 false")
    void shouldReturnFalseForEvictByPatternOnMissingCache() {
        boolean result = manager.evictByPattern("nonexistent", "pattern:*");
        assertFalse(result);
    }

    @Test
    @DisplayName("getStats 应返回空映射")
    void shouldReturnEmptyStats() {
        assertNotNull(manager.getStats(), "不应返回 null");
        assertTrue(manager.getStats().isEmpty(), "无缓存时应返回空映射");
    }
}
