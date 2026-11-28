// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.oauth.client.support.ratelimit;

import com.chua.common.support.utils.StringUtils;
import com.chua.starter.oauth.client.support.properties.AuthClientProperties;
import com.chua.starter.oauth.client.support.runtime.OauthClientRuntimeContext;
import lombok.extern.slf4j.Slf4j;

import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * o认证 客户端限流服务
 * <p>
 * 提供接口 QPS 限流（按 IP 滑动窗口）与登录防暴力破解限流（IP 维度 + 账号失败锁定）。
 * 采用进程内内存实现，配置来自 {@link AuthClientProperties.RateLimit}（前缀
 * <code>plugin.oauth.rate-limit</code>）。多实例部署时各实例独立计数，如需跨实例共享
 * 应替换为 Redis 实现。
 * </p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
public class RateLimitService {

    /**
     * 滑动窗口最大键数量，超过后触发清理
     */
    private static final int MAX_WINDOW_KEYS = 10000;

    /**
     * 窗口键空闲清理时间（毫秒），超过视为可回收
     */
    private static final long MAX_KEY_IDLE_MILLIS = 10 * 60 * 1000L;

    /**
     * 账号锁定/失败计数最大键数量，超过后触发清理
     */
    private static final int MAX_ACCOUNT_KEYS = 10000;

    /**
     * 单例实例
     */
    private static final RateLimitService INSTANCE = new RateLimitService();

    /**
     * IP 滑动窗口计数：Key = "ip:类型"，值 = 时间戳队列
     */
    private final Map<String, Deque<Long>> ipWindows = new ConcurrentHashMap<>();

    /**
     * 账号失败计数：Key = 账号，值 = 连续失败次数
     */
    private final Map<String, Integer> failCounts = new ConcurrentHashMap<>();

    /**
     * 账号锁定截止时间：Key = 账号，值 = 解锁时间戳
     */
    private final Map<String, Long> lockedUntil = new ConcurrentHashMap<>();

    /**
     * 私有构造函数（单例）
     */
    private RateLimitService() {
    }

    /**
     * 获取单例实例
     *
     * @return 限流服务实例
     */
    public static RateLimitService getInstance() {
        return INSTANCE;
    }

    /**
     * 全局接口 QPS 限流检查
     *
     * @param ip 客户端 IP
     * @return true-放行，false-拒绝
     */
    public boolean checkApiLimit(String ip) {
        AuthClientProperties.RateLimit.ApiLimit api = getApiLimitConfig();
        if (api == null || !api.isEnable()) {
            return true;
        }
        return acquire(ip + ":api", api.getWindowSeconds() * 1000, api.getQps());
    }

    /**
     * 登录接口 IP 限流检查
     *
     * @param ip 客户端 IP
     * @return true-放行，false-拒绝
     */
    public boolean checkLoginIpLimit(String ip) {
        AuthClientProperties.RateLimit.LoginLimit login = getLoginLimitConfig();
        if (login == null || !login.isEnable()) {
            return true;
        }
        return acquire(ip + ":login", login.getIpWindowSeconds() * 1000, login.getIpLimit());
    }

    /**
     * 检查账号是否被锁定
     *
     * @param username 账号
     * @return true-已锁定，false-未锁定
     */
    public boolean isAccountLocked(String username) {
        if (StringUtils.isBlank(username)) {
            return false;
        }
        /**
         * cleanup账户ifneeded。
         */
        cleanupAccountIfNeeded();
        Long until = lockedUntil.get(username);
        if (until == null) {
            return false;
        }
        if (until <= System.currentTimeMillis()) {
            // 锁定已过期，清理并放行
            lockedUntil.remove(username);
            failCounts.remove(username);
            return false;
        }
        return true;
    }

