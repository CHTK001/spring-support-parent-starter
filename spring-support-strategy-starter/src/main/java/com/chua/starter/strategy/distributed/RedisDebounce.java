package com.chua.starter.strategy.distributed;

import com.chua.common.support.annotations.Extension;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 基于Redis的分布式防抖器
 * <p>
 * 使用Redis SETNX实现分布式防抖，保证跨节点的防重复提交。
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2025-12-28
 */
@Slf4j
@RequiredArgsConstructor
@Extension("redis")
public class RedisDebounce implements StrategyDebounce {

    private static final String DEBOUNCE_PREFIX = "strategy:debounce:";
    
    /**
     * 时间格式正则：支持 1000, 1S, 1MIN, 1H, 1D
     */
    private static final Pattern DURATION_PATTERN = Pattern.compile("^(\\d+)(MS|S|SEC|MIN|H|HOUR|D|DAY)?$", Pattern.CASE_INSENSITIVE);

    private final StringRedisTemplate redisTemplate;

    @Override
    public boolean tryAcquire(String key, long durationMillis) {
        String redisKey = DEBOUNCE_PREFIX + key;

        try {
            Boolean success = redisTemplate.opsForValue().setIfAbsent(
                    redisKey, 
                    String.valueOf(System.currentTimeMillis()),
                    durationMillis, 
                    TimeUnit.MILLISECONDS
            );

            boolean acquired = Boolean.TRUE.equals(success);
            
            if (acquired) {
                log.debug("分布式防抖通过: key={}, duration={}ms", key, durationMillis);
            } else {
                log.debug("分布式防抖触发: key={}, duration={}ms", key, durationMillis);
            }

            return acquired;
        } catch (Exception e) {
            log.error("分布式防抖执行异常: key={}", key, e);
            // 异常时默认放行，避免影响业务
            return true;
        }
    }

    @Override
    public boolean tryAcquire(String key, String duration) {
        long durationMillis = parseDuration(duration);
        return tryAcquire(key, durationMillis);
    }

    @Override
    public void release(String key) {
        String redisKey = DEBOUNCE_PREFIX + key;
        try {
            redisTemplate.delete(redisKey);
            log.debug("释放防抖锁: key={}", key);
        } catch (Exception e) {
            log.error("释放防抖锁异常: key={}", key, e);
        }
    }

    @Override
    public boolean isDebounced(String key) {
        String redisKey = DEBOUNCE_PREFIX + key;
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(redisKey));
        } catch (Exception e) {
            log.error("检查防抖状态异常: key={}", key, e);
            return false;
        }
    }

    @Override
    public String getType() {
        return "REDIS_SETNX";
    }

    /**
     * 解析时间格式字符串
     *
     * @param duration 时间字符串，支持格式：1000, 1S, 1MIN, 1H, 1D
     * @return 毫秒数
     */
    private long parseDuration(String duration) {
        if (duration == null || duration.isBlank()) {
            return 1000; // 默认1秒
        }

        String trimmed = duration.trim().toUpperCase();
        Matcher matcher = DURATION_PATTERN.matcher(trimmed);

        if (!matcher.matches()) {
            log.warn("无效的时间格式: {}, 使用默认值1000ms", duration);
            return 1000;
        }

        long value = Long.parseLong(matcher.group(1));
        String unit = matcher.group(2);

        if (unit == null || unit.isEmpty() || "MS".equals(unit)) {
            return value; // 毫秒
        }

        return switch (unit) {
            case "S", "SEC" -> value * 1000;
            case "MIN" -> value * 60 * 1000;
            case "H", "HOUR" -> value * 60 * 60 * 1000;
            case "D", "DAY" -> value * 24 * 60 * 60 * 1000;
            default -> value;
        };
    }
}
