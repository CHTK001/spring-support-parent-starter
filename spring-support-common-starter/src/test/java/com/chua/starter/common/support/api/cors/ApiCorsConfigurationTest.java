package com.chua.starter.common.support.api.cors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.filter.CorsFilter;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CORS 白名单回归测试（2026-10-01 高危缺陷的防护网）。
 *
 * <p><b>为什么必须在过滤器层测、而不是断言配置对象。</b>
 * 原来的缺陷是 {@code addAllowedOriginPattern("*") + setAllowCredentials(true)}：
 * 反射本身写在配置里是对的写法，错的是 pattern 的值。任何「断言白名单集合里有这些 origin」
 * 的测试都会通过 —— 它测的是意图，不是行为。真正要钉住的是
 * <b>请求头进、响应头出</b>这条链路，所以这里用 spring-test 的
 * {@code MockHttpServletRequest/MockHttpServletResponse} 真跑一遍 {@link CorsFilter}。</p>
 *
 * <p><b>不用 Mockito。</b>AGENTS.md §3 禁用 mockito / @MockBean / mockStatic（含 pom 依赖），
 * 而 spring-test 的 Mock* 类是 Spring 自己的测试夹具，不属于 mock 框架，不违反该约束。</p>
 *
 * <p><b>敏感性的对照。</b>本测试若写成「只断言白名单内容」，把 allowedOrigins 改回 "*"
 * 依然会绿 —— 那种测试是假网。这里断言的是响应头，所以把配置改回 "*" 必然变红。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@DisplayName("CORS 白名单回归测试")
class ApiCorsConfigurationTest {

    /**
     * 预检请求的路径，取一个真实存在的接口路径。
     */
    private static final String PREFLIGHT_PATH = "/api/v2/user/page";

    /**
     * 构造一份带白名单的配置。
     *
     * @param origins 白名单
     * @return 配置属性
     */
    private ApiCorsProperties propertiesOf(String... origins) {
        ApiCorsProperties properties = new ApiCorsProperties();
        properties.setEnable(true);
        Set<String> set = new LinkedHashSet<>();
        for (String origin : origins) {
            set.add(origin);
        }
        properties.setAllowedOrigins(set);
        return properties;
    }

