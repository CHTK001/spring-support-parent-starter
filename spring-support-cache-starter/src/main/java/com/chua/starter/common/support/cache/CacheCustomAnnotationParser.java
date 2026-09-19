package com.chua.starter.common.support.cache;

import org.springframework.cache.annotation.*;
import org.springframework.cache.interceptor.CacheEvictOperation;
import org.springframework.cache.interceptor.CacheOperation;
import org.springframework.cache.interceptor.CachePutOperation;
import org.springframework.cache.interceptor.CacheableOperation;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;

import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;

/**
 * 自定义缓存注解解析器
 * <p>
 * 解除了原有 Spring 缓存 中 Key 和 Key生成器 不能同时使用的限制。
 * 默认使用 tenantedKey生成器 作为 Key生成器。
 * </p>
 *
 * @since 2025/6/6
 * @author CH
 */
public class CacheCustomAnnotationParser implements CacheAnnotationParser, Serializable {

    private static final long serialVersionUID = 1L; // 串行版本uid

    private static final Set<Class<? extends Annotation>> CACHE_OPERATION_ANNOTATIONS =
            Set.of(Cacheable.class, CacheEvict.class, CachePut.class, Caching.class);

    /**
     * 默认的 Key生成器
     */
    private static final String DEFAULT_KEY_GENERATOR = "tenantedKeyGenerator";

    @Override
    /**
     * 是否candidate类
    */
    public boolean isCandidateClass(Class<?> targetClass) {
        return AnnotationUtils.isCandidateClass(targetClass, CACHE_OPERATION_ANNOTATIONS);
    }

    @Override
    @Nullable
    /**
     * 解析缓存注解
     *
     * @param type 类型
     * @return 解析缓存注解的结果
     */
    public Collection<CacheOperation> parseCacheAnnotations(Class<?> type) {
        DefaultCacheConfig defaultConfig = new DefaultCacheConfig(type);
        return parseCacheAnnotations(defaultConfig, type);
    }

    @Override
    @Nullable
    /**
     * 解析缓存注解
     *
     * @param method 方法
     * @return 解析缓存注解的结果
     */
    public Collection<CacheOperation> parseCacheAnnotations(Method method) {
        DefaultCacheConfig defaultConfig = new DefaultCacheConfig(method.getDeclaringClass());
        return parseCacheAnnotations(defaultConfig, method);
    }

    @Nullable
    /**
     * 解析缓存注解
     *
     * @param cachingConfig 缓存配置
     * @param ae ae
     * @return 解析缓存注解的结果
     */
    private Collection<CacheOperation> parseCacheAnnotations(DefaultCacheConfig cachingConfig, AnnotatedElement ae) {
        Collection<CacheOperation> ops = parseCacheAnnotations(cachingConfig, ae, false);
        if (ops != null && ops.size() > 1) {
            Collection<CacheOperation> localOps = parseCacheAnnotations(cachingConfig, ae, true);
            if (localOps != null) {
                return localOps;
            }
        }
        return ops;
    }

    @Nullable
    /**
     * 解析缓存注解
     * @param cachingConfig 缓存配置
     * @param ae ae
     * @param localOnly 本地only
     * @param CACHE_OPERATION_ANNOTATIONS 缓存_OPERATION_注解
     * @param CACHE_OPERATION_ANNOTATIONS 缓存_OPERATION_注解
     * @param cachingConfig 缓存配置
     * @param cacheable cacheable
     * @param cachingConfig 缓存配置
     * @param cacheEvict 缓存evict
     * @param cachingConfig 缓存配置
     * @param cachePut 缓存放入
     * @param cachingConfig 缓存配置
     * @param caching caching
     * @param ops ops
     * @param ae ae
     * @param defaultConfig 默认配置
     * @param cacheable cacheable
     * @param ae ae
     * @param defaultConfig 默认配置
     * @param cacheEvict 缓存evict
     * @param ae ae
     * @param defaultConfig 默认配置
     * @param cachePut 缓存放入
     * @param ae ae
     * @param defaultConfig 默认配置
     * @param caching caching
     * @param ops ops
     * @param defaultConfig 默认配置
     * @param cacheable cacheable
     * @param defaultConfig 默认配置
     * @param cacheEvict 缓存evict
     * @param defaultConfig 默认配置
     * @param cachePut 缓存放入
     * @param target Target
     * @param builder 构建器
     */
    private Collection<CacheOperation> parseCacheAnnotations(
            DefaultCacheConfig cachingConfig, AnnotatedElement ae, boolean localOnly) {

        Collection<? extends Annotation> annotations = (localOnly ?
                AnnotatedElementUtils.getAllMergedAnnotations(ae, CACHE_OPERATION_ANNOTATIONS) :
                AnnotatedElementUtils.findAllMergedAnnotations(ae, CACHE_OPERATION_ANNOTATIONS));
        if (annotations.isEmpty()) {
            return null;
        }

        Collection<CacheOperation> ops = new ArrayList<>(1);
        annotations.stream().filter(Cacheable.class::isInstance).map(Cacheable.class::cast).forEach(
                cacheable -> ops.add(parseCacheableAnnotation(ae, cachingConfig, cacheable)));
        annotations.stream().filter(CacheEvict.class::isInstance).map(CacheEvict.class::cast).forEach(
                cacheEvict -> ops.add(parseEvictAnnotation(ae, cachingConfig, cacheEvict)));
        annotations.stream().filter(CachePut.class::isInstance).map(CachePut.class::cast).forEach(
                cachePut -> ops.add(parsePutAnnotation(ae, cachingConfig, cachePut)));
        annotations.stream().filter(Caching.class::isInstance).map(Caching.class::cast).forEach(
                caching -> parseCachingAnnotation(ae, cachingConfig, caching, ops));
        return ops;
    }

