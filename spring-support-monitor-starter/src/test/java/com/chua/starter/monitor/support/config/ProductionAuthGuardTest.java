package com.chua.starter.monitor.support.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 生产鉴权守卫门禁。
 *
 * <p>守卫一旦装配就会在启动期抛异常，这意味着它的判定逻辑<b>不能</b>只靠
 * 「把服务起崩一次」来验证——那样既慢又无法覆盖边界。本类把判定拆成纯函数后
 * 逐条钉死，其中最关键的一条是 {@code *} 与 {@code **} 的语义区别：
 * {@code /ms/*} 只匹配一层，<b>不</b>覆盖 {@code /ms/monitor/server}。
 * 若把两者混同，守卫会给出「已在拦截」的假安全感，而实际是漏放。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@DisplayName("生产鉴权守卫")
class ProductionAuthGuardTest {

    private static final List<String> MS = List.of("/ms");

    @Test
    @DisplayName("鉴权开启且未放行 /ms 时判定为无绕过")
    void secureConfigHasNoBypass() {
        var reasons = ProductionAuthGuard.detectBypasses(
                true, false, List.of("/login", "/actuator/**"), MS);
        assertTrue(reasons.isEmpty(), "安全配置不应报出绕过，实际: " + reasons);
    }

    @Test
    @DisplayName("enable=false 必须被判为绕过（这正是当前默认配置的状态）")
    void disabledAuthIsBypass() {
        var reasons = ProductionAuthGuard.detectBypasses(false, false, List.of(), MS);
        assertEquals(1, reasons.size(), "应恰好报出一条，实际: " + reasons);
        assertTrue(reasons.get(0).contains("enable=false"), "原因应点明是鉴权被关闭，实际: " + reasons);
    }

    @Test
    @DisplayName("local-admin-fallback=true 必须被判为绕过")
    void localAdminFallbackIsBypass() {
        var reasons = ProductionAuthGuard.detectBypasses(true, true, List.of(), MS);
        assertEquals(1, reasons.size(), "应恰好报出一条，实际: " + reasons);
        assertTrue(reasons.get(0).contains("local-admin-fallback"), "实际: " + reasons);
    }

    @Test
    @DisplayName("白名单 /ms/** 覆盖受保护前缀（当前默认配置的另一处绕过）")
    void wildcardWhitelistIsBypass() {
        var reasons = ProductionAuthGuard.detectBypasses(true, false, List.of("/ms/**"), MS);
        assertEquals(1, reasons.size(), "实际: " + reasons);
        assertTrue(reasons.get(0).contains("/ms/**"), "实际: " + reasons);
    }

    @Test
    @DisplayName("多条绕过要一次全部报出，不能只报第一条")
    void reportsAllBypasses() {
        var reasons = ProductionAuthGuard.detectBypasses(false, true, List.of("/ms/**", "/**"), MS);
        // enable=false + localAdminFallback + /ms/** + /**（后者同样覆盖 /ms）
        assertEquals(4, reasons.size(), "应报出全部 4 条，实际: " + reasons);
    }

    @Test
    @DisplayName("/ms/* 只匹配一层，不覆盖 /ms/monitor/server —— 不能误判为绕过")
    void singleStarDoesNotCoverSubtree() {
        assertFalse(ProductionAuthGuard.covers("/ms/*", "/ms"),
                "单星只匹配一层，不应判定为覆盖整棵子树");
        var reasons = ProductionAuthGuard.detectBypasses(true, false, List.of("/ms/*"), MS);
        assertTrue(reasons.isEmpty(), "/ms/* 不构成绕过，实际: " + reasons);
    }

    @Test
    @DisplayName("裸 /** 覆盖一切，必须判为绕过")
    void bareDoubleStarCoversEverything() {
        assertTrue(ProductionAuthGuard.covers("/**", "/ms"), "/** 应覆盖 /ms");
        var reasons = ProductionAuthGuard.detectBypasses(true, false, List.of("/**"), MS);
        assertEquals(1, reasons.size(), "实际: " + reasons);
    }

    @Test
    @DisplayName("内置默认白名单条目不得被误判成绕过（否则守卫会在正常生产配置上误报）")
    void builtinWhitelistEntriesAreSafe() {
        var builtin = List.of("/**/captcha", "/**/login", "/**/oauth", "/**/doLogin",
                "/**/logout", "/login", "/logout", "/doc.html", "/actuator", "/actuator/**",
                "/report", "/webjars/**", "/markdown/**", "/static/**", "/v1/file/**",
                "/**/*.html", "/v3/api-docs/**", "/api/system/info");
        for (String entry : builtin) {
            assertFalse(ProductionAuthGuard.covers(entry, "/ms"),
                    "内置默认白名单 " + entry + " 不应覆盖 /ms，否则守卫会在正常配置上误报");
        }
    }

    @Test
    @DisplayName("空与空白条目被忽略，不产生误报")
    void blankEntriesIgnored() {
        var reasons = ProductionAuthGuard.detectBypasses(true, false,
                java.util.Arrays.asList("", "   ", null), MS);
        assertTrue(reasons.isEmpty(), "空条目应被忽略，实际: " + reasons);
    }

    @Test
    @DisplayName("白名单为 null 时按无绕过处理，不抛 NPE")
    void nullWhitelistIsSafe() {
        var reasons = ProductionAuthGuard.detectBypasses(true, false, null, MS);
        assertTrue(reasons.isEmpty(), "实际: " + reasons);
    }

    @Test
    @DisplayName("covers 对 null 入参返回 false 而不抛异常")
    void coversIsNullSafe() {
        assertFalse(ProductionAuthGuard.covers(null, "/ms"));
        assertFalse(ProductionAuthGuard.covers("/ms/**", null));
    }
}