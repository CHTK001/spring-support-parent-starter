package com.chua.starter.ai.support.spi.impl;

import com.chua.common.support.ai.chat.route.RouteTarget;
import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.ai.support.manage.common.ApiErrorCode;
import com.chua.starter.ai.support.manage.common.ApiException;
import com.chua.starter.ai.support.spi.AccountChannelAuthManager;
import com.chua.starter.common.support.text.json.Json;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Cline 账号渠道鉴权管理器实现。
 *
 * <p>协议三步（对齐 {@code dsh-our-free-model} 的 {@code cline-oauth.ts}）：</p>
 * <ol>
 *   <li>{@code POST {workOsBase}/user_management/authorize/device}（表单 {@code client_id}）
 *       → {@code {device_code, user_code, verification_uri, verification_uri_complete, expires_in, interval}}</li>
 *   <li>轮询 {@code POST {workOsBase}/user_management/authenticate}（表单
 *       {@code grant_type=urn:ietf:params:oauth:grant-type:device_code & device_code & client_id}）
 *       → {@code {access_token, refresh_token}}；5 秒间隔，300 秒过期，连续 5 次网络失败视为失败</li>
 *   <li>{@code POST {apiBase}/api/v1/auth/register}（JSON 驼峰 {@code accessToken/refreshToken}）
 *       → {@code {success, data:{accessToken(带 workos: 前缀), refreshToken, expiresAt, userInfo}}}</li>
 * </ol>
 *
 * <p>⚠️ 三个易踩点（源码注释原样保留）：</p>
 * <ul>
 *   <li>访问令牌<b>必须保留</b> {@code workos:} 前缀（剥掉即 401，且 401 文案误导）；</li>
 *   <li>续期请求体字段是<b>驼峰</b> {@code refreshToken}/{@code grantType}；</li>
 *   <li>注册/续期响应套 {@code {success, data}} 信封，字段名是驼峰 {@code accessToken}。</li>
 * </ul>
 *
 * <p>余额：{@code GET {apiBase}/api/v1/users/{account_id}/balance}（⚠️ 用凭据的
 * {@code account_id}，不是 JWT 的 {@code sub}）。签到端点不存在，故不实现 checkin。</p>
 *
 * @since 4.0.0.43
 * @author CH
 */
@Spi("cline")
public class ClineAccountChannelAuthManager implements AccountChannelAuthManager {

    /** Cline API 基址（推理 / 账号端点 / 注册 / 续期全挂它下面）。 */
    static final String API_BASE = "https://api.cline.bot";
    /** WorkOS 基址（设备码授权与 token 轮询走它）。 */
    static final String WORKOS_BASE = "https://api.workos.com";
    /** WorkOS client id（prod；与凭据 JWT 的 client_id claim 一致）。 */
    static final String WORKOS_CLIENT_ID = "client_01K3A541FN8TA3EPPHTD2325AR";
    /** 访问令牌前缀，必须保留。 */
    static final String TOKEN_PREFIX = "workos:";

    /** 设备码授权路径（挂 workOsBase）。 */
    static final String DEVICE_AUTHORIZATION_PATH = "/user_management/authorize/device";
    /** 设备码 token 轮询路径（挂 workOsBase）。 */
    static final String DEVICE_AUTHENTICATE_PATH = "/user_management/authenticate";
    /** 注册 WorkOS token 换 Cline 自己的 token（挂 apiBase）。 */
    static final String REGISTER_PATH = "/api/v1/auth/register";
    /** 续期（挂 apiBase）。 */
    static final String REFRESH_PATH = "/api/v1/auth/refresh";
    /** 当前账号探活（挂 apiBase）。 */
    static final String ME_PATH = "/api/v1/users/me";
    /** 模型目录（挂 apiBase；460 个 id，不含免费前缀条目）。 */
    static final String MODELS_PATH = "/api/v1/models";
    /** 推荐模型（挂 apiBase；free 数组是免费模型的权威来源）。 */
    static final String RECOMMENDED_MODELS_PATH = "/api/v1/ai/cline/recommended-models";

