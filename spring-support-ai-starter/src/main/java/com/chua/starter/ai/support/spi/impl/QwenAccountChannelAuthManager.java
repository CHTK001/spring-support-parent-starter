// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.ai.support.spi.impl;

import com.chua.common.support.ai.chat.route.RouteTarget;
import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.ai.support.manage.common.ApiErrorCode;
import com.chua.starter.ai.support.manage.common.ApiException;
import com.chua.starter.common.support.channel.AccountChannelAuthManager;
import com.chua.starter.common.support.text.json.Json;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Qwen Code（通义千问 CLI）账号渠道鉴权管理器实现。
 *
 * <p>协议（对齐 QwenLM/qwen-code 官方开源实现
 * {@code packages/core/src/qwen/qwenOAuth2.ts}，OAuth 设备码 + PKCE）：</p>
 * <ol>
 *   <li>{@code POST https://chat.qwen.ai/api/v1/oauth2/device/code}（表单
 *       {@code client_id & scope=openid profile email model.completion
 *       & code_challenge=S256 摘要 & code_challenge_method=S256}）
 *       → {@code {device_code, verification_uri[, verification_uri_complete], user_code, expires_in}}</li>
 *   <li>轮询 {@code POST https://chat.qwen.ai/api/v1/oauth2/token}（表单
 *       {@code grant_type=urn:ietf:params:oauth:grant-type:device_code & device_code & code_verifier}）
 *       → {@code {access_token, refresh_token, expires_in(秒), resource_url}}；
 *       待授权是 400 + {@code authorization_pending} / {@code slow_down}（继续等），
 *       {@code expired_token} 为终态失败。</li>
 * </ol>
 *
 * <p>推理 API 是 OpenAI 兼容协议，基址取 token 响应的 {@code resource_url} 拼 {@code /v1}
 * （与官方 CLI 的 {@code resource_url} 用法一致）。官方未提供公开的余额 / 用量查询端点
 * （{@code subscription-quota.ts} 只解析 402 错误体），故 {@link #supportsCheckin()} 为
 * {@code false}，凭据状态以本地到期时间 + {@code GET {resource_url}/v1/models} 探活判定。</p>
 *
 * @since 4.0.0.55
 * @author CH
 */
@Spi("qwen-code")
public class QwenAccountChannelAuthManager implements AccountChannelAuthManager {

    /** OAuth 服务基址（官方常量 QWEN_OAUTH_BASE_URL，设备码授权与 token 轮询挂它）。 */
    static final String OAUTH_BASE = "https://chat.qwen.ai";
    /** 设备码授权路径。 */
    static final String DEVICE_CODE_PATH = "/api/v1/oauth2/device/code";
    /** 设备码 token 轮询 / 刷新路径。 */
    static final String DEVICE_TOKEN_PATH = "/api/v1/oauth2/token";
    /** 客户端 ID（qwen-code CLI 公开客户端）。 */
    static final String CLIENT_ID = "f0304373b74a44d2b584a3fb70ca9e56";
    /** 授权 scope。 */
    static final String SCOPE = "openid profile email model.completion";
    /**
     * 设备码换令牌的 grant_type（RFC 8628 规定的完整 URN）。
     *
     * <p>不能简写成 {@code device_code}：与 MiniMax 同因，简写会被判
     * {@code invalid_request}（missing required parameter），绑定弹窗直接失败。</p>
     */
    static final String GRANT_TYPE_DEVICE_CODE = "urn:ietf:params:oauth:grant-type:device_code";

    /** 轮询间隔（毫秒，约 5s）。 */
    static final long POLL_INTERVAL_MS = 5_000L;
    /** 轮询句柄记录的最后一次轮询时刻（毫秒）。 */
    static final String HANDLE_LAST_POLL_AT = "__lastPollAt";

    @Override
    public String channel() {
        return "qwen-code";
    }

    @Override
    public String displayName() {
        return "Qwen Code";
    }

    @Override
    public String loginMethod() {
        return "device-code";
    }

    /**
     * {@inheritDoc}
     *
     * <p>申请设备码 + PKCE：随机 code_verifier，摘要出 S256 challenge，下发
     * {@code client_id}/{@code scope}，返回 {@code verifyUrl} 供用户打开完成授权。</p>
     */
    @Override
    public Map<String, Object> startLogin() {
        String verifier = codeVerifier();
        String body = "client_id=" + CLIENT_ID
                + "&scope=" + formEncode(SCOPE)
                + "&code_challenge=" + formEncode(s256Challenge(verifier))
                + "&code_challenge_method=S256";
        String response = ChannelHttp.postForm(OAUTH_BASE + DEVICE_CODE_PATH, body, formHeaders());
        Map<String, Object> payload = parseJson(response);
        String deviceCode = readString(payload, "device_code");
        String verificationUri = readString(payload, "verification_uri_complete");
        if (isBlank(verificationUri)) {
            verificationUri = readString(payload, "verification_uri");
        }
        if (isBlank(deviceCode) || isBlank(verificationUri)) {
            throw new ApiException(ApiErrorCode.UPSTREAM_ERROR,
                    "Qwen 设备码授权响应缺少 device_code / verification_uri: "
                            + ChannelHttp.truncate(response));
        }

        Map<String, Object> handle = new LinkedHashMap<>();
        handle.put("handle", deviceCode);
        handle.put("code_verifier", verifier);
        handle.put("verifyUrl", verificationUri);
        // user_code 单独回给前端：授权页需要用户复核这段码（缺失时上游已拼进
        // verification_uri_complete，这里容错带出）。
        String userCode = readString(payload, "user_code");
        if (!isBlank(userCode)) {
            handle.put("user_code", userCode);
        }
        handle.put("expires_in", payload.get("expires_in"));
        handle.put("startedAt", System.currentTimeMillis());
        return handle;
    }

    /**
     * {@inheritDoc}
     *
     * <p>按 5s 间隔轮询 {@code /api/v1/oauth2/token}（device_code 形式）。
     * 400 + {@code authorization_pending} / {@code slow_down} 继续等；
     * {@code expired_token} 判终态失败（RFC 8628：设备码已过期，需重新发起）。
     * 拿到 token 后读 {@code expires_in}（秒）计算 {@code expire_time}，
     * 并保存 {@code resource_url}（推理 API 基址）。</p>
     */
    @Override
    public Map<String, Object> pollLogin(Map<String, Object> handle) {
        if (handle == null || isBlank(readString(handle, "handle"))) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "Qwen 登录句柄缺失 handle");
        }
        long now = System.currentTimeMillis();
        Long lastPollAt = toLong(handle.get(HANDLE_LAST_POLL_AT));
        if (lastPollAt != null && now - lastPollAt < POLL_INTERVAL_MS) {
            return null;
        }

        String deviceCode = readString(handle, "handle");
        String verifier = readString(handle, "code_verifier");
        if (isBlank(deviceCode) || isBlank(verifier)) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "Qwen 登录句柄缺少 device_code 或 code_verifier");
        }
        String body = "client_id=" + CLIENT_ID
                + "&grant_type=" + formEncode(GRANT_TYPE_DEVICE_CODE)
                + "&device_code=" + formEncode(deviceCode)
                + "&code_verifier=" + formEncode(verifier);

        Map<String, Object> payload;
        try {
            String response = ChannelHttp.postForm(OAUTH_BASE + DEVICE_TOKEN_PATH, body, formHeaders());
            payload = parseJson(response);
        } catch (ApiException e) {
            // 400 里区分「继续等」和「真失败」：
            //   authorization_pending / slow_down → 用户还没授权 / 要求降频，继续等；
            //   expired_token → 设备码已过期，属终态（官方 CLI 同样判失败）。
            String message = e.getMessage() == null ? "" : e.getMessage();
            if (message.contains("authorization_pending") || message.contains("slow_down")) {
                handle.put(HANDLE_LAST_POLL_AT, now);
                return null;
            }
            throw new ApiException(ApiErrorCode.UPSTREAM_ERROR,
                    "Qwen 设备码轮询失败: " + e.getMessage(), e);
        }

        String accessToken = readString(payload, "access_token");
        if (isBlank(accessToken)) {
            // 200 但带 error（非标准）或空响应：无 error 视为仍待确认。
            String error = readString(payload, "error");
            if (isBlank(error)) {
                handle.put(HANDLE_LAST_POLL_AT, now);
                return null;
            }
            throw new ApiException(ApiErrorCode.UPSTREAM_ERROR,
                    "Qwen 设备码轮询失败: " + ChannelHttp.truncate(Json.toJSONString(payload)));
        }

        Map<String, Object> credential = new LinkedHashMap<>();
        credential.put("access_token", accessToken);
        String refreshToken = readString(payload, "refresh_token");
        if (refreshToken != null) {
            credential.put("refresh_token", refreshToken);
        }
        Long expireMs = toMillis(payload.get("expires_in"));
        if (expireMs != null) {
            credential.put("expire_time", System.currentTimeMillis() + expireMs);
        }
        String resourceUrl = readString(payload, "resource_url");
        if (!isBlank(resourceUrl)) {
            credential.put("resource_url", resourceUrl);
        }
        credential.put("nickname", "Qwen Code");
        return credential;
    }

    /**
     * {@inheritDoc}
     *
     * <p>用 {@code refresh_token} 换新访问令牌：{@code grant_type=refresh_token}。
     * 上游续期响应若带回新的 {@code resource_url} 则更新，否则沿用旧值。</p>
     */
    @Override
    public Map<String, Object> refresh(Map<String, Object> credentials) {
        String refreshToken = readString(credentials, "refresh_token");
        if (isBlank(refreshToken)) {
            throw new ApiException(ApiErrorCode.UNAUTHORIZED, "Qwen 凭据缺少 refresh_token，无法续期");
        }
        String body = "client_id=" + CLIENT_ID
                + "&grant_type=" + formEncode("refresh_token")
                + "&refresh_token=" + formEncode(refreshToken);
        String response = ChannelHttp.postForm(OAUTH_BASE + DEVICE_TOKEN_PATH, body, formHeaders());
        Map<String, Object> payload = parseJson(response);
        String accessToken = readString(payload, "access_token");
        if (isBlank(accessToken)) {
            throw new ApiException(ApiErrorCode.UPSTREAM_ERROR,
                    "Qwen 续期响应缺少 access_token: " + ChannelHttp.truncate(response));
        }
        Map<String, Object> refreshed = new LinkedHashMap<>();
        refreshed.put("access_token", accessToken);
        String newRefresh = readString(payload, "refresh_token");
        refreshed.put("refresh_token", isBlank(newRefresh) ? refreshToken : newRefresh);
        Long expireMs = toMillis(payload.get("expires_in"));
        if (expireMs != null) {
            refreshed.put("expire_time", System.currentTimeMillis() + expireMs);
        }
        String resourceUrl = readString(payload, "resource_url");
        if (isBlank(resourceUrl)) {
            resourceUrl = readString(credentials, "resource_url");
        }
        if (!isBlank(resourceUrl)) {
            refreshed.put("resource_url", resourceUrl);
        }
        refreshed.put("nickname", "Qwen Code");
        return refreshed;
    }

    /**
     * {@inheritDoc}
     *
     * <p>本地到期优先：凭据带 {@code expire_time} 且已过期判 {@code expired}；
     * 否则探活 {@code GET {resource_url}/v1/models}（OpenAI 兼容标准端点，Bearer），
     * 401/403 判 {@code invalid}，2xx 判 {@code valid}；网络失败按本地有效期视为可用。</p>
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
        String base = resourceBase(credentials);
        if (base == null) {
            return status("valid", "凭据缺少 resource_url，按本地有效期视为可用");
        }
        try {
            ChannelHttp.getJson(base + "/v1/models", authHeaders(accessToken));
        } catch (ApiException e) {
            String message = e.getMessage() == null ? "" : e.getMessage();
            if (message.contains("401") || message.contains("403") || message.contains("Unauthorized")) {
                return status("invalid", "探活 401/403: " + message);
            }
            return status("valid", "探活失败（网络），按本地有效期视为可用: " + message);
        }
        return status("valid", "探活成功");
    }

    @Override
    public boolean supportsCheckin() {
        // 官方未提供签到 / 余额查询端点（subscription-quota.ts 只解析 402 错误体）。
        return false;
    }

    /**
     * {@inheritDoc}
     *
     * <p>远端目录：{@code GET {resource_url}/v1/models}（OpenAI 兼容标准端点），
     * 解析 {@code {data:[{id, ...}]}}；拉取失败或 0 条返回空列表不抛错（上层走兜底模型表）。</p>
     */
    @Override
    public List<Map<String, Object>> listModels(Map<String, Object> credentials) {
        String accessToken = readString(credentials, "access_token");
        String base = resourceBase(credentials);
        if (isBlank(accessToken) || base == null) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        try {
            String response = ChannelHttp.getJson(base + "/v1/models", authHeaders(accessToken));
            Map<String, Object> payload = parseJson(response);
            if (payload.get("data") instanceof List<?> rows) {
                for (Object row : rows) {
                    if (row instanceof Map<?, ?> entry && entry.get("id") != null) {
                        Map<String, Object> model = new LinkedHashMap<>(2);
                        model.put("id", String.valueOf(entry.get("id")));
                        Object name = entry.get("name") != null ? entry.get("name") : entry.get("id");
                        model.put("name", String.valueOf(name));
                        result.add(model);
                    }
                }
            }
        } catch (ApiException ignored) {
            // 远端目录拉取失败不致命（返回空列表，上层可走兜底模型表）。
        }
        return result;
    }

    /**
     * {@inheritDoc}
     *
     * <p>凭据转 OpenAI 兼容路由目标：provider 固定 {@code openai}，
     * baseUrl = {@code resource_url + /v1}，Bearer 访问令牌。
     * 缺 access_token 或 resource_url 时返回 {@code null}（不参与路由）。</p>
     */
    @Override
    public RouteTarget toRouteTarget(Map<String, Object> credentials) {
        String accessToken = readString(credentials, "access_token");
        String base = resourceBase(credentials);
        if (isBlank(accessToken) || base == null) {
            return null;
        }
        return RouteTarget.builder()
                .provider("openai")
                .baseUrl(base + "/v1")
                .apiKey(accessToken)
                .authScheme("api_key")
                .autoSwitchOnRateLimit(true)
                .build();
    }

    // ── 私有辅助 ──

    /**
     * form Headers
     * @return 返回值
    */
    private static Map<String, String> formHeaders() {
        return Map.of("Content-Type", "application/x-www-form-urlencoded", "Accept", "application/json");
    }

    /**
     * auth Headers
     * @param accessToken 字符串
     * @return 返回值
    */
    private static Map<String, String> authHeaders(String accessToken) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + (accessToken == null ? "" : accessToken.trim()));
        headers.put("Accept", "application/json");
        return headers;
    }

    /**
     * 规范化凭据里的 resource_url（去尾部斜杠），作为推理 API 基址。
     *
     * @param credentials 凭据
     * @return 基址；缺失时为 null
     */
    private static String resourceBase(Map<String, Object> credentials) {
        String resourceUrl = readString(credentials, "resource_url");
        if (isBlank(resourceUrl)) {
            return null;
        }
        String base = resourceUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base;
    }

    /**
     * status
     * @param detail 字符串
     * @param state 字符串
     * @return 返回值
    */
    private static Map<String, Object> status(String state, String detail) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("state", state);
        result.put("detail", detail);
        return result;
    }

    /**
     * code Verifier
     * @return 返回值
    */
    private static String codeVerifier() {
        // [P3C A20 豁免]
        byte[] raw = new byte[48];
        new SecureRandom().nextBytes(raw);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }

    /**
     * s256Challenge
     * @param verifier 字符串
     * @return 返回值
    */
    private static String s256Challenge(String verifier) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception e) {
            throw new ApiException(ApiErrorCode.INTERNAL_ERROR, "SHA-256 摘要失败: " + e.getMessage(), e);
        }
    }

    /**
     * form Encode
     * @param value 字符串
     * @return 返回值
    */
    private static String formEncode(String value) {
        try {
            return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return value;
        }
    }

    /**
     * parse Json
     * @param response 字符串
     * @return 返回值
    */
    private static Map<String, Object> parseJson(String response) {
        Map<String, Object> map = Json.getJsonObject(response);
        return map != null ? map : new LinkedHashMap<>();
    }

    /**
     * read String
     * @param Map<String 参数
     * @param key 字符串
     * @param source 参数
     * @return 返回值
    */
    private static String readString(Map<String, Object> source, String key) {
        Object value = source == null ? null : source.get(key);
        if (value instanceof String s && !s.isBlank()) {
            return s;
        }
        return null;
    }

    /**
     * is Blank
     * @param value 字符串
     * @return 返回值
    */
    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * to Long
     * @param value 对象
     * @return 返回值
    */
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

    /**
     * to Millis
     * @param value 对象
     * @return 返回值
    */
    private static Long toMillis(Object value) {
        if (value instanceof Number number && number.doubleValue() > 0) {
            double v = number.doubleValue();
            // [P3C A20 豁免]
            return v < 1e12 ? Math.round(v * 1000d) : Math.round(v);
        }
        if (value instanceof String s && !s.isBlank()) {
            try {
                return java.time.Instant.parse(s.trim()).toEpochMilli();
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }
}
