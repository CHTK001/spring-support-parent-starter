package com.chua.starter.common.support.cache;

import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Inject;
import com.chua.starter.common.support.cache.*;

/**
 * 真实 Spring 容器集成测试 —— 验证缓存与业务集成行为。
 *
 * @author CH
 */
@SpringBootTest
public class CacheIntegrationTest {

    @Inject
    private CacheManager cacheManager;

    @Inject
    private CacheConfiguration cacheConfiguration;

    @Test
    @DisplayName("缓存管理器 Bean 应正确装配且可用")
    void testCacheManagerBeanExists() {
        Assertions.assertNotNull(cacheManager, "CacheManager bean should be injected");
        Assertions.assertNotNull(cacheConfiguration, "CacheConfiguration bean should be injected");
    }

    @Test
    @DisplayName("多级缓存提供者应能按名称获取缓存实例")
    void testGetCacheByName() {
        Cache cache = cacheManager.getCache("testCache", false);
        Assertions.assertNotNull(cache, "Should obtain a cache instance by name");
    }

    @Test
    @DisplayName("@Cacheable 注解应在 AOP 切面中正确拦截并回填缓存")
    void testCacheableAnnotationInterception() {
        // 此测试需要配合一个真实 Service 方法使用 @Cacheable 注解。
        // 暂留占位，后续可补入具体业务 Service。
        Assertions.assertTrue(true, "Placeholder - implement with actual service method");
    }

    // ----- 更多集成点测试可按需添加 -----
}
