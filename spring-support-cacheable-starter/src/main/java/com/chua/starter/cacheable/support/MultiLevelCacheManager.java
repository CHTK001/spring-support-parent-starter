package com.chua.starter.cacheable.support;

import com.chua.starter.cacheable.support.cache.MultiLevelCache;
import com.chua.starter.cacheable.support.cache.MultiLevelCacheFactory;
import com.chua.starter.cacheable.support.properties.CacheProperties;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * redis-guava二级缓存
 *
 * @author CH
 * @since 2022/8/10 11:40
 */
public class MultiLevelCacheManager implements CacheManager, BeanClassLoaderAware, ApplicationContextAware {

    private final ConcurrentMap<String, Cache> cacheMap = new ConcurrentHashMap<String, Cache>();
    private final Set<String> cacheNames = new HashSet<>();

    private final CacheProperties cacheProperties;
    private final MultiLevelCacheFactory multiLevelCacheFactory;
    private ClassLoader classLoader;
    private ApplicationContext applicationContext;

    public MultiLevelCacheManager(CacheProperties cacheProperties) {
        super();
        this.cacheProperties = cacheProperties;
        this.multiLevelCacheFactory = new MultiLevelCacheFactory(cacheProperties, this.applicationContext);
    }

    @Override
    public void setBeanClassLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    @Override
    public Cache getCache(String name) {
        cacheNames.add(name);
        return cacheMap.computeIfAbsent(name, it -> new MultiLevelCache(name, multiLevelCacheFactory));
    }

    @Override
    public Collection<String> getCacheNames() {
        return cacheNames;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
        multiLevelCacheFactory.setApplicationContext(applicationContext);
    }
}
