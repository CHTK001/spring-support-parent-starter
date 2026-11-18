package com.chua.starter.ai.support.manage.service;

import com.chua.common.support.browser.BrowserEngine;
import com.chua.common.support.browser.BrowserOptions;
import com.chua.common.support.browser.BrowserPage;
import com.chua.common.support.browser.cdp.CdpLiveServer;
import com.chua.starter.ai.support.manage.common.ApiErrorCode;
import com.chua.starter.ai.support.manage.common.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Duration;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 余额密钥「账号登录」会话管理器。
 *
 * <p>有些平台（如 MiniMax）的余额令牌就是登录后的会话 Cookie，手填太麻烦。
 * 这里在后端用内置 CDP 浏览器（无头）打开供应商官方登录页，再通过
 * {@link CdpLiveServer} 把这个页面的<b>实时可交互画面</b>包装成一个 HTTP 嵌入
 * 地址交给前端 iframe：用户在弹窗里看到、点击、输入、扫码的就是后端无头浏览器
 * 里的真实登录页，前后端不在一台机器上也能用（纯 HTTP 传输画面与鼠标键盘事件）。
 * 本系统不保存、不读取账号密码；登录成功后由后端按供应商声明的截获口径
 * （登录响应体 / Cookie / localStorage）取回余额令牌，前端轮询到
 * {@code captured} 即自动回填「余额密钥」。</p>
 *
 * <p>会话带空闲 TTL 与数量上限；浏览器进程与实时嵌入会话必须显式关闭，
 * 否则会泄漏；Bean 关闭时统一回收。</p>
 *
 * @author CH
 * @since 4.0.0.58
 */
@Slf4j
@Component
public class CreditLoginSessionManager implements AutoCloseable {

    /**
     * 会话空闲 TTL：超过即回收浏览器进程
     */
    public static final long SESSION_TTL_MS = 5L * 60L * 1000L;

    /**
     * 同时打开的登录会话上限：超过先关最旧的
     */
    private static final int MAX_SESSIONS = 8;

    /**
     * 登录页视口宽度
     */
    private static final int VIEWPORT_WIDTH = 1280;

    /**
     * 登录页视口高度
     */
    private static final int VIEWPORT_HEIGHT = 900;

    /**
     * 一个会话最多暂存的响应条数
     */
    private static final int MAX_CAPTURES = 8;

    /**
     * access_token 的兜底正则（响应不是纯 JSON 结构时用）。
     * 令牌可能含 {@code /}、{@code +}（如 DeepSeek 平台会话令牌是 Base64 变体）
     */
    private static final Pattern TOKEN_PATTERN =
            Pattern.compile("\"access_token\"\\s*:\\s*\"([A-Za-z0-9._\\-+/]+)\"");

