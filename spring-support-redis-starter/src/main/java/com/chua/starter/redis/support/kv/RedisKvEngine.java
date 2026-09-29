package com.chua.starter.redis.support.kv;

import com.chua.common.support.lang.datasource.kv.KvEngine;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 基于 Spring 数据 Redis {@link StringRedisTemplate} 的 {@link KvEngine} 实现。
 *
 * <p>通过 Spring Boot 自动配置注入，作为默认 KV 引擎在应用中使用。
 * 利用 Redis 扫描 命令实现高效前缀查询，避免 Key 命令阻塞 Redis 实例。</p>
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
 * @since 4.0.0.42
 * @author CH
 */
public class RedisKvEngine implements KvEngine {

    /**
     * 底层 Spring 数据 Redis 字符串操作模板
     */
    private final StringRedisTemplate redisTemplate;

    /**
     * 扫描 命令每次扫描的批次大小
     */
    private static final int SCAN_BATCH_SIZE = 1000;

    /**
     * 构造基于指定 {@link StringRedisTemplate} 的 KV 引擎。
     *
     * @param redisTemplate 不可为 {@code null}
     */
    public RedisKvEngine(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    /**
     * 获取
    */
    public String get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    @Override
    /**
     * 放入
    */
    public void put(String key, String value) {
        if (value == null) {
            delete(key);
            return;
        }
        redisTemplate.opsForValue().set(key, value);
    }

    @Override
    /**
     * 放入
    */
    public void put(String key, String value, Duration ttl) {
        if (value == null) {
            delete(key);
            return;
        }
        // 契约要求 ttl 不可为 null；不校验会把入参失误推迟成底层 NPE，且错误现场难以定位
        if (ttl == null) {
            throw new IllegalArgumentException("过期时长不可为 null（如需永不过期请调用 put(String,String)）");
        }
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("过期时长必须大于 0: " + ttl
                    + "（Redis 会把 0 与负数当作立即删除该键，如需删除请调用 delete）");
        }
        redisTemplate.opsForValue().set(key, value, ttl);
    }

    @Override
    /**
     * containsKey
    */
    public boolean containsKey(String key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    @Override
    /**
     * 删除
    */
    public boolean delete(String key) {
        return Boolean.TRUE.equals(redisTemplate.delete(key));
    }

    @Override
    /**
     * Incr
    */
    public long incr(String key) {
        return redisTemplate.opsForValue().increment(key);
    }

    @Override
    /**
     * Expire
    */
    public void expire(String key, long seconds) {
        // 契约要求 seconds 必须大于 0，而 Redis 的 EXPIRE 对 0 与负数是「立即删除该键」。
        // 若放任通过，调用方一次入参失误就会静默丢数据，故在此显式拒绝并指向 delete
        if (seconds <= 0) {
            throw new IllegalArgumentException("过期秒数必须大于 0: " + seconds
                    + "（Redis 会把 0 与负数当作立即删除该键，如需删除请调用 delete）");
        }
        redisTemplate.expire(key, seconds, TimeUnit.SECONDS);
    }

    @Override
    /**
     * 后端支持 TTL：Redis 原生支持 EXPIRE / PEXPIREAT，因此返回 {@code true}。
     *
     * <p>KvEngine 契约要求「覆写了任一 TTL 操作的实现类必须同时覆写本方法返回 true」，
     * 本类覆写了 {@link #put(String, String, Duration)}、{@link #expire(String, long)}
     * 与 {@link #ttl(String)}，故必须覆写。</p>
     *
     * @return 支持 TTL 返回 {@code true}
     */
    public boolean supportsTtl() {
        return true;
    }

    @Override
    /**
     * Ttl
    */
    public long ttl(String key) {
        return redisTemplate.getExpire(key, TimeUnit.SECONDS);
    }

    /**
     * 使用 扫描 命令查找所有以指定前缀开头的键值对。
     * <p>SCAN 不会阻塞 Redis 实例，适合生产环境使用。每批次扫描 1000 个键。</p>
     *
     * @param prefix 键前缀，不可为 {@code null}
     * @return 匹配前缀的键值对映射；无匹配时返回空映射
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
                    // SCAN 与 GET 之间存在竞态窗口，键可能已过期；此时跳过，
                    // 避免向调用方返回 value 为 null 的「键值对」
                    if (value != null) {
                        result.put(key, value);
                    }
                }
            }
        }
        return result;
    }
}
