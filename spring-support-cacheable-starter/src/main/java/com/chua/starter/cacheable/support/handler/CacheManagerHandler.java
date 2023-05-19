package com.chua.starter.cacheable.support.handler;

import com.chua.common.support.value.Value;
import com.chua.starter.cacheable.support.properties.CacheProperties;
import org.springframework.beans.factory.NamedBean;
import org.springframework.context.ApplicationContextAware;

/**
 * 处理器
 *
 * @author CH
 * @since 2022/8/10 13:55
 */
public interface CacheManagerHandler extends NamedBean, ApplicationContextAware {
    /**
     * 设置配置
     *
     * @param cachePoolProperties 配置
     */
    void setCacheProperties(CacheProperties.CachePoolProperties cachePoolProperties);

    /**
     * 是否允许为空
     *
     * @param cacheNullValues 是否允许为空
     */
    void isCacheNullValues(boolean cacheNullValues);

    /**
     * 获取值
     *
     * @param cacheName 缓存名称
     * @param keyValue  key
     * @return 值
     */
    Value<Object> getValue(String cacheName, String keyValue);

    /**
     * 赋值
     *
     * @param cacheName 缓存
     * @param keyValue  key
     * @param value     值
     */
    void setValue(String cacheName, String keyValue, Value<Object> value);

    /**
     * 删除值
     *
     * @param cacheName 缓存名称
     * @param keyValue  key
     */
    void evict(String cacheName, String keyValue);

    /**
     * 清空
     *
     * @param cacheName 缓存名称
     */
    void clear(String cacheName);
}
