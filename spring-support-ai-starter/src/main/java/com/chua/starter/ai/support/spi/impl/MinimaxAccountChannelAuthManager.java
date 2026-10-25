package com.chua.starter.ai.support.spi.impl;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.ai.support.manage.common.ApiErrorCode;
import com.chua.starter.ai.support.manage.common.ApiException;
import com.chua.starter.ai.support.spi.AccountChannelAuthManager;
import com.chua.starter.common.support.text.json.Json;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MiniMax（MiniMax Code 中国版）账号渠道鉴权管理器实现。
 *
 * <p>协议（对齐 {@code dsh-our-free-model} 的 {@code minimax-oauth.ts}，OAuth 设备码 + PKCE）：</p>
 * <ol>
 *   <li>{@code POST {accountHost}/oauth2/device/code}（表单
 *       {@code client_id=mcode-public & scope=agent.default & audience=agent-backend & code_challenge=S256 摘要}）
 *       → {@code {device_code, verification_uri, verification_uri_complete, expires_in}}</li>
 *   <li>轮询 {@code POST {accountHost}/oauth2/token}（表单
 *       {@code grant_type=urn:ietf:params:oauth:grant-type:device_code & device_code & code_verifier}）→ {@code {access_token(JWT), refresh_token, expires_at}}；
 *       <b>pending 是 HTTP 200 + {@code status=pending}</b>（同时容忍 400 + {@code authorization_pending} /
 *       {@code slow_down} / {@code expired_token}，这三种都要继续等而非报错）。</li>
 * </ol>
 *
 * <p>⚠️ 易踩点（源码注释原样保留）：{@code expires_at} <b>不是</b> JWT，是 ISO 字符串，
 * 必须读 {@code expires_in}；客户端标识头缺失可能导致网关 403/降级；PKCE 必须 S256。</p>
 *
 * <p>凭据落库 {@code access_token}/{@code refresh_token}/{@code expire_time}/{@code nickname}。
 * 余额/免费额度在 {@link #checkin} 经 {@code GET {apiHost}/v1/user/balance} 查询。</p>
 *
 * @since 4.0.0.43
 * @author CH
 */
@Spi("minimax")
public class MinimaxAccountChannelAuthManager implements AccountChannelAuthManager {

    /** 账号服务基址（设备码授权与 token 轮询挂它）。 */
    static final String ACCOUNT_HOST = "https://account.minimax.cn";
    /** 推理 / 账号 API 基址（余额查询挂它）。 */
    static final String API_HOST = "https://agent.minimax.cn";
    /** 客户端 ID（必须严格匹配服务端注册的 PKCE 应用）。 */
    static final String CLIENT_ID = "mcode-public";

    /** 设备码授权路径（挂 accountHost）。 */
    static final String DEVICE_CODE_PATH = "/oauth2/device/code";
    /** 设备码 token 轮询路径（挂 accountHost）。 */
    static final String DEVICE_TOKEN_PATH = "/oauth2/token";

    /**
     * 设备码换令牌的 grant_type（RFC 8628 规定的完整 URN）。
     *
     * <p>⚠️ 不能简写成 {@code device_code}：实测 account.minimax.cn 会回
     * 400 {@code invalid_request}（"missing a required parameter ... is otherwise malformed"），
     * 而不是预期的 {@code authorization_pending} / {@code expired_token}，
     * 导致绑定弹窗直接显示失败、根本等不到用户授权。与 dsh-our-free-model
     * 的 minimax-oauth.ts 保持同一取值。</p>
     */
    static final String GRANT_TYPE_DEVICE_CODE = "urn:ietf:params:oauth:grant-type:device_code";
    /** 余额查询路径（挂 apiHost）。 */
    static final String BALANCE_PATH = "/v1/user/balance";
    /** 模型目录路径（挂 apiHost，query 带 region=cn&buildEnv=prod）。 */
    static final String MODELS_PATH = "/mavis/api/v1/models";

    /** 轮询间隔（毫秒，约 5s）。 */
    static final long POLL_INTERVAL_MS = 5_000L;
    /** 连续网络失败上限。 */
    static final int POLL_MAX_FAILURES = 5;
    /** 轮询句柄记录的最后一次轮询时刻（毫秒）。 */
    static final String HANDLE_LAST_POLL_AT = "__lastPollAt";

    @Override
    public String channel() {
        return "minimax";
    }

    @Override
    public String displayName() {
        return "MiniMax Code";
    }

    @Override
    public String loginMethod() {
        return "device-code";
    }

    /**
     * {@inheritDoc}
     *
     * <p>申请设备码 + PKCE：随机 code_verifier，摘要出 S256 challenge，下发
     * {@code client_id}/{@code scope}/{@code audience}，返回
     * {@code verifyUrl}（verification_uri_complete）供用户打开完成授权。</p>
     */
    @Override
    public Map<String, Object> startLogin() {
        String verifier = codeVerifier();
        String body = "client_id=" + CLIENT_ID
                + "&scope=" + formEncode("agent.default")
                + "&audience=" + formEncode("agent-backend")
                + "&code_challenge=" + formEncode(s256Challenge(verifier))
                + "&code_challenge_method=S256";
        String response = ChannelHttp.postForm(
                ACCOUNT_HOST + DEVICE_CODE_PATH, body, formHeaders());
        Map<String, Object> payload = parseJson(response);
        String deviceCode = readString(payload, "device_code");
        String verificationUri = readString(payload, "verification_uri_complete");
        if (isBlank(verificationUri)) {
            verificationUri = readString(payload, "verification_uri");
        }
        if (isBlank(deviceCode) || isBlank(verificationUri)) {
            throw new ApiException(ApiErrorCode.UPSTREAM_ERROR,
                    "MiniMax 设备码授权响应缺少 device_code / verification_uri: "
                            + ChannelHttp.truncate(response));
        }

        Map<String, Object> handle = new LinkedHashMap<>();
        handle.put("handle", deviceCode);
        handle.put("code_verifier", verifier);
        handle.put("verifyUrl", verificationUri);
        // ⚠️ user_code 必须单独回给前端：授权页虽是「打开即用」，
        // 但用户常需要在页面上手动复核这段码，前端弹窗也据此展示。
        // 上游把它只拼进 verification_uri_complete 的 query，这里显式取出来。
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
     * <p>按 5s 间隔轮询 {@code /oauth2/token}（device_code 形式）。pending 是 200 +
     * {@code status=pending}（继续等，不打上游）；标准 400 +
     * {@code authorization_pending} 形态会被 {@link ChannelHttp} 抛 400，此处捕获后继续等。
     * 拿到 token 后取 {@code expires_in} 计算 {@code expire_time} 落库。</p>
     */
    @Override
    public Map<String, Object> pollLogin(Map<String, Object> handle) {
        if (handle == null || isBlank(readString(handle, "handle"))) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "MiniMax 登录句柄缺失 handle");
        }
        long now = System.currentTimeMillis();
        Long lastPollAt = toLong(handle.get(HANDLE_LAST_POLL_AT));
        if (lastPollAt != null && now - lastPollAt < POLL_INTERVAL_MS) {
            return null;
        }

        String deviceCode = readString(handle, "handle");
        String verifier = readString(handle, "code_verifier");
        if (isBlank(deviceCode) || isBlank(verifier)) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "MiniMax 登录句柄缺少 device_code 或 code_verifier");
        }
        String body = "client_id=" + CLIENT_ID
                + "&grant_type=" + formEncode(GRANT_TYPE_DEVICE_CODE)
                + "&device_code=" + formEncode(deviceCode)
                + "&code_verifier=" + formEncode(verifier);

        Map<String, Object> payload;
        try {
            String response = ChannelHttp.postForm(ACCOUNT_HOST + DEVICE_TOKEN_PATH, body, formHeaders());
            payload = parseJson(response);
        } catch (ApiException e) {
            // 400 里要区分「继续等」和「真失败」，不能一律当失败：
            //   authorization_pending → 用户还没授权，继续等；
            //   slow_down             → 上游要求降频，继续等（下一轮受 POLL_INTERVAL 约束）；
            //   expired_token         → 这一轮的设备码已过期。用户在授权页停留较久、
            //                           或自己点了「重新发起」都会发生，必须继续等新码，
            //                           否则一个正常的绑定流程会被判成失败。
            String message = e.getMessage() == null ? "" : e.getMessage();
            if (message.contains("authorization_pending") || message.contains("slow_down")
                    || message.contains("expired_token")) {
                handle.put(HANDLE_LAST_POLL_AT, now);
                return null;
            }
            throw new ApiException(ApiErrorCode.UPSTREAM_ERROR,
                    "MiniMax 设备码轮询失败: " + e.getMessage(), e);
        }

        // 待确认：HTTP 200 + status=pending（MiniMax 自定义形态）。
        if ("pending".equalsIgnoreCase(readString(payload, "status"))) {
            handle.put(HANDLE_LAST_POLL_AT, now);
            return null;
        }
        String accessToken = readString(payload, "access_token");
        if (isBlank(accessToken)) {
            // 其它错误码（access_denied 等）视为最终失败。
            String error = readString(payload, "error");
            if (isBlank(error) && isBlank(readString(payload, "access_token"))) {
                handle.put(HANDLE_LAST_POLL_AT, now);
                return null;
            }
            throw new ApiException(ApiErrorCode.UPSTREAM_ERROR,
                    "MiniMax 设备码轮询失败: " + ChannelHttp.truncate(Json.toJSONString(payload)));
        }

        Map<String, Object> credential = new LinkedHashMap<>();
        credential.put("access_token", accessToken);
        String refreshToken = readString(payload, "refresh_token");
        if (refreshToken != null) {
            credential.put("refresh_token", refreshToken);
        }
        // ⚠️ expires_at 非 JWT，必须读 expires_in。
        Long expireMs = toMillis(payload.get("expires_in"));
        if (expireMs != null) {
            credential.put("expire_time", System.currentTimeMillis() + expireMs);
        }
        credential.put("nickname", "MiniMax");
        return credential;
    }

    /**
     * {@inheritDoc}
     *
     * <p>用 {@code refresh_token} 换新访问令牌：{@code grant_type=refresh_token}。</p>
     */
    @Override
    public Map<String, Object> refresh(Map<String, Object> credentials) {
        String refreshToken = readString(credentials, "refresh_token");
        if (isBlank(refreshToken)) {
            throw new ApiException(ApiErrorCode.UNAUTHORIZED, "MiniMax 凭据缺少 refresh_token，无法续期");
        }
        String body = "client_id=" + CLIENT_ID
                + "&grant_type=" + formEncode("refresh_token")
                + "&refresh_token=" + formEncode(refreshToken);
        String response = ChannelHttp.postForm(ACCOUNT_HOST + DEVICE_TOKEN_PATH, body, formHeaders());
        Map<String, Object> payload = parseJson(response);
        String accessToken = readString(payload, "access_token");
        if (isBlank(accessToken)) {
            throw new ApiException(ApiErrorCode.UPSTREAM_ERROR,
                    "MiniMax 续期响应缺少 access_token: " + ChannelHttp.truncate(response));
        }
        Map<String, Object> refreshed = new LinkedHashMap<>();
        refreshed.put("access_token", accessToken);
        String newRefresh = readString(payload, "refresh_token");
        refreshed.put("refresh_token", isBlank(newRefresh) ? refreshToken : newRefresh);
        Long expireMs = toMillis(payload.get("expires_in"));
        if (expireMs != null) {
            refreshed.put("expire_time", System.currentTimeMillis() + expireMs);
        }
        refreshed.put("nickname", "MiniMax");
        return refreshed;
    }

    /**
     * {@inheritDoc}
     *
     * <p>本地到期优先：凭据带 {@code expire_time} 且已过期判 {@code expired}；
     * 否则探活 {@code GET {apiHost}/v1/user/balance}（带 Bearer + 客户端标识头），
     * 401 判 {@code invalid}，2xx 判 {@code valid}（并顺带取余额）。</p>
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
        try {
            ChannelHttp.getJson(API_HOST + BALANCE_PATH, authHeaders(accessToken));
        } catch (ApiException e) {
            String message = e.getMessage() == null ? "" : e.getMessage();
            if (message.contains("401") || message.contains("403") || message.contains("Unauthorized")) {
                return status("invalid", "探活 401/403: " + message);
            }
            return status("valid", "探活失败（网络），按本地有效期视为可用: " + message);
        }
        return status("valid", "探活成功");
    }

    /**
     * {@inheritDoc}
     *
     * <p>查免费额度余额（非签到）：{@code GET {apiHost}/v1/user/balance}，
     * 把原始值并入结果。失败不抛错，返回 {@code balance=null + error}。</p>
     */
    @Override
    public Map<String, Object> checkin(Map<String, Object> credentials) {
        Map<String, Object> result = new LinkedHashMap<>(credentials);
        String accessToken = readString(credentials, "access_token");
        if (isBlank(accessToken)) {
            result.put("balance", null);
            result.put("balanceError", "凭据缺少 access_token，无法查询余额");
            return result;
        }
        try {
            String response = ChannelHttp.getJson(API_HOST + BALANCE_PATH, authHeaders(accessToken));
            Map<String, Object> payload = parseJson(response);
            Map<String, Object> data = dataOf(payload);
            Map<String, Object> source = data != null ? data : payload;
            Object balance = source.get("balance") != null ? source.get("balance") : source.get("amount");
            result.put("balance", balance);
        } catch (ApiException e) {
            result.put("balance", null);
            result.put("balanceError", e.getMessage());
        }
        return result;
    }

    @Override
    public boolean supportsCheckin() {
        // MiniMax 无每日签到端点（源码全量扫描无 checkin），只有余额查询。
        return false;
    }

    /**
     * {@inheritDoc}
     *
     * <p>远端目录：{@code GET {apiHost}/mavis/api/v1/models?region=cn&buildEnv=prod}
     *（Bearer，实测业务端点只需 Bearer 不需要 machine 头/签名）。
     * 响应结构 {@code {providers:[{providerId:minimax, config:{models:{<长名id>:条目}, model_order:[...]}}]}}——
     * 模型在 <b>models 对象的 key</b> 里（长名），条目自带 {@code name} 是短名，
     * 两者分别取（合并会丢官方展示名）；窗口取 {@code context_window_options} 最大值
     * 或 {@code limit.context}（未知记 0，不编造）；按 {@code model_order} 排序；
     * 拉取失败或 0 条返回空列表不抛错（上层走兜底模型表）。</p>
     */
    @Override
    public List<Map<String, Object>> listModels(Map<String, Object> credentials) {
        String accessToken = readString(credentials, "access_token");
        if (isBlank(accessToken)) {
            return List.of();
        }
        List<Map<String, Object>> result = new java.util.ArrayList<>();
        try {
            String response = ChannelHttp.getJson(
                    API_HOST + MODELS_PATH + "?region=cn&buildEnv=prod",
                    Map.of("Authorization", "Bearer " + accessToken, "Accept", "application/json"));
            Map<String, Object> payload = parseJson(response);
            Object providersRaw = payload.get("providers");
            if (!(providersRaw instanceof List<?> providers)) {
                return List.of();
            }
            Map<String, Object> config = null;
            for (Object p : providers) {
                if (p instanceof Map<?, ?> entry && "minimax".equals(String.valueOf(entry.get("providerId")))) {
                    config = entry.get("config") instanceof Map<?, ?> c ? (Map<String, Object>) c : null;
                    break;
                }
            }
            if (config == null || !(config.get("models") instanceof Map<?, ?> models)) {
                return List.of();
            }
            // 先按远端 model_order 建序（缺失的条目排末尾）。
            Map<String, Integer> order = new HashMap<>();
            if (config.get("model_order") instanceof List<?> orderList) {
                int i = 0;
                for (Object id : orderList) {
                    if (id instanceof String s) {
                        order.put(s, i++);
                    }
                }
            }
            for (Map.Entry<?, ?> e : models.entrySet()) {
                String id = String.valueOf(e.getKey());
                Map<String, Object> entry = e.getValue() instanceof Map<?, ?> m
                        ? (Map<String, Object>) m : Map.of();
                Map<String, Object> model = new LinkedHashMap<>();
                model.put("id", id);
                // 条目自带 name 是短名（官方 IDE 显示它）；缺失时退回 id。
                model.put("name", readString(entry, "name") != null ? readString(entry, "name") : id);
                // 窗口：context_window_options 最大值优先，其次 limit.context；0 不写。
                Long contextWindow = null;
                if (entry.get("context_window_options") instanceof List<?> options) {
                    for (Object v : options) {
                        if (v instanceof Number n && n.longValue() > 0) {
                            contextWindow = contextWindow == null || n.longValue() > contextWindow
                                    ? n.longValue() : contextWindow;
                        }
                    }
                }
                if (contextWindow == null && entry.get("limit") instanceof Map<?, ?> limit
                        && limit.get("context") instanceof Number n) {
                    contextWindow = n.longValue();
                }
                if (contextWindow != null && contextWindow > 0) {
                    model.put("contextWindow", contextWindow.intValue());
                }
                // 免费判定：条目 is_free 标记（缺失默认免费渠道语义下 true）。
                model.put("isFree", entry.get("is_free") instanceof Boolean b ? b : true);
                model.put("__order", order.getOrDefault(id, Integer.MAX_VALUE));
                result.add(model);
            }
            result.sort((a, b) -> Integer.compare(a.get("__order") instanceof Number n ? n.intValue() : 0,
                    b.get("__order") instanceof Number n ? n.intValue() : 0));
            result.forEach(m -> m.remove("__order"));
        } catch (ApiException ignored) {
            // 远端目录拉取失败不致命（返回空列表，上层可走兜底模型表）。
        }
        return result;
    }

    // ── 私有辅助 ──

    private static Map<String, String> formHeaders() {
        return Map.of("Content-Type", "application/x-www-form-urlencoded", "Accept", "application/json");
    }

    private static Map<String, String> authHeaders(String accessToken) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + (accessToken == null ? "" : accessToken.trim()));
        headers.put("Accept", "application/json");
        headers.put("HTTP-Referer", "https://agent.minimax.cn");
        headers.put("X-Client", "Minimax Code");
        return headers;
    }

    private static Map<String, Object> status(String state, String detail) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("state", state);
        result.put("detail", detail);
        return result;
    }

    /** PKCE code_verifier：43~128 位 URL-safe 随机字节（Base64URL 无填充）。 */
    private static String codeVerifier() {
        byte[] raw = new byte[48];
        new SecureRandom().nextBytes(raw);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }

    /** S256：SHA-256(verifier) 的 Base64URL 无填充。 */
    private static String s256Challenge(String verifier) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception e) {
            throw new ApiException(ApiErrorCode.INTERNAL_ERROR, "SHA-256 摘要失败: " + e.getMessage(), e);
        }
    }

    private static String formEncode(String value) {
        try {
            return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return value;
        }
    }

    private static Map<String, Object> parseJson(String response) {
        Map<String, Object> map = Json.getJsonObject(response);
        return map != null ? map : new LinkedHashMap<>();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> dataOf(Map<String, Object> payload) {
        return payload.get("data") instanceof Map<?, ?> m ? (Map<String, Object>) m : null;
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

    /** 时间值归一为毫秒（数字 <1e12 视为秒；ISO 字符串按 Instant 解析）。 */
    private static Long toMillis(Object value) {
        if (value instanceof Number number && number.doubleValue() > 0) {
            double v = number.doubleValue();
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
