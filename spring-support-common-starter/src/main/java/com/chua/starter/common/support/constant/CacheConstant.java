package com.chua.starter.common.support.constant;

/**
 * 缓存常量
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/03/08
 */
public interface CacheConstant {

    /**
     * 系统缓存管理器名称
     */
    String CACHE_MANAGER_FOR_SYSTEM = "system";

    /**
     * 默认缓存管理器名称
     */
    String CACHE_MANAGER_FOR_DEFAULT = "default";

    /**
     * Redis缓存一天时间(86400秒)
     */
    String REDIS_CACHE_ONE_DAY = "redis-cache-86400";

    /**
     * Redis缓存最小时间(600秒)
     */
    String REDIS_CACHE_MIN = "redis-cache-600";

    /**
     * Redis缓存一小时时间(3600秒)
     */
    String REDIS_CACHE_HOUR = "redis-cache-3600";

    /**
     * Redis永久缓存(-1表示永不过期)
     */
    String REDIS_CACHE_ALWAYS = "redis-cache--1";
}
