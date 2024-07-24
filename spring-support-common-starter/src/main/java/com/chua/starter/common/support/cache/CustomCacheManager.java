package com.chua.starter.common.support.cache;

import com.chua.starter.common.support.configuration.SpringBeanUtils;
import com.chua.starter.common.support.properties.CacheProperties;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 自定义缓存管理器
 * @author CH
 * @since 2024/7/22
 */
public class CustomCacheManager implements CacheManager, InitializingBean {

    private final Map<String, CacheManager> cacheManagerMap = new ConcurrentHashMap<>();
    private CacheProperties cacheProperties;
    private final List<CacheManager> cacheManagers = new LinkedList<>();
    private final Map<String, Cache> cacheMap = new ConcurrentHashMap<>();
    @Override
    public Cache getCache(String name) {
        return cacheMap.computeIfAbsent(name, k -> new CustomCache(name,
                cacheManagers.stream()
                        .filter(it -> !(it instanceof CustomCacheManager))
                        .map(it -> {
                            return it.getCache(name);
                        })
                        .collect(Collectors.toUnmodifiableList()))
        );
    }


    @Override
    public Collection<String> getCacheNames() {
        return cacheManagers.stream().filter(it -> !(it instanceof CustomCacheManager)).map(CacheManager::getCacheNames).flatMap(Collection::stream).toList();
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        Map<String, CacheManager> beansOfType = SpringBeanUtils.getApplicationContext().getBeansOfType(CacheManager.class);
        for (Map.Entry<String, CacheManager> entry : beansOfType.entrySet()) {
            cacheManagerMap.put(entry.getKey().toUpperCase(), entry.getValue());
        }
        cacheProperties = Binder.get(SpringBeanUtils.getEnvironment()).bindOrCreate(CacheProperties.PRE, CacheProperties.class);
        for (String s : cacheProperties.getType()) {
            s = s.toUpperCase();
            if(cacheManagerMap.containsKey(s)) {
                cacheManagers.add(cacheManagerMap.get(s));
            }
        }
    }
}
