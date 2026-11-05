// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.strategy.template;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 内存版幂等模板，提供 尝试acquire / release / 完成 操作。
 * <p>
 * 适用于单机场景或非强依赖分布式锁的环境。
 * 分布式场景请使用基于 Redis 的实现。
 * </p>
 *
 * @author CH
 * @since 4.0.0.50
 */
@Slf4j
@Component
public class IdempotentTemplate {

// [P3C C1 豁免] 场景化创建线程，需文档化
    private static final ScheduledExecutorService CLEANER = Executors.newSingleThreadScheduledExecutor(r -> {
// [P3C C1 豁免] 场景化创建线程，需文档化
        Thread t = new Thread(r, "idempotent-cleaner");
        t.setDaemon(true);
        return t;
    });

    /**
     * Key -> 过期时间戳（毫秒）
    */
    private static final ConcurrentHashMap<String, Long> ACQUIRED_KEYS = new ConcurrentHashMap<>();

    static {
        CLEANER.scheduleAtFixedRate(() -> {
            long now = System.currentTimeMillis();
            ACQUIRED_KEYS.entrySet().removeIf(e -> e.getValue() < now);
        }, 60, 60, TimeUnit.SECONDS);
    }

    /**
     * 尝试获取幂等锁。
     *
     * @param key     幂等键
     * @param timeout 超时时间
     * @param unit    时间单位
     * @return true 表示获取成功，false 表示已存在或获取失败
     */
    public boolean tryAcquire(String key, long timeout, TimeUnit unit) {
        if (key == null || key.isBlank()) {
            return true;
        }
        long expireAt = System.currentTimeMillis() + unit.toMillis(timeout);
        Long previous = ACQUIRED_KEYS.putIfAbsent(key, expireAt);
        if (previous != null) {
            if (previous > System.currentTimeMillis()) {
                log.warn("Idempotent key already acquired: {}", key);
                return false;
            }
            ACQUIRED_KEYS.remove(key, previous);
            ACQUIRED_KEYS.putIfAbsent(key, expireAt);
        }
        return true;
    }

    /**
     * 释放幂等锁。
     *
     * @param key 幂等键
     */
    public void release(String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        ACQUIRED_KEYS.remove(key);
    }

    /**
     * 标记幂等操作完成。
     *
     * @param key     幂等键
     * @param success 是否成功
     */
    public void complete(String key, boolean success) {
        if (key == null || key.isBlank()) {
            return;
        }
        ACQUIRED_KEYS.remove(key);    }}