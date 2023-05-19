package com.chua.starter.cacheable.support.handler;

import com.chua.starter.cacheable.support.manager.GuavaCacheManager;
import com.chua.starter.cacheable.support.manager.GuavaCache;
import com.google.common.cache.CacheBuilder;
import org.springframework.cache.Cache;

import java.util.concurrent.TimeUnit;

/**
 * 处理器
 *
 * @author CH
 * @since 2022/8/10 13:55
 */
public class GuavaCacheManagerHandler extends AbstractCacheManagerHandler {


    @Override
    public String getBeanName() {
        return "guava";
    }


    @Override
    public Cache createCache(String cacheName, boolean isCacheNullValues) {
        if (expire > 0) {
            return new GuavaCache(cacheName, CacheBuilder.newBuilder()
                    .expireAfterWrite(expire, TimeUnit.SECONDS)
                    .build(), isCacheNullValues);
        }

        return new GuavaCacheManager().getCache(cacheName);
    }
}
