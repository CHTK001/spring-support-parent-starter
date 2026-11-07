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
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * LobsterAI（有道龙虾）账号渠道鉴权管理器实现。
 *
 * <p>协议（对齐 {@code dsh-our-free-model} 的 {@code lobsterai.ts} / {@code lobsterai-oauth.ts} /
 * {@code lobsterai-credits.ts}，门户登录 + {@code authCode} 换 token）：</p>
 * <ol>
 *   <li>{@code startLogin}：本地生成 {@code uuid}（安装 UUID）与 {@code firstKeyfrom}
 *       （首次登录毫秒时间戳），拼 portal 登录 URL
 *       {@code {portalBase}/portal#/login?source=electron&redirect_uri=http://127.0.0.1:<port>/auth/callback&state=<state>}；
 *       回调走前端粘贴（用户完成登录后把浏览器地址栏的
 *       {@code http://127.0.0.1:<port>/auth/callback?code=...&state=...} 粘回）。</li>
 *   <li>{@code pollLogin}：解析粘贴回调里的 {@code code}/{@code state}（state 不匹配一律拒绝），
 *       {@code POST {apiBase}/api/auth/exchange}（<b>不带 Authorization</b>，body 五字段
 *       {@code authCode/firstKeyfrom/latestKeyfrom/uuid/version}）→
 *       {@code {accessToken, refreshToken, expiresIn, user:{id, yid, userId, nickname}}}。</li>
 *   <li>{@code refresh}：{@code POST /api/auth/refresh} 同样<b>不带 Authorization</b>，
 *       请求体除 {@code refreshToken} 外还要带 {@code firstKeyfrom/latestKeyfrom/uuid}
 *       （丢了就续期失败只能重登）；响应可能不回新 refreshToken，回写须沿用旧值。</li>
 *   <li>{@code status}：本地到期判定（{@code expires_at} 毫秒字符串；缺失时解 access token
 *       JWT 的 {@code exp}）；不做探活。</li>
 *   <li>{@code checkin}：每日签到三步（查活动槽位 → 查上下文 → 执行 check_in），幂等。</li>
 *   <li>{@code listModels}：{@code GET /api/models/available}（Bearer +
 *       {@code X-LobsterAI-Client-Capabilities} 头必需，缺了会少模型）。</li>
 * </ol>
 *
 * <p>⚠️ 易踩点（源码注释原样保留）：统一信封 {@code {code, msg, data}}，
 * {@code code !== 0} 或 {@code data} 非对象即失败（服务端在凭据失效时倾向回
 * {@code code:0} 但 {@code data:null}）；{@code uuid} 一旦生成须贯穿全生命周期原样回传，
 * 每次刷新重新生成会破坏「同一安装」识别；{@code latestKeyfrom} 刻意<b>不</b>随刷新更新
 * （对齐生产验证过的 Go 实现）；{@code version} 用动态真值（更新接口拉取，失败兜底）。</p>
 *
 * @since 4.0.0.43
 * @author CH
 */
@Spi("lobsterai")
public class LobsteraiAccountChannelAuthManager implements AccountChannelAuthManager {

    /** 上游 API 基址（推理 / 模型 / 积分 / 活动全挂它下面）。 */
    static final String API_BASE = "https://lobsterai-server.youdao.com";
    /** 登录门户基址（不含 /portal 路径）。 */
    static final String PORTAL_BASE = "https://lobsterai.youdao.com";
    /** 客户端版本号查询端点（第三方域名，响应非统一信封，载荷在 data.value.version）。 */
    static final String CLIENT_VERSION_API =
            "https://api-overmind.youdao.com/openapi/get/luna/hardware/lobsterai/prod/update";
    /** 版本号拉取失败时的兜底值（服务端不强校验 version）。 */
    static final String FALLBACK_CLIENT_VERSION = "2026.9.4";

    /** 端点路径。 */
    static final String EXCHANGE_PATH = "/api/auth/exchange";
    static final String REFRESH_PATH = "/api/auth/refresh";
    static final String MODELS_PATH = "/api/models/available";
    static final String PROFILE_SUMMARY_PATH = "/api/user/profile-summary";
    static final String ACTIVITY_SLOT_PATH = "/api/client-activities/slot";
    static final String ACTIVITY_BASE_PATH = "/api/client-activities";

    /** 用户标识头（照抄 client.go，刻意不用真版本号，避免与版本头一起变更无法归因）。 */
    static final String USER_AGENT = "LobsterAI/0.1.0";
    /** 能力声明（模型列表准入条件；缺 kimi-k3-agentic-v1 会少 1 个模型）。 */
    static final String CLIENT_CAPABILITIES = "kimi-k3-agentic-v1,thinking-level-control-v1";
    /** 活动槽位固定参数（对齐 sigin.py）。 */
    static final String SLOT_PLACEMENT = "desktop_sidebar";
    static final String SLOT_CONTAINER_API_VERSION = "2";
    static final String SLOT_PLATFORM = "win32";

    /** 凭据过期余量（毫秒）。 */
    static final long EXPIRY_LEAD_MS = 60_000L;

    /** 登录句柄：安装 UUID（登录时生成，之后每次续期原样回传）。 */
    static final String HANDLE_UUID = "uuid";
    /** 登录句柄：首次登录毫秒时间戳字符串。 */
    static final String HANDLE_FIRST_KEYFROM = "firstKeyfrom";
    /** 登录句柄：state（回调必须原样带回，防伪造）。 */
    static final String HANDLE_STATE = "state";
    /** 句柄：前端粘贴回来的回调 URL。 */
    static final String HANDLE_CALLBACK_URL = "callbackUrl";

    /** 本地回调端口（门户登录完成后浏览器跳转到这里，由本服务监听）。 */
    static final int CALLBACK_PORT = 8846;
    /** 本地回调路径。 */
    static final String CALLBACK_PATH = "/auth/callback";

    @Override
    public String channel() {
        return "lobsterai";
    }

    @Override
    public String displayName() {
        return "LobsterAI（有道）";
    }

    @Override
    public String loginMethod() {
        return "browser-oauth";
    }

    /**
     * {@inheritDoc}
     *
     * <p>本地生成 {@code uuid} 与 {@code firstKeyfrom}（两者不在服务端响应里，
     * 是登录流程自生成的状态，须随凭据持久化并贯穿整个账号生命周期），
     * 拼 portal 登录 URL。回调由后端<b>自己监听</b> {@code 127.0.0.1:8846}
     * （{@link OauthCallbackServer}）：门户登录完成后浏览器跳转到
     * {@code http://127.0.0.1:8846/auth/callback?code=...&state=...}，
     * {@code pollLogin} 自动取用，用户无需粘贴。</p>
     */
    @Override
    public Map<String, Object> startLogin() {
        String uuid = UUID.randomUUID().toString();
        String firstKeyfrom = String.valueOf(System.currentTimeMillis());
        String state = UUID.randomUUID().toString();

        OauthCallbackServer.Binding binding = OauthCallbackServer.ensure(channel(), CALLBACK_PORT);
        String redirectUri = "http://127.0.0.1:" + binding.port() + CALLBACK_PATH;
        String loginUrl = PORTAL_BASE + "/portal#/login?source=electron"
                + "&redirect_uri=" + urlEncode(redirectUri)
                + "&state=" + urlEncode(state);

        Map<String, Object> handle = new LinkedHashMap<>();
        handle.put("handle", "lobsterai-oauth");
        handle.put(HANDLE_UUID, uuid);
        handle.put(HANDLE_FIRST_KEYFROM, firstKeyfrom);
        handle.put(HANDLE_STATE, state);
        handle.put("callbackListening", binding.alive());
        handle.put("callbackPort", binding.port());
        handle.put("verifyUrl", loginUrl);
        handle.put("startedAt", System.currentTimeMillis());
        return handle;
    }

    /**
     * {@inheritDoc}
     *
     * <p>优先取后端本地回调监听器收到的跳转；监听不可用时回退到前端粘贴的
     * {@code callbackUrl}。解析 {@code code}/{@code state}，
     * 校验 state 后调 exchange 端点换凭据。state 不匹配一律拒绝（防伪造回调）。</p>
     */
    @Override
    public Map<String, Object> pollLogin(Map<String, Object> handle) {
        if (handle == null || isBlank(readString(handle, "handle"))) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "LobsterAI 登录句柄缺失 handle");
        }
        String callbackUrl = handle.get(HANDLE_CALLBACK_URL) instanceof String s && !s.isBlank()
                ? s.trim()
                : null;
        if (callbackUrl == null) {
            String query = OauthCallbackServer.take(channel());
            if (query != null) {
                callbackUrl = "http://127.0.0.1" + CALLBACK_PATH + "?" + query;
            }
        }
        if (callbackUrl == null) {
            // 尚未收到回调（用户还没完成登录）→ 继续等待。
            return null;
        }
        Map<String, String> params = parseCallbackUrl(callbackUrl);
        String state = params.get("state");
        String expectedState = readString(handle, HANDLE_STATE);
        if (expectedState != null && !expectedState.equals(state)) {
            throw new ApiException(ApiErrorCode.UNAUTHORIZED, "回调 state 校验失败（疑似伪造回调）");
        }
        String code = params.get("code");
        if (code == null) {
            return null; // 回调缺少 code（用户未完成登录），继续等待。
        }
        return exchange(code, handle);
    }

    /**
     * {@inheritDoc}
     *
     * <p>refresh 端点<b>不带 Authorization</b>；请求体 = keyfrom 载荷
     * （{@code firstKeyfrom/latestKeyfrom/version}，有才带的 {@code uuid/userId}）
     * + {@code refreshToken}。{@code latestKeyfrom} 用<b>凭据里存储的原值</b>，
     * 不取当前时刻（对齐生产验证过的实现）；响应未回新 refreshToken 时沿用旧值。</p>
     */
    @Override
    public Map<String, Object> refresh(Map<String, Object> credentials) {
        String refreshToken = readString(credentials, "refresh_token");
        if (isBlank(refreshToken)) {
            throw new ApiException(ApiErrorCode.UNAUTHORIZED,
                    "LobsterAI 凭据缺少 refresh_token，需要重新登录");
        }
        Map<String, Object> body = keyfromBody(credentials, fetchClientVersion());
        body.put("refreshToken", refreshToken);
        String response = ChannelHttp.postJson(API_BASE + REFRESH_PATH,
                Json.toJSONString(body), anonymousHeaders());
        Map<String, Object> refreshed = parseTokenResponse(response, credentials);
        // 保留不在续期响应里的身份字段（refresh 响应只带令牌，不含 user 对象）。
        for (String key : new String[]{"uuid", "first_keyfrom", "latest_keyfrom",
                "uid", "user_id", "nickname"}) {
            Object value = credentials.get(key);
            if (value != null) {
                refreshed.putIfAbsent(key, value);
            }
        }
        return refreshed;
    }

    /**
     * {@inheritDoc}
     *
     * <p>本地到期判定：{@code expires_at}（毫秒字符串）+ 60s 余量；缺失时回退解析
     * access token JWT 的 {@code exp}；都拿不到则不判过期（服务端失效由业务层识别）。</p>
     */
    @Override
    public Map<String, Object> status(Map<String, Object> credentials) {
        String accessToken = readString(credentials, "access_token");
        if (isBlank(accessToken)) {
            return status("invalid", "凭据缺少 access_token");
        }
        Long expiresAt = toLong(credentials.get("expires_at"));
        if (expiresAt == null) {
            expiresAt = jwtExpiresAtMs(accessToken);
        }
        if (expiresAt != null && expiresAt - EXPIRY_LEAD_MS <= System.currentTimeMillis()) {
            return status("expired", "访问令牌已到期（含 60s 余量）");
        }
        return status("valid", "本地有效期判定可用");
    }

    /**
     * {@inheritDoc}
     *
     * <p>每日签到三步（幂等，对齐 {@code lobsterai-credits.ts}）：</p>
     * <ol>
     *   <li>查活动槽位 {@code GET /api/client-activities/slot?placement=…&clientVersion=…
     *       &containerApiVersion=…&platform=…} → {@code slotState}/{@code activityCode}/
     *       {@code configRevision}；</li>
     *   <li>查活动上下文 {@code GET /api/client-activities/{code}/context?configRevision=…}
     *       → {@code claimedToday}/{@code actions}；</li>
     *   <li>签到 {@code POST /api/client-activities/{code}/actions/check_in}（body
     *       {@code {configRevision, idempotencyKey, payload:{}}}）。</li>
     * </ol>
     * <p>结果并入凭据 Map（{@code checkinResult}=claimed/already-claimed/inactive/failed +
     * {@code credits}），不抛错（「已领过/无活动」是正常业务状态）。</p>
     */
    @Override
    public Map<String, Object> checkin(Map<String, Object> credentials) {
        Map<String, Object> result = new LinkedHashMap<>(credentials);
        String version = fetchClientVersion();
        try {
            // 1) 活动槽位。
            String slotUrl = API_BASE + ACTIVITY_SLOT_PATH + "?"
                    + "placement=" + urlEncode(SLOT_PLACEMENT)
                    + "&clientVersion=" + urlEncode(version)
                    + "&containerApiVersion=" + SLOT_CONTAINER_API_VERSION
                    + "&platform=" + urlEncode(SLOT_PLATFORM);
            Map<String, Object> slotData = getEnvelopeData(slotUrl, credentials, "活动槽位查询");
            String slotState = String.valueOf(slotData.getOrDefault("slotState", ""));
            Map<String, Object> activity = slotData.get("activity") instanceof Map<?, ?> a
                    ? (Map<String, Object>) a : Map.of();
            String activityCode = readString(activity, "activityCode");
            // 对齐 JS readNumber：缺失归 0（configRevision=0 是合法初值）。
            long configRevision = 0;
            Object crRaw = activity.get("configRevision");
            if (crRaw instanceof Number n) {
                configRevision = n.longValue();
            } else if (crRaw instanceof String s && s.matches("-?\\d+")) {
                configRevision = Long.parseLong(s);
            }
            if (!"available".equals(slotState) || isBlank(activityCode)) {
                result.put("checkinResult", "inactive");
                result.put("checkinMessage", "无可用活动（slotState=" + slotState + "）");
                return result;
            }

            // 2) 活动上下文。
            String contextUrl = API_BASE + ACTIVITY_BASE_PATH + "/" + urlEncode(activityCode)
                    + "/context?configRevision=" + configRevision;
            Map<String, Object> contextData = getEnvelopeData(contextUrl, credentials, "活动上下文查询");
            Map<String, Object> state = contextData.get("state") instanceof Map<?, ?> s
                    ? (Map<String, Object>) s : Map.of();
            if (Boolean.TRUE.equals(state.get("claimedToday"))) {
                result.put("checkinResult", "already-claimed");
                result.put("checkinMessage", "今天已签到");
                return result;
            }
            List<String> actions = contextData.get("actions") instanceof List<?> l
                    ? l.stream().map(String::valueOf).toList()
                    : List.of();
            if (!actions.contains("check_in")) {
                result.put("checkinResult", "inactive");
                result.put("checkinMessage", "当前不可签到");
                return result;
            }

            // 3) 执行签到。
            Map<String, Object> checkinBody = new LinkedHashMap<>();
            checkinBody.put("configRevision", configRevision);
            checkinBody.put("idempotencyKey", UUID.randomUUID().toString());
            checkinBody.put("payload", Map.of());
            String response = ChannelHttp.postJson(
                    API_BASE + ACTIVITY_BASE_PATH + "/" + urlEncode(activityCode)
                            + "/actions/check_in",
                    Json.toJSONString(checkinBody), authHeaders(credentials));
            Map<String, Object> payload = parseJson(response);
            int code = readInt(payload, "code");
            if (code != 0 || payload.get("data") == null) {
                result.put("checkinResult", "failed");
                result.put("checkinMessage", String.valueOf(payload.get("msg")));
                return result;
            }
            Map<String, Object> data = (Map<String, Object>) payload.get("data");
            Map<String, Object> bodyResult = data.get("result") instanceof Map<?, ?> r
                    ? (Map<String, Object>) r : Map.of();
            Number credit = firstNumber(bodyResult, "creditsGranted", "rewardCredits", "credits");
            result.put("checkinResult", "claimed");
            result.put("credits", credit != null ? credit.doubleValue() : 0);
            result.put("checkinMessage", readString(bodyResult, "message") != null
                    ? readString(bodyResult, "message") : "签到成功");
        } catch (ApiException e) {
            result.put("checkinResult", "failed");
            result.put("checkinMessage", e.getMessage());
        }
        return result;
    }

    @Override
    public boolean supportsCheckin() {
        // 有道龙虾有每日签到（活动 check_in，每日登录奖励），支持。
        return true;
    }

    /**
     * {@inheritDoc}
     *
     * <p>远端模型目录：{@code GET /api/models/available}，query 带 keyfrom 身份载荷
     * （{@code firstKeyfrom/latestKeyfrom/version/uuid/userId}，<b>不含</b> refreshToken——
     * 进 query 既是信息泄露也不是该端点预期输入）。
     * 响应条目字段是驼峰 {@code modelId}/{@code modelName}/{@code contextWindow}；
     * 模型数组在 {@code data}（数组）或 {@code data.data}（嵌套）。
     * 服务端按 {@code X-LobsterAI-Client-Capabilities} 能力头过滤模型集合，
     * 缺 {@code kimi-k3-agentic-v1} 会永久少 {@code kimi-k3}。失败返回空列表不抛错。</p>
     */
    @Override
    public List<Map<String, Object>> listModels(Map<String, Object> credentials) {
        String accessToken = readString(credentials, "access_token");
        if (isBlank(accessToken)) {
            return List.of();
        }
        List<Map<String, Object>> result = new java.util.ArrayList<>();
        try {
            Map<String, String> headers = authHeaders(credentials);
            headers.put("X-LobsterAI-Client-Capabilities", CLIENT_CAPABILITIES);
            String version = fetchClientVersion();
            headers.put("X-LobsterAI-Client-Version", version);
            String query = modelsQuery(credentials, version);
            String response = ChannelHttp.getJson(API_BASE + MODELS_PATH + "?" + query, headers);
            Map<String, Object> payload = parseJson(response);
            int code = readInt(payload, "code");
            if (code != 0) {
                return List.of();
            }
            List<?> items = null;
            Object data = payload.get("data");
            if (data instanceof List<?> l) {
                items = l;
            } else if (data instanceof Map<?, ?> d && d.get("data") instanceof List<?> nested) {
                items = nested;
            }
            if (items == null) {
                return List.of();
            }
            for (Object item : items) {
                if (item instanceof Map<?, ?> entry) {
                    Map<String, Object> model = new LinkedHashMap<>();
                    String id = readLooseString(entry, "modelId");
                    if (isBlank(id)) {
                        continue;
                    }
                    model.put("id", id);
                    String name = readLooseString(entry, "modelName");
                    model.put("name", isBlank(name) ? id : name);
                    Long contextWindow = toLong(entry.get("contextWindow"));
                    if (contextWindow != null && contextWindow > 0) {
                        model.put("contextWindow", contextWindow.intValue());
                    }
                    model.put("isFree", true);
                    result.add(model);
                }
            }
        } catch (ApiException ignored) {
            // 远端目录拉取失败不致命（返回空列表，上层可走兜底模型表）。
        }
        return result;
    }

    /**
     * models Query
     * @param Map<String 参数
     * @param credentials 参数
     * @param version 字符串
     * @return 返回值
    */
    private String modelsQuery(Map<String, Object> credentials, String version) {
        Map<String, Object> keyfrom = keyfromBody(credentials, version);
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Object> entry : keyfrom.entrySet()) {
            if (entry.getValue() instanceof String s && !s.isEmpty()) {
                if (sb.length() > 0) {
                    sb.append('&');
                }
                sb.append(urlEncode(entry.getKey())).append('=').append(urlEncode(s));
            }
        }
        return sb.toString();
    }

    // ── 私有辅助 ──

    /**
     * exchange
     * @param Map<String 参数
     * @param code 字符串
     * @param handle 处理
     * @return 返回值
    */
    private Map<String, Object> exchange(String code, Map<String, Object> handle) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("authCode", code);
        body.put("firstKeyfrom", readString(handle, HANDLE_FIRST_KEYFROM));
        body.put("latestKeyfrom", String.valueOf(System.currentTimeMillis()));
        body.put("uuid", readString(handle, HANDLE_UUID));
        body.put("version", fetchClientVersion());
        String response = ChannelHttp.postJson(
                API_BASE + EXCHANGE_PATH, Json.toJSONString(body), anonymousHeaders());
        Map<String, Object> credential = parseTokenResponse(response, handle);
        String accessToken = readString(credential, "access_token");
        if (isBlank(accessToken)) {
            throw new ApiException(ApiErrorCode.UPSTREAM_ERROR,
                    "LobsterAI exchange 响应缺少 accessToken: " + ChannelHttp.truncate(response));
        }
        // 补齐安装状态字段（它们不在响应里，是登录流程自生成的）。
        credential.putIfAbsent("uuid", readString(handle, HANDLE_UUID));
        credential.putIfAbsent("first_keyfrom", readString(handle, HANDLE_FIRST_KEYFROM));
        credential.putIfAbsent("latest_keyfrom", body.get("latestKeyfrom"));
        // uid 解析：user.id → user.userId → user.yid → sha256(accessToken) 前 16 位。
        credential.put("uid", resolveUid(credential));
        return credential;
    }

    /**
     * parse Token Response
     * @param Map<String 参数
     * @param previous 参数
     * @param response 字符串
     * @return 返回值
    */
    private Map<String, Object> parseTokenResponse(String response, Map<String, Object> previous) {
        Map<String, Object> payload = parseJson(response);
        int code = readInt(payload, "code");
        if (code != 0 || !(payload.get("data") instanceof Map<?, ?> rawData)) {
            String msg = readString(payload, "msg");
            throw new ApiException(ApiErrorCode.UPSTREAM_ERROR,
                    "LobsterAI token 端点错误: "
                            + (msg != null && !msg.isBlank() ? msg : "code=" + code));
        }
        Map<String, Object> data = (Map<String, Object>) rawData;
        Map<String, Object> user = data.get("user") instanceof Map<?, ?> u
                ? (Map<String, Object>) u : Map.of();
        Map<String, Object> credential = new LinkedHashMap<>();
        credential.put("access_token", readString(data, "accessToken"));
        String refreshToken = readString(data, "refreshToken");
        // 响应可能不返回新 refreshToken（沿用旧的），不能覆盖成空串。
        credential.put("refresh_token", isBlank(refreshToken)
                ? readString(previous, "refresh_token") : refreshToken);
        // expires_at 取值顺序：expiresIn（相对秒）→ JWT exp → 留空（不再判过期）。
        Long expiresIn = toLong(data.get("expiresIn"));
        if (expiresIn != null && expiresIn > 0) {
            credential.put("expires_at", System.currentTimeMillis() + expiresIn * 1000);
        } else {
            Long jwtExp = jwtExpiresAtMs(readString(credential, "access_token"));
            if (jwtExp != null) {
                credential.put("expires_at", jwtExp);
            }
        }
        // 身份字段（user.id / user.yid / user.userId / nickname）。
        String userId = readString(user, "id");
        String yid = readString(user, "yid");
        String accountUserId = readString(user, "userId");
        if (isBlank(userId) && isBlank(accountUserId) && isBlank(yid)) {
            credential.put("user_id", "");
        } else {
            credential.put("user_id", !isBlank(accountUserId) ? accountUserId : (isBlank(yid) ? "" : yid));
        }
        String nickname = readString(user, "nickname");
        if (nickname != null) {
            credential.put("nickname", maskPhoneTail(nickname));
        }
        return credential;
    }

    /**
     * mask Phone Tail
     * @param value 字符串
     * @return 返回值
    */
    private static String maskPhoneTail(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.matches("1\\d{10}")) {
            return trimmed.substring(0, 3) + "******" + trimmed.substring(9);
        }
        java.util.regex.Matcher masked = java.util.regex.Pattern
                .compile("(\\d{3})(\\*+)(\\d+)").matcher(trimmed);
        if (masked.matches()) {
            int starCount = trimmed.length() - 3 - masked.group(3).length();
            if (starCount >= 0) {
                return masked.group(1) + "*".repeat(starCount)
                        + masked.group(3).substring(Math.max(0, masked.group(3).length() - 2));
            }
        }
        return trimmed;
    }

    /**
     * resolve Uid
     * @param Map<String 参数
     * @param credential 参数
     * @return 返回值
    */
    private String resolveUid(Map<String, Object> credential) {
        String userId = readString(credential, "user_id");
        if (!isBlank(userId)) {
            return userId;
        }
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] digest = sha256.digest(
                    readString(credential, "access_token").getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.substring(0, 16);
        } catch (Exception e) {
            return String.valueOf(credential.getOrDefault("user_id", ""));
        }
    }

    /**
     * keyfrom 身份载荷（refresh 请求体）：{@code firstKeyfrom/latestKeyfrom/version} + 有才带的 {@code uuid/userId}（缺省时<b>删键</b>而非写空串）。
     * @param Map<String 参数
     * @param credentials 参数
     * @param version 字符串
     * @return 返回值
    */
    private Map<String, Object> keyfromBody(Map<String, Object> credentials, String version) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("firstKeyfrom", readString(credentials, "first_keyfrom"));
        // 用凭据里存储的原值，不取当前时刻。
        body.put("latestKeyfrom", readString(credentials, "latest_keyfrom"));
        body.put("version", version);
        String uuid = readString(credentials, "uuid");
        if (!isBlank(uuid)) {
            body.put("uuid", uuid);
        }
        String userId = readString(credentials, "user_id");
        if (!isBlank(userId)) {
            body.put("userId", userId);
        }
        return body;
    }

    /** 拉客户端版本号（更新接口 {@code data.value.version}，日期式），失败用兜底值。 */
    static String fetchClientVersion() {
        try {
            String response = ChannelHttp.getJson(CLIENT_VERSION_API, anonymousHeaders());
            Map<String, Object> payload = parseJson(response);
            if (payload.get("data") instanceof Map<?, ?> outer
                    && outer.get("value") instanceof Map<?, ?> value
                    && value.get("version") instanceof String v) {
                String trimmed = v.trim();
                if (trimmed.matches("\\d+(?:\\.\\d+)*(?:-[0-9A-Za-z.-]+)?")) {
                    return trimmed;
                }
            }
        } catch (ApiException ignored) {
            // 取不到版本号不阻断主流程：服务端不强校验，用兜底值继续。
        }
        return FALLBACK_CLIENT_VERSION;
    }

    /**
     * get Envelope Data
     * @param Map<String 参数
     * @param credentials 参数
     * @param label 字符串
     * @param url 字符串
     * @return 返回值
    */
    private Map<String, Object> getEnvelopeData(String url, Map<String, Object> credentials, String label) {
        String response = ChannelHttp.getJson(url, authHeaders(credentials));
        Map<String, Object> payload = parseJson(response);
        int code = readInt(payload, "code");
        if (code != 0 || !(payload.get("data") instanceof Map<?, ?> data)) {
            String msg = readString(payload, "msg");
            throw new ApiException(ApiErrorCode.UPSTREAM_ERROR,
                    "LobsterAI " + label + "失败: "
                            + (msg != null && !msg.isBlank() ? msg : "code=" + code));
        }
        return (Map<String, Object>) data;
    }

    /**
     * auth Headers
     * @param Map<String 参数
     * @param credentials 参数
     * @return 返回值
    */
    private Map<String, String> authHeaders(Map<String, Object> credentials) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Authorization", "Bearer " + readString(credentials, "access_token"));
        headers.put("Accept", "application/json");
        headers.put("Content-Type", "application/json");
        headers.put("User-Agent", USER_AGENT);
        return headers;
    }

    /**
     * anonymous Headers
     * @return 返回值
    */
    private static Map<String, String> anonymousHeaders() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Accept", "application/json");
        headers.put("Content-Type", "application/json");
        headers.put("User-Agent", USER_AGENT);
        return headers;
    }

    /**
     * parse Callback Url
     * @param rawUrl 字符串
     * @return 返回值
    */
    private static Map<String, String> parseCallbackUrl(String rawUrl) {
        String trimmed = rawUrl.trim();
        int queryIndex = trimmed.indexOf('?');
        if (queryIndex >= 0) {
            trimmed = trimmed.substring(queryIndex + 1);
        } else if (trimmed.startsWith("http")) {
            return Map.of();
        } else if (!trimmed.startsWith("code=")) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (String pair : trimmed.split("&")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            result.put(urlDecode(pair.substring(0, eq)), urlDecode(pair.substring(eq + 1)));
        }
        return result;
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
     * parse Json
     * @param response 字符串
     * @return 返回值
    */
    private static Map<String, Object> parseJson(String response) {
        Map<String, Object> map = Json.getJsonObject(response);
        return map != null ? map : new LinkedHashMap<>();
    }

    /**
     * first Number
     * @param Map<String 参数
     * @param keys 参数
     * @param source 参数
     * @return 返回值
    */
    private static Number firstNumber(Map<String, Object> source, String... keys) {
        for (String key : keys) {
            Object value = source.get(key);
            if (value instanceof Number n && n.doubleValue() == n.doubleValue()) {
                return n;
            }
        }
        return null;
    }

    /**
     * read Int
     * @param Map<String 参数
     * @param key 字符串
     * @param source 参数
     * @return 返回值
    */
    private static int readInt(Map<String, Object> source, String key) {
        Object value = source.get(key);
        if (value instanceof Number n) {
            return n.intValue();
        }
        if (value instanceof String s && s.matches("-?\\d+")) {
            return Integer.parseInt(s);
        }
        return -1;
    }

    /**
     * jwt Expires At Ms
     * @param jwt 字符串
     * @return 返回值
    */
    private static Long jwtExpiresAtMs(String jwt) {
        if (isBlank(jwt)) {
            return null;
        }
        try {
            String[] parts = jwt.split("\\.");
            if (parts.length < 2) {
                return null;
            }
            byte[] decoded = Base64.getUrlDecoder().decode(parts[1]);
            Map<String, Object> payload = parseJson(new String(decoded, StandardCharsets.UTF_8));
            Object exp = payload.get("exp");
            if (exp instanceof Number n) {
                return Math.round(n.doubleValue() * 1000d);
            }
        } catch (IllegalArgumentException ignored) {
            return null;
        }
        return null;
    }

    /**
     * url Encode
     * @param value 字符串
     * @return 返回值
    */
    private static String urlEncode(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /**
     * url Decode
     * @param value 字符串
     * @return 返回值
    */
    private static String urlDecode(String value) {
        return java.net.URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    /**
     * read Loose String
     * @param Map<? 参数
     * @param key 字符串
     * @param source 参数
     * @return 返回值
    */
    private static String readLooseString(Map<?, ?> source, String key) {
        Object value = source == null ? null : source.get(key);
        if (value instanceof String s && !s.isBlank()) {
            return s;
        }
        if (value instanceof Number n) {
            return String.valueOf(n);
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