    /**
     * 解析缓存注解
     * @param ae ae
     * @param defaultConfig 默认配置
     * @param cacheable cacheable
     * @param ae ae
     * @param defaultConfig 默认配置
     * @param cacheEvict 缓存evict
     * @param ae ae
     * @param defaultConfig 默认配置
     * @param cachePut 缓存放入
     * @param ae ae
     * @param defaultConfig 默认配置
     * @param caching caching
     * @param ops ops
     * @param defaultConfig 默认配置
     * @param cacheable cacheable
     * @param defaultConfig 默认配置
     * @param cacheEvict 缓存evict
     * @param defaultConfig 默认配置
     * @param cachePut 缓存放入
     * @param target Target
     * @param builder 构建器
     */
    private CacheableOperation parseCacheableAnnotation(
            AnnotatedElement ae, DefaultCacheConfig defaultConfig, Cacheable cacheable) {

        CacheableOperation.Builder builder = new CacheableOperation.Builder();
        builder.setName(ae.toString());
        builder.setCacheNames(cacheable.cacheNames());
        builder.setCondition(cacheable.condition());
        builder.setUnless(cacheable.unless());
        builder.setKey(cacheable.key());
        builder.setKeyGenerator(cacheable.keyGenerator());
        builder.setCacheManager(cacheable.cacheManager());
        builder.setCacheResolver(cacheable.cacheResolver());
        builder.setSync(cacheable.sync());

        defaultConfig.applyDefault(builder);
        return builder.build();
    }

    /**
     * 解析evict注解
     * @param ae ae
     * @param defaultConfig 默认配置
     * @param cacheEvict 缓存evict
     * @param ae ae
     * @param defaultConfig 默认配置
     * @param cachePut 缓存放入
     * @param ae ae
     * @param defaultConfig 默认配置
     * @param caching caching
     * @param ops ops
     * @param defaultConfig 默认配置
     * @param cacheable cacheable
     * @param defaultConfig 默认配置
     * @param cacheEvict 缓存evict
     * @param defaultConfig 默认配置
     * @param cachePut 缓存放入
     * @param target Target
     * @param builder 构建器
     */
    private CacheEvictOperation parseEvictAnnotation(
            AnnotatedElement ae, DefaultCacheConfig defaultConfig, CacheEvict cacheEvict) {

        CacheEvictOperation.Builder builder = new CacheEvictOperation.Builder();
        builder.setName(ae.toString());
        builder.setCacheNames(cacheEvict.cacheNames());
        builder.setCondition(cacheEvict.condition());
        builder.setKey(cacheEvict.key());
        builder.setKeyGenerator(cacheEvict.keyGenerator());
        builder.setCacheManager(cacheEvict.cacheManager());
        builder.setCacheResolver(cacheEvict.cacheResolver());
        builder.setCacheWide(cacheEvict.allEntries());
        builder.setBeforeInvocation(cacheEvict.beforeInvocation());

        defaultConfig.applyDefault(builder);
        return builder.build();
    }

