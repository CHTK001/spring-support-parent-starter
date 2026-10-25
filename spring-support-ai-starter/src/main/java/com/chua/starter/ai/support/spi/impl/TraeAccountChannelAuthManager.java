package com.chua.starter.ai.support.spi.impl;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.ai.support.manage.common.ApiErrorCode;
import com.chua.starter.ai.support.manage.common.ApiException;
import com.chua.starter.ai.support.spi.AccountChannelAuthManager;
import com.chua.starter.common.support.text.json.Json;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * TRAE 账号渠道鉴权管理器实现。
 *
 * <p>协议（对齐 {@code dsh-our-free-model} 的 {@code trae-oauth.ts} /
 * {@code trae.ts} / {@code trae-credits.ts}）：
 * 本地回调登录 → ExchangeToken 轮换续期 → 签到领取 → 远端模型目录。</p>
 *
 * @since 4.0.0.43
 * @author CH
 */
@Spi("trae")
public class TraeAccountChannelAuthManager implements AccountChannelAuthManager {

    /**
     * 日志
     */
    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(TraeAccountChannelAuthManager.class);

    static final String OAUTH_HOST = "https://api.trae.com.cn";
    static final String UG_HOST = "https://api.trae.cn";
    static final String CONSOLE_HOST = "https://www.trae.cn";
    static final String AGENT_HOST = "https://trae-api-cn.mchost.guru";
    static final String CLIENT_ID = "en1oxy7wnw8j9n";
    static final String APP_ID = "6eefa01c-1036-4c7e-9ca5-d891f63bfcd8";
    static final String IDE_VERSION = "0.1.52";
    static final String IDE_VERSION_CODE = "20260811";
    static final String USER_AGENT = "Trae/0.1.52";
    static final String PLUGIN_VERSION = "2.3.62834";
    static final String EXCHANGE_PATH = "/cloudide/api/v3/trae/oauth/ExchangeToken";
    static final String USER_INFO_PATH = "/cloudide/api/v3/trae/GetUserInfo";
    static final String CHECKIN_STATUS_PATH = "/trae/api/v2/ug/checkin_credits/status";
    static final String CHECKIN_CLAIM_PATH = "/trae/api/v2/ug/checkin_credits/claim";
    static final String ENT_USAGE_PATH = "/trae/api/v2/pay/ide_user_ent_usage";
    static final String BATCH_MODELS_PATH = "/api/ide/v1/batch_get_detail_param";
    static final String[] CHANNELS = {
            "solo_agent", "solo_work_lite", "solo_agent_remote", "solo_work_remote",
            "solo_agent_lite", "chat_v3", "builder_v3", "solo_coder",
            "solo_design_lite", "solo_design_remote", "git_ai", "code_reviewer",
            "code_review_summary", "multimodal", "system_diagnosis",
    };
    static final String[][] FALLBACK_MODELS = {
            {"DeepSeek-V4-Flash-Official", "DeepSeek V4 Flash Official"},
            {"Doubao-Seed-2.1-Pro", "Doubao Seed 2.1 Pro"},
            {"seed-code-pro-0430", "Seed Code Pro 0430"},
            {"Doubao-Seed-2.1-Turbo", "Doubao Seed 2.1 Turbo"},
            {"Doubao-Seed-2.0-Code", "Doubao Seed 2.0 Code"},
            {"glm-5.2", "GLM-5.2"},
            {"glm-5-turbo", "GLM-5 Turbo"},
            {"glm-5", "GLM-5"},
            {"DeepSeek-V4-Pro", "DeepSeek V4 Pro"},
            {"DeepSeek-V4-Flash", "DeepSeek V4 Flash"},
            {"kimi-k3", "Kimi K3"},
            {"kimi-k2.7-code", "Kimi K2.7 Code"},
            {"kimi-k2.6", "Kimi K2.6"},
            {"minimax-m3", "MiniMax M3"},
            {"qwen-3.7-plus", "Qwen 3.7 Plus"},
            {"sagitta", "Sagitta"},
            {"aquila", "Aquila"},
    };
    static final String HANDLE_MACHINE_ID = "machine_id";
    static final String HANDLE_DEVICE_ID = "device_id";
    static final String HANDLE_CALLBACK_URL = "callbackUrl";

    @Override
    public String channel() { return "trae"; }

    @Override
    public String displayName() { return "TRAE (字节)"; }

    @Override
    public String loginMethod() {
        return "browser-oauth";
    }