    /**
     * 跑一次真实预检，返回响应。
     *
     * @param properties 配置属性
     * @param origin     请求来源
     * @return 响应
     */
    private MockHttpServletResponse preflight(ApiCorsProperties properties, String origin)
            throws jakarta.servlet.ServletException, java.io.IOException {
        CorsFilter filter = new ApiCorsConfiguration(properties).corsFilterFilterRegistrationBean().getFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", PREFLIGHT_PATH);
        request.addHeader("Origin", origin);
        request.addHeader("Access-Control-Request-Method", "GET");
        request.addHeader("Access-Control-Request-Headers", "authorization,content-type");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    /**
     * 高危缺陷的直接防护：任意第三方来源不得被放行。
     *
     * <p>这一条在改成白名单之前是红的 —— {@code "*"} 会把 {@code https://evil.example.com}
     * 原样反射回 {@code Access-Control-Allow-Origin}，任意网站可读接口响应。</p>
     */
    @Test
    @DisplayName("白名单外的第三方来源不被放行")
    void 陌生来源不放行() throws jakarta.servlet.ServletException, java.io.IOException {
        MockHttpServletResponse response = preflight(
                propertiesOf("http://localhost:8096"), "https://evil.example.com");
        assertNull(response.getHeader("Access-Control-Allow-Origin"),
                "白名单外的 Origin 不得出现在 Access-Control-Allow-Origin —— " +
                        "这是任意网站可携带凭证读取接口响应的直接成因");
    }

    /**
     * {@code Origin: null} 同样不得放行。
     *
     * <p>实测（2026-10-01）原实现会把 {@code null} 也原样反射回去，而带 {@code null} 来源的
     * 请求可以来自本地文件、沙箱 iframe 等非浏览器站点。</p>
     */
    @Test
    @DisplayName("Origin 为 null 时不放行")
    void null来源不放行() throws jakarta.servlet.ServletException, java.io.IOException {
        MockHttpServletResponse response = preflight(
                propertiesOf("http://localhost:8096"), "null");
        assertNull(response.getHeader("Access-Control-Allow-Origin"),
                "Origin: null 不得被放行");
    }

    /**
     * 白名单内的来源必须放行，否则功能会被误伤。
     */
    @Test
    @DisplayName("白名单内的来源正常放行")
    void 白名单内来源放行() throws jakarta.servlet.ServletException, java.io.IOException {
        String origin = "http://localhost:8096";
        MockHttpServletResponse response = preflight(propertiesOf(origin), origin);
        assertEquals(origin, response.getHeader("Access-Control-Allow-Origin"));
        // 预检响应只回显本次请求的方法（Access-Control-Request-Method: GET），
        // 不是把配置里的 "*" 原样吐出来 —— 断言写成 "*" 会得到 expected <*> but was: <GET>。
        assertEquals("GET", response.getHeader("Access-Control-Allow-Methods"));
    }

    /**
     * 端口通配必须仍然可用（198 上前端按 IP 段访问，不能靠枚举 IP）。
     */
    @Test
    @DisplayName("端口通配白名单内的来源正常放行")
    void 端口通配放行() throws jakarta.servlet.ServletException, java.io.IOException {
        String origin = "http://192.168.50.198:8096";
        MockHttpServletResponse response =
                preflight(propertiesOf("http://192.168.50.*:8096"), origin);
        assertEquals(origin, response.getHeader("Access-Control-Allow-Origin"),
                "端口通配是本仓既有约定（按内网网段放行），收紧白名单后不能把它一起收紧掉");
    }

    /**
     * 白名单为空时不得放行任何跨域来源。
     *
     * <p>这条保证「忘配 allowed-origins」退化为「跨域不可用」而不是「跨域全开」——
     * 后者就是原缺陷。</p>
     */
    @Test
    @DisplayName("白名单为空时不放行任何跨域来源")
    void 白名单为空不放行() throws jakarta.servlet.ServletException, java.io.IOException {
        MockHttpServletResponse response = preflight(propertiesOf(), "http://localhost:8096");
        assertNull(response.getHeader("Access-Control-Allow-Origin"),
                "白名单为空必须是「不放行」，不能回退到「全放行」");
    }

    /**
     * 不带 Origin 的同源请求不受跨域配置影响。
     *
     * <p>防的是「把白名单写空导致连正常调用也受影响」这种过度收紧。</p>
     */
    @Test
    @DisplayName("不带 Origin 的请求不被跨域过滤器拒绝")
    void 同源请求不受影响() throws jakarta.servlet.ServletException, java.io.IOException {
        CorsFilter filter =
                new ApiCorsConfiguration(propertiesOf()).corsFilterFilterRegistrationBean().getFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", PREFLIGHT_PATH);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        assertEquals(200, response.getStatus(), "无 Origin 的请求不应被 CORS 过滤器拒绝");
        assertNull(response.getHeader("Access-Control-Allow-Origin"));
    }

    /**
     * 响应加密依赖的四个头必须在 exposedHeaders 里。
     *
     * <p>前端 {@code packages/core/src/httpCrypto.ts} 读 {@code x-response-version} /
     * {@code x-ec} / {@code x-ck} / {@code x-ot}。原来为了省事写了
     * {@code addExposedHeader("*")}，收紧 CORS 时若漏掉任何一个，
     * 浏览器会拦掉响应头、而加密机制**静默失效**（不报错，只是读不到）。</p>
     */
    @Test
    @DisplayName("响应加密依赖的头必须在 exposedHeaders 中")
    void 加密响应头已暴露() throws jakarta.servlet.ServletException, java.io.IOException {
        String origin = "http://localhost:8096";
        MockHttpServletResponse response = preflight(propertiesOf(origin), origin);
        String expose = response.getHeader("Access-Control-Expose-Headers");
        assertNotNull(expose, "必须显式声明 exposedHeaders");
        for (String header : new String[]{"x-response-version", "x-ec", "x-ck", "x-ot"}) {
            assertTrue(expose.contains(header),
                    "exposedHeaders 缺少 " + header + " —— 前端 httpCrypto.ts 依赖它，"
                            + "缺失不会报错，只会让响应加密/密钥协商静默失效");
        }
    }

    /**
     * 携带凭证必须显式开启且默认关闭。
     */
    @Test
    @DisplayName("allowCredentials 默认关闭且未配置时不放行凭证")
    void 凭证默认关闭() throws jakarta.servlet.ServletException, java.io.IOException {
        ApiCorsProperties properties = propertiesOf("http://localhost:8096");
        assertFalse(properties.isAllowCredentials(), "默认必须关闭");
        String origin = "http://localhost:8096";
        MockHttpServletResponse response = preflight(properties, origin);
        assertNull(response.getHeader("Access-Control-Allow-Credentials"),
                "未开启时不发 Access-Control-Allow-Credentials");
    }

    /**
     * 安全响应头过滤器的注册顺序必须在 CORS 之后。
     *
     * <p>顺序反了会让本过滤器先跑、而 CORS 后写，导致安全头被覆盖不全。</p>
     */
    @Test
    @DisplayName("安全响应头过滤器比 CORS 晚执行且覆盖全部路径")
    void 安全头过滤器顺序() {
        ApiSecurityHeaderFilter.ApiSecurityHeaderProperties security = new ApiSecurityHeaderFilter.ApiSecurityHeaderProperties();
        FilterRegistrationBean<ApiSecurityHeaderFilter> registration =
                new ApiCorsConfiguration(propertiesOf()).apiSecurityHeaderFilterRegistrationBean(security);
        assertEquals(Integer.MIN_VALUE + 10, registration.getOrder(),
                "必须是 HIGHEST_PRECEDENCE + 10，让 CORS 先写完 Access-Control-* 头");
        // getUrlPatterns() 返回 Set（Servlet 规范如此），不是 List；用 containsAll 断言内容
        assertEquals(1, registration.getUrlPatterns().size());
        assertTrue(registration.getUrlPatterns().contains("/*"),
                "必须覆盖全部路径，否则静态资源与 404 响应拿不到安全头");
        assertNotNull(registration.getFilter());
        assertNotNull(security);
    }
}