    /** 客户端标识请求头（照官方原样下发，缺失可能被网关策略拒/降级）。 */
    static final Map<String, String> CLIENT_HEADERS = Map.of(
            "HTTP-Referer", "https://cline.bot",
            "X-Title", "Cline",
            "X-IS-MULTIROOT", "false",
            "X-CLIENT-TYPE", "cline-sdk");

    /** 轮询间隔（毫秒）。 */
    static final long POLL_INTERVAL_MS = 5_000L;
    /** 设备码默认有效期（毫秒）。 */
    static final long DEVICE_EXPIRES_MS = 300_000L;
    /** 连续网络失败上限。 */
    static final int POLL_MAX_FAILURES = 5;
    /** 轮询句柄里记录的最后一次轮询时刻（毫秒），用于控制服务端调用频率。 */
    static final String HANDLE_LAST_POLL_AT = "__lastPollAt";

    @Override
    public String channel() {
        return "cline";
    }

    @Override
    public String displayName() {
        return "Cline";
    }

    /**
     * {@inheritDoc}
     *
     * <p>设备码渠道：先向 WorkOS 申请设备码，返回 {@code verifyUrl} 供用户打开完成授权。</p>
     */
    @Override
    public Map<String, Object> startLogin() {
        String body = "client_id=" + encode(WORKOS_CLIENT_ID);
        String response = ChannelHttp.postForm(
                WORKOS_BASE + DEVICE_AUTHORIZATION_PATH, body, formHeaders());

        Map<String, Object> payload = parseJson(response);
        String deviceCode = readString(payload, "device_code");
        String userCode = readString(payload, "user_code");
        String verificationUri = readString(payload, "verification_uri");
        String verificationUriComplete = readString(payload, "verification_uri_complete");
        if (isBlank(deviceCode) || isBlank(userCode) || isBlank(verificationUri)) {
            throw new ApiException(ApiErrorCode.UPSTREAM_ERROR,
                    "Cline 设备码授权响应缺少 device_code / user_code / verification_uri: "
                            + ChannelHttp.truncate(response));
        }

        Map<String, Object> handle = new LinkedHashMap<>();
        handle.put("handle", deviceCode);
        handle.put("user_code", userCode);
        handle.put("verifyUrl", isBlank(verificationUriComplete) ? verificationUri : verificationUriComplete);
        handle.put("expires_in", pollExpiryMs(payload));
        handle.put("interval", pollIntervalMs(payload));
        handle.put("startedAt", System.currentTimeMillis());
        return handle;
    }

