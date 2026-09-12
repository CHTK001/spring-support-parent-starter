package com.chua.starter.common.support.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;

import java.util.function.Supplier;

/**
* 缓存操作模板
* <p>
* 提供高层次的手动缓存操作方法，内部自动使用 {@link TenantedKeyGenerator}
* 拼接完整的缓存键（包含应用名、平台名、租户标识），保证手动操作与
* {@code @Cacheable} / {@code @CachePut} / {@code @CacheEvict} 注解生成的 key 一致。
* </p>
*
* <h3>用法示例：</h3>
* <pre>{@code
* // 注入
* &#64;Autowired
* private CacheTemplate cacheTemplate;
*
* // 存储
* cacheTemplate.put("users", "user:" + userId, userObj);
*
* // 读取
* User user = cacheTemplate.get("users", "user:" + userId, User.class);
*
* // 删除
* cacheTemplate.evict("users", "user:" + userId);
*
* // 按模式清除
* cacheTemplate.evictByPattern("users", "user:*");
* }</pre>acheTemplate.evictByPattern("users", "user:*");
* }</pre>
*
* @since 2025/8/3
* @author CH
 */
public class CacheTemplate {

    /** 日志 */
    private static final Logger log = LoggerFactory.getLogger(CacheTemplate.class);

    /** 缓存管理器 */
    private final MultiLevelCacheManager cacheManager;
    /** 密钥生成器 */
    private final TenantedKeyGenerator keyGenerator;

    /**
    * 创建 缓存template 实例
    * @param cacheManager 缓存管理器
    * @param keyGenerator tenanted钥匙生成器
    * @param keyGenerator 钥匙生成器
     */
    public CacheTemplate(MultiLevelCacheManager cacheManager, TenantedKeyGenerator keyGenerator) {
        this.cacheManager = cacheManager;
        this.keyGenerator = keyGenerator;
    }

    /**
    * 根据业务键构建完整的缓存键（与 {@code @Cacheable} 注解生成的 钥匙 一致）
    *
    * @param businessKey 业务键，例如 {@code "user:123"}
    * @return 完整缓存键，例如 {@code "cache:myapp:default:tenant-0:user:123"}
     */
    public String buildKey(String businessKey) {
        return keyGenerator.generate(null, null, businessKey).toString();
    }

    /**
    * 存入缓存
    *
    * @param cacheName   缓存名（必须与 {@code @Cacheable(cacheNames = "...")} 一致）
    * @param businessKey 业务键
    * @param value       缓存值
     */
    public void put(String cacheName, String businessKey, Object value) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache == null) {
            log.warn("缓存模板写入失败: 未找到缓存实例 [{}]", cacheName);
            return;
        }
        String fullKey = buildKey(businessKey);
        cache.put(fullKey, value);
        log.debug("缓存模板写入: cache={}, key={}", cacheName, fullKey);
    }

    /**
    * 从缓存读取
    *
    * @param cacheName   缓存名
    * @param businessKey 业务键
    * @param type        值类型
    * @param <T>         值泛型
    * @return 缓存值，未命中返回 {@code null}
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String cacheName, String businessKey, Class<T> type) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache == null) {
            log.warn("缓存模板读取失败: 未找到缓存实例 [{}]", cacheName);
            return null;
        }
        String fullKey = buildKey(businessKey);
        Cache.ValueWrapper wrapper = cache.get(fullKey);
        if (wrapper == null) {
            log.debug("缓存模板未命中: cache={}, key={}", cacheName, fullKey);
            return null;
        }
        Object value = wrapper.get();
        log.debug("缓存模板命中: cache={}, key={}", cacheName, fullKey);
        if (value == null || type.isInstance(value)) {
            return (T) value;
        }
        log.warn("缓存模板类型不匹配: cache={}, key={}, expected={}, actual={}",
                cacheName, fullKey, type.getName(), value.getClass().getName());
        return null;
    }

    /**
    * 删除缓存
    *
    * @param cacheName   缓存名
    * @param businessKey 业务键
     */
    public void evict(String cacheName, String businessKey) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache == null) {
            log.warn("缓存模板删除失败: 未找到缓存实例 [{}]", cacheName);
            return;
        }
        String fullKey = buildKey(businessKey);
        cache.evict(fullKey);
        log.debug("缓存模板删除: cache={}, key={}", cacheName, fullKey);
    }

    /**
    * 按模式清除缓存
    *
    * @param cacheName 缓存名
    * @param pattern   模式字符串（例如 {@code "user:*"}）
    * @return 是否执行成功
     */
    public boolean evictByPattern(String cacheName, String pattern) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache instanceof MultiLevelCache mlc) {
            int affected = mlc.evictByPattern(pattern);
            log.info("缓存模板按模式清除: name={}, pattern={}, affectedLevels={}", cacheName, pattern, affected);
            return affected > 0;
        }
        if (cache != null) {
            cache.clear();
            log.info("缓存模板按模式清除（fallback 到 clear）: name={}, pattern={}", cacheName, pattern);
            return true;
        }
        log.warn("缓存模板按模式清除失败: 未找到缓存实例 [{}]", cacheName);
        return false;
    }

    /**
    * 清空整个缓存区域
    *
    * @param cacheName 缓存名
     */
    public void clear(String cacheName) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache == null) {
            log.warn("缓存模板清空失败: 未找到缓存实例 [{}]", cacheName);
            return;
        }
        cache.clear();
        log.info("缓存模板清空: name={}", cacheName);
    }

    /**
    * 清空所有多级缓存
     */
    public void clearAll() {
        cacheManager.clearAll();
        log.info("缓存模板清空所有缓存");
    }

    /**
    * 获取缓存管理器的统计信息
    *
    * @return 缓存名到统计的映射
     */
    public java.util.Map<String, MultiLevelCacheManager.CacheStats> getStats() {
        return cacheManager.getStats();
    }

    /**
    * 获取缓存管理器（供高级使用）
    * @return 获取缓存管理器的结果
     */
    public MultiLevelCacheManager getCacheManager() {
        return cacheManager;
    }

    /**
    * 获取键生成器（供高级使用）
    * @return 获取钥匙生成器的结果
     */
    public TenantedKeyGenerator getKeyGenerator() {
        return keyGenerator;
    }
}
