package com.chua.starter.ai.support.spi.impl;

import com.chua.common.support.ai.chat.route.RouteTarget;
import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.ai.support.manage.common.ApiErrorCode;
import com.chua.starter.ai.support.manage.common.ApiException;
import com.chua.starter.ai.support.spi.AccountChannelAuthManager;
import com.chua.starter.common.support.text.json.Json;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * WorkBuddy（腾讯）账号渠道鉴权管理器实现。
 *
 * <p>对接 <b>wb2api 网关</b>（{@code workbuddy2api-ui}，把腾讯 CodeBuddy / WorkBuddy
 * 账号反代为 OpenAI / Anthropic 兼容 API 的自托管网关）：本渠道<b>不直接持有</b>
 * WorkBuddy 账号令牌，而是把网关当作账号池接入渠道管理——绑定后拿到的
 * {@code access_token} 是网关的 API Key，账号池、多账号轮换与自动签到全部由网关负责。</p>
 *
 * <ol>
 *   <li>{@code startLogin}：用管理密钥登录网关 {@code POST /admin/login} 拿管理会话
 *       （cookie + csrf）→ {@code POST /admin/oauth {realm}} 发起浏览器授权，
 *       返回 {@code auth_url}（用户打开完成 CodeBuddy / WorkBuddy 登录/扫码，
 *       与 TRAE / Gemini 的 {@code browser-oauth} 体验一致）。</li>
 *   <li>{@code pollLogin}：按句柄轮询 {@code GET /admin/oauth/{flowId}}；
 *       状态 {@code complete} 后 {@code POST /admin/access} 取网关 API Key，
 *       返回可持久化凭据；等待 / 需选地区时返回 {@code null}；失败抛异常。</li>
 *   <li>{@code listModels}：{@code GET {gateway}/v1/models}（Bearer API Key），
 *       远端返回 OpenAI 兼容 {@code data[]}，模型名带 {@code cn:} / {@code global:} 前缀。</li>
 *   <li>{@code checkin}：调 {@code POST /admin/tasks/{id}/runs} 触发网关签到任务；
 *       网关自动签到为常驻后台能力，本渠道仅提供「立即执行」入口。</li>
 * </ol>
 *
 * <p><b>配置</b>（仿 {@code loomCfg} 覆盖范式，优先级 系统属性 → 环境变量 → 默认值）：</p>
 * <ul>
 *   <li>{@code chua.workbuddy.gateway-url} / {@code WORKBUDDY_GATEWAY_URL}：
 *       网关地址，默认 {@code http://127.0.0.1:7863}（wb2api console 默认监听端口）；</li>
 *   <li>{@code chua.workbuddy.admin-key} / {@code WORKBUDDY_ADMIN_KEY}：
 *       网关管理密钥（<b>必填</b>，未配置时绑定直接报错引导，不产生半成品流）；</li>
 *   <li>{@code chua.workbuddy.realm} / {@code WORKBUDDY_REALM}：
 *       授权域，{@code cn}（国内版 CodeBuddy）或 {@code global}（国际版 WorkBuddy），
 *       默认 {@code cn}；</li>
 *   <li>{@code chua.workbuddy.region} / {@code WORKBUDDY_REGION}：
 *       国际版授权完成后的注册地区（ISO 双字母，如 {@code SG}），仅 realm=global 且
 *       上游要求时使用；缺省时 {@code needs_region} 按等待处理，由用户在授权页完成。</li>
 * </ul>
 *
 * <p><b>凭据落库</b>：{@code access_token}（网关 API Key） + {@code gateway_url} +
 * {@code realm} + {@code base_url}（{@code {gateway_url}/v1}，供对话链路直连）。
 * {@code refresh} 无意义（网关自动轮换账号令牌），返回原凭据副本。</p>
 *
 * @since 4.0.0.43
 * @author CH
 */
@Spi("workbuddy")
public class WorkBuddyAccountChannelAuthManager implements AccountChannelAuthManager {

    /** 默认网关地址（wb2api console 默认监听）。 */
    static final String DEFAULT_GATEWAY = "http://127.0.0.1:7863";

    /** 句柄键：授权流 id。 */
    static final String HANDLE_FLOW_ID = "flowId";
    /** 句柄键：管理会话 cookie（wb2a_admin=...）。 */
    static final String HANDLE_COOKIE = "cookie";
    /** 句柄键：管理会话 csrf。 */
    static final String HANDLE_CSRF = "csrf";
    /** 句柄键：授权域。 */
    static final String HANDLE_REALM = "realm";

    /** 共享 HttpClient（各渠道实现同源约定，15s 连接超时）。 */
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Override
    public String channel() {
        return "workbuddy";
    }

    @Override
    public String displayName() {
        return "WorkBuddy（腾讯）";
    }

    @Override
    public String loginMethod() {
        return "browser-oauth";
    }

    /**
     * {@inheritDoc}
     *
     * <p>用配置的管理密钥登录 wb2api 网关 → 发起 {@code realm} 的浏览器授权，
     * 返回句柄（含授权流 id、管理会话 cookie / csrf）与 {@code verifyUrl}
     * （用户浏览器打开完成 CodeBuddy / WorkBuddy 登录）。</p>
     */
    @Override
    public Map<String, Object> startLogin() throws Exception {
        String gateway = cfg("gateway-url", DEFAULT_GATEWAY);
        String adminKey = cfg("admin-key", "");
        if (isBlank(adminKey)) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM,
                    "WorkBuddy 网关未配置管理密钥：请设置 chua.workbuddy.admin-key 或 WORKBUDDY_ADMIN_KEY");
        }
        String realm = cfg("realm", "cn");
        // (1) 管理密钥登录：sameOrigin 校验要求 Origin 头 = 网关 origin
        Map<String, Object> session = adminLogin(gateway, adminKey);
        // (2) 发起浏览器授权
        Map<String, Object> flow = oauthStart(gateway, realm, session);
        String flowId = stringOf(flow.get("id"));
        String authUrl = stringOf(flow.get("auth_url"));
        if (isBlank(flowId) || isBlank(authUrl)) {
            throw new ApiException(ApiErrorCode.UPSTREAM_ERROR,
                    "WorkBuddy 网关授权返回不完整（缺 flow id / auth_url）: " + flow);
        }
        Map<String, Object> handle = new LinkedHashMap<>(6);
        handle.put("handle", "workbuddy-oauth-" + flowId);
        handle.put(HANDLE_FLOW_ID, flowId);
        handle.put(HANDLE_COOKIE, session.get(HANDLE_COOKIE));
        handle.put(HANDLE_CSRF, session.get(HANDLE_CSRF));
        handle.put(HANDLE_REALM, realm);
        handle.put("verifyUrl", authUrl);
        handle.put("loginUrl", authUrl); // 旧键兼容
        handle.put("startedAt", System.currentTimeMillis());
        return handle;
    }

    /**
     * {@inheritDoc}
     *
     * <p>按句柄轮询网关授权流：{@code complete} 后取 API Key 返回凭据；
     * {@code waiting / needs_region / retry} 返回 {@code null}（继续等）；
     * {@code failed} 抛异常。</p>
     */
    @Override
    public Map<String, Object> pollLogin(Map<String, Object> handle) throws Exception {
        if (handle == null || isBlank(readString(handle, "handle"))) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "WorkBuddy 登录句柄缺失 handle");
        }
        String gateway = cfg("gateway-url", DEFAULT_GATEWAY);
        String flowId = readString(handle, HANDLE_FLOW_ID);
        String cookie = readString(handle, HANDLE_COOKIE);
        String csrf = readString(handle, HANDLE_CSRF);
        String realm = readString(handle, HANDLE_REALM);
        if (isBlank(flowId) || isBlank(cookie)) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "WorkBuddy 登录句柄不完整（缺 flowId / cookie）");
        }
        Map<String, Object> flow = oauthPoll(gateway, flowId, cookie);
        String status = stringOf(flow.get("status"));
        if ("complete".equalsIgnoreCase(status)) {
            String apiKey = adminAccess(gateway, cookie, csrf);
            if (isBlank(apiKey)) {
                throw new ApiException(ApiErrorCode.UPSTREAM_ERROR, "WorkBuddy 网关未返回 API Key");
            }
            return buildCredential(apiKey, gateway, realm);
        }
        if ("failed".equalsIgnoreCase(status)) {
            throw new ApiException(ApiErrorCode.UPSTREAM_ERROR,
                    "WorkBuddy 授权失败：" + stringOf(flow.get("message")));
        }
        // waiting / needs_region / retry：继续等（region 在授权页或句柄侧处理）
        return null;
    }

    /**
     * {@inheritDoc}
     *
     * <p>网关自动轮换账号令牌，API Key 长期有效，无 refresh 语义——返回原凭据副本。</p>
     */
    @Override
    public Map<String, Object> refresh(Map<String, Object> credentials) {
        return credentials == null ? new LinkedHashMap<>() : new LinkedHashMap<>(credentials);
    }

    /**
     * {@inheritDoc}
     *
     * <p>用网关 API Key 探活 {@code GET /v1/models}：200 视为有效，401 判无效，
     * 网络失败按本地有效（网关离线不影响凭据本身）。</p>
     */
    @Override
    public Map<String, Object> status(Map<String, Object> credentials) throws Exception {
        String apiKey = readString(credentials, "access_token");
        String gateway = readString(credentials, "gateway_url");
        if (isBlank(apiKey) || isBlank(gateway)) {
            return Map.of("status", "invalid", "message", "凭据缺少 access_token / gateway_url");
        }
        try {
            String body = getV1(gateway, "/v1/models", apiKey);
            return Map.of("status", "valid", "message", "网关可达，模型目录 " + countModels(body) + " 条");
        } catch (ApiException e) {
            if (e.getErrorCode() == ApiErrorCode.UNAUTHORIZED) {
                return Map.of("status", "invalid", "message", "网关 API Key 无效（401）");
            }
            return Map.of("status", "valid", "message", "网关探活失败，凭据本身有效");
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>触发 wb2api 网关签到任务：先 {@code GET /admin/tasks} 找类型含
     * {@code checkin} 的任务，再 {@code POST /admin/tasks/{id}/runs} 立即执行。
     * 网关账号池内所有账号统一签到（任务执行粒度在网关侧）。</p>
     */
    @Override
    public Map<String, Object> checkin(Map<String, Object> credentials) throws Exception {
        String gateway = readString(credentials, "gateway_url");
        String adminKey = cfg("admin-key", "");
        if (isBlank(gateway) || isBlank(adminKey)) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM,
                    "WorkBuddy 签到需网关地址与管理密钥配置（chua.workbuddy.admin-key）");
        }
        Map<String, Object> session = adminLogin(gateway, adminKey);
        String cookie = stringOf(session.get(HANDLE_COOKIE));
        String csrf = stringOf(session.get(HANDLE_CSRF));
        List<Map<String, Object>> tasks = adminTasks(gateway, cookie);
        String checkinTaskId = null;
        if (tasks != null) {
            for (Map<String, Object> t : tasks) {
                String name = stringOf(t.get("name")).toLowerCase(Locale.ROOT);
                String id = stringOf(t.get("id"));
                if (name.contains("checkin") || name.contains("签到") || name.contains("sign")) {
                    checkinTaskId = id;
                    break;
                }
            }
        }
        if (isBlank(checkinTaskId)) {
            return Map.of("ok", false, "message", "网关未发现可立即执行的签到任务（自动签到常驻后台）");
        }
        Map<String, Object> result = adminTaskRun(gateway, checkinTaskId, cookie, csrf);
        return Map.of("ok", true, "message",
                "已触发网关签到任务 " + checkinTaskId + "（网关账号池统一执行）", "result", result);
    }

    @Override
    public boolean supportsCheckin() {
        return true;
    }

    /**
     * {@inheritDoc}
     *
     * <p>{@code GET {gateway}/v1/models}（Bearer API Key），解析 OpenAI 兼容
     * {@code data[].id}（模型名带 {@code cn:} / {@code global:} 前缀）。</p>
     */
    @Override
    public List<Map<String, Object>> listModels(Map<String, Object> credentials) throws Exception {
        String apiKey = readString(credentials, "access_token");
        String gateway = readString(credentials, "gateway_url");
        if (isBlank(apiKey) || isBlank(gateway)) {
            return List.of();
        }
        String body = getV1(gateway, "/v1/models", apiKey);
        List<Map<String, Object>> models = new ArrayList<>(32);
        Map<String, Object> root = Json.getJsonObject(body);
        Object rawData = root == null ? null : root.get("data");
        if (rawData instanceof List<?> list) {
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> m)) {
                    continue;
                }
                Object idObj = m.get("id");
                if (idObj == null) {
                    continue;
                }
                String id = String.valueOf(idObj).trim();
                if (id.isEmpty()) {
                    continue;
                }
                Map<String, Object> row = new LinkedHashMap<>(4);
                row.put("id", id);
                row.put("name", id);
                Object ctx = m.get("context_length");
                if (ctx instanceof Number n) {
                    row.put("contextWindow", n.intValue());
                }
                models.add(row);
            }
        }
        return models;
    }

    // ==================== wb2api 网关调用 ====================

    /** 管理登录：POST /admin/login {key}，Origin 必须等于网关 origin。 */
    private Map<String, Object> adminLogin(String gateway, String adminKey) {
        String body = "{\"key\":\"" + jsonEscape(adminKey) + "\"}";
        HttpResponse<String> resp = send("POST", gateway + "/admin/login", body,
                Map.of("Content-Type", "application/json", "Origin", gateway), null, 30);
        Map<String, Object> out = new LinkedHashMap<>(3);
        // Set-Cookie: wb2a_admin=<id>; ...
        String setCookie = resp.headers().firstValue("Set-Cookie").orElse(null);
        if (setCookie != null) {
            String id = extractCookieValue(setCookie, "wb2a_admin");
            if (id != null && !id.isBlank()) {
                out.put(HANDLE_COOKIE, "wb2a_admin=" + id);
            }
        }
        Map<String, Object> parsed = parseBody(resp);
        Object csrf = parsed == null ? null : parsed.get("csrf");
        out.put(HANDLE_CSRF, csrf == null ? "" : String.valueOf(csrf));
        if (out.get(HANDLE_COOKIE) == null || isBlank(stringOf(out.get(HANDLE_COOKIE)))) {
            throw new ApiException(ApiErrorCode.UNAUTHORIZED,
                    "WorkBuddy 网关管理登录失败：管理密钥不正确或会话未建立");
        }
        return out;
    }

    /** 发起浏览器授权：POST /admin/oauth {realm}。 */
    private Map<String, Object> oauthStart(String gateway, String realm, Map<String, Object> session) {
        String body = "{\"realm\":\"" + jsonEscape(realm) + "\"}";
        HttpResponse<String> resp = send("POST", gateway + "/admin/oauth", body,
                Map.of("Content-Type", "application/json", "Origin", gateway,
                        "Cookie", stringOf(session.get(HANDLE_COOKIE)),
                        "X-CSRF-Token", stringOf(session.get(HANDLE_CSRF))),
                null, 30);
        return parseBody(resp);
    }

    /** 轮询授权流：POST /admin/oauth/{flowId}/poll（console 对外路由，内部转 GET core）。 */
    private Map<String, Object> oauthPoll(String gateway, String flowId, String cookie) {
        HttpResponse<String> resp = send("POST", gateway + "/admin/oauth/" + urlEncode(flowId) + "/poll",
                "{}", Map.of("Content-Type", "application/json", "Origin", gateway,
                        "Cookie", cookie), null, 30);
        return parseBody(resp);
    }

    /** 取网关 API Key：POST /admin/access（管理会话）。 */
    private String adminAccess(String gateway, String cookie, String csrf) {
        HttpResponse<String> resp = send("POST", gateway + "/admin/access", "{}",
                Map.of("Content-Type", "application/json", "Origin", gateway,
                        "Cookie", cookie, "X-CSRF-Token", csrf),
                null, 30);
        Map<String, Object> parsed = parseBody(resp);
        Object key = parsed == null ? null : parsed.get("api_key");
        return key == null ? "" : String.valueOf(key);
    }

    /** 网关任务列表：GET /admin/tasks（管理会话）。 */
    private List<Map<String, Object>> adminTasks(String gateway, String cookie) {
        HttpResponse<String> resp = send("GET", gateway + "/admin/tasks", null,
                Map.of("Cookie", cookie), null, 30);
        Map<String, Object> parsed = parseBody(resp);
        Object raw = parsed == null ? null : parsed.get("items");
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>(8);
        for (Object item : list) {
            if (item instanceof Map<?, ?> m) {
                Map<String, Object> row = new LinkedHashMap<>();
                m.forEach((k, v) -> row.put(String.valueOf(k), v));
                out.add(row);
            }
        }
        return out;
    }

    /** 立即执行网关任务：POST /admin/tasks/{id}/runs（管理会话，需 request_id）。 */
    private Map<String, Object> adminTaskRun(String gateway, String taskId, String cookie, String csrf) {
        String requestId = "wb2a-" + System.currentTimeMillis() + "-" + (int) (Math.random() * 1000);
        HttpResponse<String> resp = send("POST", gateway + "/admin/tasks/" + urlEncode(taskId) + "/runs",
                "{\"request_id\":\"" + jsonEscape(requestId) + "\"}",
                Map.of("Content-Type", "application/json", "Origin", gateway,
                        "Cookie", cookie, "X-CSRF-Token", csrf),
                null, 60);
        return parseBody(resp);
    }

    /** 公开 API GET（Bearer API Key）。 */
    private String getV1(String gateway, String path, String apiKey) {
        HttpResponse<String> resp = send("GET", gateway + path, null,
                Map.of("Authorization", "Bearer " + apiKey), null, 30);
        return resp.body();
    }

    // ==================== HTTP 基础 ====================

    private HttpResponse<String> send(String method, String url, String body,
            Map<String, String> headers, String proxy, int timeoutSec) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(timeoutSec));
            if ("GET".equalsIgnoreCase(method)) {
                builder.GET();
            } else if (body == null || body.isEmpty()) {
                builder.method(method, HttpRequest.BodyPublishers.noBody());
            } else {
                builder.method(method, HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
            }
            if (headers != null) {
                headers.forEach((k, v) -> {
                    if (k != null && v != null && !v.isBlank()) {
                        builder.header(k, v);
                    }
                });
            }
            HttpResponse<String> response = CLIENT.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = response.statusCode();
            if (status == 401) {
                throw new ApiException(ApiErrorCode.UNAUTHORIZED,
                        "WorkBuddy 网关鉴权失败（401）: " + url);
            }
            if (status == 429) {
                throw new ApiException(ApiErrorCode.RATE_LIMITED,
                        "WorkBuddy 网关请求过于频繁（429）: " + url);
            }
            if (status < 200 || status >= 300) {
                throw new ApiException(ApiErrorCode.UPSTREAM_ERROR,
                        "WorkBuddy 网关请求失败: " + method + " " + url + " -> " + status + " "
                                + truncate(response.body()));
            }
            return response;
        } catch (ApiException e) {
            throw e;
        } catch (java.io.IOException e) {
            throw new ApiException(ApiErrorCode.UPSTREAM_ERROR,
                    "WorkBuddy 网关不可达: " + url + " (" + e.getMessage() + ")", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(ApiErrorCode.INTERNAL_ERROR,
                    "WorkBuddy 网关请求被中断: " + url, e);
        }
    }

    /** 解析响应体为 Map（非法 JSON 返回 null）。 */
    private Map<String, Object> parseBody(HttpResponse<String> response) {
        try {
            return Json.getJsonObject(response.body());
        } catch (Exception e) {
            return null;
        }
    }

    // ==================== 工具 ====================

    /** 读取可覆盖配置：系统属性 {@code chua.workbuddy.<key>} → 环境变量 {@code WORKBUDDY_<KEY_UPPER>} → 默认。 */
    private static String cfg(String key, String fallback) {
        String value = System.getProperty("chua.workbuddy." + key);
        if (value == null || value.isBlank()) {
            // 键里的连字符映射为环境变量的下划线（admin-key → WORKBUDDY_ADMIN_KEY）
            String envKey = "WORKBUDDY_" + key.toUpperCase(Locale.ROOT).replace('-', '_');
            value = System.getenv(envKey);
        }
        return (value == null || value.isBlank()) ? fallback : value;
    }

    private static Map<String, Object> buildCredential(String apiKey, String gateway, String realm) {
        Map<String, Object> credential = new LinkedHashMap<>(5);
        credential.put("access_token", apiKey);
        credential.put("gateway_url", gateway);
        credential.put("realm", realm);
        credential.put("base_url", gateway.replaceAll("/+$", "") + "/v1");
        return credential;
    }

    private static String extractCookieValue(String setCookie, String name) {
        if (setCookie == null) {
            return null;
        }
        String prefix = name + "=";
        for (String part : setCookie.split(";")) {
            String trimmed = part.trim();
            if (trimmed.startsWith(prefix)) {
                return trimmed.substring(prefix.length());
            }
        }
        return null;
    }

    private static String countModels(String body) {
        if (body == null || body.isBlank()) {
            return "0";
        }
        try {
            Map<String, Object> root = Json.getJsonObject(body);
            Object data = root == null ? null : root.get("data");
            return data instanceof List<?> list ? String.valueOf(list.size()) : "?";
        } catch (Exception e) {
            return "?";
        }
    }

    private static String jsonEscape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String urlEncode(String value) {
        try {
            return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return value;
        }
    }

    private static String truncate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() > 200 ? value.substring(0, 200) + "…" : value;
    }

    private static String stringOf(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String readString(Map<String, Object> source, String key) {
        if (source == null) {
            return "";
        }
        Object value = source.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    @Override
    public RouteTarget toRouteTarget(Map<String, Object> credentials) {
        String accessToken = readString(credentials, "access_token");
        if (isBlank(accessToken)) {
            return null;
        }
        String baseUrl = readString(credentials, "base_url");
        if (isBlank(baseUrl)) {
            String gateway = readString(credentials, "gateway_url");
            if (!isBlank(gateway)) {
                baseUrl = gateway.replaceAll("/+$", "") + "/v1";
            }
        }
        if (isBlank(baseUrl)) {
            return null;
        }
        return RouteTarget.builder()
                .provider("openai")
                .baseUrl(baseUrl)
                .apiKey(accessToken)
                .authScheme("api_key")
                .autoSwitchOnRateLimit(true)
                .build();
    }
}
