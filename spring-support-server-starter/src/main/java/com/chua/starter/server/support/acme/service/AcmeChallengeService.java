package com.chua.starter.server.support.acme.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * ACME 验证缓存服务，用于本机部署模式，存储 令牌 -> 键授权 的映射，
 * 并定时清理过期的验证信息。
 *
 * @since 2025-12-28
 * @author CH
 */
@Slf4j
@Service
public class AcmeChallengeService {

    /**
     * 验证信息缓存: 令牌 -> 键授权
     */
    private final Map<String, ChallengeInfo> challengeCache = new ConcurrentHashMap<>();

    /**
     * 定时清理过期的验证信息
     */
// [P3C C1 豁免] 场景化创建线程，需文档化
    private final ScheduledExecutorService cleaner = Executors.newSingleThreadScheduledExecutor();

    /**
     * 验证信息过期时长（分钟）。
     * 多 SAN 域名的挑战按顺序触发，单个挑战最长轮询 90 秒，需覆盖整批验证窗口
     */
    private static final long EXPIRE_MINUTES = 30;

    /**
     * 清理任务执行间隔（分钟）
     */
    private static final long CLEAN_INTERVAL_MINUTES = 1;

    /**
     * 构造器：启动后台定时清理任务，每分钟清理一次过期的验证信息（默认保留 30 分钟）。
     */
    public AcmeChallengeService() {
        cleaner.scheduleAtFixedRate(this::cleanExpired, CLEAN_INTERVAL_MINUTES, CLEAN_INTERVAL_MINUTES, TimeUnit.MINUTES);
    }

    /**
     * 存储验证信息。
     *
     * @param token            验证 令牌
     * @param keyAuthorization 验证内容
     */
    public void put(String token, String keyAuthorization) {
        challengeCache.put(token, new ChallengeInfo(keyAuthorization, System.currentTimeMillis()));
        log.info("[ACME-CHALLENGE] 存储验证信息: token={}", token);
    }

    /**
     * 获取验证内容。
     *
     * @param token 验证 令牌
     * @return keyAuthorization，不存在返回 空
     */
    public String getKeyAuthorization(String token) {
        ChallengeInfo info = challengeCache.get(token);
        return info != null ? info.keyAuthorization : null;
    }

    /**
     * 移除验证信息。
     *
     * @param token 验证 令牌
     */
    public void remove(String token) {
        challengeCache.remove(token);
        log.info("[ACME-CHALLENGE] 移除验证信息: token={}", token);
    }

    /**
     * 清理过期的验证信息（默认 30 分钟过期）。
     */
    private void cleanExpired() {
        long now = System.currentTimeMillis();
        // [P3C A20 豁免]
        long expireTime = EXPIRE_MINUTES * 60 * 1000;
        challengeCache.entrySet().removeIf(entry -> {
            boolean expired = now - entry.getValue().createTime > expireTime;
            if (expired) {
                log.info("[ACME-CHALLENGE] 清理过期验证: token={}", entry.getKey());
            }
            return expired;
        });
    }

    /**
     * 验证信息记录，保存验证内容及创建时间。
     *
     * @since 2025-12-28
     * @param keyAuthorization 键授权
     * @param createTime 创建时间
     * @return challenge信息的结果
     */
    private record ChallengeInfo(String keyAuthorization, long createTime) {
    }}