    @Override
    public Map<String, Object> startLogin() {
        String machineId = randomHex(16);
        String deviceId = randomHex(16);
        String traceId = machineTraceId(machineId, deviceId);
        // ⚠️ 先起本地回调监听，再构造 auth_callback_url：redirect 地址里的端口必须
        // 是实际监听端口（18080 被占用时会回退到随机端口），否则浏览器跳转会打偏，
        // 用户就只能手工粘贴回调 URL。
        OauthCallbackServer.Binding binding = OauthCallbackServer.ensure(channel(), 18080);
        String callbackUrl = "http://127.0.0.1:" + binding.port() + "/authorize";
        StringBuilder query = new StringBuilder();
        appendParam(query, "login_version", "1");
        appendParam(query, "auth_from", "solo");
        appendParam(query, "login_channel", "native_ide");
        appendParam(query, "plugin_version", PLUGIN_VERSION);
        appendParam(query, "auth_type", "local");
        appendParam(query, "client_id", CLIENT_ID);
        appendParam(query, "redirect", "0");
        appendParam(query, "login_trace_id", traceId);
        appendParam(query, "auth_callback_url", callbackUrl);
        appendParam(query, "machine_id", machineId);
        appendParam(query, "device_id", deviceId);
        appendParam(query, "x_device_id", deviceId);
        appendParam(query, "x_machine_id", machineId);
        appendParam(query, "x_device_brand", "PC");
        appendParam(query, "x_device_type", "PC");
        appendParam(query, "x_os_version", "1.0");
        appendParam(query, "x_app_version", IDE_VERSION);
        appendParam(query, "x_app_type", "stable");
        Map<String, Object> handle = new LinkedHashMap<>();
        handle.put("handle", "trae-local-callback");
        handle.put(HANDLE_MACHINE_ID, machineId);
        handle.put(HANDLE_DEVICE_ID, deviceId);
        handle.put(HANDLE_CALLBACK_URL, "");
        handle.put("callbackListening", binding.alive());
        handle.put("callbackPort", binding.port());
        handle.put("verifyUrl", CONSOLE_HOST + "/authorization?" + query);
        handle.put("loginUrl", CONSOLE_HOST + "/authorization?" + query); // 旧键兼容
        handle.put("startedAt", System.currentTimeMillis());
        return handle;
    }

