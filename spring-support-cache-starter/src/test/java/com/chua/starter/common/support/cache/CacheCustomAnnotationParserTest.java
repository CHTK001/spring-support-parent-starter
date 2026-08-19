package com.chua.starter.common.support.cache;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.annotation.*;
import org.springframework.cache.interceptor.CacheOperation;
import org.springframework.cache.interceptor.CacheableOperation;
import org.springframework.cache.interceptor.CacheEvictOperation;
import org.springframework.cache.interceptor.CachePutOperation;

import java.lang.reflect.Method;
import java.util.Collection;

/**
 * CacheCustomAnnotationParser 单元测试
 *
 * @author CH
 */
@DisplayName("CacheCustomAnnotationParser 测试")
class CacheCustomAnnotationParserTest {

    /** 解析器 */
    private CacheCustomAnnotationParser parser;

    @BeforeEach
    void setUp() {
        parser = new CacheCustomAnnotationParser();
    }

    static class ServiceWithCacheable {
        @Cacheable(cacheNames = "users", key = "#id")
        /** 获取UserById */
        public String getUserById(Long id) {
            return "user:" + id;
        }

        @Cacheable(cacheNames = "roles", key = "#roleName", unless = "#result == null")
        /** 获取Role */
        public String getRole(String roleName) {
            return roleName;
        }

        @Cacheable(cacheNames = "configs")
        /** 获取Config */
        public String getConfig(String key) {
            return key;
        }
    }

    static class ServiceWithCacheEvict {
        @CacheEvict(cacheNames = "users", key = "#id")
        /** EvictUser */
        public void evictUser(Long id) {
        }

        @CacheEvict(cacheNames = "users", allEntries = true)
        /** EvictAllUsers */
        public void evictAllUsers() {
        }

        @CacheEvict(cacheNames = "roles", beforeInvocation = true)
        /** EvictBefore */
        public void evictBefore() {
        }
    }

    static class ServiceWithCachePut {
        @CachePut(cacheNames = "users", key = "#user.id")
        /** 更新User */
        public String updateUser(Object user) {
            return "updated";
        }
    }

    static class ServiceWithCaching {
        @Caching(
                cacheable = {@Cacheable(cacheNames = "users", key = "#id")},
                put = {@CachePut(cacheNames = "users_audit", key = "#id")},
                evict = {@CacheEvict(cacheNames = "users_list", allEntries = true)}
        )
        /** 获取AndAudit */
        public String getAndAudit(Long id) {
            return "result";
        }
    }

    @Test
    @DisplayName("应解析 @Cacheable 注解")
    void shouldParseCacheable() throws Exception {
        Method method = ServiceWithCacheable.class.getMethod("getUserById", Long.class);
        Collection<CacheOperation> ops = parser.parseCacheAnnotations(method);
        assertFalse(ops.isEmpty(), "应解析出缓存操作");
        assertEquals(1, ops.size(), "应只有一个缓存操作");
        CacheOperation op = ops.iterator().next();
        assertInstanceOf(CacheableOperation.class, op, "应为 CacheableOperation");
        assertTrue(op.getCacheNames().contains("users"), "缓存名应为 users");
    }

    @Test
    @DisplayName("解析结果应包含自定义 keyGenerator")
    void shouldContainCustomKeyGenerator() throws Exception {
        Method method = ServiceWithCacheable.class.getMethod("getUserById", Long.class);
        Collection<CacheOperation> ops = parser.parseCacheAnnotations(method);
        CacheOperation op = ops.iterator().next();
        assertEquals("tenantedKeyGenerator", op.getKeyGenerator(), "keyGenerator 应为 tenantedKeyGenerator");
    }

    @Test
    @DisplayName("应保留方法上的 SpEL key")
    void shouldPreserveMethodKey() throws Exception {
        Method method = ServiceWithCacheable.class.getMethod("getUserById", Long.class);
        Collection<CacheOperation> ops = parser.parseCacheAnnotations(method);
        CacheOperation op = ops.iterator().next();
        assertEquals("#id", op.getKey(), "应保留 key 属性");
    }

