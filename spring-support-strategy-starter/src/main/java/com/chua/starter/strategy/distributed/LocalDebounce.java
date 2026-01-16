package com.chua.starter.strategy.distributed;

import com.chua.common.support.annotations.Extension;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 基于内存的本地防抖器
 * <p>
 * 使用ConcurrentHashMap实现本地防抖，适用于单机场景。
 * 支持自动过期清理。
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2025-12-28
 */
@Slf4j
@Extension("local")
public class LocalDebounce implements StrategyDebounce {

    /**
     * 时间格式正则：支持 1000, 1S, 1MIN, 1H, 1D
     */
    private static final Pattern DURATION_PATTERN = Pattern.compile("^(\\d+)(MS|S|SEC|MIN|H|HOUR|D|DAY)?$", Pattern.CASE_INSENSITIVE);

    /**
     * 防抖锁缓存：key -> 过期时间戳
     */
    private static final Map<String, Long> DEBOUNCE_CACHE = new ConcurrentHashMap<>();

    /**
     * 清理调度器
     */
    private static final ScheduledExecutorService CLEANER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "debounce-cleaner");
        thread.setDaemon(true);
        return thread;
    });

    static {
        // 每分钟清理过期的防抖锁
        CLEANER.scheduleAtFixedRate(() -> {
            long now = System.currentTimeMillis();
            DEBOUNCE_CACHE.entrySet().removeIf(entry -> entry.getValue() <= now);
        }, 1, 1, TimeUnit.MINUTES);
    }

    @Override
    public boolean tryAcquire(String key, long durationMillis) {
        long now = System.currentTimeMillis();
        long expireAt = now + durationMillis;

        // 使用 computeIfAbsent 原子操作
        Long existingExpireAt = DEBOUNCE_CACHE.putIfAbsent(key, expireAt);
        
        if (existingExpireAt == null) {
            // 成功获取锁
            log.debug("本地防抖通过: key={}, duration={}ms", key, durationMillis);
            return true;
        }
        
        // 检查是否已过期
        if (existingExpireAt <= now) {
            // 已过期，尝试更新
            if (DEBOUNCE_CACHE.replace(key, existingExpireAt, expireAt)) {
                log.debug("本地防抖通过(过期重置): key={}, duration={}ms", key, durationMillis);
                return true;
            }
        }
        
        log.debug("本地防抖触发: key={}, duration={}ms", key, durationMillis);
        return false;
    }

    @Override
    public boolean tryAcquire(String key, String duration) {
        long durationMillis = parseDuration(duration);
        return tryAcquire(key, durationMillis);
    }

    @Override
    public void release(String key) {
        DEBOUNCE_CACHE.remove(key);
        log.debug("释放本地防抖锁: key={}", key);
    }

    @Override
    public boolean isDebounced(String key) {
        Long expireAt = DEBOUNCE_CACHE.get(key);
        if (expireAt == null) {
            return false;
        }
        
        if (expireAt <= System.currentTimeMillis()) {
            // 已过期，移除并返回false
            DEBOUNCE_CACHE.remove(key, expireAt);
            return false;
        }
        
        return true;
    }

    @Override
    public String getType() {
        return "LOCAL_MEMORY";
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

    /**
     * 清除指定key的防抖锁
     */
    public static void remove(String key) {
        DEBOUNCE_CACHE.remove(key);
    }

    /**
     * 清除所有防抖锁
     */
    public static void clear() {
        DEBOUNCE_CACHE.clear();
    }

    /**
     * 获取当前防抖锁数量
     */
    public static int size() {
        return DEBOUNCE_CACHE.size();
    }
}
