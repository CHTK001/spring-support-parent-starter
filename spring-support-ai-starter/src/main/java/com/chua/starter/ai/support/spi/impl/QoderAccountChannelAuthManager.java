package com.chua.starter.ai.support.spi.impl;

import com.chua.common.support.ai.chat.route.RouteTarget;
import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.ai.support.manage.common.ApiErrorCode;
import com.chua.starter.ai.support.manage.common.ApiException;
import com.chua.starter.common.support.channel.AccountChannelAuthManager;
import com.chua.starter.common.support.text.json.Json;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Qoder 账号渠道鉴权管理器实现（国际版 + 国内版）。
 *
 * <p>协议（对齐 {@code dsh-our-free-model} 的 {@code qoder-oauth.ts} / {@code qoder.ts}）：</p>
 * <ol>
 *   <li>{@code startLogin}：纯本地生成 PKCE 会话（verifier + challenge + nonce + machineId），
 *       拼授权 URL {@code {authBase}/device/selectAccounts?challenge&challenge_method=S256&nonce&machine_id&client_id}，
 *       无网络调用；</li>
 *   <li>{@code pollLogin}：轮询 {@code GET {openApiBase}/api/v1/deviceToken/poll?nonce&verifier&challenge_method=S256}
 *       （⚠️ 挂 openApiBase 不是 authBase；404 = 用户尚未授权，继续轮询；其它非 2xx 立即失败）；</li>
 *   <li>响应 {@code {token, refresh_token, expires_at, user_id, user_name, ...}} 归一为凭据
 *       （{@code security_oauth_token} 与 {@code access_token} 双写同值，{@code machine_id} 必须持久化）。</li>
 * </ol>
 *
 * <p>渠道 id 取 {@code @Spi} 别名：{@code qoder}（国际，{@code qoder.com}）与
 * {@code qoder-cn}（国内，{@code qoder.cn}），共用同一套协议实现，差异全部收敛在配置。</p>
 *
 * @since 4.0.0.43
 * @author CH
 */
@Spi("qoder")
public class QoderAccountChannelAuthManager implements AccountChannelAuthManager {

    static final String CHANNEL = "qoder";
    static final String AUTH_BASE = "https://qoder.com";
    static final String OPEN_API_BASE = "https://openapi.qoder.sh";
    static final String CLIENT_ID = "e883ade2-e6e3-4d6d-adf7-f92ceff5fdcb";

    /** 授权页路径（挂 authBase）。 */
    static final String DEVICE_SELECT_PATH = "/device/selectAccounts";
    /** 轮询取 token（挂 openApiBase）。 */
    static final String POLL_PATH = "/api/v1/deviceToken/poll";
    /** 续期（挂 openApiBase）。 */
    static final String REFRESH_PATH = "/api/v1/deviceToken/refresh";
    /** 用户信息（挂 openApiBase）。 */
    static final String USERINFO_PATH = "/api/v1/userinfo";
    /** 用量（挂 openApiBase，sash 端点）。 */
    static final String USAGE_PATH = "/sash/api/v2/me/usage";
    /** 活动列表（挂 openApiBase，sash 端点）。 */
    static final String CAMPAIGNS_PATH = "/sash/api/v1/me/campaigns";

    /** 轮询句柄里的键。 */
    static final String H_VERIFIER = "verifier";
    static final String H_NONCE = "nonce";
    static final String H_MACHINE_ID = "machine_id";
    static final String H_LAST_POLL_AT = "__lastPollAt";

    /** 登录轮询间隔（毫秒）；源码 Djr = 1e3。 */
    static final long POLL_INTERVAL_MS = 1_000L;
    /** PKCE 字母表（URL 安全的 64 字符）。 */
    private static final char[] PKCE_ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    public String channel() {
        return CHANNEL;
    }

    @Override
    public String displayName() {
        return "Qoder";
    }

