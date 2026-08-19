package com.chua.starter.ai.support.service.impl;

import com.chua.common.support.utils.StringUtils;
import com.chua.starter.ai.support.service.AiAccessGuardService;
import com.chua.starter.ai.support.service.SysAiAppKeyService;
import com.chua.starter.ai.support.service.SysAiGlobalSettingService;
import com.chua.starter.ai.support.service.SysAiTokenService;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * AI 访问守卫服务实现。
 *
 * @author CH
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AiAccessGuardServiceImpl implements AiAccessGuardService {

    /** Bearer_prefix */
    private static final String BEARER_PREFIX = "Bearer ";

    /**
     * app_key_enabled 开关缓存，5s TTL — 避免每次请求都查询远程 DB/Redis
     */
    private static final Cache<String, Boolean> APP_KEY_ENABLED_CACHE = Caffeine.newBuilder()
            .expireAfterWrite(5, TimeUnit.SECONDS)
            .maximumSize(10)
            .build();

    /**
     * Bearer token 校验缓存，2s TTL — 尽量缩短"禁用/删除 Key 后僵尸窗口"，
     * 同时避免每次请求都查询远端 DB/Redis
     */
    private static final Cache<String, Boolean> BEARER_TOKEN_CACHE = Caffeine.newBuilder()
            .expireAfterWrite(2, TimeUnit.SECONDS)
            .maximumSize(1000)
            .build();

    /** SYSAI全局设置服务 */
    private final SysAiGlobalSettingService sysAiGlobalSettingService;
    /** SYSAIAPP密钥服务 */
    private final SysAiAppKeyService sysAiAppKeyService;
    /** SYSAI令牌服务 */
    private final SysAiTokenService sysAiTokenService;

    @Override
    public boolean isAppKeyEnabled() {
        return APP_KEY_ENABLED_CACHE.get("app_key_enabled", k ->
                sysAiGlobalSettingService.getBoolean("runtime", "app_key_enabled", true));
    }

    @Override
    public boolean verifyAppKey(String appKey, String appSecret, String clientIp) {
        if (!isAppKeyEnabled()) {
            return true;
        }
        boolean passed = sysAiAppKeyService.verify(appKey, appSecret, clientIp);
        if (passed) {
            sysAiAppKeyService.updateUseInfoByAppKey(appKey);
            if (!isGroupRateLimited(appKey)) {
                return false;
            }
        }
        return passed;
    }

    @Override
    public boolean verifyBearerToken(String authorizationHeader, String clientIp) {
        // 与 verifyAppKey 保持一致：app_key_enabled 关闭时免鉴权（仅当管理员显式关闭）
        if (!isAppKeyEnabled()) {
            return true;
        }
        if (StringUtils.isBlank(authorizationHeader) || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            return false;
        }
        String token = authorizationHeader.substring(BEARER_PREFIX.length()).trim();
        if (StringUtils.isBlank(token)) {
            return false;
        }
        String cacheKey = token + "::" + (clientIp != null ? clientIp : "");
        boolean passed = BEARER_TOKEN_CACHE.get(cacheKey, k ->
                sysAiAppKeyService.verifyAppKeyOnly(token, clientIp));
        if (passed) {
            sysAiAppKeyService.updateUseInfoByAppKey(token);
            // 限流检查有副作用（更新 rateCount），不并入 Caffeine 缓存
            if (!isGroupRateLimited(token)) {
                return false;
            }
        }
        return passed;
    }

    /**
     * 按 AppKey 绑定分组的 Token 限流检查。
     *
     * @param appKey AppKey
     * @return 未触发限流返回 true
     */
    private boolean isGroupRateLimited(String appKey) {
        try {
            Integer groupId = sysAiAppKeyService.getGroupIdByAppKey(appKey);
            if (groupId == null) {
                return true;
            }
            return sysAiTokenService.checkRateLimitByGroup(groupId);
        } catch (Exception e) {
            log.warn("[AiAccessGuard] 分组限流检查失败，放行: {}", e.getMessage());
            return true;
        }
    }
}