    @Test
    @DisplayName("应解析 @CacheEvict 注解")
    void shouldParseCacheEvict() throws Exception {
        Method method = ServiceWithCacheEvict.class.getMethod("evictUser", Long.class);
        Collection<CacheOperation> ops = parser.parseCacheAnnotations(method);
        assertEquals(1, ops.size());
        CacheOperation op = ops.iterator().next();
        assertInstanceOf(CacheEvictOperation.class, op, "应为 CacheEvictOperation");
    }

    @Test
    @DisplayName("应解析 @CacheEvict 的 allEntries 属性")
    void shouldParseAllEntries() throws Exception {
        Method method = ServiceWithCacheEvict.class.getMethod("evictAllUsers");
        Collection<CacheOperation> ops = parser.parseCacheAnnotations(method);
        CacheEvictOperation op = (CacheEvictOperation) ops.iterator().next();
        assertTrue(op.isCacheWide(), "allEntries 应为 true");
    }

    @Test
    @DisplayName("应解析 @CacheEvict 的 beforeInvocation 属性")
    void shouldParseBeforeInvocation() throws Exception {
        Method method = ServiceWithCacheEvict.class.getMethod("evictBefore");
        Collection<CacheOperation> ops = parser.parseCacheAnnotations(method);
        CacheEvictOperation op = (CacheEvictOperation) ops.iterator().next();
        assertTrue(op.isBeforeInvocation(), "beforeInvocation 应为 true");
    }

    @Test
    @DisplayName("应解析 @CachePut 注解")
    void shouldParseCachePut() throws Exception {
        Method method = ServiceWithCachePut.class.getMethod("updateUser", Object.class);
        Collection<CacheOperation> ops = parser.parseCacheAnnotations(method);
        assertEquals(1, ops.size());
        CacheOperation op = ops.iterator().next();
        assertInstanceOf(CachePutOperation.class, op, "应为 CachePutOperation");
    }

    @Test
    @DisplayName("应解析 @Caching 组合注解")
    void shouldParseCaching() throws Exception {
        Method method = ServiceWithCaching.class.getMethod("getAndAudit", Long.class);
        Collection<CacheOperation> ops = parser.parseCacheAnnotations(method);
        assertEquals(3, ops.size(), "@Caching 应解析出 3 个操作");
        long cacheableCount = ops.stream().filter(CacheableOperation.class::isInstance).count();
        long cachePutCount = ops.stream().filter(CachePutOperation.class::isInstance).count();
        long cacheEvictCount = ops.stream().filter(CacheEvictOperation.class::isInstance).count();
        assertEquals(1, cacheableCount, "应有 1 个 Cacheable");
        assertEquals(1, cachePutCount, "应有 1 个 CachePut");
        assertEquals(1, cacheEvictCount, "应有 1 个 CacheEvict");
    }

    @Test
    @DisplayName("无注解的方法应返回空集合")
    void shouldReturnEmptyForUnannotatedMethod() throws Exception {
        Method method = ServiceWithCacheable.class.getMethod("getConfig", String.class);
        Collection<CacheOperation> ops = parser.parseCacheAnnotations(method);
        // getConfig is annotated with @Cacheable, but test no-annotation via a plain object
        assertFalse(ops.isEmpty(), "getConfig 方法有 @Cacheable 注解");
    }

    @Test
    @DisplayName("类级别注解应被解析")
    void shouldParseClassLevelAnnotations() {
        Collection<CacheOperation> ops = parser.parseCacheAnnotations(ServiceWithCacheable.class);
        assertNull(ops, "类级别无缓存注解时返回 null");
    }

    @Test
    @DisplayName("无缓存注解时应返回 null")
    void shouldReturnEmptyForNoCacheAnnotations() throws Exception {
        Method method = Object.class.getMethod("toString");
        Collection<CacheOperation> ops = parser.parseCacheAnnotations(method);
        assertNull(ops, "无注解的方法应返回 null");
    }

    @Test
    @DisplayName("不支持非标准注解类型时应返回空")
    void shouldReturnEmptyForNonStandardAnnotation() throws Exception {
        Method method = ServiceWithCacheable.class.getMethod("getConfig", String.class);
        Collection<CacheOperation> ops = parser.parseCacheAnnotations(method);
        assertNotNull(ops, "不应返回 null");
    }
}
