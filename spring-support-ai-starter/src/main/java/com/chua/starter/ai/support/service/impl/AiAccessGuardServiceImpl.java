package com.chua.starter.ai.support.service.impl;

import com.chua.common.support.utils.StringUtils;
import com.chua.starter.ai.support.service.AiAccessGuardService;
import com.chua.starter.ai.support.service.SysAiAppKeyService;
import com.chua.starter.ai.support.service.SysAiGlobalSettingService;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * AI 访问守卫服务实现。
 *
 * @author CH
 */
@Service
@RequiredArgsConstructor
public class AiAccessGuardServiceImpl implements AiAccessGuardService {

    private static final String BEARER_PREFIX = "Bearer ";

    /**
     * app_key_enabled 开关缓存，5s TTL — 避免每次请求都查询远程 DB/Redis
     */
    private static final Cache<String, Boolean> APP_KEY_ENABLED_CACHE = Caffeine.newBuilder()
            .expireAfterWrite(5, TimeUnit.SECONDS)
            .maximumSize(10)
            .build();

    /**
     * Bearer token 校验缓存，30s TTL — 高频 API 调用避免重复查询远程 DB
     */
    private static final Cache<String, Boolean> BEARER_TOKEN_CACHE = Caffeine.newBuilder()
            .expireAfterWrite(30, TimeUnit.SECONDS)
            .maximumSize(1000)
            .build();

    private final SysAiGlobalSettingService sysAiGlobalSettingService;
    private final SysAiAppKeyService sysAiAppKeyService;

    @Override
    public boolean isAppKeyEnabled() {
        return APP_KEY_ENABLED_CACHE.get("app_key_enabled", k ->
                sysAiGlobalSettingService.getBoolean("runtime", "app_key_enabled", false));
    }

    @Override
    public boolean verifyAppKey(String appKey, String appSecret, String clientIp) {
        if (!isAppKeyEnabled()) {
            return true;
        }
        boolean passed = sysAiAppKeyService.verify(appKey, appSecret, clientIp);
        if (passed) {
            sysAiAppKeyService.updateUseInfoByAppKey(appKey);
        }
        return passed;
    }

    @Override
    public boolean verifyBearerToken(String authorizationHeader, String clientIp) {
        if (StringUtils.isBlank(authorizationHeader) || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            return false;
        }
        String token = authorizationHeader.substring(BEARER_PREFIX.length()).trim();
        if (StringUtils.isBlank(token)) {
            return false;
        }
        String cacheKey = token + "::" + (clientIp != null ? clientIp : "");
        return BEARER_TOKEN_CACHE.get(cacheKey, k -> {
            boolean passed = sysAiAppKeyService.verify(token, token, clientIp);
            if (passed) {
                sysAiAppKeyService.updateUseInfoByAppKey(token);
            }
            return passed;
        });
    }
}
