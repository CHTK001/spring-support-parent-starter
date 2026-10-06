package com.chua.starter.oauth.client.support.filter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link OAuthServerFilter} 跨域白名单回归测试
 *
 * <p>守护的修复：原先 {@code applyCorsHeaders} 无条件把请求的 {@code Origin}
 * 回显到 {@code Access-Control-Allow-Origin}。当时因为没有同时下发
 * {@code Access-Control-Allow-Credentials} 而未直接可利用，但这是一颗定时炸弹 ——
 * 只要后续任何一处为了「修跨域报错」补上该头，任意站点即可读取用户私有桶内容。
 * 跨域白名单这类逻辑一旦被改回反射，测试必须立刻失败。</p>
 */
@DisplayName("OAuthServerFilter: 跨域来源白名单")
class OAuthServerFilterCorsTest {

    @Test
    @DisplayName("P2 核心反例：未配置白名单时不得放行任意来源（fail-closed）")
    void noConfigurationAllowsNothing() {
        OAuthServerFilter filter = new OAuthServerFilter();
        // 默认空集合 → 一个跨域头都不下发
        assertFalse(filter.isOriginAllowed("https://evil.tld"));
        assertFalse(filter.isOriginAllowed("http://localhost:5173"));
        assertFalse(filter.isOriginAllowed("*"));
    }

    @Test
    @DisplayName("仅白名单内的来源被放行")
    void allowlistIsRespected() {
        OAuthServerFilter filter = new OAuthServerFilter();
        filter.setCorsAllowedOrigins(Set.of("https://app.example.com", "http://localhost:5173"));

        assertTrue(filter.isOriginAllowed("https://app.example.com"));
        assertTrue(filter.isOriginAllowed("http://localhost:5173"));
        assertFalse(filter.isOriginAllowed("https://evil.tld"));
        assertFalse(filter.isOriginAllowed("https://app.example.com.evil.tld"));
        assertFalse(filter.isOriginAllowed("http://localhost:8080"));
    }

    @Test
    @DisplayName("归一化：尾斜杠与大小写不影响判定，避免「配了却不生效」")
    void normalizationKeepsConfigPractical() {
        OAuthServerFilter filter = new OAuthServerFilter();
        filter.setCorsAllowedOrigins(Set.of("HTTPS://App.Example.COM/"));

        assertTrue(filter.isOriginAllowed("https://app.example.com"));
        assertTrue(filter.isOriginAllowed("https://APP.example.com"));
        assertTrue(filter.isOriginAllowed("https://app.example.com/"));
    }

    @Test
    @DisplayName("* 仍可用于本地联调，但只按显式通配生效")
    void wildcardIsOptIn() {
        OAuthServerFilter filter = new OAuthServerFilter();
        filter.setCorsAllowedOrigins(Set.of("*"));
        assertTrue(filter.isOriginAllowed("https://any.tld"));

        // 空集合不等于通配：修复前「未配置 = 全放行」，现在必须收紧
        filter.setCorsAllowedOrigins(Set.of());
        assertFalse(filter.isOriginAllowed("https://any.tld"));
    }

    @Test
    @DisplayName("null / 空串入参不抛异常且不放行")
    void nullSafe() {
        OAuthServerFilter filter = new OAuthServerFilter();
        filter.setCorsAllowedOrigins(Set.of("https://app.example.com"));
        assertFalse(filter.isOriginAllowed(null));
        assertFalse(filter.isOriginAllowed(""));
        filter.setCorsAllowedOrigins(null);
        assertFalse(filter.isOriginAllowed("https://app.example.com"));
    }
}