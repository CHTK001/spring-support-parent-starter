package com.chua.starter.common.support.cache;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.interceptor.SimpleKey;

import java.lang.reflect.Method;

/**
 * TenantedKeyGenerator 单元测试
 *
 * @author CH
 */
@DisplayName("TenantedKeyGenerator 测试")
class TenantedKeyGeneratorTest {

    private TenantedKeyGenerator generator;
    private CacheProperties.KeyProperties keyProperties;

    @BeforeEach
    void setUp() throws Exception {
        generator = new TenantedKeyGenerator();
        keyProperties = new CacheProperties.KeyProperties();
        keyProperties.setPrefix("cache:");
        keyProperties.setWithApplication(true);
        keyProperties.setWithPlatform(true);
        keyProperties.setWithTenant(true);
        generator.setKeyProperties(keyProperties);

        // 使用反射注入 applicationName 和 platformName (since they use @Value)
        java.lang.reflect.Field appField = TenantedKeyGenerator.class.getDeclaredField("applicationName");
        appField.setAccessible(true);
        appField.set(generator, "test-app");

        java.lang.reflect.Field platField = TenantedKeyGenerator.class.getDeclaredField("platformName");
        platField.setAccessible(true);
        platField.set(generator, "test-platform");

        // 设置租户ID提供者
        generator.setTenantIdProvider(() -> "tenant-123");
    }

    @Test
    @DisplayName("生成的键应包含应用名、平台名和租户ID")
    void shouldIncludeApplicationPlatformAndTenant() throws Exception {
        Method method = String.class.getMethod("toString");
        Object key = generator.generate("target", method);
        String keyStr = key.toString();
        assertAll(
                () -> assertTrue(keyStr.startsWith("cache:"), "应以 cache: 开头"),
                () -> assertTrue(keyStr.contains("test-app"), "应包含应用名"),
                () -> assertTrue(keyStr.contains("test-platform"), "应包含平台名"),
                () -> assertTrue(keyStr.contains("tenant-123"), "应包含租户ID")
        );
    }

    @Test
    @DisplayName("禁用应用名时不应包含应用名")
    void shouldNotIncludeApplicationWhenDisabled() throws Exception {
        keyProperties.setWithApplication(false);
        Method method = String.class.getMethod("toString");
        String key = generator.generate("target", method).toString();
        assertFalse(key.contains("test-app"), "不应包含应用名");
    }

    @Test
    @DisplayName("禁用平台名时不应包含平台名")
    void shouldNotIncludePlatformWhenDisabled() throws Exception {
        keyProperties.setWithPlatform(false);
        Method method = String.class.getMethod("toString");
        String key = generator.generate("target", method).toString();
        assertFalse(key.contains("test-platform"), "不应包含平台名");
    }

    @Test
    @DisplayName("禁用租户ID时不应包含租户ID")
    void shouldNotIncludeTenantWhenDisabled() throws Exception {
        keyProperties.setWithTenant(false);
        Method method = String.class.getMethod("toString");
        String key = generator.generate("target", method).toString();
        assertFalse(key.contains("tenant-123"), "不应包含租户ID");
    }

    @Test
    @DisplayName("不同的参数应生成不同的键")
    void shouldGenerateDifferentKeysForDifferentParams() throws Exception {
        Method method = String.class.getMethod("toString");
        String key1 = generator.generate("target", method, "param1").toString();
        String key2 = generator.generate("target", method, "param2").toString();
        assertNotEquals(key1, key2, "不同参数应生成不同的键");
    }

    @Test
    @DisplayName("无参方法应使用 SimpleKey.EMPTY")
    void shouldUseEmptyKeyForNoArgs() throws Exception {
        Method method = String.class.getMethod("toString");
        String key = generator.generate("target", method).toString();
        assertTrue(key.endsWith(SimpleKey.EMPTY.toString()), "应以 SimpleKey.EMPTY 结尾");
    }

    @Test
    @DisplayName("generateWithTenant 应使用指定的租户ID")
    void generateWithTenantShouldUseProvidedTenant() throws Exception {
        Method method = String.class.getMethod("toString");
        String key = generator.generateWithTenant("custom-tenant", "target", method);
        assertTrue(key.contains("custom-tenant"), "应包含指定租户ID");
        assertFalse(key.contains("tenant-123"), "不应包含默认租户ID");
    }

    @Test
    @DisplayName("getCurrentTenantId 应返回当前租户ID")
    void shouldReturnCurrentTenantId() {
        assertEquals("tenant-123", generator.getCurrentTenantId());
    }

    @Test
    @DisplayName("没有 keyProperties 时应使用默认行为")
    void shouldUseDefaultsWhenNoKeyProperties() throws Exception {
        generator.setKeyProperties(null);
        Method method = String.class.getMethod("toString");
        String key = generator.generate("target", method).toString();
        // 默认 should be true for all flags
        assertTrue(key.contains("test-app"), "默认应包含应用名");
        assertTrue(key.contains("test-platform"), "默认应包含平台名");
        assertTrue(key.contains("tenant-123"), "默认应包含租户ID");
    }
}
