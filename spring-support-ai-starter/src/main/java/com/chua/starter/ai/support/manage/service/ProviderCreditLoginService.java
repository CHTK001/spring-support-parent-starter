package com.chua.starter.ai.support.manage.service;

import com.chua.common.support.browser.BrowserOptions;
import com.chua.common.support.browser.BrowserPage;
import com.chua.common.support.browser.cdp.CdpBrowserEngine;
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
 * <p>查询类型 {@code extra.credit_kind = account-login} 时，余额令牌不再手工填写：
 * 页面上的「登录」按钮触发本服务，弹出内嵌浏览器窗口（CDP 引擎，common 层
 * {@link CdpBrowserEngine}，有头模式），由用户在窗口内自行输入账号密码完成上游门户
 * 登录；服务端按余额 SPI 的「账号登录」钩子截获会话令牌：</p>
 * <ul>
 *   <li>{@link CreditQueryProvider#loginPageUrl(String)}：登录页地址；</li>
 *   <li>{@link CreditQueryProvider#loginCaptureUrlPattern()}：令牌交换接口的地址特征，
 *       命中的响应体里按 {@link CreditQueryProvider#loginTokenJsonPath()} 下钻取令牌；</li>
 *   <li>{@link CreditQueryProvider#loginCookieName()}：登录成功后会话 Cookie 的键名；</li>
 *   <li>{@link CreditQueryProvider#loginStorageKey()}：令牌所在的 localStorage 键名。</li>
 * </ul>
 *
 * <p>截获到的令牌回填 {@code extra.credit_key}（与 SPI 契约一致），余额查询、面板、
 * 快照等既有链路零改动复用。账号密码只出现在弹出的浏览器窗口里，不落库。</p>
 *
 * <p>实现说明：令牌交换响应的截获在页面上下文完成——注入的初始化脚本覆写
 * {@code fetch} / {@code XMLHttpRequest}，把命中地址特征的响应体暂存到
 * {@code window.__chuaCreditCaptures} 与 sessionStorage，Java 侧轮询读取。
 * 登录窗口使用独立引擎（有头），避免污染共享无头引擎的进程级参数。</p>
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
     * 等待用户在弹出窗口里完成登录的时间上限（毫秒）：覆盖打开门户、输入账号密码、
     * 验证码 / 滑块与登录跳转全程；超时未截获令牌按失败返回，窗口随即关闭
     */
    private static final long TOKEN_WAIT_MILLIS = 180_000L;

    /**
     * 轮询间隔（毫秒）
     */
    private static final long POLL_INTERVAL_MILLIS = 800L;

    /**
     * 静默续期的回访等待上限（毫秒）：无头页面加载 + 平台可能的令牌轮换窗口。
     * 比人工登录短得多——没有输入环节，页面几十秒内还读不到任何令牌即认为登录态失效
     */
    private static final long RENEW_WAIT_MILLIS = 45_000L;

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
     * 静默续期结果。
     *
     * @param status   结果状态：{@link #RENEWED} / {@link #VALID_NO_ROTATION} / {@link #REJECTED}
     * @param token    平台轮换出的新令牌（仅 {@link #RENEWED} 时有值）
     * @param message  结果说明
     * @param renewUrl 实际回访的页面地址
     */
    public record RenewOutcome(String status, String token, String message, String renewUrl) {

        /**
         * 平台在回访时轮换了令牌，新令牌已截获
         */
        public static final String RENEWED = "renewed";

        /**
         * 门户会话仍有效，本次回访未轮换令牌（旧令牌继续可用）
         */
        public static final String VALID_NO_ROTATION = "valid-no-rotation";

        /**
         * 回访未读到会话令牌：登录态已失效，需要人工重新登录
         */
        public static final String REJECTED = "rejected";
    }

    /**
     * 弹出登录窗口，等待用户完成登录并截获会话令牌。
     *
     * <p>用独立有头 CDP 引擎打开登录页（真实浏览器窗口，用户在其中手动输入
     * 账号密码），轮询 SPI 钩子直到截获令牌或超时。成功时把令牌回填
     * {@code extra.credit_key}（persist=true 时落库）；失败不抛异常，
     * 返回带原因的 {@link LoginOutcome}，由调用方决定提示方式。</p>
     *
     * @param entity  供应商
     * @param spi     余额 SPI 实现（提供登录页与截获钩子；null 且未显式配登录地址时失败）
     * @param persist 截获成功后是否把令牌落库（登录按钮 true，试查不弹窗）
     * @return 登录结果
     */
    public LoginOutcome login(SysAccountChannel entity, CreditQueryProvider spi, boolean persist) {
        Map<String, Object> extra = entity.getExtra() == null ? Map.of() : entity.getExtra();
        String explicitUrl = text(extra.get("credit_login_url"));
        String loginUrl = !explicitUrl.isEmpty() ? explicitUrl
                : spi == null ? null : spi.loginPageUrl(entity.getBaseUrl());
        if (loginUrl == null || loginUrl.isBlank()) {
            return new LoginOutcome(false, null, "该供应商没有可用的登录页（未收录登录入口）", null);
        }
        if (spi == null) {
            return new LoginOutcome(false, null, "该供应商未配置可截获令牌的登录实现", loginUrl);
        }
        // 独立有头引擎：进程级参数（无头/视口等）以本引擎首次启动为准，
        // 与共享无头引擎互不影响；用完必须 close 释放浏览器进程
        CdpBrowserEngine engine = new CdpBrowserEngine();
        try {
            engine.start(new BrowserOptions().setHeadless(false).setWidth(1280).setHeight(900));
        } catch (RuntimeException e) {
            engine.close();
            log.warn("[credit-login] 供应商 {} 启动登录浏览器失败: {}", entity.getProviderId(), e.getMessage());
            return new LoginOutcome(false, null, "无法启动登录浏览器：" + e.getMessage(), loginUrl);
        }
        try (BrowserPage page = engine.openPage(loginUrl)) {
            String capturePattern = spi.loginCaptureUrlPattern();
            if (capturePattern != null && !capturePattern.isBlank()) {
                page.addInitScript(captureScript(capturePattern));
            }
            String token = awaitToken(page, spi, capturePattern);
            if (token == null || token.isBlank()) {
                return new LoginOutcome(false, null,
                        "等待登录超时：请在弹出的浏览器窗口内完成上游门户登录后重试", loginUrl);
            }
            if (persist) {
                persistToken(entity, extra, token);
            }
            log.info("[credit-login] 供应商 {} 登录成功，令牌 {} 已{}回填",
                    entity.getProviderId(), mask(token), persist ? "" : "未");
            return new LoginOutcome(true, token, "登录成功", loginUrl);
        } catch (RuntimeException e) {
            log.warn("[credit-login] 供应商 {} 登录失败: {}", entity.getProviderId(), e.getMessage());
            return new LoginOutcome(false, null, "登录执行失败：" + e.getMessage(), loginUrl);
        } finally {
            engine.close();
        }
    }

    /**
     * 静默续期：无头浏览器预置旧令牌回访上游门户，截获轮换出的新令牌。
     *
     * <p>与 {@link #login} 的差别：不弹窗、无需用户输入——把旧令牌（Cookie /
     * localStorage）预置进无头页面后打开「已登录落地页」，页面 SDK 认会话：
     * 平台若在回访时轮换令牌，新值按登录截获钩子（Cookie / localStorage /
     * 响应截获）读到并随 {@code renewed} 返回；整窗只见到旧令牌说明会话有效但
     * 平台未轮换（{@code valid-no-rotation}，旧令牌继续可用）；全程读不到任何
     * 令牌说明登录态已失效（{@code rejected}，由调用方告警并引导人工重登）。
     * 不落库——新令牌由调用方与续期状态字段合并后一次写入。</p>
     *
     * @param entity 供应商
     * @param spi    余额 SPI 实现（提供回访页与截获钩子）
     * @return 续期结果
     */
    public RenewOutcome renewByPageVisit(SysAccountChannel entity, CreditQueryProvider spi) {
        Map<String, Object> extra = entity.getExtra() == null ? Map.of() : entity.getExtra();
        String oldToken = text(extra.get("credit_key"));
        if (oldToken.isEmpty()) {
            return new RenewOutcome(RenewOutcome.REJECTED, null, "没有可续期的余额令牌", null);
        }
        String renewUrl = spi.renewPageUrl(entity.getBaseUrl());
        if (renewUrl == null || renewUrl.isBlank()) {
            renewUrl = spi.loginPageUrl(entity.getBaseUrl());
        }
        if (renewUrl == null || renewUrl.isBlank()) {
            return new RenewOutcome(RenewOutcome.REJECTED, null, "该供应商没有可回访的续期页面", null);
        }
        String capturePattern = spi.loginCaptureUrlPattern();
        // 独立无头引擎：续期无人值守，弹窗没有意义；进程级参数以本引擎首次启动为准，
        // 与共享无头引擎、登录有头引擎互不影响；用完必须 close 释放浏览器进程
        CdpBrowserEngine engine = new CdpBrowserEngine();
        try {
            engine.start(new BrowserOptions().setHeadless(true));
        } catch (RuntimeException e) {
            engine.close();
            log.warn("[credit-renew] 供应商 {} 启动续期浏览器失败: {}", entity.getProviderId(), e.getMessage());
            return new RenewOutcome(RenewOutcome.REJECTED, null, "无法启动续期浏览器：" + e.getMessage(), renewUrl);
        }
        try (BrowserPage page = engine.openPage()) {
            if (capturePattern != null && !capturePattern.isBlank()) {
                page.addInitScript(captureScript(capturePattern));
            }
            String cookieName = spi.loginCookieName();
            if (cookieName != null && !cookieName.isBlank()) {
                page.setCookiesForUrl(Map.of(cookieName, oldToken), renewUrl);
            }
            String storageKey = spi.loginStorageKey();
            if (storageKey != null && !storageKey.isBlank()) {
                page.addInitScript(presetStorageScript(storageKey, oldToken));
            }
            page.navigate(renewUrl);
            // 整窗轮询：预置的旧令牌首轮就能读到，不能见旧即停——平台可能在页面
            // 加载数秒后才轮换；读到「与旧令牌不同」的值才算轮换成功
            String firstSeen = null;
            long deadline = System.currentTimeMillis() + RENEW_WAIT_MILLIS;
            while (System.currentTimeMillis() < deadline) {
                String token = peekToken(page, spi, capturePattern);
                if (token != null && !token.isBlank()) {
                    if (firstSeen == null) {
                        firstSeen = token;
                    }
                    if (!token.equals(oldToken)) {
                        log.info("[credit-renew] 供应商 {} 平台已轮换令牌，新令牌 {}",
                                entity.getProviderId(), mask(token));
                        return new RenewOutcome(RenewOutcome.RENEWED, token, "平台已轮换令牌，新令牌已截获", renewUrl);
                    }
                }
                sleep();
            }
            if (firstSeen != null) {
                log.info("[credit-renew] 供应商 {} 门户会话仍有效，本次回访未轮换令牌", entity.getProviderId());
                return new RenewOutcome(RenewOutcome.VALID_NO_ROTATION, null,
                        "门户会话仍有效，本次回访未轮换令牌", renewUrl);
            }
            log.warn("[credit-renew] 供应商 {} 回访门户未读到会话令牌，登录态可能已失效", entity.getProviderId());
            return new RenewOutcome(RenewOutcome.REJECTED, null,
                    "回访门户未读到会话令牌：登录态可能已失效，请人工重新登录", renewUrl);
        } catch (RuntimeException e) {
            log.warn("[credit-renew] 供应商 {} 续期回访失败: {}", entity.getProviderId(), e.getMessage());
            return new RenewOutcome(RenewOutcome.REJECTED, null, "续期回访执行失败：" + e.getMessage(), renewUrl);
        } finally {
            engine.close();
        }
    }

    /**
     * 预置 localStorage 令牌的初始化脚本：在页面任何脚本运行前写入旧令牌，
     * 让页面 SDK 以「已登录」状态启动（登录截获钩子随后可读到轮换出的新值）。
     *
     * @param key   localStorage 键名
     * @param token 旧令牌
     * @return 初始化脚本
     */
    private String presetStorageScript(String key, String token) {
        return "(function(){ try { localStorage.setItem("
                + quote(key) + ", " + quote(token) + "); } catch (e) {} })()";
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
     * 轮询截获令牌：按 SPI 钩子依次尝试 Cookie / localStorage / 响应截获。
     *
     * @param page           浏览器页面
     * @param spi            余额 SPI 实现
     * @param capturePattern 令牌交换接口地址特征（可为 null）
     * @return 令牌；超时未截获返回 null
     */
    private String awaitToken(BrowserPage page, CreditQueryProvider spi, String capturePattern) {
        long deadline = System.currentTimeMillis() + TOKEN_WAIT_MILLIS;
        while (System.currentTimeMillis() < deadline) {
            String token = peekToken(page, spi, capturePattern);
            if (token != null && !token.isBlank()) {
                return token;
            }
            sleep();
        }
        return null;
    }

    /**
     * 单轮令牌嗅探：按 SPI 钩子依次尝试 Cookie / localStorage / 响应截获。
     *
     * <p>登录与静默续期共用同一套钩子读取口径：登录场景轮询到非空即成功；
     * 续期场景需比对「读到的令牌是否与旧值不同」来判定平台是否轮换。</p>
     *
     * @param page           浏览器页面
     * @param spi            余额 SPI 实现
     * @param capturePattern 令牌交换接口地址特征（可为 null）
     * @return 本轮读到的令牌；没有返回 null
     */
    private String peekToken(BrowserPage page, CreditQueryProvider spi, String capturePattern) {
        String cookieName = spi.loginCookieName();
        if (cookieName != null && !cookieName.isBlank()) {
            String value = page.cookies().get(cookieName);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        String storageKey = spi.loginStorageKey();
        if (storageKey != null && !storageKey.isBlank()) {
            String raw = evaluateQuietly(page,
                    "localStorage.getItem(" + quote(storageKey) + ")");
            String token = unwrapStorageToken(raw);
            if (token != null && !token.isBlank()) {
                return token;
            }
        }
        if (capturePattern != null && !capturePattern.isBlank()) {
            String token = capturedToken(page, spi.loginTokenJsonPath());
            if (token != null && !token.isBlank()) {
                return token;
            }
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
