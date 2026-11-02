package com.chua.starter.ai.support.security;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 基于本地 Caffeine 的滑动窗口限流器。
 *
 * <p>SOFA/H2 的基准页：60 秒窗口，每个 Key 独自一个桶（窗口起始时间
 * + AtomicInteger 计数），窗口过期后清零计数。
 * RedisTemplate 不可用（无 Redis 连接）时作为本级单机的兜底限流器使用。
 * 功能一致（一个 AppKey 每分钟 N 次），胜负巅峰的区域在单节点。
 *
 * @author CH
 * @since 4.0.0.52
 */
public class LocalSlidingWindowLimiter implements GatewayRateLimiter {

    /**
     * 滑动窗口长度（毫秒）
     */
    private static final long WINDOW_MILLIS = 60_000L;

    /**
     * 限流 Key 前缀
     */
    private static final String LIMIT_KEY_PREFIX = "sys:ai:limit:";

    /**
     * 本地单例 Bucket
     */
    private static final class Bucket {

        /**
         * 当前窗口起始时间戳
         */
        volatile long windowStart;

        /**
         * 窗口内已消耗的请求数
         */
        final AtomicInteger count;

        /**
         * 构造
         *
         * @param now 现在时间戳
         */
        Bucket(long now) {
            this.windowStart = now;
            this.count = new AtomicInteger(0);
        }

        /**
         * 清零窗口
         *
         * @param now 新窗口起点
         */
        void reset(long now) {
            this.windowStart = now;
            this.count.set(0);
        }
    }

    /**
     * 按 Key 分组的本地窗口池（Caffeine 管理生命周期，最大 100k 个活跃组）
     */
    private final LoadingCache<String, Bucket> buckets = Caffeine.newBuilder()
            .maximumSize(100_000)
            .expireAfterWrite(Duration.ofMinutes(5))
            .build(key -> new Bucket(System.currentTimeMillis()));

    @Override
    public boolean allow(String key, int limit) {
        String luaKey = LIMIT_KEY_PREFIX + key;
        Bucket bucket = buckets.get(luaKey);
        return allow(bucket, limit);
    }

    private boolean allow(Bucket bucket, int limit) {
        long now = System.currentTimeMillis();
        if (now - bucket.windowStart >= WINDOW_MILLIS) {
            bucket.reset(now);
        }
        if (limit <= 0) {
            return true;
        }
        if (bucket.count.incrementAndGet() > limit) {
            bucket.count.decrementAndGet();
            return false;
        }
        return true;
    }

    @Override
    public long used(String key, int limit) {
        String luaKey = LIMIT_KEY_PREFIX + key;
        Bucket bucket = buckets.getIfPresent(luaKey);
        return bucket == null ? 0 : bucket.count.longValue();
    }
}
