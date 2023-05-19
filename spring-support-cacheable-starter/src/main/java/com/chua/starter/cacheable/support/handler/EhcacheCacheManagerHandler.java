package com.chua.starter.cacheable.support.handler;

import lombok.SneakyThrows;
import org.springframework.cache.Cache;
import org.springframework.cache.ehcache.EhCacheCacheManager;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * ehcache
 *
 * @author CH
 * @since 2022/8/10 13:55
 */
public class EhcacheCacheManagerHandler extends AbstractCacheManagerHandler {


    @Override
    public String getBeanName() {
        return "ehcache";
    }


    @Override
    @SneakyThrows
    public Cache createCache(String cacheName, boolean isCacheNullValues) {
        String ehcache = cachePoolProperties.getEhcache();
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource resource = resolver.getResource(ehcache);
        net.sf.ehcache.CacheManager cacheManager = new net.sf.ehcache.CacheManager(resource.getURL());
        EhCacheCacheManager ehCacheCacheManager = new EhCacheCacheManager(cacheManager);
        return ehCacheCacheManager.getCache(cacheName);
    }
}
