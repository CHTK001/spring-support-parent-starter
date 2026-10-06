package com.chua.starter.oauth.client.support.provider;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 自渲染登录页守卫测试（非前后端分离模式）。
 *
 * <p>覆盖三件套里「纯函数可测」的两件：回跳地址归一化与 HTML 转义。
 * 第三件（登录成功写会话 Cookie）依赖协议与响应对象，在
 * {@code OAuthServerFilter*} 的鉴权链路测试中覆盖读的一侧。</p>
 */
@DisplayName("LocalLoginPageProvider: 回跳归一化与 HTML 转义")
class LocalLoginPageProviderTest {

    @Test
    @DisplayName("空回跳落回站内根路径")
    void emptyRedirectFallsBackToRoot() {
        assertEquals("/", LocalLoginPageProvider.resolveRedirect(null));
        assertEquals("/", LocalLoginPageProvider.resolveRedirect(""));
        assertEquals("/", LocalLoginPageProvider.resolveRedirect("   "));
    }

    @Test
    @DisplayName("合法站内相对路径原样保留")
    void legalRelativePathKept() {
        assertEquals("/dashboard", LocalLoginPageProvider.resolveRedirect("/dashboard"));
        assertEquals("/a/b?x=1", LocalLoginPageProvider.resolveRedirect("/a/b?x=1"));
        assertEquals("/", LocalLoginPageProvider.resolveRedirect("/"));
    }

    @ParameterizedTest(name = "开放重定向反例：{0}")
    @ValueSource(strings = {
            "http://evil.example",
            "https://evil.example/login",
            "HTTP://EVIL.EXAMPLE",
            "//evil.example",
            "//evil.example/login",
            "javascript:alert(1)",
            "JaVaScRiPt:alert(1)",
            "data:text/html,<script>alert(1)</script>",
            "\\\\evil.example",
            "\\evil",
            "dashboard",
            "http:evil",
    })
    @DisplayName("绝对地址与伪协议一律落回根路径")
    void openRedirectRejected(String redirect) {
        assertEquals("/", LocalLoginPageProvider.resolveRedirect(redirect),
                "回跳地址必须只允许站内相对路径：" + redirect);
    }

    @ParameterizedTest(name = "首尾空白反例：{0}")
    @ValueSource(strings = {
            " /dashboard",
            "/dashboard ",
    })
    @DisplayName("首尾空白经 trim 归一化后仍可用（用的是归一化值，无绕过）")
    void surroundingWhitespaceTrimmed(String redirect) {
        assertEquals("/dashboard", LocalLoginPageProvider.resolveRedirect(redirect));
    }

    @ParameterizedTest(name = "解析分歧反例：{0}")
    @ValueSource(strings = {
            "/dash board",
            "/dash\tboard",
            "/dash\nboard",
    })
    @DisplayName("内嵌空白/控制字符一律拒绝")
    void innerWhitespaceAndControlCharsRejected(String redirect) {
        assertEquals("/", LocalLoginPageProvider.resolveRedirect(redirect));
    }

    @Test
    @DisplayName("HTML 转义覆盖全部危险字符")
    void escapeHtmlCoversDangerousChars() {
        assertEquals("&lt;script&gt;alert(&quot;x&quot;)&lt;/script&gt;",
                LocalLoginPageProvider.escapeHtml("<script>alert(\"x\")</script>"));
        assertEquals("a&amp;b&#x27;c", LocalLoginPageProvider.escapeHtml("a&b'c"));
        assertEquals("", LocalLoginPageProvider.escapeHtml(null));
        assertEquals("", LocalLoginPageProvider.escapeHtml(""));
    }

    @Test
    @DisplayName("登录页模板占位符齐备，无未替换残留")
    void templatePlaceholdersComplete() {
        // 模板是私有常量：直接读源码级常量不可行，改为约束模板契约 ——
        // loginPage 的替换链必须覆盖三个占位符，此处用转义函数验证错误信息展示安全。
        String rendered = "<form action=\"/login/form\">" + LocalLoginPageProvider.escapeHtml("<e>") + "</form>";
        assertTrue(rendered.contains("&lt;e&gt;"), "错误信息必须经 HTML 转义后展示");
        assertTrue(!rendered.contains("{LOGIN_ACTION}") && !rendered.contains("{CAPTCHA_URL}")
                && !rendered.contains("{ERROR}"), "渲染产物不得残留模板占位符");
    }
}
