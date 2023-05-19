package com.chua.starter.cacheable.support.handler;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.Cache;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.caffeine.CaffeineCacheManager;

import java.util.concurrent.TimeUnit;

/**
 * caffeine
 *
 * @author CH
 * @since 2022/8/10 13:55
 */
public class CaffeineCacheManagerHandler extends AbstractCacheManagerHandler {


    @Override
    public String getBeanName() {
        return "caffeine";
    }


    @Override
    public Cache createCache(String cacheName, boolean isCacheNullValues) {
        if (expire > 0) {
            return new CaffeineCache(cacheName, Caffeine.newBuilder()
                    .expireAfterWrite(expire, TimeUnit.SECONDS)
                    .build(), isCacheNullValues);
        }

        return new CaffeineCacheManager().getCache(cacheName);
    }
}
