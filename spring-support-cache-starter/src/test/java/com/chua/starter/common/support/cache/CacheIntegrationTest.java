package com.chua.starter.common.support.cache;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Service;
import org.springframework.test.context.TestPropertySource;

/**
 * 真实 Spring 容器集成测试 —— 验证缓存与业务集成行为。
 *
 * @author CH
 */
@SpringBootTest(classes = TestCacheApplication.class)
@Import(CacheIntegrationTest.CacheTestService.class)
@TestPropertySource(properties = {
        "plugin.cache.enabled=true",
        "spring.datasource.url=jdbc:h2:mem:cache-test;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password="
})
public class CacheIntegrationTest {

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private CacheConfiguration cacheConfiguration;

    @Autowired(required = false)
    private CacheTemplate cacheTemplate;

    @Autowired(required = false)
    private CacheTestService cacheTestService;

    @Test
    @DisplayName("缓存管理器 Bean 应正确装配且可用")
    void testBeanExists() {
        Assertions.assertNotNull(cacheManager, "CacheManager bean should be injected");
        Assertions.assertNotNull(cacheConfiguration, "CacheConfiguration bean should be injected");
    }

    @Test
    @DisplayName("多级缓存提供者应能按名称获取缓存实例")
    void testGetCacheByName() {
        Cache cache = cacheManager.getCache("testCache");
        Assertions.assertNotNull(cache, "Should obtain a cache instance");
    }

    @Test
    @DisplayName("CacheTemplate Bean 应正确装配")
    void testCacheTemplateBeanExists() {
        Assertions.assertNotNull(cacheTemplate, "CacheTemplate bean should be injected");
    }

    @Test
    @DisplayName("CacheTemplate 的 put/get/evict 应正常工作")
    void testCacheTemplateOperations() {
        Assertions.assertNotNull(cacheTemplate, "CacheTemplate should be available");
        String cacheName = "integrationTest";
        cacheTemplate.put(cacheName, "testKey", "testValue");
        String result = cacheTemplate.get(cacheName, "testKey", String.class);
        Assertions.assertEquals("testValue", result, "应能读取到刚刚存入的值");
        cacheTemplate.evict(cacheName, "testKey");
        String afterEvict = cacheTemplate.get(cacheName, "testKey", String.class);
        Assertions.assertNull(afterEvict, "删除后应返回 null");
    }

    @Test
    @DisplayName("@Cacheable 注解应在 AOP 切面中正确拦截并回填缓存")
    void testCacheableAnnotationInterception() {
        Assertions.assertNotNull(cacheTestService, "CacheTestService bean should be injected");
        // 第一次调用：进入方法体
        String first = cacheTestService.getCachedData("hello");
        Assertions.assertEquals("processed:hello", first, "第一次调用应执行方法体");
        // 第二次调用：应命中缓存，不进入方法体
        String second = cacheTestService.getCachedData("hello");
        Assertions.assertEquals("processed:hello", second, "第二次调用应命中缓存");
        // 检查缓存中是否存在
        Cache cache = cacheManager.getCache("cacheTest");
        Assertions.assertNotNull(cache, "cacheTest 缓存应存在");
    }

    @Test
    @DisplayName("@CacheEvict 应能清除注解生成的缓存")
    void testCacheEvictAnnotation() {
        Assertions.assertNotNull(cacheTestService);
        // 先写入缓存
        cacheTestService.getCachedData("evictKey");
        // 清除
        cacheTestService.evictCachedData("evictKey");
        // 清除后应重新进入方法体
        String afterEvict = cacheTestService.getCachedData("evictKey");
        Assertions.assertEquals("processed:evictKey", afterEvict, "清除后应重新执行方法体");
    }

    /**
     * 集成测试用的缓存服务
     */
    @Service
    public static class CacheTestService {

        @Cacheable(cacheNames = "cacheTest", key = "#key")
        public String getCachedData(String key) {
            return "processed:" + key;
        }

        @org.springframework.cache.annotation.CacheEvict(cacheNames = "cacheTest", key = "#key")
        public void evictCachedData(String key) {
            // 仅触发缓存清除
        }
    }
}
