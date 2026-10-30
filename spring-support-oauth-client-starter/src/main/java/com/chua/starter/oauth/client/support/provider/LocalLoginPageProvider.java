package com.chua.starter.oauth.client.support.provider;

import com.chua.starter.oauth.client.support.properties.AuthClientProperties;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

/**
 * 业务自渲染登录页（非前后端分离模式）。
 *
 * <p><b>为什么需要它。</b>client-starter 的登录协议（{@code POST /v1/users/login} 换
 * accessToken）早已就绪，但登录成功后没有任何地方写会话凭据 ——
 * {@code x-oauth-cookie} 在各协议里只读不写，浏览器拿着一段 JSON 文本里的令牌，
 * 后续导航请求既无请求头也无 Cookie，非分离模式的「登录一次、后续请求自动带凭据」
 * 链路是断的。本控制器补上缺的三件套：</p>
 * <ol>
 *   <li>{@code GET /login} 返回手写 HTML 表单（无模板引擎依赖）；</li>
 *   <li>登录成功后由 {@link #login} 写 {@code HttpOnly} 会话 Cookie
 *       （见 {@link UserStatisticProvider#writeSessionCookie}）；</li>
 *   <li>表单按 {@code ?redirect_url=} 回跳，回跳目标只允许同站相对路径
 *       （见 {@link #resolveRedirect}）。</li>
 * </ol>
 *
 * <p><b>安全边界。</b></p>
 * <ul>
 *   <li>回跳地址经 {@link #resolveRedirect} 归一化：只接受本站相对路径，
 *       绝对 URL（含 {@code //evil} 这种协议相对地址）一律落回 {@code /}。
 *       这是开放重定向的直接防线，对应服务端 oauth-starter 侧
 *       {@code SystemCallbackPolicy} 的同类约束。</li>
 *   <li>表单 POST 走 {@code application/x-www-form-urlencoded}；需要 CSRF 防护的
 *       部署请让业务应用在过滤器链中对 {@code POST /v1/users/login} 启用双提交校验。</li>
 *   <li>Cookie 为 {@code HttpOnly}，JS 读不到；跨站场景请在反向代理层按需补
 *       {@code Secure}（本机 HTTP 调试不得加，否则浏览器直接拒收）。</li>
 * </ul>
 *
 * @since 4.0.0.42
 */
@Slf4j
@RestController
@RequestMapping
@RequiredArgsConstructor
@ConditionalOnProperty(name = "plugin.oauth.temp.open", havingValue = "true", matchIfMissing = false)
public class LocalLoginPageProvider {

    private final AuthClientProperties authProperties;

    /**
     * 登录页 HTML 模板占位符：
     * <ul>
     *   <li>{@code {LOGIN_ACTION}} —— 表单提交地址（含 redirect_url 回跳参数）；</li>
     *   <li>{@code {CAPTCHA_URL}} —— 验证码图片地址；</li>
     *   <li>{@code {ERROR}} —— 上次失败原因（已做 HTML 转义后填入，见 {@link #escapeHtml}）。</li>
     * </ul>
     */
    private static final String LOGIN_PAGE_TEMPLATE = """
            <!DOCTYPE html>
            <html lang="zh-CN">
            <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <title>登录</title>
            <style>
            body{font-family:system-ui,-apple-system,"PingFang SC","Microsoft YaHei",sans-serif;background:#f4f6fa;margin:0;display:flex;min-height:100vh;align-items:center;justify-content:center}
            .card{background:#fff;border-radius:8px;box-shadow:0 2px 16px rgba(0,0,0,.08);padding:32px;width:320px}
            .card h1{font-size:20px;margin:0 0 20px;text-align:center}
            .card label{display:block;font-size:14px;margin:12px 0 4px;color:#333}
            .card input{width:100%;box-sizing:border-box;padding:8px 10px;font-size:14px;border:1px solid #d9d9d9;border-radius:4px}
            .card input:focus{outline:none;border-color:#1677ff}
            .captcha-row{display:flex;gap:8px}
            .captcha-row input{flex:1}
            .captcha-row img{height:36px;border:1px solid #d9d9d9;border-radius:4px;cursor:pointer}
            .error{color:#cf1322;font-size:13px;min-height:18px;margin-top:10px}
            .card button{width:100%;margin-top:12px;padding:9px 0;font-size:15px;color:#fff;background:#1677ff;border:0;border-radius:4px;cursor:pointer}
            .card button:hover{background:#0958d9}
            </style>
            </head>
            <body>
            <form class="card" method="post" action="{LOGIN_ACTION}">
            <h1>登录</h1>
            <label for="username">账号</label>
            <input id="username" name="username" autocomplete="username" required>
            <label for="password">密码</label>
            <input id="password" type="password" name="password" autocomplete="current-password" required>
            <label for="verifyCode">验证码（点击图片换一张）</label>
            <div class="captcha-row">
            <input id="verifyCode" name="verifyCode" autocomplete="off" required>
            <img id="captcha" src="{CAPTCHA_URL}" alt="验证码" onclick="this.src='{CAPTCHA_URL}&amp;t='+Date.now()">
            </div>
            <div class="error">{ERROR}</div>
            <button type="submit">登 录</button>
            </form>
            </body>
            </html>
            """;