    @Override
    public Map<String, Object> pollLogin(Map<String, Object> handle) throws Exception {
        if (handle == null || isBlank(readString(handle, "handle"))) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "TRAE 登录句柄缺失 handle");
        }
        String callbackUrl = handle.get(HANDLE_CALLBACK_URL) instanceof String s && !s.isBlank() ? s.trim() : null;
        if (callbackUrl == null) {
            // 优先取本地回调监听器收到的浏览器跳转（用户授权后自动到达，无需粘贴）；
            // 没有收到时返回 null 继续等 SSE 轮询。
            callbackUrl = OauthCallbackServer.take(channel());
            if (callbackUrl != null && !callbackUrl.startsWith("http")) {
                // 监听器存的是回调的 query 串，补全成完整 URL 复用既有解析。
                callbackUrl = "http://127.0.0.1/authorize?" + callbackUrl;
            }
        }
        if (callbackUrl == null) { return null; }
        Map<String, String> params = parseCallbackUrl(callbackUrl);
        String refreshToken = params.getOrDefault("refreshToken", "");
        Map<String, Object> userInfo = parseJsonParam(params.get("userInfo"));
        Map<String, Object> userJwt = parseJsonParam(params.get("userJwt"));
        if (refreshToken.isEmpty()) {
            refreshToken = jsonString(userJwt, "RefreshToken");
        }
        String jwtToken = jsonString(userJwt, "Token");
        String uid = jsonString(userInfo, "UserID");
        String nickname = jsonString(userInfo, "ScreenName");
        String enterpriseId = jsonString(userInfo, "TenantID");
        if (refreshToken.isEmpty() && jwtToken.isEmpty()) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM,
                    "TRAE 回调未携带 refreshToken / userJwt.Token / code（可能走了 PKCE 新流程）");
        }
        Map<String, Object> exchange;
        if (!refreshToken.isEmpty()) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("ClientID", CLIENT_ID);
            body.put("RefreshToken", refreshToken);
            body.put("ClientSecret", "-");
            body.put("UserID", "");
            String response = ChannelHttp.postJson(
                    OAUTH_HOST + EXCHANGE_PATH, Json.toJSONString(body), oAuthHeaders());
            exchange = parseExchangeResponse(response);
        } else {
            exchange = new LinkedHashMap<>();
            exchange.put("access_token", jwtToken);
            exchange.put("refresh_token", "");
            exchange.put("expires_at", "");
        }
        String accessToken = String.valueOf(exchange.get("access_token"));
        Map<String, Object> userInfoResult = fetchUserInfo(accessToken, uid, nickname, enterpriseId);
        if (userInfoResult != null) {
            uid = (String) userInfoResult.get("uid");
            nickname = (String) userInfoResult.get("nickname");
            enterpriseId = (String) userInfoResult.get("enterprise_id");
        }
        String machineId = readString(handle, HANDLE_MACHINE_ID);
        String deviceId = readString(handle, HANDLE_DEVICE_ID);
        return buildCredential(exchange, uid, nickname, enterpriseId, machineId, deviceId);
    }

    @Override
    public Map<String, Object> refresh(Map<String, Object> credentials) throws Exception {
        String refreshToken = readString(credentials, "refresh_token");
        if (isBlank(refreshToken)) {
            throw new ApiException(ApiErrorCode.UNAUTHORIZED, "TRAE 凭据缺少 refresh_token，需要重新登录");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ClientID", CLIENT_ID);
        body.put("RefreshToken", refreshToken);
        body.put("ClientSecret", "-");
        body.put("UserID", "");
        String response = ChannelHttp.postJson(
                OAUTH_HOST + EXCHANGE_PATH, Json.toJSONString(body), oAuthHeaders());
        Map<String, Object> exchange = parseExchangeResponse(response);
        Map<String, Object> credential = new LinkedHashMap<>(credentials);
        credential.put("access_token", exchange.get("access_token"));
        String newRefreshToken = exchange.get("refresh_token") instanceof String s && !s.isEmpty() ? s : refreshToken;
        credential.put("refresh_token", newRefreshToken);
        String expiresAt = exchange.get("expires_at") instanceof String s && !s.isEmpty()
                ? s : credential.get("expires_at") instanceof String e ? e : "";
        credential.put("expires_at", expiresAt);
        return credential;
    }

    @Override
    public Map<String, Object> status(Map<String, Object> credentials) {
        String accessToken = readString(credentials, "access_token");
        if (isBlank(accessToken)) {
            return status("invalid", "凭据缺少 access_token");
        }
        Long expiresAtMs = parseExpiresAtMs(credentials.get("expires_at"), accessToken);
        if (expiresAtMs != null && expiresAtMs <= System.currentTimeMillis()) {
            return status("expired", "访问令牌已到期");
        }
        return status("valid", "本地有效期判定可用");
    }

    @Override
    public Map<String, Object> checkin(Map<String, Object> credentials) {
        Map<String, Object> result = new LinkedHashMap<>(credentials);
        String accessToken = readString(credentials, "access_token");
        if (isBlank(accessToken)) {
            result.put("checkinError", "凭据缺少 access_token，无法签到");
            return result;
        }
        String uid = readString(credentials, "uid");
        if (isBlank(uid)) {
            result.put("checkinError", "凭据缺少 uid，无法派生签到设备身份");
            return result;
        }
        Map<String, Object> statusResponse;
        try {
            String response = ChannelHttp.postJson(
                    UG_HOST + CHECKIN_STATUS_PATH, "{}", checkinHeaders(accessToken, uid));
            statusResponse = parseJson(response);
        } catch (ApiException e) {
            result.put("checkinError", "签到状态查询失败: " + e.getMessage());
            return result;
        }
        Boolean checkedIn = statusResponse.get("checked_in") instanceof Boolean b ? b : null;
        if (Boolean.TRUE.equals(checkedIn)) {
            result.put("checkin", "already");
            result.put("checkinDetail", "今日已签到");
            return result;
        }
        Boolean enable = statusResponse.get("enable") instanceof Boolean b ? b : Boolean.TRUE;
        if (!Boolean.TRUE.equals(enable)) {
            result.put("checkin", "disabled");
            result.put("checkinDetail", "签到已停用");
            return result;
        }
        try {
            String claimResponse = ChannelHttp.postJson(
                    UG_HOST + CHECKIN_CLAIM_PATH, "{}", checkinHeaders(accessToken, uid));
            Map<String, Object> claimResult = parseJson(claimResponse);
            Object code = claimResult.get("code");
            if (code instanceof Number n && n.intValue() != 0) {
                String message = claimResult.get("message") instanceof String s ? s : "未知";
                result.put("checkin", "failed");
                result.put("checkinError", "签到领取失败: " + message + " (code=" + code + ")");
                return result;
            }
        } catch (ApiException e) {
            result.put("checkinError", "签到领取失败: " + e.getMessage());
            return result;
        }
        result.put("checkin", "claimed");
        result.put("checkinDetail", "今日签到成功");
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("require_usage", true);
            body.put("req_source", 2);
            String response = ChannelHttp.postJson(
                    UG_HOST + ENT_USAGE_PATH, Json.toJSONString(body), checkinHeaders(accessToken, uid));
            Map<String, Object> payload = parseJson(response);
            Long remain = sumEntitlementRemain(payload);
            if (remain != null) {
                result.put("credits", remain);
            }
        } catch (ApiException e) {
            log.debug("[trae] 查询额度失败，已忽略: {}", e.getMessage());
        }
        return result;
    }

    @Override
    public boolean supportsCheckin() { return true; }

    @Override
    public List<Map<String, Object>> listModels(Map<String, Object> credentials) {
        String accessToken = readString(credentials, "access_token");
        if (!isBlank(accessToken)) {
            try {
                List<Map<String, Object>> models = fetchRemoteModels(accessToken);
                if (!models.isEmpty()) { return models; }
            } catch (ApiException e) {
                log.debug("[trae] 接口调用失败，已忽略: {}", e.getMessage());
            }
        }
        return fallbackModels();
    }

    // ── 私有辅助 ──

    private static Map<String, Object> buildCredential(
            Map<String, Object> exchange, String uid, String nickname, String enterpriseId,
            String machineId, String deviceId) {
        Map<String, Object> credential = new LinkedHashMap<>();
        credential.put("access_token", exchange.get("access_token"));
        credential.put("refresh_token", exchange.get("refresh_token") != null
                ? exchange.get("refresh_token") : "");
        credential.put("expires_at", exchange.get("expires_at") != null
                ? exchange.get("expires_at") : "");
        if (uid != null) { credential.put("uid", uid); }
        if (nickname != null) {
            credential.put("nickname", nickname);
            credential.putIfAbsent("email", nickname);
        }
        if (enterpriseId != null && !enterpriseId.isEmpty()) {
            credential.put("enterprise_id", enterpriseId);
        }
        if (machineId != null) { credential.put("machine_id", machineId); }
        if (deviceId != null) { credential.put("device_id", deviceId); }
        return credential;
    }

    private static Map<String, Object> parseExchangeResponse(String response) {
        Map<String, Object> payload = parseJson(response);
        // ExchangeToken 响应可能套信封 {code, message, data}。
        Map<String, Object> data = payload;
        if (payload.containsKey("data") && payload.get("data") instanceof Map<?, ?> m) {
            data = new LinkedHashMap<>((Map<String, Object>) m);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("access_token", firstNonBlank(readString(data, "access_token"),
                readString(data, "AccessToken")));
        result.put("refresh_token", firstNonBlank(readString(data, "refresh_token"),
                readString(data, "RefreshToken")));
        result.put("expires_at", firstNonBlank(readString(data, "expires_at"),
                readString(data, "ExpiresAt")));
        if (result.get("access_token") == null) {
            throw new ApiException(ApiErrorCode.UPSTREAM_ERROR,
                    "TRAE ExchangeToken 响应缺少 access_token: " + ChannelHttp.truncate(response));
        }
        return result;
    }

    /** 调 GetUserInfo 补齐身份（失败返回 null，调用方回退回调值）。 */
    private static Map<String, Object> fetchUserInfo(
            String accessToken, String fallbackUid, String fallbackNickname, String fallbackEnterpriseId) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("UserID", "");
            String response = ChannelHttp.postJson(
                    OAUTH_HOST + USER_INFO_PATH, Json.toJSONString(body),
                    Map.of("Content-Type", "application/json", "Cloud-IDE-JWT", accessToken));
            Map<String, Object> payload = parseJson(response);
            Map<String, Object> data = payload;
            if (payload.containsKey("data") && payload.get("data") instanceof Map<?, ?> m) {
                data = new LinkedHashMap<>((Map<String, Object>) m);
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("uid", firstNonBlank(readString(data, "uid"), fallbackUid));
            result.put("nickname", firstNonBlank(readString(data, "nickname"), fallbackNickname));
            result.put("enterprise_id", firstNonBlank(readString(data, "enterprise_id"), fallbackEnterpriseId));
            return result;
        } catch (ApiException e) {
            return null;
        }
    }

    private static List<Map<String, Object>> fetchRemoteModels(String accessToken) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("channels", CHANNELS);
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Cloud-IDE-JWT", accessToken);
        headers.put("X-App-Id", APP_ID);
        headers.put("X-Ide-Version", IDE_VERSION);
        headers.put("X-App-Version-Code", IDE_VERSION_CODE);
        headers.put("User-Agent", USER_AGENT);
        String response = ChannelHttp.postJson(AGENT_HOST + BATCH_MODELS_PATH, Json.toJSONString(body), headers);
        Map<String, Object> payload = parseJson(response);
        Map<String, Object> data = payload;
        if (payload.containsKey("data") && payload.get("data") instanceof Map<?, ?> m) {
            data = new LinkedHashMap<>((Map<String, Object>) m);
        }
        // 模型目录在 data.models（数组）或 data 本身。
        List<Map<String, Object>> models = new ArrayList<>();
        Object rawModels = data.get("models");
        if (rawModels instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> m) {
                    Map<String, Object> model = new LinkedHashMap<>((Map<String, Object>) m);
                    String id = readString(model, "id") != null ? readString(model, "id") : readString(model, "model");
                    if (id != null) {
                        model.put("id", id);
                        model.putIfAbsent("name", id);
                        model.putIfAbsent("contextWindow", 200_000);
                        models.add(model);
                    }
                }
            }
        }
        return models;
    }

    private static List<Map<String, Object>> fallbackModels() {
        List<Map<String, Object>> models = new ArrayList<>(FALLBACK_MODELS.length);
        for (String[] entry : FALLBACK_MODELS) {
            Map<String, Object> model = new LinkedHashMap<>();
            model.put("id", entry[0]);
            model.put("name", entry[1]);
            model.put("contextWindow", 200_000);
            models.add(model);
        }
        return models;
    }

    /** 解析粘贴回调 URL 的 query 参数。 */
    private static Map<String, String> parseCallbackUrl(String rawUrl) {
        String query;
        String trimmed = rawUrl.trim();
        int queryIndex = trimmed.indexOf('?');
        if (queryIndex >= 0) {
            query = trimmed.substring(queryIndex + 1);
        } else if (trimmed.startsWith("http")) {
            return Map.of();
        } else {
            query = trimmed.startsWith("refreshToken=") ? trimmed : null;
        }
        if (query == null) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) { continue; }
            String key = java.net.URLDecoder.decode(pair.substring(0, eq), java.nio.charset.StandardCharsets.UTF_8);
            String value = java.net.URLDecoder.decode(pair.substring(eq + 1), java.nio.charset.StandardCharsets.UTF_8);
            result.put(key, value);
        }
        return result;
    }

    private static Map<String, Object> parseJsonParam(String raw) {
        if (raw == null || raw.isBlank()) {
            return new LinkedHashMap<>();
        }
        return parseJson(raw);
    }

    private static String jsonString(Map<String, Object> source, String key) {
        if (source == null) {
            return "";
        }
        Object value = source.get(key);
        return value instanceof String s ? s : (value != null ? String.valueOf(value) : "");
    }

    /** 签到请求头：Cloud-IDE-JWT + 派生设备身份。 */
    private static Map<String, String> checkinHeaders(String accessToken, String uid) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Cloud-IDE-JWT", accessToken);
        headers.put("Host", "api.trae.cn");
        headers.put("X-Device-Id", checkinDeviceId(uid));
        headers.put("X-Market-User-Id", checkinMarketUserId(uid));
        headers.put("X-Session-Id", checkinSessionId(uid));
        return headers;
    }

    /** OAuth 请求头。 */
    private static Map<String, String> oAuthHeaders() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("User-Agent", USER_AGENT);
        headers.put("X-App-Id", APP_ID);
        return headers;
    }

    /** 签到设备身份：按 user_id 确定性派生 15 位数字 device_id。 */
    private static String checkinDeviceId(String uid) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] hash = digest.digest(("trae-checkin-device-" + uid).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(15);
            long value = 0;
            for (int i = 0; i < 6; i++) {
                value = (value << 8) | (hash[i] & 0xFF);
            }
            sb.append(value % 10_000_000_000_000_000L);
            return sb.toString();
        } catch (Exception e) {
            return "111111111111111";
        }
    }

    /** 签到 market_user_id：按 user_id 确定性派生 UUID。 */
    private static String checkinMarketUserId(String uid) {
        String seed = "trae-market-user-" + uid;
        byte[] bytes = seed.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        StringBuilder hex = new StringBuilder(32);
        for (byte b : bytes) {
            hex.append(String.format("%02x", b));
        }
        String h = hex.substring(0, 32);
        return h.substring(0, 8) + "-" + h.substring(8, 12) + "-" + h.substring(12, 16)
                + "-" + h.substring(16, 20) + "-" + h.substring(20);
    }

    /** 签到 session_id：按 user_id 确定性派生 64hex。 */
    private static String checkinSessionId(String uid) {
        String seed = "trae-session-" + uid;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(seed.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return java.util.UUID.randomUUID().toString().replace("-", "");
        }
    }

    /** 积分余额：sum(entitlements[].credits_limit - entitlements[].credits_amount)。 */
    private static Long sumEntitlementRemain(Map<String, Object> payload) {
        Object raw = payload.get("entitlements");
        if (!(raw instanceof List<?> list)) {
            return null;
        }
        long total = 0;
        boolean found = false;
        for (Object item : list) {
            if (item instanceof Map<?, ?> m) {
                Map<String, Object> ent = new LinkedHashMap<>((Map<String, Object>) m);
                Object limit = ent.get("credits_limit");
                Object used = ent.get("credits_amount");
                long limitVal = limit instanceof Number n ? n.longValue() : 0;
                long usedVal = used instanceof Number n ? n.longValue() : 0;
                total += Math.max(0, limitVal - usedVal);
                found = true;
            }
        }
        return found ? total : null;
    }

    /** 随机 32 hex（16 字节）。 */
    static String randomHex(int byteCount) {
        byte[] bytes = new byte[byteCount];
        new SecureRandom().nextBytes(bytes);
        StringBuilder sb = new StringBuilder(byteCount * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /** login_trace_id = machine_id XOR device_id 的 hex 表示（对齐原版）。 */
    static String machineTraceId(String machineId, String deviceId) {
        StringBuilder sb = new StringBuilder(32);
        for (int i = 0; i < 16; i++) {
            int m = Integer.parseInt(machineId.substring(i * 2, i * 2 + 2), 16);
            int d = Integer.parseInt(deviceId.substring(i * 2, i * 2 + 2), 16);
            sb.append(String.format("%02x", m ^ d));
        }
        return sb.toString();
    }

    static void appendParam(StringBuilder sb, String key, String value) {
        if (!sb.isEmpty()) {
            sb.append('&');
        }
        sb.append(key).append('=').append(java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8));
    }

    /** 解析 expires_at（毫秒时间戳字符串；秒值自动 ×1000；缺失时解析 JWT exp claim）。 */
    private static Long parseExpiresAtMs(Object rawExpiry, String accessToken) {
        if (rawExpiry instanceof String s && !s.isBlank()) {
            try {
                long value = Long.parseLong(s.trim());
                return value < 10_000_000_000L ? value * 1000 : value;
            } catch (NumberFormatException e) {
                log.debug("[trae] 解析数字失败，已忽略: {}", e.getMessage());
            }
        }
        // 兜底：解析 access_token JWT 的 exp claim。
        try {
            String[] parts = accessToken.split("\\.");
            if (parts.length >= 2) {
                byte[] decoded = java.util.Base64.getUrlDecoder().decode(parts[1]);
                Map<String, Object> claims = Json.getJsonObject(new String(decoded, java.nio.charset.StandardCharsets.UTF_8));
                if (claims != null && claims.get("exp") instanceof Number n) {
                    return n.longValue() * 1000;
                }
            }
        } catch (Exception e) {
            log.debug("[trae] 操作失败，已忽略: {}", e.getMessage());
        }
        return null;
    }

    private static Map<String, Object> status(String state, String detail) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("state", state);
        result.put("detail", detail);
        return result;
    }

    private static Map<String, Object> parseJson(String response) {
        Map<String, Object> map = Json.getJsonObject(response);
        return map != null ? map : new LinkedHashMap<>();
    }

    private static String readString(Map<String, Object> source, String key) {
        if (source == null) {
            return null;
        }
        Object value = source.get(key);
        if (value instanceof String s && !s.isBlank()) {
            return s;
        }
        return null;
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

}
