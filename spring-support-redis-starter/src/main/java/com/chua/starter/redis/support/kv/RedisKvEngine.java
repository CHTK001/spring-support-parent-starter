package com.chua.starter.redis.support.kv;

import com.chua.common.support.lang.datasource.kv.KvEngine;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 基于 Spring Data Redis {@link StringRedisTemplate} 的 {@link KvEngine} 实现。
 *
 * <p>通过 Spring Boot 自动配置注入，作为默认 KV 引擎在应用中使用。
 * 利用 Redis SCAN 命令实现高效前缀查询，避免 KEYS 命令阻塞 Redis 实例。</p>
 *
 * <h2>使用方式</h2>
 * <pre>{@code
 * @Service
 * public class UserService {
 *
 *     @Autowired
 *     private KvEngine kvEngine;
 *
 *     public void setToken(String userId, String token) {
 *         kvEngine.put("token:" + userId, token, Duration.ofHours(1));
 *     }
 *
 *     public String getToken(String userId) {
 *         return kvEngine.get("token:" + userId);
 *     }
 *
 *     public void setUserInfo(String userId, Map<String, String> info) {
 *         for (Map.Entry<String, String> entry : info.entrySet()) {
 *             kvEngine.put("user:" + userId + ":" + entry.getKey(), entry.getValue());
 *         }
 *     }
 *
 *     public Map<String, String> getUserInfo(String userId) {
 *         return kvEngine.findAllByPrefix("user:" + userId + ":");
 *     }
 * }
 * }</pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
public class RedisKvEngine implements KvEngine {

    /**
     * 底层 Spring Data Redis 字符串操作模板
     */
    private final StringRedisTemplate redisTemplate;

    /**
     * SCAN 命令每次扫描的批次大小
     */
    private static final int SCAN_BATCH_SIZE = 1000;

    /**
     * 构造基于指定 {@link StringRedisTemplate} 的 KV 引擎。
     *
     * @param redisTemplate 不可为 null
     */
    public RedisKvEngine(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public String get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    @Override
    public void put(String key, String value) {
        redisTemplate.opsForValue().set(key, value);
    }

    @Override
    public void put(String key, String value, Duration ttl) {
        redisTemplate.opsForValue().set(key, value, ttl);
    }

    @Override
    public boolean containsKey(String key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    @Override
    public boolean delete(String key) {
        return Boolean.TRUE.equals(redisTemplate.delete(key));
    }

    @Override
    public long incr(String key) {
        return redisTemplate.opsForValue().increment(key);
    }

    @Override
    public void expire(String key, long seconds) {
        redisTemplate.expire(key, seconds, TimeUnit.SECONDS);
    }

    @Override
    public long ttl(String key) {
        return redisTemplate.getExpire(key, TimeUnit.SECONDS);
    }

    /**
     * 使用 SCAN 命令查找所有以指定前缀开头的键值对。
     * <p>SCAN 不会阻塞 Redis 实例，适合生产环境使用。每批次扫描 1000 个键。</p>
     *
     * @param prefix 键前缀，不可为 null
     * @return 匹配前缀的键值对映射；无匹配时返回空 Map
     */
    @Override
    public Map<String, String> findAllByPrefix(String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        ScanOptions options =
                ScanOptions.scanOptions()
                        .match(prefix + "*")
                        .count(SCAN_BATCH_SIZE)
                        .build();
        try (var cursor = redisTemplate.scan(options)) {
            while (cursor.hasNext()) {
                String key = cursor.next();
                if (key != null && key.startsWith(prefix)) {
                    String value = redisTemplate.opsForValue().get(key);
                    result.put(key, value);
                }
            }
        }
        return result;
    }
}