    /**
     * 返回登录页。
     *
     * <p>路径取 {@code plugin.oauth.login-page} 的配置值（默认 {@code /login}），
     * 避免「改了配置登录页就 404」的分叉。{@code WebRequest} 对该路径精确放行，
     * 未登录的浏览器导航由 {@code HtmlAdviceResolver} 302 到这里。</p>
     *
     * @param redirect 回跳地址（仅同站相对路径有效，其余落回 {@code /}）
     * @param error 上次登录失败原因（登录接口失败重定向回来时携带）
     * @param request 当前请求（取 context-path 拼表单 action 与验证码地址）
     * @return 登录页 HTML
     */
    @GetMapping(value = "${plugin.oauth.login-page:/login}", produces = "text/html;charset=UTF-8")
    public String loginPage(
            @RequestParam(name = "redirect_url", required = false) String redirect,
            @RequestParam(name = "error", required = false) String error,
            HttpServletRequest request) {
        String contextPath = request.getContextPath();
        String target = LocalLoginPageProvider.resolveRedirect(redirect);
        // 表单直投 JSON 登录接口会 415；改为投自渲染表单端点，由它完成「验参 + 调协议 + 写 Cookie + 302」。
        String action = contextPath + authProperties.getLoginFormAction() + "?redirect_url=" + urlEncode(target);
        String page = LOGIN_PAGE_TEMPLATE
                .replace("{LOGIN_ACTION}", escapeHtml(action))
                .replace("{CAPTCHA_URL}", escapeHtml(contextPath + "/v1/users/loginCode"))
                .replace("{ERROR}", escapeHtml(error == null ? "" : error));
        return page;
    }

    /**
     * 归一化回跳地址，只允许同站相对路径。
     *
     * <p>拒绝：绝对 URL（{@code http://…}、{@code https://…}）、协议相对地址
     * （{@code //evil}）、反斜杠开头（部分浏览器按绝对地址解析 {@code \\evil}）、
     * 空白与控制字符。大小写不敏感地识别协议头，且先 trim 再判定，避免
     * 前导空格绕过 {@code startsWith} 检查的老把戏。</p>
     *
     * @param redirect 调用方传入的回跳地址，可空
     * @return 安全的站内路径；不合法时返回 {@code /}
     */
    public static String resolveRedirect(String redirect) {
        if (redirect == null) {
            return "/";
        }
        String candidate = redirect.trim();
        if (candidate.isEmpty()) {
            return "/";
        }
        // 控制字符 / 内嵌空白： Location 头注入与解析分歧的源头，直接拒绝
        for (int i = 0; i < candidate.length(); i++) {
            char c = candidate.charAt(i);
            if (c <= 0x1F || c == 0x7F || Character.isWhitespace(c)) {
                return "/";
            }
        }
        String lower = candidate.toLowerCase();
        if (lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("//")) {
            return "/";
        }
        int colon = candidate.indexOf(':');
        int slash = candidate.indexOf('/');
        int backslash = candidate.indexOf('\\');
        int query = candidate.indexOf('?');
        int hash = candidate.indexOf('#');
        int firstDelim = candidate.length();
        if (slash >= 0) {
            firstDelim = Math.min(firstDelim, slash);
        }
        if (backslash >= 0) {
            firstDelim = Math.min(firstDelim, backslash);
        }
        if (query >= 0) {
            firstDelim = Math.min(firstDelim, query);
        }
        if (hash >= 0) {
            firstDelim = Math.min(firstDelim, hash);
        }
        // 第一个路径分隔符前出现冒号 → 疑似 scheme（如 javascript:、data:），拒绝
        if (colon >= 0 && colon < firstDelim) {
            return "/";
        }
        if (candidate.startsWith("\\")) {
            return "/";
        }
        if (!candidate.startsWith("/")) {
            return "/";
        }
        if (candidate.startsWith("//")) {
            return "/";
        }
        return candidate;
    }

    /**
     * HTML 转义（登录页模板插值前必须经过它）。
     *
     * @param value 原始字符串，可空
     * @return 转义后字符串；空输入返回空串
     */
    public static String escapeHtml(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&#x27;");
                default -> out.append(c);
            }
        }
        return out.toString();
    }

    /**
     * url Encode
     * @param value 字符串
     * @return 返回值
    */
    private static String urlEncode(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