    /**
     * localStorage 值的 JWT 形态校验：三段式 base64url（header.payload.signature）。
     * SenseNova 的余额令牌是平台登录 JWT；OAuth 令牌交换返回的不透明串没有点分结构
     */
    private static final Pattern JWT_PATTERN =
            Pattern.compile("^[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{4,}\\.[A-Za-z0-9_-]{8,}$");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 页面注入脚本：包裹 fetch / XHR，把登录响应暂存到 window 变量，
     * 并同步到 sessionStorage（登录成功后若发生整页跳转也能找回）。
     * 默认只记 URL 带登录 / 令牌 / 会话特征且响应体含 {@code access_token} 的响应；
     * {@code __CHUA_URL_PATTERN__} 非空时（如 DeepSeek 的令牌不在 access_token 里），
     * 改为按地址特征圈定要截获的响应。占位符由 {@link #captureScript(String)} 替换。
     */
    private static final String CAPTURE_SCRIPT = """
            (() => {
              const KEY = '__chuaCreditCapture';
              const URL_PATTERN = '__CHUA_URL_PATTERN__';
              const load = () => {
                try { return JSON.parse(sessionStorage.getItem(KEY) || '[]') || []; } catch (e) { return []; }
              };
              const save = (list) => {
                try { sessionStorage.setItem(KEY, JSON.stringify(list)); } catch (e) {}
              };
              window[KEY] = load();
              const record = (url, text) => {
                try {
                  if (!text || window[KEY].length >= 8) {
                    return;
                  }
                  const u = String(url || '');
                  let hit = false;
                  if (URL_PATTERN) {
                    try { hit = new RegExp(URL_PATTERN, 'i').test(u); } catch (e) { hit = false; }
                  } else {
                    hit = text.indexOf('access_token') !== -1;
                  }
                  if (hit) {
                    window[KEY].push({ url: u, body: String(text) });
                    save(window[KEY]);
                  }
                } catch (e) {}
              };
              const interesting = (url) => /login|token|auth|session/i.test(String(url || ''));
              const origFetch = window.fetch;
              if (origFetch) {
                window.fetch = function (...args) {
                  return origFetch.apply(this, args).then((resp) => {
                    try {
                      const req = args[0];
                      const url = typeof req === 'string' ? req : (req && req.url) || '';
                      if (interesting(url)) {
                        resp.clone().text().then((t) => record(url, t)).catch(() => {});
                      }
                    } catch (e) {}
                    return resp;
                  });
                };
              }
              const Xhr = window.XMLHttpRequest;
              if (Xhr) {
                const origOpen = Xhr.prototype.open;
                const origSend = Xhr.prototype.send;
                Xhr.prototype.open = function (method, url, ...rest) {
                  try { this.__chuaCreditUrl = url; } catch (e) {}
                  return origOpen.call(this, method, url, ...rest);
                };
                Xhr.prototype.send = function (...args) {
                  this.addEventListener('load', () => {
                    try { record(this.__chuaCreditUrl, this.responseText); } catch (e) {}
                  });
                  return origSend.apply(this, args);
                };
              }
            })();
            """;

    /**
     * 活跃会话
     */
    private final Map<String, CreditLoginSession> sessions = new ConcurrentHashMap<>();

    /**
     * 实时画面嵌入服务（懒启动，全部会话共用一个端口）
     */
    private volatile CdpLiveServer liveServer;

    /**
     * 发起一次账号登录会话：打开内嵌浏览器导航到登录页，并把它注册成
     * 可嵌入的实时页面。
     *
     * <p>截获口径由供应商实现声明：默认在页面里收集体内含 {@code access_token} 的
     * 登录响应；{@code captureUrlPattern} 非空时改按地址特征截获，并用
     * {@code tokenJsonPath} 从响应体取令牌（如 DeepSeek 的 {@code data.biz_data.user.token}）。
     * {@code cookieName} 非空时优先走 Cookie 截获；{@code storageKey} 非空时再开
     * localStorage 轮询（登录后前端 SDK 把 JWT 写进页面 localStorage，如 SenseNova）。</p>
     *
     * @param loginUrl          登录页地址
     * @param cookieName        余额密钥所在的 Cookie 名；null 走响应体截获
     * @param captureUrlPattern 要截获的响应地址特征（正则）；null 用默认口径
     * @param tokenJsonPath     令牌在响应体里的 JSON 路径；null 找 access_token
     * @param storageKey        余额令牌所在的 localStorage 键名；null 不走 localStorage
     * @return 会话视图（含实时页面嵌入路径与过期时间）
     */
    public synchronized LoginView start(String loginUrl, String cookieName,
                                        String captureUrlPattern, String tokenJsonPath, String storageKey) {
        evictExpired();
        if (sessions.size() >= MAX_SESSIONS) {
            sessions.values().stream()
                    .min(Comparator.comparingLong(session -> session.lastAccess))
                    .ifPresent(oldest -> close(oldest.id));
        }
        CdpLiveServer live = liveServer();
        BrowserEngine engine = BrowserEngine.cdp();
        BrowserPage page = null;
        try {
            // 无头浏览器跑在后端所在机器上；画面与交互经 CdpLiveServer 以 HTTP 提供，
            // 前端 iframe 直接嵌入，前后端不同机也能用
            BrowserOptions options = new BrowserOptions()
                    .setHeadless(true)
                    .setWidth(VIEWPORT_WIDTH)
                    .setHeight(VIEWPORT_HEIGHT)
                    .setLocale("zh-CN");
            page = engine.openPage(options);
            page.addInitScript(captureScript(captureUrlPattern));
            page.navigate(loginUrl);
            String id = UUID.randomUUID().toString().replace("-", "");
            // live.view 返回完整地址（http://localhost:端口/live/s/xxx），这里只保留路径；
            // 对外完整地址由 Controller 按请求来源主机拼接（前后端可能不在一台机器）
            String embedPath = URI.create(live.view(page, id)).getPath();
            CreditLoginSession session = new CreditLoginSession(id, engine, page, live, embedPath,
                    cookieName, tokenJsonPath, storageKey);
            sessions.put(id, session);
            return view(session, "pending", null);
        } catch (RuntimeException e) {
            // 启动失败必须释放浏览器进程，否则泄漏
            if (page != null) {
                try {
                    page.close();
                } catch (Exception ignored) {
                    log.debug("[credit-login] 关闭页面失败: {}", e.getMessage());
                }
            }
            try {
                engine.close();
            } catch (Exception ignored) {
                log.debug("[credit-login] 关闭引擎失败: {}", e.getMessage());
            }
            throw e;
        }
    }

    /**
     * 按本次会话的截获口径生成页面注入脚本：把响应地址特征写进
     * {@code __CHUA_URL_PATTERN__} 占位符（默认口径传空串）。特征来自
     * 供应商实现，按 JS 单引号字符串字面量转义。
     *
     * @param captureUrlPattern 要截获的响应地址特征；null 用默认口径
     * @return 注入脚本
     */
    private static String captureScript(String captureUrlPattern) {
        String pattern = captureUrlPattern == null ? ""
                : captureUrlPattern.replace("\\", "\\\\").replace("'", "\\'");
        return CAPTURE_SCRIPT.replace("__CHUA_URL_PATTERN__", pattern);
    }

    /**
     * 轮询会话：检测令牌截获状态；已截获时 status=captured。
     *
     * @param sessionId 会话标识
     * @return 会话视图
     */
    public LoginView poll(String sessionId) {
        CreditLoginSession session = require(sessionId);
        if (session.token != null) {
            return view(session, "captured", null);
        }
        String captures = session.page.evaluate("JSON.stringify(window.__chuaCreditCapture || [])", false);
        String token = extractToken(captures, session.tokenJsonPath);
        if (token == null) {
            token = captureCookieToken(session);
        }
        if (token == null) {
            token = captureStorageToken(session);
        }
        if (token != null) {
            session.token = token;
            log.info("[credit-login] 已截获余额令牌: sessionId={}", sessionId);
            return view(session, "captured", null);
        }
        return view(session, "pending", null);
    }

    /**
     * 结束并回收一个会话（浏览器进程 + 实时嵌入会话）。
     *
     * @param sessionId 会话标识
     */
    public void close(String sessionId) {
        if (sessionId == null) {
            return;
        }
        CreditLoginSession session = sessions.remove(sessionId);
        if (session != null) {
            closeQuietly(session);
        }
    }

    /**
     * 取会话；过期或不存在时报「请重新发起」。
     *
     * @param sessionId 会话标识
     * @return 会话
     */
    private CreditLoginSession require(String sessionId) {
        evictExpired();
        CreditLoginSession session = sessions.get(sessionId);
        if (session == null) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "登录会话已结束或超时，请重新发起");
        }
        session.lastAccess = System.currentTimeMillis();
        return session;
    }

    /**
     * 回收空闲超过 TTL 的会话。
     */
    private void evictExpired() {
        long now = System.currentTimeMillis();
        sessions.values().removeIf(session -> {
            if (now - session.lastAccess > SESSION_TTL_MS) {
                closeQuietly(session);
                return true;
            }
            return false;
        });
    }

    /**
     * 组装回传给页面的会话视图。
     *
     * @param session 会话
     * @param status  状态（pending / captured）
     * @param message 附加说明，可为空
     * @return 会话视图
     */
    private LoginView view(CreditLoginSession session, String status, String message) {
        long expireAt = session.lastAccess + SESSION_TTL_MS;
        return new LoginView(session.id, status, session.token, message, session.embedPath, expireAt);
    }

    /**
     * 懒启动实时画面嵌入服务：随机端口，空闲回收时间比会话 TTL 略长，
     * 避免服务端先回收了画面而登录会话还在。
     *
     * @return 嵌入服务
     */
    private CdpLiveServer liveServer() {
        CdpLiveServer live = liveServer;
        if (live == null) {
            try {
                live = CdpLiveServer.start(0);
            } catch (Exception e) {
                throw new ApiException(ApiErrorCode.INTERNAL_ERROR, "启动内嵌实时浏览器服务失败：" + e.getMessage());
            }
            live.idleTimeout(Duration.ofMillis(SESSION_TTL_MS + 60_000L));
            liveServer = live;
        }
        return live;
    }

    /**
     * 实时嵌入服务的实际监听端口（前端拼对外地址用）。
     *
     * @return 端口；服务未启动为 0
     */
    public synchronized int livePort() {
        CdpLiveServer live = liveServer;
        return live == null ? 0 : live.port();
    }

    /**
     * Cookie 截获模式：登录态在页面 Cookie 里（可能 HttpOnly，页面脚本读不到），
     * 用 CDP 读全量 Cookie，命中实现声明的 Cookie 名即认为登录成功。
     *
     * @param session 会话
     * @return 余额密钥；未命中为 null
     */
    private String captureCookieToken(CreditLoginSession session) {
        String cookieName = session.loginCookieName;
        if (cookieName == null || cookieName.isBlank()) {
            return null;
        }
        try {
            Map<String, String> cookies = session.page.cookies();
            String value = cookies == null ? null : cookies.get(cookieName);
            return value == null || value.isBlank() ? null : value.trim();
        } catch (RuntimeException e) {
            log.debug("[credit-login] 读取页面 Cookie 失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * localStorage 截获模式：登录态令牌由前端 SDK 写进页面 localStorage
     * （一般要等登录跳转回站后才写入），轮询页面脚本读取实现声明的主键；
     * 主键取不到时扫描全部键，把值里含 JWT 特征的登录态 JSON 带回来兜底
     * （如 SenseNova 的 NOVA_AUTH:nova:login 整包 JSON）。
     *
     * @param session 会话
     * @return 余额密钥；未命中为 null
     */
    private String captureStorageToken(CreditLoginSession session) {
        String storageKey = session.storageKey;
        if (storageKey == null || storageKey.isBlank()) {
            return null;
        }
        try {
            String value = session.page.evaluate(storageProbeScript(storageKey), false);
            return storageTokenOf(value);
        } catch (RuntimeException e) {
            log.debug("[credit-login] 读取页面 localStorage 失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 生成 localStorage 读取脚本：先读实现声明的主键；取不到时扫描全部键，
     * 把值里含 JWT 特征（base64url 段以 eyJ 开头）的值带回来，由 Java 侧再解一层。
     * 键名按 JS 单引号字符串字面量转义。
     *
     * @param storageKey 主键名
     * @return 脚本
     */
    private static String storageProbeScript(String storageKey) {
        String key = storageKey.replace("\\", "\\\\").replace("'", "\\'");
        return "("
                + "(() => {"
                + " try {"
                + "  const key = '" + key + "';"
                + "  const direct = window.localStorage.getItem(key);"
                + "  if (direct) { return direct; }"
                + "  for (let i = 0; i < window.localStorage.length; i++) {"
                + "   const name = window.localStorage.key(i);"
                + "   const value = window.localStorage.getItem(name);"
                + "   if (value && value.indexOf('eyJ') !== -1) { return value; }"
                + "  }"
                + " } catch (e) {}"
                + " return '';"
                + "})()"
                + ")";
    }

    /**
     * 从 localStorage 读回的值里提取余额令牌。
     *
     * <p>值可能是令牌原值（JWT 三段式）或登录态 JSON（按 token / access_token
     * 字段递归取值）。只认 JWT 形态或无空白的较长令牌串，避免把页面其它
     * 状态值误当令牌。</p>
     *
     * @param value localStorage 读回的原始值
     * @return 余额令牌；取不到为 null
     */
    private static String storageTokenOf(String value) {
        if (value == null || value.isBlank() || "null".equals(value)) {
            return null;
        }
        String text = value.trim();
        if (isTokenShape(text)) {
            return text;
        }
        try {
            return findStorageTokenField(MAPPER.readTree(text));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 是否是可信的令牌形态：JWT 三段式，或无空白的较长令牌串。
     *
     * @param text 候选值
     * @return 形态吻合为 true
     */
    private static boolean isTokenShape(String text) {
        if (JWT_PATTERN.matcher(text).matches()) {
            return true;
        }
        return text.length() >= 20 && text.chars().noneMatch(Character::isWhitespace);
    }

    /**
     * 在登录态 JSON 里递归找令牌字段：字段名 access_token / token，
     * 值须是可信令牌形态（如 SenseNova 登录态包里的 {@code token} 即平台 JWT）。
     *
     * @param node JSON 节点
     * @return 令牌；没有为 null
     */
    private static String findStorageTokenField(JsonNode node) {
        if (node == null) {
            return null;
        }
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String name = field.getKey();
                JsonNode value = field.getValue();
                if (value.isTextual() && ("access_token".equalsIgnoreCase(name) || "token".equalsIgnoreCase(name))) {
                    String text = value.asText().trim();
                    if (isTokenShape(text)) {
                        return text;
                    }
                }
                String nested = findStorageTokenField(value);
                if (nested != null) {
                    return nested;
                }
            }
        } else if (node.isArray()) {
            for (JsonNode item : node) {
                String nested = findStorageTokenField(item);
                if (nested != null) {
                    return nested;
                }
            }
        }
        return null;
    }

    /**
     * 从截获的响应里提取余额令牌。
     *
     * <p>默认口径：响应可能是任意结构（data 包裹 / 顶层 / 嵌套对象），先按 JSON
     * 递归找 {@code access_token} 字段，找不到再用正则兜底。指定
     * {@code tokenJsonPath} 时（令牌不在 access_token 里，如 DeepSeek 的
     * {@code data.biz_data.user.token}），按点分路径逐段下钻取值；路径未命中时
     * 递归找 {@code token} 字段兜底——同一平台不同登录方式的令牌位置可能不同
     * （如 DeepSeek 密码 / 短信登录在 {@code data.biz_data.user.token}、
     * OAuth 令牌交换在 {@code data.biz_data.token}）。</p>
     *
     * @param capturesJson 截获列表的 JSON 串
     * @param tokenJsonPath 令牌在响应体里的 JSON 路径；null 走 access_token 口径
     * @return 令牌；没有截获到为 null
     */
    private static String extractToken(String capturesJson, String tokenJsonPath) {
        if (capturesJson == null || capturesJson.isBlank() || "null".equals(capturesJson)) {
            return null;
        }
        boolean byPath = tokenJsonPath != null && !tokenJsonPath.isBlank();
        try {
            JsonNode root = MAPPER.readTree(capturesJson);
            if (root.isArray()) {
                for (JsonNode item : root) {
                    String body = item.path("body").asText("");
                    String token = byPath ? tokenByPath(body, tokenJsonPath) : findTokenInJson(body);
                    if (token == null && byPath) {
                        token = recursiveToken(body);
                    }
                    if (token == null && !byPath) {
                        Matcher matcher = TOKEN_PATTERN.matcher(body);
                        if (matcher.find()) {
                            token = matcher.group(1);
                        }
                    }
                    if (token != null) {
                        return token;
                    }
                }
            }
        } catch (Exception e) {
            log.debug("[credit-login] 解析截获响应失败: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 在响应体里递归找 {@code token} / {@code access_token} 字段（值须是可信令牌
     * 形态，避免把普通长字符串误当令牌）。
     *
     * @param body 响应体
     * @return 令牌；解析失败或没有该字段为 null
     */
    private static String recursiveToken(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            return findStorageTokenField(MAPPER.readTree(body));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 按点分 JSON 路径从响应体里取令牌。
     *
     * @param body 响应体
     * @param path 点分路径（如 {@code data.biz_data.user.token}）
     * @return 路径上的文本值；解析失败、缺段或不是非空文本为 null
     */
    private static String tokenByPath(String body, String path) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            JsonNode node = MAPPER.readTree(body);
            for (String segment : path.split("\\.")) {
                if (node == null || node.isMissingNode() || node.isNull()) {
                    return null;
                }
                node = node.get(segment);
            }
            return node != null && node.isTextual() && !node.asText().isBlank() ? node.asText() : null;
        } catch (Exception e) {
            log.debug("[credit-login] 按路径取令牌失败: path={}, msg={}", path, e.getMessage());
            return null;
        }
    }

    /**
     * 把响应体当 JSON 解析后递归找 access_token 字段。
     *
     * @param body 响应体
     * @return 令牌；解析失败或没有该字段为 null
     */
    private static String findTokenInJson(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            return findTokenField(MAPPER.readTree(body));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 递归查找 access_token 字段。
     *
     * @param node JSON 节点
     * @return 令牌；没有为 null
     */
    private static String findTokenField(JsonNode node) {
        if (node == null) {
            return null;
        }
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if ("access_token".equalsIgnoreCase(field.getKey())
                        && field.getValue().isTextual()
                        && !field.getValue().asText().isBlank()) {
                    return field.getValue().asText();
                }
                String nested = findTokenField(field.getValue());
                if (nested != null) {
                    return nested;
                }
            }
        } else if (node.isArray()) {
            for (JsonNode item : node) {
                String nested = findTokenField(item);
                if (nested != null) {
                    return nested;
                }
            }
        }
        return null;
    }

    /**
     * 释放一个会话的浏览器资源与实时嵌入会话。
     *
     * @param session 会话
     */
    private void closeQuietly(CreditLoginSession session) {
        try {
            session.close();
        } catch (Exception e) {
            log.debug("[credit-login] 回收会话失败: {}", e.getMessage());
        }
    }

    /**
     * Bean 关闭时回收全部会话与嵌入服务，避免 Chrome 进程泄漏。
     */
    @Override
    public void close() {
        sessions.values().forEach(this::closeQuietly);
        sessions.clear();
        CdpLiveServer live = liveServer;
        liveServer = null;
        if (live != null) {
            try {
                live.close();
            } catch (Exception e) {
                log.debug("[credit-login] 关闭实时嵌入服务失败: {}", e.getMessage());
            }
        }
    }

    /**
     * 一个账号登录会话：引擎 + 页面 + 实时嵌入地址 + 截获状态。
     */
    private static final class CreditLoginSession implements AutoCloseable {

        /**
         * 会话标识
         */
        private final String id;

        /**
         * 浏览器引擎（独占，关闭时一并释放）
         */
        private final BrowserEngine engine;

        /**
         * 登录页
         */
        private final BrowserPage page;

        /**
         * 实时画面嵌入服务（关闭会话时同步摘除嵌入会话）
         */
        private final CdpLiveServer live;

        /**
         * 实时页面的嵌入路径（如 {@code /live/s/xxxx}）
         */
        private final String embedPath;

        /**
         * 余额密钥所在的 Cookie 名（Cookie 截获模式）；null 走响应体截获
         */
        private final String loginCookieName;

        /**
         * 令牌在响应体里的 JSON 路径（如 DeepSeek 的 data.biz_data.user.token）；
         * null 走 access_token 口径
         */
        private final String tokenJsonPath;

        /**
         * 余额令牌所在的 localStorage 键名（localStorage 截获模式）；
         * null 不走 localStorage
         */
        private final String storageKey;

        /**
         * 最近访问时间（TTL 依据）
         */
        private volatile long lastAccess = System.currentTimeMillis();

        /**
         * 截获到的余额令牌；未截获为 null
         */
        private volatile String token;

        /**
         * 是否已关闭
         */
        private volatile boolean closed;

        private CreditLoginSession(String id, BrowserEngine engine, BrowserPage page, CdpLiveServer live,
                                   String embedPath, String loginCookieName,
                                   String tokenJsonPath, String storageKey) {
            this.id = id;
            this.engine = engine;
            this.page = page;
            this.live = live;
            this.embedPath = embedPath;
            this.loginCookieName = loginCookieName;
            this.tokenJsonPath = tokenJsonPath;
            this.storageKey = storageKey;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            try {
                // 同步摘除实时嵌入会话（内部会关页面），避免残留的嵌入地址指向已关页面
                live.close(id);
            } catch (Exception e) {
                log.debug("[credit-login] 摘除实时会话失败: {}", e.getMessage());
            }
            try {
                page.close();
            } catch (Exception e) {
                log.debug("[credit-login] 关闭页面失败: {}", e.getMessage());
            }
            try {
                engine.close();
            } catch (Exception e) {
                log.debug("[credit-login] 关闭引擎失败: {}", e.getMessage());
            }
        }
    }

    /**
     * 会话视图：轮询 / 发起后的统一返回。
     *
     * @param sessionId 会话标识
     * @param status    状态（pending / captured）
     * @param token     截获到的余额令牌，未截获为 null
     * @param message   附加说明，可为空
     * @param embedPath 实时页面的嵌入路径（如 {@code /live/s/xxxx}）
     * @param expireAt  会话过期时间（epoch 毫秒）
     */
    public record LoginView(String sessionId, String status, String token, String message,
                            String embedPath, long expireAt) {
    }
}
