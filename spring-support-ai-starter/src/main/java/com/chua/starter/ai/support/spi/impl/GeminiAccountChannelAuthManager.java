package com.chua.starter.ai.support.spi.impl;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.ai.support.manage.common.ApiErrorCode;
import com.chua.starter.ai.support.manage.common.ApiException;
import com.chua.starter.ai.support.spi.AccountChannelAuthManager;
import com.chua.starter.common.support.text.json.Json;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Gemini (Google Cloud Code Assist 免费线) 账号渠道鉴权管理器实现。
 *
 * <p>协议（对齐 {@code dsh-our-free-model} 的 {@code gemini-oauth.ts} / {@code gemini.ts}）：</p>
 * <ol>
 *   <li>{@code startLogin}：本地生成 {@code state}（16 字节随机 hex），拼 Google
 *       授权 URL（六个 scope + {@code access_type=offline} + {@code prompt=consent}）；
 *       回调走前端粘贴：用户完成浏览器授权后，把重定向到
 *       {@code http://localhost:<port>/oauth-callback?code=...&state=...} 的 URL
 *       粘贴回来。回调的 {@code code} 与 {@code state} 由 {@code pollLogin} 处理。</li>
 *   <li>{@code pollLogin}：用粘贴回的 {@code code} 调 Google token 端点换
 *       {@code access_token}/{@code refresh_token}（表单<b>必须</b>带
 *       {@code client_secret}，缺了会静默失败：浏览器显示成功但账号不出现）。
 *       身份从 {@code id_token}（OIDC，授权 URL 带 {@code openid} scope 故必然返回）
 *       解出 {@code sub}/{@code email}，解不出时兜底一次 userinfo。</li>
 *   <li>{@code refresh}：用 {@code refresh_token} 续期（同样必须带
 *       {@code client_secret}）；Google <b>偶尔轮换</b> refresh_token，
 *       响应里给了新值必须回写，否则下次续期用的是已作废的旧值。</li>
 *   <li>{@code status}：本地到期判定（{@code expiry} RFC3339 时间戳 + 60s 余量）；
 *       配额端点探活不可靠（403 {@code SUBSCRIPTION_REQUIRED} 需带 project，
 *       见 {@code gemini-credits.ts}），故不做 401 探活，本地判定够用。</li>
 *   <li>{@code checkin}：Gemini 无每日签到/免费额度领取机制（配额按 5 小时窗口
 *       重置），不支持签到。</li>
 *   <li>{@code listModels}：静态模型表（上游模型名是准入钥匙，档位后缀拼接由
 *       provider 适配层负责）；不拉远端目录。</li>
 * </ol>
 *
 * <p>凭据键（snake_case，与上游 JSON 逐字一致）：</p>
 * <ul>
 *   <li>{@code access_token} / {@code refresh_token} / {@code token_type} /
 *       {@code expires_in}（秒）/ {@code expiry}（RFC3339）/ {@code scope}</li>
 *   <li>{@code sub}（OIDC 稳定账号主键）/ {@code email}（展示名）</li>
 *   <li>{@code client_id} / {@code client_secret}（登录时写入，续期必需）</li>
 * </ul>
 *
 * @since 4.0.0.43
 * @author CH
 */
@Spi("gemini")
public class GeminiAccountChannelAuthManager implements AccountChannelAuthManager {

    static final String AUTH_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth";
    static final String TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token";
    static final String USERINFO_ENDPOINT = "https://www.googleapis.com/oauth2/v2/userinfo";
    static final String CALLBACK_PATH = "/oauth-callback";
    static final String REDIRECT_HOST = "localhost";
    /**
     * 回调端口（固定）。
     *
     * <p>⚠️ Google 要求 {@code redirect_uri} 与客户端注册值<b>完全一致</b>，
     * 所以这个端口不能回退到随机端口——被占用只能让用户先释放 8845。</p>
     */
    static final int CALLBACK_PORT = 8845;

    /** 上游 Cloud Code 客户端的公开 client（与 gemini-oauth.ts 默认值一致）。 */
    static final String DEFAULT_CLIENT_ID =
            "1071006060591-tmhssin2h21lcre235vtolojh4g403ep.apps.googleusercontent.com";
    static final String DEFAULT_CLIENT_SECRET = "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";

    /** 六项 scope（逐字照抄；cloud-platform 必需，后两项是 Cloud Code 自己的通道）。 */
    static final String SCOPES = String.join(" ",
            "openid",
            "https://www.googleapis.com/auth/cloud-platform",
            "https://www.googleapis.com/auth/userinfo.email",
            "https://www.googleapis.com/auth/userinfo.profile",
            "https://www.googleapis.com/auth/cclog",
            "https://www.googleapis.com/auth/experimentsandconfigs");

    /** 凭据过期余量（毫秒，同原版 {@code Credentials.Valid()} 留 60 秒）。 */
    static final long EXPIRY_LEAD_MS = 60_000L;

    /** 登录句柄：state（回调必须原样带回，防伪造）。 */
    static final String HANDLE_STATE = "state";

    @Override
    public String channel() {
        return "gemini";
    }

    @Override
    public String displayName() {
        return "Gemini";
    }

    @Override
    public String loginMethod() {
        return "browser-oauth";
    }

    /**
     * {@inheritDoc}
     *
     * <p>本地生成 state（16 字节随机 hex），拼 Google 授权 URL。回调由后端
     * <b>自己监听</b> {@code localhost:8845/oauth-callback}（{@link OauthCallbackServer}）：
     * Google 要求 {@code redirect_uri} 与注册值完全一致，故端口不能回退，
     * 被占用时只能提示失败（避免把用户导向一个打不通的地址）。
     * {@code pollLogin} 自动取监听器收到的回调，用户无需粘贴。</p>
     */
    @Override
    public Map<String, Object> startLogin() {
        byte[] stateBytes = new byte[16];
        new java.security.SecureRandom().nextBytes(stateBytes);
        StringBuilder state = new StringBuilder(32);
        for (byte b : stateBytes) {
            state.append(String.format("%02x", b));
        }

        OauthCallbackServer.Binding binding = OauthCallbackServer.ensure(channel(), CALLBACK_PORT, false);

        String query = "client_id=" + encode(DEFAULT_CLIENT_ID)
                + "&response_type=code"
                + "&redirect_uri=" + encode("http://" + REDIRECT_HOST + ":" + CALLBACK_PORT + CALLBACK_PATH)
                + "&scope=" + encode(SCOPES)
                + "&state=" + state
                + "&access_type=offline"
                + "&include_granted_scopes=true"
                + "&prompt=consent";
        String loginUrl = AUTH_ENDPOINT + "?" + query;

        Map<String, Object> handle = new LinkedHashMap<>();
        handle.put("handle", "gemini-oauth");
        handle.put(HANDLE_STATE, state.toString());
        handle.put("callbackListening", binding.alive());
        handle.put("callbackPort", binding.port());
        handle.put("verifyUrl", loginUrl);
        handle.put("loginUrl", loginUrl); // 旧键兼容
        handle.put("startedAt", System.currentTimeMillis());
        return handle;
    }

    /**
     * {@inheritDoc}
     *
     * <p>{@code handle} 的形态：优先取后端本地回调监听器收到的浏览器跳转
     * （{@code http://localhost:8845/oauth-callback?code=...&state=...}）；
     * 监听端口不可用时回退到前端粘贴的 {@code callbackUrl}。
     * 本方法解析 {@code code}/{@code state}，校验 state 后调 token 端点换凭据。
     * state 不匹配一律拒绝（防伪造回调）。</p>
     */
    @Override
    public Map<String, Object> pollLogin(Map<String, Object> handle) {
        if (handle == null || isBlank(readString(handle, "handle"))) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "Gemini 登录句柄缺失 handle");
        }
        String callbackUrl = handle.get("callbackUrl") instanceof String s && !s.isBlank()
                ? s.trim()
                : null;
        if (callbackUrl == null) {
            String query = OauthCallbackServer.take(channel());
            if (query != null) {
                callbackUrl = "http://" + REDIRECT_HOST + ":" + CALLBACK_PORT + CALLBACK_PATH + "?" + query;
            }
        }
        if (callbackUrl == null) {
            // 尚未收到回调（用户还没完成授权）→ 继续等待。
            return null;
        }
        Map<String, String> params = parseCallbackUrl(callbackUrl);
        String error = params.get("error");
        if (error != null) {
            String description = params.getOrDefault("error_description", "");
            throw new ApiException(ApiErrorCode.UNAUTHORIZED,
                    "Google 授权被拒绝: " + (description.isEmpty() ? error : error + ": " + description));
        }
        String state = params.get("state");
        String expectedState = readString(handle, HANDLE_STATE);
        if (expectedState != null && !expectedState.equals(state)) {
            throw new ApiException(ApiErrorCode.UNAUTHORIZED, "回调 state 校验失败（疑似伪造回调）");
        }
        String code = params.get("code");
        if (code == null) {
            return null; // 回调缺少 code（用户未完成授权），继续等待。
        }

        Map<String, String> form = new LinkedHashMap<>();
        form.put("client_id", DEFAULT_CLIENT_ID);
        form.put("client_secret", DEFAULT_CLIENT_SECRET);
        form.put("code", code);
        form.put("grant_type", "authorization_code");
        form.put("redirect_uri", "http://" + REDIRECT_HOST + ":" + CALLBACK_PORT + CALLBACK_PATH);
        String response = ChannelHttp.postForm(TOKEN_ENDPOINT, formString(form), null);
        Map<String, Object> credential = parseTokenGrant(response, null);
        credential.put("client_id", DEFAULT_CLIENT_ID);
        credential.put("client_secret", DEFAULT_CLIENT_SECRET);
        return credential;
    }

    /**
     * {@inheritDoc}
     *
     * <p>续期表单<b>必须</b>带 {@code client_secret}（缺了是静默症状：
     * 浏览器成功但账号不出现）。Google 偶尔轮换 refresh_token，
     * 新值非空时必须回写。</p>
     */
    @Override
    public Map<String, Object> refresh(Map<String, Object> credentials) {
        String refreshToken = readString(credentials, "refresh_token");
        if (isBlank(refreshToken)) {
            throw new ApiException(ApiErrorCode.UNAUTHORIZED,
                    "Gemini 凭据缺少 refresh_token，需要重新授权");
        }
        String clientId = readString(credentials, "client_id");
        String clientSecret = readString(credentials, "client_secret");
        Map<String, String> form = new LinkedHashMap<>();
        form.put("client_id", isBlank(clientId) ? DEFAULT_CLIENT_ID : clientId);
        form.put("client_secret", isBlank(clientSecret) ? DEFAULT_CLIENT_SECRET : clientSecret);
        form.put("refresh_token", refreshToken);
        form.put("grant_type", "refresh_token");
        String response = ChannelHttp.postForm(TOKEN_ENDPOINT, formString(form), null);
        Map<String, Object> refreshed = parseTokenGrant(response, refreshToken);
        // 保留不在续期响应里的身份字段。
        copyIfPresent(credentials, refreshed, "sub");
        copyIfPresent(credentials, refreshed, "email");
        refreshed.putIfAbsent("client_id", isBlank(clientId) ? DEFAULT_CLIENT_ID : clientId);
        refreshed.putIfAbsent("client_secret", isBlank(clientSecret) ? DEFAULT_CLIENT_SECRET : clientSecret);
        return refreshed;
    }

    /**
     * {@inheritDoc}
     *
     * <p>本地到期判定：{@code expiry}（RFC3339）+ 60s 余量；无 expiry 时不做
     * 探活（配额端点 403 需带 project，见 gemini-credits.ts，不可靠）。</p>
     */
    @Override
    public Map<String, Object> status(Map<String, Object> credentials) {
        String accessToken = readString(credentials, "access_token");
        if (isBlank(accessToken)) {
            return status("invalid", "凭据缺少 access_token");
        }
        String expiry = readString(credentials, "expiry");
        if (expiry != null) {
            Long expiryMs = parseInstant(expiry);
            if (expiryMs != null && expiryMs - System.currentTimeMillis() <= EXPIRY_LEAD_MS) {
                return status("expired", "访问令牌已到期（含 60s 余量）");
            }
        }
        return status("valid", "本地有效期判定可用");
    }

    @Override
    public boolean supportsCheckin() {
        // Gemini 无每日签到（配额按 5 小时窗口重置），不支持。
        return false;
    }

    /**
     * {@inheritDoc}
     *
     * <p>静态模型表（对齐 {@code gemini.ts} 的 {@code GEMINI_FALLBACK_MODELS}）：
     * 主模型 {@code gemini-3.8-flash}，四档思考预算走 efforts，不暴露带档位后缀的
     * 模型名，不暴露 lite（上游恒 404）。不拉远端目录。</p>
     */
    @Override
    public List<Map<String, Object>> listModels(Map<String, Object> credentials) {
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("id", "gemini-3.8-flash");
        model.put("name", "Gemini 3.8 Flash");
        model.put("contextWindow", 1_000_000);
        model.put("isFree", true);
        return List.of(model);
    }

    // ── 私有辅助 ──

    /**
     * 解析 Google token 端点响应，归一为凭据 Map。
     *
     * <p>三种失败形态都要给能定位问题的文案（原版口径）：
     * 解析不了 / {@code error} 字段 / 缺 {@code access_token}。</p>
     *
     * @param response 响应体
     * @param previousRefreshToken 旧 refresh_token（响应未轮换时保留）
     */
    private Map<String, Object> parseTokenGrant(String response, String previousRefreshToken) {
        Map<String, Object> payload = parseJson(response);
        String errorCode = readString(payload, "error");
        if (errorCode != null) {
            String description = readString(payload, "error_description");
            throw new ApiException(ApiErrorCode.UNAUTHORIZED,
                    "Gemini token 端点错误: " + (description == null ? errorCode : errorCode + ": " + description));
        }
        String accessToken = readString(payload, "access_token");
        if (accessToken == null) {
            throw new ApiException(ApiErrorCode.UPSTREAM_ERROR,
                    "Gemini token 端点未返回 access_token: " + ChannelHttp.truncate(response));
        }
        Map<String, Object> credential = new LinkedHashMap<>();
        credential.put("access_token", accessToken);
        credential.put("token_type", readString(payload, "token_type") != null
                ? readString(payload, "token_type") : "Bearer");
        Object expiresIn = payload.get("expires_in");
        credential.put("expires_in", expiresIn instanceof Number n ? n.intValue() : 3600);
        // RFC3339 expiry（原版 credentialFromGeminiGrant 的 geminiExpiryFrom）。
        long seconds = expiresIn instanceof Number n ? n.intValue() : 3600;
        credential.put("expiry", java.time.Instant.now().plusSeconds(seconds > 0 ? seconds : 3600)
                .toString());
        String refreshToken = readString(payload, "refresh_token");
        if (refreshToken == null) {
            refreshToken = previousRefreshToken;
        }
        if (refreshToken != null) {
            credential.put("refresh_token", refreshToken);
        }
        String scope = readString(payload, "scope");
        if (scope != null) {
            credential.put("scope", scope);
        }
        // 身份优先取 id_token（与 access_token 同一次响应，零额外请求）。
        String idToken = readString(payload, "id_token");
        Map<String, String> identity = idToken == null ? Map.of() : identityFromIdToken(idToken);
        if (identity.get("sub") != null) {
            credential.put("sub", identity.get("sub"));
        }
        if (identity.get("email") != null) {
            credential.put("email", identity.get("email"));
            credential.putIfAbsent("nickname", identity.get("email"));
        }
        return credential;
    }

    /** 从 OIDC id_token 解出 sub / email（base64url 第二段 JSON）。 */
    private static Map<String, String> identityFromIdToken(String idToken) {
        try {
            String[] parts = idToken.split("\\.");
            if (parts.length < 2) {
                return Map.of();
            }
            byte[] decoded = java.util.Base64.getUrlDecoder().decode(parts[1]);
            Map<String, Object> payload = Json.getJsonObject(new String(decoded, java.nio.charset.StandardCharsets.UTF_8));
            Map<String, String> result = new LinkedHashMap<>();
            if (payload != null) {
                String sub = readString(payload, "sub");
                if (sub != null) {
                    result.put("sub", sub);
                }
                String email = readString(payload, "email");
                if (email != null) {
                    result.put("email", email);
                }
            }
            return result;
        } catch (IllegalArgumentException e) {
            return Map.of();
        }
    }

    /** 解析粘贴回来的回调 URL 的 query 参数（{@code code}/{@code state}/{@code error}）。 */
    private static Map<String, String> parseCallbackUrl(String rawUrl) {
        String trimmed = rawUrl.trim();
        int queryIndex = trimmed.indexOf('?');
        if (queryIndex >= 0) {
            trimmed = trimmed.substring(queryIndex + 1);
        } else if (trimmed.startsWith("http")) {
            return Map.of(); // 完整 URL 但没有 query，没有 code。
        } else if (!trimmed.startsWith("code=") && !trimmed.startsWith("error=")) {
            return Map.of(); // 用户可能只粘了 query 部分（code=...&state=...）。
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (String pair : trimmed.split("&")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = decode(pair.substring(0, eq));
            String value = decode(pair.substring(eq + 1));
            result.put(key, value);
        }
        return result;
    }

    private static Map<String, Object> status(String state, String detail) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("state", state);
        result.put("detail", detail);
        return result;
    }

    private static String formString(Map<String, String> form) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : form.entrySet()) {
            if (!sb.isEmpty()) {
                sb.append('&');
            }
            sb.append(encode(entry.getKey())).append('=').append(encode(entry.getValue()));
        }
        return sb.toString();
    }

    private static String encode(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static String decode(String value) {
        return java.net.URLDecoder.decode(value, java.nio.charset.StandardCharsets.UTF_8);
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

    /** RFC3339 / ISO 字符串 → 毫秒；解析失败返回 null（不判过期）。 */
    private static Long parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return java.time.Instant.parse(value.trim()).toEpochMilli();
        } catch (Exception e) {
            return null;
        }
    }

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
}
