package com.chua.starter.ai.support.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.Collections;
import java.util.List;

/**
 * 基于 Redis ZSET 的滑动窗口限流器。
 *
 * <p>窗口长度 60 秒：每个请求获得一个递增的唯一标识（{@code <ms>:<seq>}），
 * 写入有序集合（score = 请求时间戳，value = 唯一标识），然后清掉窗口前头的记录并
 * 核对窗口中的请求数：超过配额则拒绝（1），否则放行（0）。</p>
 *
 * <h2>原子性</h2>
 * <p>全部操作写在一把 Lua 脚本里，单凭一次网络往返完成，靠
 * {@code RedisTemplate.execute(RedisScript, List, Object...)} 提交。</p>
 *
 * @author CH
 * @since 4.0.0.52
 */
@Slf4j
public class RedisSlidingWindowLimiter implements GatewayRateLimiter {

    /**
     * 限流 Key 前缀
     */
    private static final String LIMIT_KEY_PREFIX = "sys:ai:limit:";
    /**
     * 序列号子键：与限流 Key 同前缀，避免冲突
     */
    private static final String SEQ_SUFFIX = ":seq";
    /**
     * 滑动窗口长度（毫秒）
     */
    private static final long WINDOW_MILLIS = 60_000L;
    /**
     * 窗口边缘外的清理 Key 缓冲秒数
     */
    private static final int EXPIRE_SECONDS = 120;

    /**
     * 限流 Lua 脚本：拒绝返回 1，允许返回 0
     */
    private static final String ALLOW_SCRIPT =
            "local key = KEYS[1]\n"
                    + "local limit = tonumber(ARGV[1])\n"
                    + "local window = tonumber(ARGV[2])\n"
                    + "local now = tonumber(ARGV[3])\n"
                    + "local uniq = redis.call('INCR', key .. ':seq')\n"
                    + "local member = now .. ':' .. uniq\n"
                    + "redis.call('ZADD', key, now, member)\n"
                    + "redis.call('ZREMRANGEBYSCORE', key, 0, now - window)\n"
                    + "local count = redis.call('ZCARD', key)\n"
                    + "if count > limit then\n"
                    + "    return 1\n"
                    + "end\n"
                    + "if count == 0 then\n"
                    + "    redis.call('DEL', key, key .. ':seq')\n"
                    + "end\n"
                    + "redis.call('EXPIRE', key, EXPIRE_SECONDS)\n"
                    + "return 0";

    /**
     * 计数 Lua 脚本（只读，用于诊断）
     */
    private static final String USED_SCRIPT =
            "local from = tonumber(ARGV[1])\n"
                    + "local to = tonumber(ARGV[2])\n"
                    + "return redis.call('ZCOUNT', KEYS[1], from, to)";

    /**
     * 限流脚本
     */
    private static final RedisScript<Long> ALLOW_SCRIPT_OBJ = new DefaultRedisScript<>(ALLOW_SCRIPT, Long.class);
    private static final RedisScript<Long> USED_SCRIPT_OBJ = new DefaultRedisScript<>(USED_SCRIPT, Long.class);

    /**
     * 底层 Redis 操作模板
     */
    private final RedisTemplate<String, String> redisTemplate;

    /**
     * 构造器注入 RedisTemplate（框架自动装配的 String 映射模板）
     *
     * @param redisTemplate Redis 操作模板
     */
    public RedisSlidingWindowLimiter(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean allow(String key, int limit) {
        String luaKey = LIMIT_KEY_PREFIX + key;
        try {
            Long result = redisTemplate.execute(ALLOW_SCRIPT_OBJ,
                    Collections.singletonList(luaKey),
                    limit, WINDOW_MILLIS, System.currentTimeMillis());
            return result != null && result.longValue() == 0;
        } catch (Exception e) {
            log.error("[RedisSlidingWindowLimiter] 限流脚本执行失败 key={}: {}", luaKey, e.getMessage());
            return false;
        }
    }

    @Override
    public long used(String key, int limit) {
        String luaKey = LIMIT_KEY_PREFIX + key;
        try {
            Long count = redisTemplate.execute(USED_SCRIPT_OBJ,
                    Collections.singletonList(luaKey),
                    System.currentTimeMillis() - WINDOW_MILLIS,
                    System.currentTimeMillis());
            return count == null ? 0 : count.longValue();
        } catch (Exception e) {
            log.error("[RedisSlidingWindowLimiter] 查询已用请求数失败 key={}: {}", luaKey, e.getMessage());
            return 0;
        }
    }
}