    /**
     * {@inheritDoc}
     *
     * <p>纯本地 PKCE 会话 + 授权 URL；用户打开 verifyUrl 完成授权后，
     * 前端轮询 {@link #pollLogin}。404 轮询语义：用户未授权前上游返回 404
     * （"会话未就绪"），必须继续等，其它非 2xx 才失败。</p>
     */
    @Override
    public Map<String, Object> startLogin() {
        Map<String, Object> session = newSession();
        String challenge = (String) session.get("challenge");
        String nonce = (String) session.get("nonce");
        String machineId = (String) session.get("machine_id");

        String query = "challenge=" + encode(challenge)
                + "&challenge_method=S256"
                + "&nonce=" + encode(nonce)
                + "&machine_id=" + encode(machineId)
                + "&client_id=" + encode(clientId());
        String verifyUrl = authBase() + DEVICE_SELECT_PATH + "?" + query;

        Map<String, Object> handle = new LinkedHashMap<>(session);
        handle.put("handle", nonce);
        handle.put("verifyUrl", verifyUrl);
        handle.put(H_LAST_POLL_AT, 0L);
        return handle;
    }

    /**
     * {@inheritDoc}
     *
     * <p>每 1 秒最多打一次上游（前端轮询更快时直接返回 {@code null}）。
     * 轮询 URL 带 verifier（PKCE 校验）；成功响应解析 token 与 user 信息。</p>
     */
    @Override
    public Map<String, Object> pollLogin(Map<String, Object> handle) {
        if (handle == null || isBlank(readString(handle, H_VERIFIER)) || isBlank(readString(handle, H_NONCE))) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "Qoder 登录句柄缺失 verifier / nonce");
        }
        long now = System.currentTimeMillis();
        Long lastPollAt = toLong(handle.get(H_LAST_POLL_AT));
        if (lastPollAt != null && now - lastPollAt < POLL_INTERVAL_MS) {
            return null;
        }
        handle.put(H_LAST_POLL_AT, now);

        String query = "nonce=" + encode(readString(handle, H_NONCE))
                + "&verifier=" + encode(readString(handle, H_VERIFIER))
                + "&challenge_method=S256";
        String url = openApiBase() + POLL_PATH + "?" + query;
        String response = getTolerant404(url, Map.of("Accept", "application/json"));

        Map<String, Object> payload = parseJson(response);
        String accessToken = firstString(payload, "token", "device_token", "access_token");
        if (isBlank(accessToken)) {
            // 2xx 但尚无 token：继续等。
            return null;
        }
        return buildCredential(payload, handle);
    }

    /**
     * {@inheritDoc}
     *
     * <p>请求体只发 {@code refresh_token} 与 {@code machine_id}（⚠️ 两者都必须，
     * 缺 machine_id 会因设备绑定校验失败）。保留原凭据的 {@code uid}/{@code nickname}
     * （续期响应不带，加密推理依赖 uid）。</p>
     */
    @Override
    public Map<String, Object> refresh(Map<String, Object> credentials) {
        String refreshToken = readString(credentials, "refresh_token");
        String machineId = readString(credentials, "machine_id");
        if (isBlank(refreshToken) || isBlank(machineId)) {
            throw new ApiException(ApiErrorCode.UNAUTHORIZED,
                    "Qoder 凭据缺少 refresh_token 或 machine_id，无法续期");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("refresh_token", refreshToken);
        body.put("machine_id", machineId);
        String response = ChannelHttp.postJson(openApiBase() + REFRESH_PATH, Json.toJSONString(body),
                Map.of("Content-Type", "application/json", "Accept", "application/json"));
        Map<String, Object> payload = parseJson(response);
        String accessToken = firstString(payload, "token", "device_token", "access_token");
        if (isBlank(accessToken)) {
            throw new ApiException(ApiErrorCode.UPSTREAM_ERROR,
                    "Qoder 续期响应缺少 token: " + ChannelHttp.truncate(response));
        }
        Map<String, Object> refreshed = buildCredential(payload, null);
        // 保留不在续期响应里的字段（machine_id 已在 body 里，uid/nickname 需回填）。
        refreshed.put("machine_id", machineId);
        copyIfPresent(credentials, refreshed, "uid");
        copyIfPresent(credentials, refreshed, "nickname");
        return refreshed;
    }

    /**
     * {@inheritDoc}
     *
     * <p>本地到期优先；否则探活 {@code GET /api/v1/userinfo}（Bearer），401 判 invalid。</p>
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
            String response = ChannelHttp.getJson(openApiBase() + USERINFO_PATH,
                    authHeaders(accessToken));
            Map<String, Object> payload = parseJson(response);
            Map<String, Object> result = status("valid", "探活成功");
            String name = readString(payload, "name");
            if (name != null) {
                result.put("nickname", name);
            }
            return result;
        } catch (ApiException e) {
            String message = e.getMessage() == null ? "" : e.getMessage();
            if (message.contains("401") || message.contains("403")) {
                return status("invalid", "探活 401/403: " + message);
            }
            return status("valid", "探活失败（网络），按本地有效期视为可用: " + message);
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>每日签到：领取全部 {@code CLAIM_BENEFIT} 且 {@code CLAIMABLE} 的活动。
     * 活动列表需带 {@code Cosy-ClientType: 10} 头（缺则 campaigns 为空）；
     * 已领取（replayed）不视为失败。无活动可领时返回 {@code already-claimed}。</p>
     */
    @Override
    public Map<String, Object> checkin(Map<String, Object> credentials) {
        Map<String, Object> result = new LinkedHashMap<>(credentials);
        String accessToken = readString(credentials, "access_token");
        if (isBlank(accessToken)) {
            result.put("claimResult", "凭据缺少 access_token，无法签到");
            return result;
        }
        int claimed = 0;
        int replayed = 0;
        int amount = 0;
        try {
            String campaignsBody = ChannelHttp.getJson(openApiBase() + CAMPAIGNS_PATH, checkinHeaders(accessToken));
            Map<String, Object> campaigns = parseJson(campaignsBody);
            Object rawList = campaigns.get("campaigns");
            if (rawList instanceof List<?> list) {
                for (Object item : list) {
                    if (!(item instanceof Map<?, ?> entry)) {
                        continue;
                    }
                    String actionType = String.valueOf(entry.get("actionType"));
                    String claimStatus = String.valueOf(entry.get("claimStatus"));
                    String campaignId = entry.get("campaignId") == null ? "" : String.valueOf(entry.get("campaignId"));
                    if (!"CLAIM_BENEFIT".equals(actionType) || campaignId.isBlank()) {
                        continue;
                    }
                    if ("CLAIMED".equals(claimStatus)) {
                        replayed++;
                        continue;
                    }
                    if (!"CLAIMABLE".equals(claimStatus)) {
                        continue;
                    }
                    String claimBody = ChannelHttp.postJson(
                            openApiBase() + CAMPAIGNS_PATH + "/" + encode(campaignId) + "/claim",
                            "{}", checkinHeaders(accessToken));
                    Map<String, Object> claimResult = parseJson(claimBody);
                    Object status = claimResult.get("status");
                    if (Boolean.TRUE.equals(claimResult.get("replayed"))) {
                        replayed++;
                    } else if (status != null && !"CLAIMED".equals(String.valueOf(status))) {
                        result.put("claimResult", "签到失败: " + ChannelHttp.truncate(claimBody));
                        return result;
                    } else {
                        claimed++;
                        Object a = claimResult.get("amount");
                        if (a instanceof Number n) {
                            amount += n.intValue();
                        }
                    }
                }
            }
        } catch (ApiException e) {
            result.put("claimResult", "签到请求失败: " + e.getMessage());
            return result;
        }
        if (claimed > 0) {
            result.put("claimResult", "已领取 " + claimed + " 项活动，积分 " + amount);
            result.put("claimedCredits", amount);
        } else if (replayed > 0) {
            result.put("claimResult", "今日已领取过（" + replayed + " 项）");
        } else {
            result.put("claimResult", "当前无活动可领取（活动每日 10:00 UTC+8 刷新）");
        }
        return result;
    }

    @Override
    public boolean supportsCheckin() {
        return true;
    }

    /**
     * {@inheritDoc}
     *
     * <p>模型目录走加密端点（需 WASM 签名），Java 侧无法复刻；
     * 返回国际/国内版各自的内置兜底目录（不含加密端点 key 之外的在线目录）。</p>
     */
    @Override
    public List<Map<String, Object>> listModels(Map<String, Object> credentials) {
        return FALLBACK_MODELS;
    }

    /** 国际版兜底目录（对齐 QODER_FALLBACK_MODELS 常用条目）。 */
    static final List<Map<String, Object>> FALLBACK_MODELS = List.of(
            model("qfmodel", "Qwen-Flash", 200_000, true),
            model("dfmodel", "DeepSeek-Flash", 1_000_000, true),
            model("gmodel", "GLM-5.3", 1_000_000, true),
            model("gfmodel", "GLM-5.3-Flash", 1_000_000, true),
            model("ultimate", "Ultimate", 1_000_000, true),
            model("performance", "Performance", 1_000_000, true));

    /**
     * 授权页所在站点。
     *
     * @return 形如 {@code https://qoder.com}（国内变体覆写）
     */
    protected String authBase() {
        return AUTH_BASE;
    }

    /**
     * OpenAPI 站点（轮询取 token / 续期 / 用户信息 / 签到都挂这里）。
     *
     * @return 形如 {@code https://openapi.qoder.sh}（国内变体覆写）
     */
    protected String openApiBase() {
        return OPEN_API_BASE;
    }

    /**
     * 授权页使用的 client_id。
     *
     * @return 国际版 client_id（国内变体覆写）
     */
    protected String clientId() {
        return CLIENT_ID;
    }

    // ── 私有辅助 ──

    /**
     * new Session
     * @return 返回值
    */
    private static Map<String, Object> newSession() {
        int length = 43 + RANDOM.nextInt(86);
        StringBuilder verifierBuilder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            verifierBuilder.append(PKCE_ALPHABET[RANDOM.nextInt(PKCE_ALPHABET.length)]);
        }
        String verifier = verifierBuilder.toString();
        String challenge;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.UTF_8));
            challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new ApiException(ApiErrorCode.INTERNAL_ERROR, "SHA-256 不可用: " + e.getMessage(), e);
        }
        Map<String, Object> session = new LinkedHashMap<>();
        session.put(H_VERIFIER, verifier);
        session.put("challenge", challenge);
        session.put(H_NONCE, UUID.randomUUID().toString());
        session.put(H_MACHINE_ID, UUID.randomUUID().toString());
        return session;
    }

    /**
     * build Credential
     * @param Map<String 参数
     * @param handle 处理
     * @param payload 参数
     * @return 返回值
    */
    private static Map<String, Object> buildCredential(Map<String, Object> payload, Map<String, Object> handle) {
        Map<String, Object> credential = new LinkedHashMap<>();
        String accessToken = firstString(payload, "token", "device_token", "access_token");
        credential.put("access_token", accessToken);
        credential.put("security_oauth_token", accessToken);
        String refreshToken = firstString(payload, "refresh_token", "refreshToken");
        if (refreshToken != null) {
            credential.put("refresh_token", refreshToken);
        }
        Long expireTime = toMillis(payload.get("expires_at") != null ? payload.get("expires_at") : payload.get("expiresAt"));
        if (expireTime != null) {
            credential.put("expire_time", expireTime);
        }
        Long refreshExpire = toMillis(payload.get("refresh_token_expires_at"));
        if (refreshExpire != null) {
            credential.put("refresh_token_expire_time", refreshExpire);
        }
        if (handle != null && handle.get(H_MACHINE_ID) != null) {
            credential.put("machine_id", handle.get(H_MACHINE_ID));
        }
        String uid = firstString(payload, "user_id", "userId");
        if (uid != null) {
            credential.put("uid", uid);
        }
        String nickname = firstString(payload, "user_name", "userName");
        if (nickname != null) {
            credential.put("nickname", nickname);
        }
        return credential;
    }

    /**
     * get Tolerant404
     * @param Map<String 参数
     * @param headers 参数
     * @param url 字符串
     * @return 返回值
    */
    private static String getTolerant404(String url, Map<String, String> headers) {
        try {
            return ChannelHttp.getJson(url, headers);
        } catch (ApiException e) {
            String message = e.getMessage() == null ? "" : e.getMessage();
            if (message.contains("404")) {
                return null;
            }
            // 限流同样按「尚未授权」返回 null：轮询本就在等用户操作，
            // 上游 429 只是让我们慢一点，不该把整个绑定判失败。
            if (ChannelHttp.isRateLimited(e)) {
                return null;
            }
            throw e;
        }
    }

    /**
     * sash Headers
     * @return 返回值
    */
    private static Map<String, String> sashHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Accept", "application/json");
        headers.put("Cosy-ClientType", "10");
        return headers;
    }

    /**
     * checkin Headers
     * @param accessToken 字符串
     * @return 返回值
    */
    private static Map<String, String> checkinHeaders(String accessToken) {
        Map<String, String> headers = new HashMap<>(sashHeaders());
        headers.put("Authorization", "Bearer " + accessToken);
        return headers;
    }

    /**
     * auth Headers
     * @param accessToken 字符串
     * @return 返回值
    */
    private static Map<String, String> authHeaders(String accessToken) {
        return Map.of("Authorization", "Bearer " + accessToken, "Accept", "application/json");
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
     * 构造一条模型描述（子类 {@link QoderCnAccountChannelAuthManager} 也用）。
     *
     * @param id 模型 id
     * @param name 展示名
     * @param contextWindow 上下文窗口
     * @param supportsImage 是否支持图片
     * @return 模型描述
     */
    static Map<String, Object> model(String id, String name, int contextWindow, boolean supportsImage) {
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("id", id);
        model.put("name", name);
        model.put("contextWindow", contextWindow);
        model.put("supportsImage", supportsImage);
        model.put("isFree", true);
        return model;
    }

    /**
     * first String
     * @param Map<String 参数
     * @param keys 参数
     * @param source 参数
     * @return 返回值
    */
    private static String firstString(Map<String, Object> source, String... keys) {
        for (String key : keys) {
            Object value = source.get(key);
            if (value instanceof String s && !s.isBlank()) {
                return s;
            }
        }
        return null;
    }

    /**
     * read String
     * @param Map<String 参数
     * @param key 字符串
     * @param source 参数
     * @return 返回值
    */
    private static String readString(Map<String, Object> source, String key) {
        return source == null ? null : firstString(source, key);
    }

    /**
     * to Millis
     * @param value 对象
     * @return 返回值
    */
    private static Long toMillis(Object value) {
        if (value instanceof Number number && number.doubleValue() > 0) {
            double v = number.doubleValue();
            return v < 1e12 ? Math.round(v * 1000d) : Math.round(v);
        }
        if (value instanceof String s && !s.isBlank()) {
            try {
                return java.time.Instant.parse(s.trim()).toEpochMilli();
            } catch (java.time.format.DateTimeParseException ignored) {
                return null;
            }
        }
        return null;
    }

    /**
     * 编码
     * @param value 字符串
     * @return 返回值
    */
    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
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
        return null;
    }

    /**
     * copy If Present
     * @param Map<String 参数
     * @param from 参数
     * @param key 字符串
     * @param to 参数
     * @return 返回值
    */
    private static void copyIfPresent(Map<String, Object> from, Map<String, Object> to, String key) {
        Object value = from.get(key);
        if (value != null) {
            to.put(key, value);
        }
    }

    /**
     * parse Json
     * @param response 字符串
     * @return 返回值
    */
    private static Map<String, Object> parseJson(String response) {
        if (response == null) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> map = Json.getJsonObject(response);
        return map != null ? map : new LinkedHashMap<>();
    }

    @Override
    public RouteTarget toRouteTarget(Map<String, Object> credentials) {
        String accessToken = readString(credentials, "access_token");
        if (isBlank(accessToken)) {
            return null;
        }
        return RouteTarget.builder()
                .provider("openai")
                .baseUrl(openApiBase() + "/api/v1")
                .apiKey(accessToken)
                .authScheme("api_key")
                .autoSwitchOnRateLimit(true)
                .build();
    }
}
