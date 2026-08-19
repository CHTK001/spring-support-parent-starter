package com.chua.starter.common.support.cache;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.cache.interceptor.SimpleKeyGenerator;

/**
 * CacheConfiguration 单元测试
 * <p>
 * 验证自动配置的核心逻辑：Bean 名称、条件等。
 * 完整的容器集成测试见 {@link CacheIntegrationTest}。
 * </p>
 *
 * @author CH
 */
@DisplayName("CacheConfiguration 测试")
class CacheConfigurationTest {

    /** Configuration */
    private final CacheConfiguration configuration = new CacheConfiguration();

    @Test
    @DisplayName("caffeineCacheManager Bean 名称常量应正确")
    void caffeineBeanNameShouldBeCorrect() {
        assertEquals("caffeineCacheManager", CacheProperties.CAFFEINE_BEAN_NAME);
    }

    @Test
    @DisplayName("redisCacheManager Bean 名称常量应正确")
    void redisBeanNameShouldBeCorrect() {
        assertEquals("redisCacheManager", CacheProperties.REDIS_BEAN_NAME);
    }

    @Test
    @DisplayName("customTenantedKeyGenerator 应创建 SimpleKeyGenerator 实例")
    void customTenantedKeyGeneratorShouldBeSimpleKeyGenerator() {
        KeyGenerator generator = configuration.customTenantedKeyGenerator();
        assertNotNull(generator);
        assertInstanceOf(SimpleKeyGenerator.class, generator);
    }

    @Test
    @DisplayName("cacheCustomAnnotationParser 应创建默认解析器")
    void cacheCustomAnnotationParserShouldBeCreated() {
        CacheCustomAnnotationParser parser = configuration.cacheCustomAnnotationParser();
        assertNotNull(parser);
    }

    @Test
    @DisplayName("customCacheOperationSource 应使用给定的解析器")
    void customCacheOperationSourceShouldUseGivenParser() {
        CacheCustomAnnotationParser parser = new CacheCustomAnnotationParser();
        var source = configuration.customCacheOperationSource(parser);
        assertNotNull(source);
    }
}