    /**
     * 记录一次登录失败，达到阈值后锁定账号
     *
     * @param username 账号
     */
    public void recordLoginFailure(String username) {
        if (StringUtils.isBlank(username)) {
            return;
        }
        AuthClientProperties.RateLimit.LoginLimit login = getLoginLimitConfig();
        if (login == null || !login.isEnable()) {
            return;
        }
        int count = failCounts.merge(username, 1, Integer::sum);
        if (count >= login.getAccountFailLimit()) {
            long lockMillis = login.getAccountLockMinutes() * 60_000L;
            lockedUntil.put(username, System.currentTimeMillis() + lockMillis);
            failCounts.remove(username);
            log.warn("[OAuth限流]账号已被锁定: {}, 锁定{}分钟", username, login.getAccountLockMinutes());
        }
    }

    /**
     * 登录成功，清除账号失败计数与锁定状态
     *
     * @param username 账号
     */
    public void resetAccount(String username) {
        if (StringUtils.isBlank(username)) {
            return;
        }
        lockedUntil.remove(username);
        failCounts.remove(username);
    }

    /**
     * 滑动窗口计数器：窗口内次数未达上限则记录并放行
     *
     * @param key      计数键
     * @param windowMs 窗口大小（毫秒）
     * @param limit    窗口内上限
     * @return true-放行，false-拒绝
     */
    private boolean acquire(String key, long windowMs, int limit) {
        if (limit <= 0) {
            return false;
        }
        /**
         * cleanupifneeded。
         */
        cleanupIfNeeded();
        long now = System.currentTimeMillis();
        Deque<Long> queue = ipWindows.computeIfAbsent(key, k -> new ConcurrentLinkedDeque<>());
        synchronized (queue) {
            // 清理过期时间戳
            while (!queue.isEmpty() && now - queue.peekFirst() > windowMs) {
                queue.pollFirst();
            }
            if (queue.size() >= limit) {
                return false;
            }
            queue.addLast(now);
            return true;
        }
    }

    /**
     * 窗口键数量超限时清理长期空闲的键，防止海量随机 IP 导致内存泄漏
     */
    private void cleanupIfNeeded() {
        if (ipWindows.size() <= MAX_WINDOW_KEYS) {
            return;
        }
        long now = System.currentTimeMillis();
        ipWindows.entrySet().removeIf(entry -> {
            Deque<Long> queue = entry.getValue();
            Long last = queue.peekLast();
            return last != null && now - last > MAX_KEY_IDLE_MILLIS;
        });
    }

    /**
     * 账号锁定/失败计数键数量超限时清理已过期的锁定记录，防止长期运行内存缓慢增长
     */
    private void cleanupAccountIfNeeded() {
        if (lockedUntil.size() <= MAX_ACCOUNT_KEYS) {
            return;
        }
        long now = System.currentTimeMillis();
        // 清理已过期的锁定记录
        lockedUntil.entrySet().removeIf(entry -> entry.getValue() <= now);
        // 清理未处于锁定状态的失败计数
        failCounts.keySet().removeIf(username -> !lockedUntil.containsKey(username));
    }

    /**
     * 获取全局接口限流配置
     *
     * @return 配置，未启用或未初始化返回 空
     */
    private AuthClientProperties.RateLimit.ApiLimit getApiLimitConfig() {
        AuthClientProperties.RateLimit rateLimit = getRateLimitConfig();
        return rateLimit == null ? null : rateLimit.getApi();
    }

    /**
     * 获取登录限流配置
     *
     * @return 配置，未启用或未初始化返回 空
     */
    private AuthClientProperties.RateLimit.LoginLimit getLoginLimitConfig() {
        AuthClientProperties.RateLimit rateLimit = getRateLimitConfig();
        return rateLimit == null ? null : rateLimit.getLogin();
    }

    /**
     * 获取限流总配置
     *
     * @return 总配置，未启用返回 空
     */
    private AuthClientProperties.RateLimit getRateLimitConfig() {
        AuthClientProperties properties = OauthClientRuntimeContext.getAuthClientProperties();
        if (properties == null) {
            return null;
        }
        AuthClientProperties.RateLimit rateLimit = properties.getRateLimit();
        return rateLimit != null && rateLimit.isEnable() ? rateLimit : null;
    }
}