package com.chua.starter.common.support.cache;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CacheProperties 单元测试
 * <p>
 * 验证配置绑定正确性以及各内部类的默认值。
 * </p>
 *
 * @author CH
 */
@DisplayName("CacheProperties 测试")
class CachePropertiesTest {

    /** 属性 */
    private CacheProperties properties;

    @BeforeEach
    void setUp() {
        properties = new CacheProperties();
    }

    @Test
    @DisplayName("默认 prefix 应为 plugin.cache")
    void shouldHaveDefaultPrefix() {
        assertEquals("plugin.cache", CacheProperties.PRE);
    }

    @Test
    @DisplayName("默认 enabled 为 false")
    void shouldDefaultDisabled() {
        assertFalse(properties.isEnabled());
    }

    @Test
    @DisplayName("默认 levels 包含 caffeine 和 redis")
    void shouldDefaultLevels() {
        assertEquals(List.of("caffeine", "redis"), properties.getLevels());
    }

    @Test
    @DisplayName("caffeine 配置应有合理的默认值")
    void shouldHaveCaffeineDefaults() {
        CacheProperties.CaffeineProperties caffeine = properties.getCaffeine();
        assertEquals(256, caffeine.getInitialCapacity());
        assertEquals(10_000L, caffeine.getMaximumSize());
        assertEquals(300, caffeine.getExpireAfterWrite());
        assertEquals(0, caffeine.getExpireAfterAccess());
        assertTrue(caffeine.isRecordStats());
        assertFalse(caffeine.isWeakKeys());
        assertFalse(caffeine.isWeakValues());
    }

    @Test
    @DisplayName("redis 配置应有合理的默认值")
    void shouldHaveRedisDefaults() {
        CacheProperties.RedisProperties redis = properties.getRedis();
        assertEquals(600, redis.getTtl());
        assertEquals("cache:", redis.getKeyPrefix());
        assertEquals("jackson", redis.getSerializer());
        assertFalse(redis.isCacheNullValues());
        assertEquals(":", redis.getSeparator());
        assertTrue(redis.isUseKeyPrefix());
    }

    @Test
    @DisplayName("key 配置应有合理的默认值")
    void shouldHaveKeyDefaults() {
        CacheProperties.KeyProperties key = properties.getKey();
        assertEquals("cache:", key.getPrefix());
        assertEquals(":", key.getSeparator());
        assertTrue(key.isWithTenant());
        assertTrue(key.isWithApplication());
        assertTrue(key.isWithPlatform());
    }

    @Test
    @DisplayName("默认 caffeineBean 和 redisBean 常量应一致")
    void shouldHaveBeanNameConstants() {
        assertEquals("caffeineCacheManager", CacheProperties.CAFFEINE_BEAN_NAME);
        assertEquals("redisCacheManager", CacheProperties.REDIS_BEAN_NAME);
    }

    @Test
    @DisplayName("Bean 名称属性默认应与常量一致")
    void shouldDefaultBeanNames() {
        assertEquals(CacheProperties.CAFFEINE_BEAN_NAME, properties.getCaffeineBean());
        assertEquals(CacheProperties.REDIS_BEAN_NAME, properties.getRedisBean());
    }

    @Test
    @DisplayName("expires 映射应可设置和获取")
    void shouldSupportExpiresMap() {
        Map<String, Long> expires = new LinkedHashMap<>();
        expires.put("users", 600L);
        expires.put("roles", 1200L);
        properties.setExpires(expires);
        assertEquals(2, properties.getExpires().size());
        assertEquals(Long.valueOf(600L), properties.getExpires().get("users"));
    }

    @Test
    @DisplayName("cacheNullValues 默认 false")
    void shouldDefaultCacheNullValues() {
        assertFalse(properties.isCacheNullValues());
    }

    @Test
    @DisplayName("nullValueTtl 默认 60")
    void shouldDefaultNullValueTtl() {
        assertEquals(60L, properties.getNullValueTtl());
    }

    @Test
    @DisplayName("recordStats 默认 true")
    void shouldDefaultRecordStats() {
        assertTrue(properties.isRecordStats());
    }

    @Test
    @DisplayName("key 配置的 setter/getter 应正常工作")
    void shouldWorkKeyPropertiesSetters() {
        CacheProperties.KeyProperties key = properties.getKey();
        key.setPrefix("myapp:");
        key.setSeparator("|");
        key.setWithTenant(false);
        key.setWithApplication(false);
        key.setWithPlatform(false);

        assertEquals("myapp:", key.getPrefix());
        assertEquals("|", key.getSeparator());
        assertFalse(key.isWithTenant());
        assertFalse(key.isWithApplication());
        assertFalse(key.isWithPlatform());
    }

    @Test
    @DisplayName("caffeine 配置的 setter 应正常工作")
    void shouldWorkCaffeineSetters() {
        CacheProperties.CaffeineProperties caffeine = properties.getCaffeine();
        caffeine.setInitialCapacity(512);
        caffeine.setMaximumSize(100_000);
        caffeine.setExpireAfterWrite(600);
        caffeine.setExpireAfterAccess(120);
        caffeine.setRecordStats(false);
        caffeine.setWeakKeys(true);
        caffeine.setWeakValues(true);

        assertEquals(512, caffeine.getInitialCapacity());
        assertEquals(100_000L, caffeine.getMaximumSize());
        assertEquals(600, caffeine.getExpireAfterWrite());
        assertEquals(120, caffeine.getExpireAfterAccess());
        assertFalse(caffeine.isRecordStats());
        assertTrue(caffeine.isWeakKeys());
        assertTrue(caffeine.isWeakValues());
    }

    @Test
    @DisplayName("redis 配置的 setter 应正常工作")
    void shouldWorkRedisSetters() {
        CacheProperties.RedisProperties redis = properties.getRedis();
        redis.setTtl(3600);
        redis.setKeyPrefix("mycache:");
        redis.setSerializer("jdk");
        redis.setCacheNullValues(true);
        redis.setSeparator("-");
        redis.setUseKeyPrefix(false);

        assertEquals(3600, redis.getTtl());
        assertEquals("mycache:", redis.getKeyPrefix());
        assertEquals("jdk", redis.getSerializer());
        assertTrue(redis.isCacheNullValues());
        assertEquals("-", redis.getSeparator());
        assertFalse(redis.isUseKeyPrefix());
    }
}
