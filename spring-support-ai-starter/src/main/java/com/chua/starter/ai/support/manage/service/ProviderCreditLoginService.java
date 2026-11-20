package com.chua.starter.ai.support.manage.service;

import com.chua.common.support.browser.BrowserPage;
import com.chua.common.support.browser.cdp.Cdp;
import com.chua.starter.account.channel.service.AccountChannelService;
import com.chua.starter.account.channel.store.SysAccountChannel;
import com.chua.starter.datasearch.credit.spi.CreditQueryProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 供应商余额「账号密码登录」执行器。
 *
 * <p>查询类型 {@code extra.credit_kind = account-login} 时，余额令牌不再手工填写，
 * 而是由本服务用内嵌浏览器（CDP 引擎，common 层 {@link Cdp}）打开上游登录页、
 * 自动填入账号密码并提交，再按余额 SPI 的「账号登录」钩子截获会话令牌：</p>
 * <ul>
 *   <li>{@link CreditQueryProvider#loginPageUrl(String)}：登录页地址；</li>
 *   <li>{@link CreditQueryProvider#loginCaptureUrlPattern()}：令牌交换接口的地址特征，
 *       命中的响应体里按 {@link CreditQueryProvider#loginTokenJsonPath()} 下钻取令牌；</li>
 *   <li>{@link CreditQueryProvider#loginCookieName()}：登录成功后会话 Cookie 的键名；</li>
 *   <li>{@link CreditQueryProvider#loginStorageKey()}：令牌所在的 localStorage 键名。</li>
 * </ul>
 *
 * <p>截获到的令牌回填 {@code extra.credit_key}（与 SPI 契约一致），余额查询、面板、
 * 快照等既有链路零改动复用。账号密码来自 {@code extra.credit_username / credit_password}。</p>
 *
 * <p>实现说明：令牌交换响应的截获在页面上下文完成——注入的初始化脚本覆写
 * {@code fetch} / {@code XMLHttpRequest}，把命中地址特征的响应体暂存到
 * {@code window.__chuaCreditCaptures} 与 sessionStorage，Java 侧轮询读取；
 * 表单填写用原生 value setter 派发 input/change 事件，兼容 React / Vue 受控输入。</p>
 *
 * @author CH
 * @since 4.0.0.55
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProviderCreditLoginService {

    /**
     * 查询类型标识（extra.credit_kind）：账号密码登录
     */
    public static final String KIND = "account-login";

    /**
     * 页面上暂存截获响应的变量名（window 属性 + sessionStorage 键同名）
     */
    private static final String CAPTURE_STORE = "__chuaCreditCaptures";

    /**
     * 等待登录表单出现的时间上限（毫秒）
     */
    private static final long FORM_WAIT_MILLIS = 20_000L;

    /**
     * 等待令牌截获的时间上限（毫秒）：覆盖慢速登录跳转与令牌交换
     */
    private static final long TOKEN_WAIT_MILLIS = 60_000L;

    /**
     * 轮询间隔（毫秒）
     */
    private static final long POLL_INTERVAL_MILLIS = 800L;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 渠道服务：仅用 updateExtra 回填截获到的令牌（不触碰渠道表其它字段）
     */
    private final AccountChannelService accountChannelService;

    /**
     * 登录结果。
     *
     * @param ok       是否成功截获令牌
     * @param token    截获的会话令牌（成功时有值）
     * @param message  结果说明（失败时为原因）
     * @param loginUrl 实际打开的登录页地址
     */
    public record LoginOutcome(boolean ok, String token, String message, String loginUrl) {
    }

    /**
     * 执行一次自动登录并截获会话令牌。
     *
     * <p>成功时把令牌回填 {@code extra.credit_key}（persist=true 时落库）；
     * 失败不抛异常，返回带原因的 {@link LoginOutcome}，由调用方决定提示方式。</p>
     *
     * @param entity  供应商
     * @param spi     余额 SPI 实现（提供登录页与截获钩子；null 且未显式配登录地址时失败）
     * @param persist 截获成功后是否把令牌落库（实时查询链路 true，试查 false）
     * @return 登录结果
     */
    public LoginOutcome login(SysAccountChannel entity, CreditQueryProvider spi, boolean persist) {
        Map<String, Object> extra = entity.getExtra() == null ? Map.of() : entity.getExtra();
        String username = text(extra.get("credit_username"));
        String password = text(extra.get("credit_password"));
        if (username.isEmpty() || password.isEmpty()) {
            return new LoginOutcome(false, null, "未配置登录账号或密码", null);
        }
        String explicitUrl = text(extra.get("credit_login_url"));
        String loginUrl = !explicitUrl.isEmpty() ? explicitUrl
                : spi == null ? null : spi.loginPageUrl(entity.getBaseUrl());
        if (loginUrl == null || loginUrl.isBlank()) {
            return new LoginOutcome(false, null, "该供应商没有可用的登录页（未收录登录入口）", null);
        }
        if (spi == null) {
            return new LoginOutcome(false, null, "该供应商未配置可截获令牌的登录实现", loginUrl);
        }
        try (BrowserPage page = Cdp.open(loginUrl)) {
            String capturePattern = spi.loginCaptureUrlPattern();
            if (capturePattern != null && !capturePattern.isBlank()) {
                page.addInitScript(captureScript(capturePattern));
            }
            String formError = fillLoginForm(page, username, password);
            if (formError != null) {
                return new LoginOutcome(false, null, formError, loginUrl);
            }
            String token = awaitToken(page, spi, capturePattern);
            if (token == null || token.isBlank()) {
                return new LoginOutcome(false, null,
                        "登录后未截获会话令牌（该站点可能带验证码 / 滑块，或登录失败）", loginUrl);
            }
            if (persist) {
                persistToken(entity, extra, token);
            }
            log.info("[credit-login] 供应商 {} 自动登录成功，令牌 {} 已{}回填",
                    entity.getProviderId(), mask(token), persist ? "" : "未");
            return new LoginOutcome(true, token, "登录成功", loginUrl);
        } catch (RuntimeException e) {
            log.warn("[credit-login] 供应商 {} 自动登录失败: {}", entity.getProviderId(), e.getMessage());
            return new LoginOutcome(false, null, "自动登录执行失败：" + e.getMessage(), loginUrl);
        }
    }

    /**
     * 回填截获到的令牌：写 {@code extra.credit_key} 并落库。
     *
     * <p>与 {@code promoteCreditKey} 同一通道（{@link AccountChannelService#updateExtra}），
     * 余额查询、面板、快照等既有链路零改动读到新令牌。</p>
     *
     * @param entity 供应商
     * @param extra  当前扩展配置
     * @param token  截获的会话令牌
     */
    private void persistToken(SysAccountChannel entity, Map<String, Object> extra, String token) {
        try {
            Map<String, Object> patch = new LinkedHashMap<>(extra);
            patch.put("credit_key", token);
            accountChannelService.updateExtra(entity.getProviderId(), patch);
            // 同步内存里的 extra（与调用方共享同一 Map），本次查询的指纹 / 快照立即可见
            extra.put("credit_key", token);
        } catch (RuntimeException e) {
            // 回填失败不影响本次查询：令牌仍随结果返回，调用方本次照常使用
            log.warn("[credit-login] 供应商 {} 令牌回填失败（本次仍按截获令牌查询）: {}",
                    entity.getProviderId(), e.getMessage());
        }
    }

    /**
     * 打开登录页并自动填写提交。
     *
     * @param page     浏览器页面
     * @param username 登录账号
     * @param password 登录密码
     * @return 失败原因；成功提交返回 null
     */
    private String fillLoginForm(BrowserPage page, String username, String password) {
        long deadline = System.currentTimeMillis() + FORM_WAIT_MILLIS;
        String state = null;
        while (System.currentTimeMillis() < deadline) {
            state = evaluateFill(page, username, password);
            if ("ok".equals(state)) {
                break;
            }
            sleep();
        }
        if (!"ok".equals(state)) {
            return "登录页 " + page.url() + " 上没有找到可填写的密码输入框";
        }
        String clicked = page.evaluate(submitScript(), false);
        if (!"clicked".equals(clicked)) {
            return "登录页 " + page.url() + " 上没有找到提交按钮";
        }
        return null;
    }

    /**
     * 在页面里定位账号 / 密码输入框并填值（不提交）。
     *
     * <p>用原生 value setter 赋值并派发 input / change 事件，React / Vue 的
     * 受控输入都能收到；账号框取密码框所在表单里它之前的第一个文本输入。</p>
     *
     * @param page     浏览器页面
     * @param username 登录账号
     * @param password 登录密码
     * @return 页面脚本返回的状态：ok / no-password-input
     */
    private String evaluateFill(BrowserPage page, String username, String password) {
        String script = "(function(){"
                + "var pw = document.querySelector('input[type=password]:not([disabled]):not([readonly])');"
                + "if (!pw) { return 'no-password-input'; }"
                + "var scope = pw.closest('form') || document;"
                + "var inputs = Array.prototype.slice.call(scope.querySelectorAll('input'));"
                + "var user = null;"
                + "for (var i = 0; i < inputs.length; i++) {"
                + "  var el = inputs[i];"
                + "  if (el === pw) { break; }"
                + "  var t = (el.type || 'text').toLowerCase();"
                + "  if (t === 'text' || t === 'email' || t === 'tel' || t === 'username' || t === '') { user = el; break; }"
                + "}"
                + "if (!user) { return 'no-username-input'; }"
                + "var setVal = function(el, v) {"
                + "  var desc = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value');"
                + "  if (desc && desc.set) { desc.set.call(el, v); } else { el.value = v; }"
                + "  el.dispatchEvent(new Event('input', {bubbles: true}));"
                + "  el.dispatchEvent(new Event('change', {bubbles: true}));"
                + "};"
                + "setVal(user, " + quote(username) + ");"
                + "setVal(pw, " + quote(password) + ");"
                + "return 'ok';"
                + "})()";
        try {
            return page.evaluate(script, false);
        } catch (RuntimeException e) {
            return "no-password-input";
        }
    }

    /**
     * 提交登录表单：优先 type=submit，其次按「登录 / log in / sign in」文本找按钮。
     *
     * @return 提交脚本
     */
    private String submitScript() {
        return "(function(){"
                + "var btn = document.querySelector('button[type=submit],input[type=submit]');"
                + "if (!btn) {"
                + "  btn = Array.prototype.slice.call(document.querySelectorAll('button,[role=button]'))"
                + "    .find(function(b) { return /登录|登陆|log\\s*in|sign\\s*in/i.test(b.textContent || ''); });"
                + "}"
                + "if (!btn) { return 'no-button'; }"
                + "btn.click();"
                + "return 'clicked';"
                + "})()";
    }

    /**
     * 轮询截获令牌：按 SPI 钩子依次尝试 Cookie / localStorage / 响应截获。
     *
     * @param page           浏览器页面
     * @param spi            余额 SPI 实现
     * @param capturePattern 令牌交换接口地址特征（可为 null）
     * @return 令牌；超时未截获返回 null
     */
    private String awaitToken(BrowserPage page, CreditQueryProvider spi, String capturePattern) {
        String cookieName = spi.loginCookieName();
        String storageKey = spi.loginStorageKey();
        String tokenPath = spi.loginTokenJsonPath();
        long deadline = System.currentTimeMillis() + TOKEN_WAIT_MILLIS;
        while (System.currentTimeMillis() < deadline) {
            if (cookieName != null && !cookieName.isBlank()) {
                String value = page.cookies().get(cookieName);
                if (value != null && !value.isBlank()) {
                    return value;
                }
            }
            if (storageKey != null && !storageKey.isBlank()) {
                String raw = evaluateQuietly(page,
                        "localStorage.getItem(" + quote(storageKey) + ")");
                String token = unwrapStorageToken(raw);
                if (token != null && !token.isBlank()) {
                    return token;
                }
            }
            if (capturePattern != null && !capturePattern.isBlank()) {
                String token = capturedToken(page, tokenPath);
                if (token != null && !token.isBlank()) {
                    return token;
                }
            }
            sleep();
        }
        return null;
    }

    /**
     * 从暂存区读取截获的响应体并按 JSON 路径取令牌。
     *
     * <p>暂存有两份：window 变量（同源单页内即时可读）与 sessionStorage
     * （登录跳转整页刷新后仍在）。两处都读到非空数组时逐条尝试。</p>
     *
     * @param page      浏览器页面
     * @param tokenPath 令牌 JSON 路径（null 时在响应体里递归找 access_token / token）
     * @return 令牌；没有截获到返回 null
     */
    private String capturedToken(BrowserPage page, String tokenPath) {
        String raw = evaluateQuietly(page,
                "(function(){ try { return JSON.stringify([].concat(window."
                        + CAPTURE_STORE + " || [], JSON.parse(sessionStorage.getItem('"
                        + CAPTURE_STORE + "') || '[]'))); } catch (e) { return '[]'; } })()");
        if (raw == null || raw.isBlank() || "[]".equals(raw)) {
            return null;
        }
        try {
            JsonNode rows = MAPPER.readTree(raw);
            if (!rows.isArray()) {
                return null;
            }
            for (JsonNode row : rows) {
                JsonNode body = row.get("body");
                if (body == null || body.isNull()) {
                    continue;
                }
                String token = tokenFromJson(body.asText(""), tokenPath);
                if (token != null && !token.isBlank()) {
                    return token;
                }
            }
        } catch (Exception ignored) {
            // 响应体不是 JSON 或结构不符，继续等下一轮
        }
        return null;
    }

    /**
     * 从截获的响应体文本里取令牌。
     *
     * @param body      响应体文本
     * @param tokenPath 令牌 JSON 路径（按 / 分段下钻）；null 时递归找 access_token / token
     * @return 令牌；取不到为 null
     */
    private String tokenFromJson(String body, String tokenPath) {
        try {
            JsonNode root = MAPPER.readTree(body);
            JsonNode current = root;
            if (tokenPath != null && !tokenPath.isBlank()) {
                for (String segment : tokenPath.split("/")) {
                    if (segment.isBlank()) {
                        continue;
                    }
                    current = current.get(segment);
                    if (current == null) {
                        return null;
                    }
                }
                return current.isTextual() || current.isNumber() ? current.asText() : null;
            }
            return findFirstToken(root);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 在 JSON 树里递归找第一个 access_token / token 字段（SPI 默认口径）。
     *
     * @param node JSON 节点
     * @return 令牌文本；找不到为 null
     */
    private String findFirstToken(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isObject()) {
            for (String field : List.of("access_token", "token")) {
                JsonNode hit = node.get(field);
                if (hit != null && (hit.isTextual() || hit.isNumber()) && !hit.asText().isBlank()) {
                    return hit.asText();
                }
            }
            for (JsonNode child : node) {
                String token = findFirstToken(child);
                if (token != null) {
                    return token;
                }
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                String token = findFirstToken(child);
                if (token != null) {
                    return token;
                }
            }
        }
        return null;
    }

    /**
     * 解 localStorage 暂存值：JSON 串时按 token / access_token 再解一层（SPI 契约）。
     *
     * @param raw localStorage 原值
     * @return 令牌；解不出为 null
     */
    private String unwrapStorageToken(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length() >= 2) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        if (!trimmed.startsWith("{")) {
            return trimmed;
        }
        try {
            JsonNode node = MAPPER.readTree(trimmed);
            for (String field : List.of("token", "access_token")) {
                JsonNode hit = node.get(field);
                if (hit != null && (hit.isTextual() || hit.isNumber()) && !hit.asText().isBlank()) {
                    return hit.asText();
                }
            }
        } catch (Exception ignored) {
            // 非 JSON 暂存值按原文返回
        }
        return trimmed;
    }

    /**
     * 注入页面的响应截获脚本：覆写 fetch 与 XHR，把命中地址特征的响应体暂存。
     *
     * <p>暂存双写：window 变量（同源单页即时可读）+ sessionStorage（整页跳转后仍在）。
     * 响应体只留最近 20 条，避免长会话撑爆内存。</p>
     *
     * @param pattern 令牌交换接口地址特征（正则）
     * @return 初始化脚本
     */
    private String captureScript(String pattern) {
        String regex = quote(pattern);
        return "(function(){"
                + "var PATTERN = " + regex + ";"
                + "var store = function(url, body) {"
                + "  try {"
                + "    if (!new RegExp(PATTERN).test(String(url || ''))) { return; }"
                + "    var row = {url: String(url), body: String(body), at: Date.now()};"
                + "    var list = (window." + CAPTURE_STORE + " = window." + CAPTURE_STORE + " || []);"
                + "    list.push(row);"
                + "    if (list.length > 20) { list.splice(0, list.length - 20); }"
                + "    var saved = [];"
                + "    try { saved = JSON.parse(sessionStorage.getItem('" + CAPTURE_STORE + "') || '[]'); } catch (e) {}"
                + "    saved.push(row);"
                + "    if (saved.length > 20) { saved.splice(0, saved.length - 20); }"
                + "    sessionStorage.setItem('" + CAPTURE_STORE + "', JSON.stringify(saved));"
                + "  } catch (e) {}"
                + "};"
                + "if (!window." + CAPTURE_STORE + ") {"
                + "  var origFetch = window.fetch;"
                + "  if (origFetch) {"
                + "    window.fetch = function() {"
                + "      var args = arguments;"
                + "      return origFetch.apply(this, args).then(function(res) {"
                + "        try { res.clone().text().then(function(t) { store(res.url || args[0], t); }); } catch (e) {}"
                + "        return res;"
                + "      });"
                + "    };"
                + "  }"
                + "  var OrigXhrOpen = XMLHttpRequest.prototype.open;"
                + "  XMLHttpRequest.prototype.open = function(method, url) {"
                + "    this.__chuaUrl = url;"
                + "    return OrigXhrOpen.apply(this, arguments);"
                + "  };"
                + "  var OrigXhrSend = XMLHttpRequest.prototype.send;"
                + "  XMLHttpRequest.prototype.send = function() {"
                + "    var xhr = this;"
                + "    xhr.addEventListener('load', function() {"
                + "      try { store(xhr.__chuaUrl, xhr.responseText); } catch (e) {}"
                + "    });"
                + "    return OrigXhrSend.apply(this, arguments);"
                + "  };"
                + "}"
                + "})()";
    }

    /**
     * 执行页面脚本，异常时返回 null（轮询场景里单次失败不中断）。
     *
     * @param page  浏览器页面
     * @param script 脚本
     * @return 执行结果；失败为 null
     */
    private String evaluateQuietly(BrowserPage page, String script) {
        try {
            return page.evaluate(script, false);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * 字符串转 JS 字面量（JSON 引号转义；转义失败时退化为逐字符反斜杠 u 转义）。
     *
     * @param value 原文
     * @return 可内插进脚本的双引号字面量
     */
    private String quote(String value) {
        try {
            return MAPPER.writeValueAsString(value == null ? "" : value);
        } catch (Exception e) {
            StringBuilder escaped = new StringBuilder("\"");
            for (char c : (value == null ? "" : value).toCharArray()) {
                escaped.append(String.format("\\u%04x", (int) c));
            }
            return escaped.append('"').toString();
        }
    }

    /**
     * 令牌掩码：保留前 6 位 + 后 4 位。
     *
     * @param token 令牌
     * @return 掩码文本
     */
    private String mask(String token) {
        if (token == null || token.isBlank()) {
            return "";
        }
        String trimmed = token.trim();
        if (trimmed.length() <= 10) {
            return trimmed.charAt(0) + "****";
        }
        return trimmed.substring(0, 6) + "****" + trimmed.substring(trimmed.length() - 4);
    }

    /**
     * 读扩展配置里的字符串值。
     *
     * @param value 原值
     * @return 非空字符串；空为 ""
     */
    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    /**
     * 轮询等待
     */
    private void sleep() {
        try {
            Thread.sleep(POLL_INTERVAL_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("登录轮询被中断", e);
        }
    }
}
