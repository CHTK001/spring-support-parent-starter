package com.chua.starter.ai.support.service.impl;

import com.chua.starter.ai.support.entity.SysAiAppKey;
import com.chua.starter.ai.support.service.AppKeyQuotaGate;
import com.chua.starter.ai.support.service.SysAiAppKeyService;
import com.chua.starter.common.support.appkey.AppKeyAuthenticator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * appKey 认证契约实现（appKey 台账的唯一出口）。
 *
 * <p>{@code sys_ai_app_key} 属于 AI 模块，其它模块（oauth-client 的本地回退认证等）
 * 只依赖 {@link AppKeyAuthenticator} 契约，由本类翻译成一次台账查询 + 统一额度闸门判定，
 * 避免调用方各自拼 SQL 直查 AI 的表。</p>
 *
 * <p>判定口径必须复用 {@link AppKeyQuotaGate}：它同时服务网关与实例端口，
 * 在这里再写一遍会出现「网关放行、实例拒绝」这类不一致。</p>
 *
 * @author CH
 * @since 4.0.0.52
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiAppKeyAuthenticator implements AppKeyAuthenticator {

    /**
     * appKey 台账服务
     */
    private final SysAiAppKeyService appKeyService;

    @Override
    public boolean isAvailable() {
        return appKeyService != null;
    }

    @Override
    public String authenticate(String appKey, String ip) {
        if (appKey == null || appKey.isBlank()) {
            return null;
        }
        SysAiAppKey key;
        try {
            // 鉴权必须看最新库值，不能走分钟级缓存
            key = appKeyService.getByAppKeyLive(appKey);
        } catch (Exception e) {
            log.warn("[AppKey] 台账查询失败: {}", e.getMessage());
            return null;
        }
        AppKeyQuotaGate.Deny deny = AppKeyQuotaGate.check(key, ip);
        if (deny.denied()) {
            log.debug("[AppKey] 校验未通过: appKey={}, deny={}", appKey, deny);
            return null;
        }
        String userId = key.getUserId();
        return userId == null || userId.isBlank() ? appKey : userId;
    }
}
