package com.chua.starter.cacheable.support.cache;

import com.chua.common.support.collection.DoubleLinkedLinkedList;
import com.chua.common.support.collection.DoubleLinkedList;
import com.chua.common.support.collection.Node;
import com.chua.common.support.function.Splitter;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.ClassUtils;
import com.chua.common.support.value.Value;
import com.chua.starter.cacheable.support.builder.CacheBuilder;
import com.chua.starter.cacheable.support.properties.CacheProperties;
import com.google.common.base.Strings;
import org.springframework.beans.BeansException;
import org.springframework.cache.Cache;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 缓存工厂
 *
 * @author CH
 * @since 2022/8/10 13:39
 */
public class MultiLevelCacheFactory implements ApplicationContextAware {
    private final String name;
    private final CacheProperties cacheProperties;
    private ApplicationContext applicationContext;

    private static final Map<String, Class<CacheBuilder>> CACHE = new ConcurrentHashMap<>();

    private DoubleLinkedList<Cache> doubleLinkedList = new DoubleLinkedLinkedList<>();

    public MultiLevelCacheFactory(String name, CacheProperties cacheProperties, ApplicationContext applicationContext) {
        this.name = name;
        this.cacheProperties = cacheProperties;
        this.applicationContext = applicationContext;
        this.initial();
    }

    /**
     * 初始化
     */
    private void initial() {
        CACHE.putAll(ServiceProvider.of(CacheBuilder.class).listType());
        String link = cacheProperties.getLink();
        List<String> strings = Splitter.on("->").trimResults().omitEmptyStrings().splitToList(link);
        for (String string : strings) {
            Class<CacheBuilder> builderClass = CACHE.get(string);
            CacheBuilder cacheBuilder = ClassUtils.forObject(builderClass);
            if (null == cacheBuilder) {
                continue;
            }
            doubleLinkedList.add(cacheBuilder.build(name, cacheProperties, null));
        }
    }

    /**
     * 是否允许空
     *
     * @return 是否允许空
     */
    public boolean isCacheNullValues() {
        return cacheProperties.isCacheNullValues();
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    public String cachePrefix(String key) {
        return Strings.isNullOrEmpty(cacheProperties.getCachePrefix()) ? key : cacheProperties.getCachePrefix() + ":" + key;
    }

    /**
     * 获取值
     *
     * @param keyValue key
     * @return 值
     */
    public Value<Object> getValue(String keyValue) {
        Value<Object> value = Value.of(null);
        Iterator<Node<Cache>> iterator = doubleLinkedList.iteratorNode();
        while (iterator.hasNext()) {
            Node<Cache> next = iterator.next();
            Cache.ValueWrapper wrapper = next.getData().get(keyValue);
            if (null != wrapper) {
                Object o = wrapper.get();
                preRender(next, keyValue, o);
                return (Value<Object>) o;
            }
        }
        return value;
    }

    private void preRender(Node<Cache> next, String keyValue, Object o) {
        Node<Cache> node;
        while ((node = next.getPrev()) != null) {
            node.getData().put(keyValue, o);
        }
    }

    /**
     * 获取值
     *
     * @param keyValue key
     * @param value    值
     */
    public void setValue(String keyValue, Object value) {
        Iterator<Cache> iterator = doubleLinkedList.iterator();
        while (iterator.hasNext()) {
            iterator.next().put(keyValue, Value.of(value));
        }

    }

    /**
     * 删除值
     *
     * @param keyValue  key
     */
    public void evict(String keyValue) {
        Iterator<Cache> iterator = doubleLinkedList.iterator();
        while (iterator.hasNext()) {
            iterator.next().evictIfPresent(keyValue);
        }
    }

    /**
     * 清空
     *
     */
    public void clear() {
        Iterator<Cache> iterator = doubleLinkedList.iterator();
        while (iterator.hasNext()) {
            iterator.next().clear();
        }
    }
}
