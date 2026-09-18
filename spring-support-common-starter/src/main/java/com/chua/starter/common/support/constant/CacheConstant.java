package com.chua.starter.common.support.constant;

/**
* 缓存常量
* <p>
* 集中存放缓存管理器名称、缓存名称、TTL 等基础常量，避免在代码中出现魔法值。
* </p>
*
* @since 2024/03/08
* @author CH
 */
public final class CacheConstant {

    /**
    * 缓存常量。
    */
    private CacheConstant() {
    }

    // ==================== 缓存管理器 Bean 名称 ====================

    /**
    * 默认 Caffeine 缓存管理器名称
    */
    public static final String CAFFEINE_CACHE_MANAGER = "caffeineCacheManager";

    /**
    * 默认 Redis 缓存管理器名称
    */
    public static final String REDIS_CACHE_MANAGER = "redisCacheManager";

    /**
    * 默认缓存管理器名称（默认 Caffeine）
    */
    public static final String CACHE_MANAGER_FOR_DEFAULT = "default";

    /**
    * 系统缓存管理器名称
    */
    public static final String CACHE_MANAGER_FOR_SYSTEM = "system";

    /**
    * 多级缓存管理器名称
    */
    public static final String CACHE_MANAGER_FOR_MULTI = "multiLevelCacheManager";

    // ==================== 缓存层级标识 ====================

    /**
    * Caffeine 缓存层级标识
    */
    public static final String LEVEL_CAFFEINE = "caffeine";

    /**
    * Redis 缓存层级标识
    */
    public static final String LEVEL_REDIS = "redis";

    // ==================== Redis 预置 TTL 缓存名 ====================

    /**
    * Redis 缓存一天时间（86400 秒）
    */
    public static final String REDIS_CACHE_ONE_DAY = "redis-cache-86400";

    /**
    * Redis 缓存最小时间（600 秒）
    */
    public static final String REDIS_CACHE_MIN = "redis-cache-600";

    /**
    * Redis 缓存一分钟时间（60 秒）
    */
    public static final String REDIS_CACHE_MINUTES = "redis-cache-60";

    /**
    * Redis 缓存半小时时间（30 秒）
    */
    public static final String REDIS_CACHE_HALF_MINUTES = "redis-cache-30";

    /**
    * Redis 缓存十秒时间（10 秒）
    */
    public static final String REDIS_CACHE_TEN_SECOND = "redis-cache-10";

    /**
    * Redis 缓存一小时时间（3600 秒）
    */
    public static final String REDIS_CACHE_HOUR = "redis-cache-3600";

    /**
    * Redis 永久缓存（-1 表示永不过期）
    */
    public static final String REDIS_CACHE_ALWAYS = "redis-cache--1";

    // ==================== 预置缓存名 ====================

    /**
    * 用户缓存名
    */
    public static final String CACHE_USER = "user";

    /**
    * 角色缓存名
    */
    public static final String CACHE_ROLE = "role";

    /**
    * 权限缓存名
    */
    public static final String CACHE_PERMISSION = "permission";

    /**
    * 字典缓存名
    */
    public static final String CACHE_DICT = "dict";

    /**
    * 配置缓存名
    */
    public static final String CACHE_CONFIG = "config";

    // ==================== 分隔符与前缀 ====================

    /**
    * 缓存键分隔符
    */
    public static final String KEY_SEPARATOR = ":";

    /**
    * 缓存键默认前缀
    */
    public static final String KEY_DEFAULT_PREFIX = "cache:";

    /**
    * 永不过期标识
    */
    public static final long TTL_NEVER_EXPIRE = -1L;
}