    /**
     * {@inheritDoc}
     *
     * <p>按 5 秒间隔轮询 WorkOS authenticate 端点；拿到 WorkOS token 后
     * 立即走 Cline 注册端点换出可持久化凭据。轮询间隔短于前端轮询周期时
     * 直接返回 {@code null}（待确认），不重复打上游。</p>
     */
    @Override
    public Map<String, Object> pollLogin(Map<String, Object> handle) {
        if (handle == null || isBlank(readString(handle, "handle"))) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "Cline 登录句柄缺失 handle");
        }
        long now = System.currentTimeMillis();
        Long lastPollAt = toLong(handle.get(HANDLE_LAST_POLL_AT));
        long interval = POLL_INTERVAL_MS;
        Object intervalRaw = handle.get("interval");
        if (intervalRaw instanceof Number n && n.longValue() > 0) {
            interval = n.longValue();
        }
        if (lastPollAt != null && now - lastPollAt < interval) {
            return null;
        }

        String deviceCode = readString(handle, "handle");
        String body = "grant_type=" + encode("urn:ietf:params:oauth:grant-type:device_code")
                + "&device_code=" + encode(deviceCode)
                + "&client_id=" + encode(WORKOS_CLIENT_ID);
        String response;
        try {
            response = ChannelHttp.postForm(
                    WORKOS_BASE + DEVICE_AUTHENTICATE_PATH, body, formHeaders());
        } catch (ApiException e) {
            // ⚠️ 用户尚未在授权页确认时，WorkOS 回 HTTP 400 + authorization_pending，
            // 这是设备码流的正常等待态（不是失败）。只有其它错误才向上抛。
            if (isPendingApproval(e)) {
                handle.put(HANDLE_LAST_POLL_AT, now);
                return null;
            }
            // 上游限流同样是「继续等」，不是失败。
            if (ChannelHttp.isRateLimited(e)) {
                handle.put(HANDLE_LAST_POLL_AT, now);
                return null;
            }
            throw e;
        }

        Map<String, Object> payload = parseJson(response);
        String accessToken = readString(payload, "access_token");
        String refreshToken = readString(payload, "refresh_token");
        if (isBlank(accessToken) || isBlank(refreshToken)) {
            // WorkOS 在用户未完成授权前返回 authorization_pending 等错误体，继续等待。
            handle.put(HANDLE_LAST_POLL_AT, now);
            return null;
        }

        // 步骤 3：用 WorkOS token 换 Cline 自己的 token（响应 {success,data} 信封）。
        Map<String, Object> registerBody = new LinkedHashMap<>();
        registerBody.put("accessToken", accessToken);
        registerBody.put("refreshToken", refreshToken);
        String registered = ChannelHttp.postJson(
                API_BASE + REGISTER_PATH, Json.toJSONString(registerBody), clientHeaders());

        Map<String, Object> credential = parseClineTokenResponse(registered);
        if (isBlank(readString(credential, "access_token"))) {
            throw new ApiException(ApiErrorCode.UPSTREAM_ERROR,
                    "Cline 登录注册响应缺少 accessToken: " + ChannelHttp.truncate(registered));
        }
        return credential;
    }

    /**
     * {@inheritDoc}
     *
     * <p>请求体字段是<b>驼峰</b> {@code refreshToken}/{@code grantType}；
     * 响应解析同 {@link #pollLogin} 的注册步骤。保留原凭据里的
     * {@code account_id}/{@code email}/{@code nickname}（续期响应可能缺 userInfo）。</p>
     */
    @Override
    public Map<String, Object> refresh(Map<String, Object> credentials) {
        String refreshToken = readString(credentials, "refresh_token");
        if (isBlank(refreshToken)) {
            throw new ApiException(ApiErrorCode.UNAUTHORIZED, "Cline 凭据缺少 refresh_token，无法续期");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("refreshToken", refreshToken);
        body.put("grantType", "refresh_token");
        String response = ChannelHttp.postJson(API_BASE + REFRESH_PATH, Json.toJSONString(body), clientHeaders());
        Map<String, Object> refreshed = parseClineTokenResponse(response);
        if (isBlank(readString(refreshed, "access_token"))) {
            throw new ApiException(ApiErrorCode.UPSTREAM_ERROR,
                    "Cline 续期响应缺少 accessToken: " + ChannelHttp.truncate(response));
        }
        // 保留不在续期响应里的展示与账号字段。
        copyIfPresent(credentials, refreshed, "account_id");
        copyIfPresent(credentials, refreshed, "email");
        copyIfPresent(credentials, refreshed, "nickname");
        return refreshed;
    }

    /**
     * {@inheritDoc}
     *
     * <p>本地到期优先：凭据带 {@code expire_time} 且已过期直接判 {@code expired}；
     * 否则探活 {@code GET /api/v1/users/me}（带 {@code workos:} 前缀令牌 + 客户端标识头），
     * 401 判 {@code invalid}，2xx 判 {@code valid}。</p>
     */
    @Override
    public Map<String, Object> status(Map<String, Object> credentials) {
        String accessToken = readString(credentials, "access_token");
        if (isBlank(accessToken)) {
            return status("invalid", "凭据缺少 access_token");
        }
        Long expireTime = toLong(credentials.get("expire_time"));
        if (expireTime != null && expireTime <= System.currentTimeMillis()) {
            return status("expired", "访问令牌已到期");
        }
        String response;
        try {
            response = ChannelHttp.getJson(API_BASE + ME_PATH, authHeaders(accessToken));
        } catch (ApiException e) {
            String message = e.getMessage() == null ? "" : e.getMessage();
            if (message.contains("401") || message.contains("403") || message.contains("Unauthorized")) {
                return status("invalid", "探活 401/403: " + message);
            }
            return status("valid", "探活失败（网络），按本地有效期视为可用: " + message);
        }
        Map<String, Object> result = status("valid", "探活成功");
        Map<String, Object> payload = parseJson(response);
        Map<String, Object> data = dataOf(payload);
        if (data != null) {
            String id = readString(data, "id");
            if (id != null) { result.put("account_id", id); }
            String email = readString(data, "email");
            if (email != null) { result.put("email", email); }
        }
        return result;
    }

    /**
     * {@inheritDoc}
     *
     * <p>查积分余额（非签到）：{@code GET /api/v1/users/{account_id}/balance}，
     * 把原始值与归一化值（÷100000，⚠️ 单位不确定，保留原始值供核对）并入结果。
     * 余额查询失败不抛错，返回 {@code balance=null + error}。</p>
     */
    @Override
    public Map<String, Object> checkin(Map<String, Object> credentials) {
        Map<String, Object> result = new LinkedHashMap<>(credentials);
        String accessToken = readString(credentials, "access_token");
        String accountId = readString(credentials, "account_id");
        if (isBlank(accessToken) || isBlank(accountId)) {
            result.put("balance", null);
            result.put("balanceError", "凭据缺少 access_token 或 account_id，无法查询余额");
            return result;
        }
        String url = API_BASE + "/api/v1/users/" + encode(accountId.trim()) + "/balance";
        try {
            String response = ChannelHttp.getJson(url, authHeaders(accessToken));
            Map<String, Object> payload = parseJson(response);
            Map<String, Object> data = dataOf(payload);
            if (data != null) {
                Object raw = data.get("balance");
                if (raw instanceof Number number) {
                    result.put("balance", number.doubleValue() / 100_000d);
                    result.put("rawBalance", number.longValue());
                }
            }
        } catch (ApiException e) {
            result.put("balance", null);
            result.put("balanceError", e.getMessage());
        }
        return result;
    }

    @Override
    public boolean supportsCheckin() {
        // Cline 没有签到端点（源码全量扫描无 checkin/campaign 命中），只有余额查询。
        return false;
    }

    /**
     * {@inheritDoc}
     *
     * <p>合并远端 {@code recommended-models} 的 {@code free} 数组（免费模型权威来源）
     * 与 {@code /models} 目录；每个模型以完整目录 id（如 {@code cline-free/deepseek-v4.1-flash}）
     * 为 key，保证模型管理按 (provider, model) 落库。远端失败时返回空列表不抛错。</p>
     */
    @Override
    public List<Map<String, Object>> listModels(Map<String, Object> credentials) {
        String accessToken = readString(credentials, "access_token");
        if (isBlank(accessToken)) {
            return List.of();
        }
        Set<String> remoteFreeIds = new java.util.LinkedHashSet<>();
        List<Map<String, Object>> models = new java.util.ArrayList<>();
        Map<String, Map<String, Object>> byId = new LinkedHashMap<>();

        // 免费模型（权威）：recommended-models 的 free 数组。
        try {
            String response = ChannelHttp.getJson(API_BASE + RECOMMENDED_MODELS_PATH, authHeaders(accessToken));
            Map<String, Object> payload = parseJson(response);
            Map<String, Object> envelope = dataOf(payload);
            Object free = envelope == null ? null : envelope.get("free");
            if (free instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Map<?, ?> entry && entry.get("id") != null) {
                        String id = String.valueOf(entry.get("id"));
                        remoteFreeIds.add(id);
                        byId.put(id, modelEntry(id, entry, true));
                    }
                }
            }
        } catch (ApiException e) {
            // 免费目录拉取失败不致命：继续合并 /models。
        }

        // 全量目录：/models（460 个 id，不含 cline-free/*）。
        try {
            String response = ChannelHttp.getJson(API_BASE + MODELS_PATH, authHeaders(accessToken));
            Map<String, Object> payload = parseJson(response);
            Object ids = payload.get("data");
            if (ids instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Map<?, ?> entry && entry.get("id") != null) {
                        String id = String.valueOf(entry.get("id"));
                        if (!byId.containsKey(id)) {
                            byId.put(id, modelEntry(id, entry,
                                    id.endsWith(":free") || id.startsWith("cline-free/")));
                        }
                    }
                }
            }
        } catch (ApiException e) {
            // 全量目录拉取失败不致命。
        }

        models.addAll(byId.values());
        return models;
    }

    // ── 私有辅助 ──

    /** 解析注册/续期响应（{success,data} 信封或裸响应，字段驼峰），归一为本模块凭据 Map。 */
    private static Map<String, Object> parseClineTokenResponse(String response) {
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Object> payload = parseJson(response);
        Map<String, Object> inner = dataOf(payload);
        Map<String, Object> source = inner != null ? inner : payload;
        String accessToken = readString(source, "accessToken") != null ? readString(source, "accessToken") : readString(source, "access_token");
        if (accessToken != null) {
            // 幂等补齐 workos: 前缀（服务端已带则原样保留）。
            result.put("access_token", accessToken.startsWith(TOKEN_PREFIX) ? accessToken : TOKEN_PREFIX + accessToken);
        }
        String refreshToken = readString(source, "refreshToken") != null ? readString(source, "refreshToken") : readString(source, "refresh_token");
        if (refreshToken != null) {
            result.put("refresh_token", refreshToken);
        }
        Object expiresAt = source.get("expiresAt") != null ? source.get("expiresAt") : source.get("expires_at");
        Long expireTimeMs = toMillis(expiresAt);
        if (expireTimeMs != null) {
            result.put("expire_time", expireTimeMs);
        }
        Map<String, Object> userInfo = source.get("userInfo") instanceof Map<?, ?> ui ? (Map<String, Object>) ui : Map.of();
        String accountId = userInfo.get("clineUserId") != null
                ? String.valueOf(userInfo.get("clineUserId"))
                : (source.get("accountId") != null ? String.valueOf(source.get("accountId")) : null);
        if (accountId != null) {
            result.put("account_id", accountId);
            result.putIfAbsent("nickname", accountId);
        }
        String email = userInfo.get("email") instanceof String e ? e : (source.get("email") instanceof String es ? es : null);
        if (email != null) {
            result.put("email", email);
            result.putIfAbsent("nickname", email);
        }
        return result;
    }

    private static Map<String, Object> modelEntry(String id, Map<?, ?> entry, boolean free) {
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("id", id);
        model.put("name", entry.get("name") instanceof String s && !s.isBlank() ? s : id);
        if (entry.get("description") instanceof String d) {
            model.put("description", d);
        }
        model.put("isFree", free);
        return model;
    }

    private static Map<String, String> clientHeaders() {
        Map<String, String> headers = new HashMap<>(CLIENT_HEADERS);
        headers.put("Content-Type", "application/json");
        headers.put("Accept", "application/json");
        return headers;
    }

    private static Map<String, String> formHeaders() {
        return Map.of("Content-Type", "application/x-www-form-urlencoded", "Accept", "application/json");
    }

    /** 带 {@code workos:} 前缀令牌的鉴权头（前缀必须保留，剥掉即 401）。 */
    private static Map<String, String> authHeaders(String accessToken) {
        String token = accessToken == null ? "" : accessToken.trim();
        if (token.isEmpty()) {
            token = TOKEN_PREFIX;
        }
        String bearer = token.startsWith(TOKEN_PREFIX) ? token : TOKEN_PREFIX + token;
        Map<String, String> headers = new HashMap<>(CLIENT_HEADERS);
        headers.put("Authorization", "Bearer " + bearer);
        headers.put("Accept", "application/json");
        return headers;
    }

    private static Map<String, Object> status(String state, String detail) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("state", state);
        result.put("detail", detail);
        return result;
    }

    /** 从 payload 里读 expires_in（秒/毫秒），归一为毫秒上限。 */
    private static long pollExpiryMs(Map<String, Object> payload) {
        Long ms = toMillis(payload.get("expires_in"));
        return ms != null ? ms : DEVICE_EXPIRES_MS;
    }

    private static long pollIntervalMs(Map<String, Object> payload) {
        Long ms = toMillis(payload.get("interval"));
        return ms != null ? ms : POLL_INTERVAL_MS;
    }

    /** 时间值归一为毫秒（数字 <1e12 视为秒；ISO 字符串按 Date.parse）。 */
    private static Long toMillis(Object value) {
        if (value instanceof Number number && number.doubleValue() > 0) {
            double v = number.doubleValue();
            return v < 1e12 ? Math.round(v * 1000d) : Math.round(v);
        }
        if (value instanceof String s && !s.isBlank()) {
            long parsed = java.time.Instant.parse(s.trim()).toEpochMilli();
            return parsed;
        }
        return null;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /**
     * 判断异常是否为设备码「等待用户授权」态。
     *
     * <p>WorkOS 用 HTTP 400 + {@code authorization_pending} / {@code slow_down}
     * 表示仍在等待，两者都应继续轮询；其余错误（如 {@code access_denied}、
     * {@code expired_token}）才是真失败。</p>
     *
     * @param e 上游异常
     * @return true 表示仍在等待
     */
    private static boolean isPendingApproval(ApiException e) {
        String message = e.getMessage();
        if (message == null) {
            return false;
        }
        String lower = message.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("authorization_pending") || lower.contains("slow_down");
    }

    private static void copyIfPresent(Map<String, Object> from, Map<String, Object> to, String key) {
        Object value = from.get(key);
        if (value != null) {
            to.put(key, value);
        }
    }

    private static Map<String, Object> parseJson(String response) {
        Map<String, Object> map = Json.getJsonObject(response);
        return map != null ? map : new LinkedHashMap<>();
    }

    /** 读 {success,data} 信封的 data；无信封时返回 null（调用方回退顶层）。 */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> dataOf(Map<String, Object> payload) {
        return payload.get("data") instanceof Map<?, ?> m ? (Map<String, Object>) m : null;
    }

    /** 读字符串字段（容忍缺失/非字符串），返回可选值。 */
    private static String readString(Map<String, Object> source, String key) {
        Object value = source == null ? null : source.get(key);
        if (value instanceof String s && !s.isBlank()) {
            return s;
        }
        return null;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static Long toLong(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        if (value instanceof String s && !s.isBlank()) {
            try {
                return Long.parseLong(s.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    @Override
    public RouteTarget toRouteTarget(Map<String, Object> credentials) {
        String accessToken = readString(credentials, "access_token");
        if (isBlank(accessToken)) {
            return null;
        }
        return RouteTarget.builder()
                .provider("openai")
                .baseUrl(API_BASE + "/api/v1")
                .apiKey(accessToken)
                .authScheme("api_key")
                .autoSwitchOnRateLimit(true)
                .build();
    }
}
