package com.chua.starter.strategy.template;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 本地内存幂等性实现（Redis 不可用时的降级方案）。
 * <p>
 * 仅适合单实例部署，重启后幂等状态丢失。生产环境推荐使用 {@link RedisIdempotentTemplate}。
 * </p>
 *
 * @author CH
 * @since 2026-05-07
 */
@Slf4j
public class LocalIdempotentTemplate implements IdempotentTemplate {

    private final Map<String, Long> localCache = new ConcurrentHashMap<>();

    @Override
    public boolean tryAcquire(String key, long timeout, TimeUnit unit) {
        long timeoutMillis = unit.toMillis(timeout);
        long now = System.currentTimeMillis();

        // 清理过期数据
        localCache.entrySet().removeIf(entry -> now - entry.getValue() > timeoutMillis);

        Long existing = localCache.get(key);
        if (existing != null && now - existing < timeoutMillis) {
            log.info("本地幂等拦截重复请求: key={}", key);
            return false;
        }

        localCache.put(key, now);
        return true;
    }

    @Override
    public void complete(String key, boolean releaseOnFinish) {
        if (releaseOnFinish) {
            release(key);
        }
    }

    @Override
    public void release(String key) {
        localCache.remove(key);
        log.debug("本地幂等键已释放: key={}", key);
    }
}
