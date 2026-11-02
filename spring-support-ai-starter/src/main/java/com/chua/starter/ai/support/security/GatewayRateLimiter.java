package com.chua.starter.ai.support.security;

/**
 * 滑动窗口限流器。
 *
 * <p>以窗口内（60 秒）的请求数做判据：</p>
 * <ul>
 *   <li>{@code RedisSlidingWindowLimiter} — 基于 Redis ZSET，多实例共享；</li>
 *   <li>{@code LocalSlidingWindowLimiter} — 本地 Caffeine 窗口，仅单实例可用时的兜底。</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.52
 */
public interface GatewayRateLimiter {

    /**
     * 尝试允许一个 Key 的请求。
     *
     * @param key   限流 Key（如 AppKey、IP、路径）
     * @param limit 窗口内的最大请求数
     * @return 允许返回 true，达到上限返回 false
     */
    boolean allow(String key, int limit);

    /**
     * 获取指定 Key 在当前窗口中已消耗的请求数（只读，用于诊断）。
     *
     * @param key   限流 Key
     * @param limit 窗口上限（用于域名，若窗口未配则视为 0）
     * @return 已消耗数
     */
    long used(String key, int limit);
}
