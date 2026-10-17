package com.chua.starter.ai.support.configuration.probe;

import com.chua.starter.ai.support.configuration.CacheResilienceConfiguration;
import com.chua.starter.common.support.cache.CacheManagerResolver;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.cache.interceptor.CacheResolver;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * 主缓存管理器解析回归探针。
 *
 * <p>守护的缺陷（实测导致线上应用起不来）：Spring 的 {@code CacheAspectSupport} 在
 * 没有 {@code CacheResolver} 时会 {@code beanFactory.getBean(CacheManager.class)}，
 * 上下文里存在多个 {@code CacheManager} 且没有可解析的主管理器时抛
 * {@code NoUniqueBeanDefinitionException: no CacheResolver specified and expected single matching
 * CacheManager but found 5}，上下文 refresh 失败、端口起不来。</p>
 *
 * <p>本探针锁定 {@link CacheManagerResolver} 与 {@link CacheResilienceConfiguration} 的契约：
 * 无论装配了几个缓存管理器，主管理器都能确定解析出来，不再抛异常。以 {@code @Test} 承载，
 * 走 {@code mvn test} 即可拦住回归。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
class CacheManagerResolverProbe {

    /**
     * 只有一个缓存管理器时，解析结果就是它（与 Spring 原生一致）。
     */
    @Test
    void resolveReturnsTheOnlyManager() {
        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(SingleManager.class)) {
            CacheManager only = ctx.getBean("onlyManager", CacheManager.class);
            assertSame(only, CacheManagerResolver.resolve(ctx));
        }
    }

    /**
     * 多个缓存管理器但恰好一个 {@code @Primary} 时，解析结果与 Spring 原生兜底一致（取主管理器）。
     */
    @Test
    void resolveHonoursPrimaryManager() {
        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(PrimaryManager.class)) {
            CacheManager primary = ctx.getBean("primaryManager", CacheManager.class);
            assertSame(primary, CacheManagerResolver.resolve(ctx));
        }
    }

    /**
     * 核心回归：5 个缓存管理器、一个主管理器都没有时，仍能确定解析出多级管理器而不是抛异常。
     */
    @Test
    void resolveIsDeterministicWithoutAnyPrimary() {
        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(ManyManagers.class)) {
            CacheManager expected = ctx.getBean("multiLevelCacheManager", CacheManager.class);
            assertSame(expected, CacheManagerResolver.resolve(ctx));
            assertSame(expected, CacheManagerResolver.resolve(ctx), "重复解析必须稳定");
        }
    }

    /**
     * 没有约定名时按 Bean 名排序取第一个，保证同配置每次结果相同（可复现）。
     */
    @Test
    void pickFallsBackToLexicographicOrder() {
        CacheManager zeta = new ConcurrentMapCacheManager();
        CacheManager alpha = new ConcurrentMapCacheManager();
        Map<String, CacheManager> beans = new LinkedHashMap<>();
        beans.put("zetaManager", zeta);
        beans.put("alphaManager", alpha);

        assertSame(alpha, CacheManagerResolver.pick(beans));
        assertSame(alpha, CacheManagerResolver.pick(beans), "顺序必须稳定");
    }

    /**
     * 空集返回 {@code null}（调用方按原语义处理），不抛异常。
     */
    @Test
    void pickReturnsNullOnEmpty() {
        assertNull(CacheManagerResolver.pick(Map.of()));
        assertNull(CacheManagerResolver.pick(null));
    }

    /**
     * 应用真正使用的 {@link CacheResilienceConfiguration}：多管理器场景下
     * {@code cacheManager()} / {@code cacheResolver()} 都必须给非空值——
     * 这正是让 {@code CacheAspectSupport} 绕开脆弱兜底、应用得以启动的关键。
     */
    @Test
    void resilienceConfigurerSuppliesManagerAndResolver() {
        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(ManyManagers.class)) {
            CacheResilienceConfiguration configurer = new CacheResilienceConfiguration(ctx);
            CacheManager manager = configurer.cacheManager();
            assertNotNull(manager, "主管理器必须解析成功");
            CacheResolver resolver = configurer.cacheResolver();
            assertNotNull(resolver, "缓存解析器必须非空，否则仍会回落到唯一性兜底");
        }
    }

    /**
     * 单个管理器场景。
     */
    @Configuration(proxyBeanMethods = false)
    static class SingleManager {
        /**
         * 唯一缓存管理器。
         *
         * @return 缓存管理器
         */
        @Bean("onlyManager")
        CacheManager onlyManager() {
            return new ConcurrentMapCacheManager();
        }
    }

    /**
     * 两个管理器、其中一个 {@code @Primary} 场景。
     */
    @Configuration(proxyBeanMethods = false)
    static class PrimaryManager {
        /**
         * 主缓存管理器。
         *
         * @return 缓存管理器
         */
        @Bean("primaryManager")
        @Primary
        CacheManager primaryManager() {
            return new ConcurrentMapCacheManager();
        }

        /**
         * 非主管理器。
         *
         * @return 缓存管理器
         */
        @Bean("secondaryManager")
        CacheManager secondaryManager() {
            return new ConcurrentMapCacheManager();
        }
    }

    /**
     * 复刻线上真实装配：AI 模块的 3 个管理器 + 框架的 2 个管理器，全部没有 {@code @Primary}。
     */
    @Configuration(proxyBeanMethods = false)
    static class ManyManagers {
        /**
         * 多级缓存管理器。
         *
         * @return 缓存管理器
         */
        @Bean("multiLevelCacheManager")
        CacheManager multiLevelCacheManager() {
            return new ConcurrentMapCacheManager();
        }

        /**
         * 框架 Caffeine 缓存管理器。
         *
         * @return 缓存管理器
         */
        @Bean("caffeineCacheManager")
        CacheManager caffeineCacheManager() {
            return new ConcurrentMapCacheManager();
        }

        /**
         * AI 模块 Caffeine 缓存管理器。
         *
         * @return 缓存管理器
         */
        @Bean("aiCacheManager")
        CacheManager aiCacheManager() {
            return new ConcurrentMapCacheManager();
        }

        /**
         * Redis 永久缓存管理器。
         *
         * @return 缓存管理器
         */
        @Bean("redis-cache--1")
        CacheManager redisCacheAlways() {
            return new ConcurrentMapCacheManager();
        }

        /**
         * Redis 十分钟缓存管理器。
         *
         * @return 缓存管理器
         */
        @Bean("redis-cache-600")
        CacheManager redisCacheMin() {
            return new ConcurrentMapCacheManager();
        }
    }
}
