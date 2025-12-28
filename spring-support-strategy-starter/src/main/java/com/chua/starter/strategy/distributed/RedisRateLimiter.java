package com.chua.starter.strategy.distributed;

import com.chua.common.support.annotations.Extension;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;

/**
 * 基于Redis的滑动窗口分布式限流器
 * <p>
 * 使用Redis + Lua脚本实现滑动窗口限流算法，保证原子性和高性能。
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2025-12-28
 */
@Slf4j
@Extension("redis")
public class RedisRateLimiter implements StrategyRateLimiter {

    private static final String RATE_LIMITER_PREFIX = "strategy:ratelimit:";
    
    private final StringRedisTemplate redisTemplate;

    /**
     * 滑动窗口限流Lua脚本
     * <p>
     * 使用有序集合(ZSET)实现滑动窗口：
     * - score: 请求时间戳
     * - member: 唯一请求ID
     * </p>
     */
    private static final String SLIDING_WINDOW_SCRIPT = """
            local key = KEYS[1]
            local now = tonumber(ARGV[1])
            local window_size = tonumber(ARGV[2])
            local limit = tonumber(ARGV[3])
            local request_id = ARGV[4]
            
            -- 计算窗口起始时间
            local window_start = now - window_size * 1000
            
            -- 移除窗口外的请求
            redis.call('ZREMRANGEBYSCORE', key, '-inf', window_start)
            
            -- 获取当前窗口内的请求数
            local current_count = redis.call('ZCARD', key)
            
            -- 判断是否超过限制
            if current_count < limit then
                -- 添加当前请求
                redis.call('ZADD', key, now, request_id)
                -- 设置过期时间（窗口大小 + 1秒缓冲）
                redis.call('EXPIRE', key, window_size + 1)
                return 1
            else
                return 0
            end
            """;

    /**
     * 获取剩余许可数的Lua脚本
     */
    private static final String GET_AVAILABLE_SCRIPT = """
            local key = KEYS[1]
            local now = tonumber(ARGV[1])
            local window_size = tonumber(ARGV[2])
            local limit = tonumber(ARGV[3])
            
            -- 计算窗口起始时间
            local window_start = now - window_size * 1000
            
            -- 移除窗口外的请求
            redis.call('ZREMRANGEBYSCORE', key, '-inf', window_start)
            
            -- 获取当前窗口内的请求数
            local current_count = redis.call('ZCARD', key)
            
            return limit - current_count
            """;

    private final DefaultRedisScript<Long> slidingWindowScript;
    private final DefaultRedisScript<Long> getAvailableScript;

    public RedisRateLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        
        this.slidingWindowScript = new DefaultRedisScript<>();
        this.slidingWindowScript.setScriptText(SLIDING_WINDOW_SCRIPT);
        this.slidingWindowScript.setResultType(Long.class);
        
        this.getAvailableScript = new DefaultRedisScript<>();
        this.getAvailableScript.setScriptText(GET_AVAILABLE_SCRIPT);
        this.getAvailableScript.setResultType(Long.class);
    }

    @Override
    public boolean tryAcquire(String key, int limitForPeriod, int periodSeconds) {
        return tryAcquire(key, limitForPeriod, periodSeconds, 0);
    }

    @Override
    public boolean tryAcquire(String key, int limitForPeriod, int periodSeconds, long timeoutMillis) {
        String redisKey = RATE_LIMITER_PREFIX + key;
        long now = System.currentTimeMillis();
        String requestId = now + ":" + Thread.currentThread().getId() + ":" + Math.random();

        try {
            Long result = redisTemplate.execute(
                    slidingWindowScript,
                    Collections.singletonList(redisKey),
                    String.valueOf(now),
                    String.valueOf(periodSeconds),
                    String.valueOf(limitForPeriod),
                    requestId
            );

            boolean acquired = result != null && result == 1;
            
            if (!acquired && timeoutMillis > 0) {
                // 带超时的重试
                long deadline = System.currentTimeMillis() + timeoutMillis;
                long sleepTime = Math.min(50, timeoutMillis / 10);
                
                while (System.currentTimeMillis() < deadline) {
                    try {
                        Thread.sleep(sleepTime);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                    
                    now = System.currentTimeMillis();
                    requestId = now + ":" + Thread.currentThread().getId() + ":" + Math.random();
                    result = redisTemplate.execute(
                            slidingWindowScript,
                            Collections.singletonList(redisKey),
                            String.valueOf(now),
                            String.valueOf(periodSeconds),
                            String.valueOf(limitForPeriod),
                            requestId
                    );
                    
                    if (result != null && result == 1) {
                        return true;
                    }
                }
            }

            if (acquired) {
                log.debug("分布式限流通过: key={}, limit={}/{}", key, limitForPeriod, periodSeconds);
            } else {
                log.debug("分布式限流触发: key={}, limit={}/{}", key, limitForPeriod, periodSeconds);
            }

            return acquired;
        } catch (Exception e) {
            log.error("分布式限流执行异常: key={}", key, e);
            // 异常时默认放行，避免影响业务
            return true;
        }
    }

    @Override
    public long getAvailablePermits(String key, int limitForPeriod, int periodSeconds) {
        String redisKey = RATE_LIMITER_PREFIX + key;
        long now = System.currentTimeMillis();

        try {
            Long result = redisTemplate.execute(
                    getAvailableScript,
                    Collections.singletonList(redisKey),
                    String.valueOf(now),
                    String.valueOf(periodSeconds),
                    String.valueOf(limitForPeriod)
            );
            return result != null ? result : limitForPeriod;
        } catch (Exception e) {
            log.error("获取剩余许可数异常: key={}", key, e);
            return limitForPeriod;
        }
    }

    @Override
    public void reset(String key) {
        String redisKey = RATE_LIMITER_PREFIX + key;
        try {
            redisTemplate.delete(redisKey);
            log.debug("重置限流计数: key={}", key);
        } catch (Exception e) {
            log.error("重置限流计数异常: key={}", key, e);
        }
    }

    @Override
    public String getType() {
        return "REDIS_SLIDING_WINDOW";
    }
}
