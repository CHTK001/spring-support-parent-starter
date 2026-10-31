package com.chua.starter.oauth.client.support.properties;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AuthClientProperties} 内置白名单守卫测试
 *
 * <p>本 starter 被所有服务共用，内置默认白名单是「隐式生效且难以察觉」的信任边界：
 * 任何应用只要注册了同名路由就会被静默关闭鉴权。下面每条断言对应一个已被移除的
 * 历史遗留条目，防止有人在「修跨域 / 修登录」时顺手把它加回来。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@DisplayName("AuthClientProperties: 内置默认白名单守卫")
class AuthClientPropertiesWhitelistTest {

    /**
     * 免鉴权列表中<b>绝不允许</b>出现的路径
     *
     * <p>它们要么泄露凭据，要么泄露用户身份。写进默认值等于对所有使用方开放。</p>
     */
    private static final List<String> FORBIDDEN = List.of(
            // 供应商凭据：读密钥的路径绝不能免鉴权
            "/api/providers/**/credentials",
            // 当前登录用户信息：未登录也应 401
            "/api/auth/user",
            "/api/settings/client"
    );

    @Test
    @DisplayName("凭据与用户信息路径不得出现在内置白名单中")
    void forbiddenPathsAreNotWhitelisted() {
        List<String> effective = new AuthClientProperties().getEffectiveWhitelist();
        for (String path : FORBIDDEN) {
            assertFalse(effective.contains(path),
                    path + " 不得作为内置默认白名单；确需放行的应用请在自己的 plugin.oauth.whitelist 中显式声明");
        }
    }

    @Test
    @DisplayName("useDefaultWhitelist=false 时内置清单完全不参与")
    void builtinListCanBeDisabled() {
        AuthClientProperties properties = new AuthClientProperties();
        properties.setUseDefaultWhitelist(false);
        properties.setWhitelist(List.of("/login"));

        assertTrue(properties.getEffectiveWhitelist().contains("/login"));
        assertFalse(properties.getEffectiveWhitelist().contains("/actuator/**"),
                "关闭内置清单后不应再混入任何内置项");
    }

    @Test
    @DisplayName("登录链路永不被关闭：useDefaultWhitelist=false 也照样放行")
    void loginPathsSurviveDefaultWhitelistDisabled() {
        AuthClientProperties properties = new AuthClientProperties();
        properties.setUseDefaultWhitelist(false);
        properties.setWhitelist(List.of("/actuator/health"));

        List<String> effective = properties.getEffectiveWhitelist();
        org.springframework.util.AntPathMatcher matcher = new org.springframework.util.AntPathMatcher();
        for (String uri : List.of("/login", "/v2/auth/login", "/v2/user/login", "/v2/auth/captcha")) {
            assertTrue(effective.stream().anyMatch(p -> matcher.match(p, uri)),
                    uri + " 必须匿名可访问：登录接口要求 token 会形成死循环（登录 403 自锁）");
        }
    }

    @Test
    @DisplayName("登录链路永不被关闭：yaml 清单为空也照样放行")
    void loginPathsSurviveEmptyYamlWhitelist() {
        AuthClientProperties properties = new AuthClientProperties();
        properties.setWhitelist(List.of());

        org.springframework.util.AntPathMatcher matcher = new org.springframework.util.AntPathMatcher();
        List<String> effective = properties.getEffectiveWhitelist();
        assertTrue(effective.stream().anyMatch(p -> matcher.match(p, "/v2/auth/login")),
                "登录接口是鉴权组件自身的必备豁免，任何配置都无权移除");
    }

    @Test
    @DisplayName("登录链路所需路径仍然放行，避免把功能改坏")
    void loginPathsStillAllowed() {
        List<String> effective = new AuthClientProperties().getEffectiveWhitelist();
        for (String path : List.of("/**/login", "/**/login/**", "/**/logout", "/**/captcha",
                "/v1/sys/setting", "/actuator/**", "/static/**")) {
            assertTrue(effective.contains(path), "登录/静态链路路径 " + path + " 不应被移除");
        }
    }

    @Test
    @DisplayName("自渲染表单提交端点被白名单覆盖（Ant 深层语义守卫）")
    void loginFormActionIsWhitelisted() {
        // Ant 下 /**/login 不匹配 /login/form：若有人删掉深层条目，表单提交会被鉴权
        // 拦回登录页（登录页自锁）。用 Spring 的 AntPathMatcher 做真实判定，不靠肉眼。
        org.springframework.util.AntPathMatcher matcher = new org.springframework.util.AntPathMatcher();
        List<String> effective = new AuthClientProperties().getEffectiveWhitelist();
        String formAction = new AuthClientProperties().getLoginFormAction();
        assertTrue(effective.stream().anyMatch(p -> matcher.match(p, formAction)),
                "表单提交端点 " + formAction + " 必须命中至少一条白名单，否则登录页自锁");
        assertTrue(matcher.match("/**/login/**", "/v1/users/login/form"),
                "对照：深层条目应覆盖带前缀的表单路径");
    }
}