    /**
     * 解析放入注解
     * @param ae ae
     * @param defaultConfig 默认配置
     * @param cachePut 缓存放入
     * @param ae ae
     * @param defaultConfig 默认配置
     * @param caching caching
     * @param ops ops
     * @param defaultConfig 默认配置
     * @param cacheable cacheable
     * @param defaultConfig 默认配置
     * @param cacheEvict 缓存evict
     * @param defaultConfig 默认配置
     * @param cachePut 缓存放入
     * @param target Target
     * @param builder 构建器
     */
    private CacheOperation parsePutAnnotation(
            AnnotatedElement ae, DefaultCacheConfig defaultConfig, CachePut cachePut) {

        CachePutOperation.Builder builder = new CachePutOperation.Builder();
        builder.setName(ae.toString());
        builder.setCacheNames(cachePut.cacheNames());
        builder.setCondition(cachePut.condition());
        builder.setUnless(cachePut.unless());
        builder.setKey(cachePut.key());
        builder.setKeyGenerator(cachePut.keyGenerator());
        builder.setCacheManager(cachePut.cacheManager());
        builder.setCacheResolver(cachePut.cacheResolver());

        defaultConfig.applyDefault(builder);
        return builder.build();
    }

    /**
     * 解析缓存注解
     * @param ae ae
     * @param defaultConfig 默认配置
     * @param caching caching
     * @param ops ops
     * @param defaultConfig 默认配置
     * @param cacheable cacheable
     * @param defaultConfig 默认配置
     * @param cacheEvict 缓存evict
     * @param defaultConfig 默认配置
     * @param cachePut 缓存放入
     * @param target Target
     * @param builder 构建器
     */
    private void parseCachingAnnotation(
            AnnotatedElement ae, DefaultCacheConfig defaultConfig, Caching caching, Collection<CacheOperation> ops) {

        for (Cacheable cacheable : caching.cacheable()) {
            ops.add(parseCacheableAnnotation(ae, defaultConfig, cacheable));
        }
        for (CacheEvict cacheEvict : caching.evict()) {
            ops.add(parseEvictAnnotation(ae, defaultConfig, cacheEvict));
        }
        for (CachePut cachePut : caching.put()) {
            ops.add(parsePutAnnotation(ae, defaultConfig, cachePut));
        }
    }

    /**
     * 默认缓存配置
     * @author CH
     * @since 4.0.0
     */
    private static class DefaultCacheConfig {

        private final Class<?> target; // Target

        @Nullable
        /**
         * 缓存名称
        */
        private String[] cacheNames;

        @Nullable
        /**
         * 密钥生成器
        */
        private String keyGenerator;

        @Nullable
        /**
         * 缓存管理器
        */
        private String cacheManager;

        @Nullable
        /**
         * 缓存解析器
        */
        private String cacheResolver;

        /**
         * Initialized
        */
        private boolean initialized = false;

        /**
         * 创建 默认缓存配置 实例
         * @param target Target
         */
        public DefaultCacheConfig(Class<?> target) {
            this.target = target;
        }

        /**
         * 应用默认
         *
         * @param builder 构建器
         */
        public void applyDefault(CacheOperation.Builder builder) {
            if (!this.initialized) {
                CacheConfig annotation = AnnotatedElementUtils.findMergedAnnotation(this.target, CacheConfig.class);
                if (annotation != null) {
                    this.cacheNames = annotation.cacheNames();
                    this.keyGenerator = annotation.keyGenerator();
                    this.cacheManager = annotation.cacheManager();
                    this.cacheResolver = annotation.cacheResolver();
                }
                this.initialized = true;
            }

            if (builder.getCacheNames().isEmpty() && this.cacheNames != null) {
                builder.setCacheNames(this.cacheNames);
            }

 // 设置 Key生成器，默认使用 tenantedKey生成器
            if (!StringUtils.hasText(builder.getKeyGenerator())) {
                if (StringUtils.hasText(this.keyGenerator)) {
                    builder.setKeyGenerator(this.keyGenerator);
                } else {
                    builder.setKeyGenerator(DEFAULT_KEY_GENERATOR);
                }
            }

            if (StringUtils.hasText(builder.getCacheManager()) || StringUtils.hasText(builder.getCacheResolver())) {
 // One 的 these 是否 设置 so we should not inherit anything
            } else if (StringUtils.hasText(this.cacheResolver)) {
                builder.setCacheResolver(this.cacheResolver);
            } else if (StringUtils.hasText(this.cacheManager)) {
                builder.setCacheManager(this.cacheManager);
            }
        }
    }
}
