package com.chua.starter.ai.support.chat;

import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * 供应商级限流闸门：并发上限 + 每分钟请求数。
 *
 * <p>配置来自供应商 {@code extra}：{@code max_concurrency}（并发上限）与
 * {@code requests_per_minute}（每分钟请求数）；缺省或 ≤0 表示不限制，
 * 此时 {@link #acquire} 直接返回空许可，不产生任何开销。</p>
 *
 * <p>两道闸门按「供应商」维度共享：同一供应商下的所有 Key 共用一份配额。
 * 拿不到许可时最多等待 {@code waitMillis}，超时返回 {@code null}，由调用方
 * 回「供应商繁忙」而不是无限挂住请求。</p>
 *
 * @author CH
 * @since 4.0.0.52
 */
public final class ProviderThrottle {

    /**
     * 并发信号量，键为「供应商|上限」：上限被改小时换一把新锁，避免旧许可永远对不上
     */
    private static final Map<String, Semaphore> CONCURRENCY = new ConcurrentHashMap<>(16);

    /**
     * 每分钟请求时间窗（毫秒时间戳，队头最旧）
     */
    private static final Map<String, Deque<Long>> WINDOW = new ConcurrentHashMap<>(16);

    private static final long WINDOW_MILLIS = 60_000L;

    private ProviderThrottle() {
    }

    /**
     * 许可：持有期间占用一个并发名额，{@link #release()} 归还。
     *
     * <p>未启用并发上限时是空许可，release 不做事；重复 release 也安全。</p>
     */
    public static final class Lease {

        private final Semaphore semaphore;

        private volatile boolean released;

        private Lease(Semaphore semaphore) {
            this.semaphore = semaphore;
        }

        /**
         * 归还并发名额（幂等）。
         */
        public void release() {
            if (released || semaphore == null) {
                return;
            }
            released = true;
            semaphore.release();
        }
    }

    /**
     * 申请一个供应商许可。
     *
     * @param providerId     供应商 ID（为空表示不做限流）
     * @param maxConcurrency 并发上限（≤0 不限制）
     * @param rpm            每分钟请求数（≤0 不限制）
     * @param waitMillis     拿不到许可时最多等待多久
     * @return 许可；等待超时返回 {@code null}
     */
    public static Lease acquire(String providerId, int maxConcurrency, int rpm, long waitMillis) {
        if (providerId == null || (maxConcurrency <= 0 && rpm <= 0)) {
            return new Lease(null);
        }
        long deadline = System.currentTimeMillis() + Math.max(0L, waitMillis);
        Semaphore semaphore = maxConcurrency > 0 ? CONCURRENCY.computeIfAbsent(
                providerId + "|" + maxConcurrency, key -> new Semaphore(maxConcurrency, true)) : null;
        Deque<Long> stamps = rpm > 0 ? WINDOW.computeIfAbsent(providerId, key -> new ConcurrentLinkedDeque<>()) : null;
        while (true) {
            long now = System.currentTimeMillis();
            if (semaphore != null) {
                try {
                    // [P3C A20 豁免]
                    if (!semaphore.tryAcquire(200, TimeUnit.MILLISECONDS)) {
                        if (System.currentTimeMillis() >= deadline) {
                            return null;
                        }
                        continue;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }
            if (stamps != null) {
                synchronized (stamps) {
                    prune(stamps, now);
                    if (stamps.size() >= rpm) {
                        if (semaphore != null) {
                            semaphore.release();
                        }
                        if (now >= deadline) {
                            return null;
                        }
                        try {
                            // [P3C A20 豁免]
                            Thread.sleep(100L);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return null;
                        }
                        continue;
                    }
                    stamps.addLast(now);
                }
            }
            return new Lease(semaphore);
        }
    }

    /**
     * 丢弃时间窗里超过一分钟的请求记录。
     *
     * @param stamps 时间戳队列
     * @param now    当前时间
     */
    private static void prune(Deque<Long> stamps, long now) {
        while (!stamps.isEmpty() && now - stamps.peekFirst() > WINDOW_MILLIS) {
            stamps.pollFirst();
        }
    }
}
