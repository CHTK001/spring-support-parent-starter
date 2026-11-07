package com.chua.starter.ai.support.spi.impl;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.ai.support.manage.common.ApiErrorCode;
import com.chua.starter.ai.support.manage.common.ApiException;
import com.chua.starter.common.support.channel.AccountChannelAuthManager;
import com.chua.starter.common.support.text.json.Json;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ZCode（智谱 Z.ai / GLM 智谱清言）账号渠道鉴权管理器实现。
 *
 * <p>协议（对齐 {@code dsh-our-free-model} 的 {@code zcode-login.ts}，服务端中介设备授权流）：</p>
 * <ol>
 *   <li>生成 device_mid（32 字节 hex，<b>插件自己生成</b>，不被绑定校验）；
 *       {@code POST {authHost}/api/v1/oauth/cli/init}（Bearer device_mid）→
 *       {@code {flow_id, authorize_url, poll_interval_sec}}</li>
 *   <li>用户打开 {@code authorize_url} 完成授权；插件轮询
 *       {@code GET {authHost}/api/v1/oauth/cli/poll/{flow_id}}（Bearer device_mid）
 *       → {@code {status: ready, token(zcode_jwt), user}}（status 也可能 pending）。</li>
 * </ol>
 *
 * <p>⚠️ 易踩点（源码注释原样保留）：init 鉴权头是 Bearer 自生成 device_mid（不是用户 token）；
 * 凭据 JWT 无 exp，<b>不可自动续期</b>（refresh 不可用，过期只能重绑）；
 * 额度是每模型固定上限（dailyQuota），非签到发放。</p>
 *
 * <p>凭据落库 {@code access_token}(zcode_jwt) + {@code user}/{@code nickname}。</p>
 *
 * @since 4.0.0.43
 * @author CH
 */
@Spi("zcode")
public class ZcodeAccountChannelAuthManager implements AccountChannelAuthManager {

    /** 认证 / 网关基址（init 与 poll 都挂它）。 */
    static final String AUTH_HOST = "https://zcode.z.ai";
    /** OAuth CLI 前缀。 */
    static final String OAUTH_PATH_PREFIX = "/api/v1/oauth/cli";
    /** 轮询间隔（毫秒，约 2s）。 */
    static final long POLL_INTERVAL_MS = 2_000L;
    /** 轮询句柄记录的最后一次轮询时刻（毫秒）。 */
    static final String HANDLE_LAST_POLL_AT = "__lastPollAt";

    @Override
    public String channel() {
        return "zcode";
    }

    @Override
    public String displayName() {
        return "ZCode";
    }

    @Override
    public String loginMethod() {
        return "browser-device";
    }

    /**
     * {@inheritDoc}
     *
     * <p>生成 device_mid 并向服务端 init，拿 {@code flow_id} 与 {@code authorize_url}。
     * 返回句柄带 {@code verifyUrl}=authorize_url 供用户打开，凭据里的 device_mid 一并记录。</p>
     */
    @Override
    public Map<String, Object> startLogin() {
        String deviceMid = deviceMid();
        // ⚠️ init 的 body 必须是 {"provider":"bigmodel"}，空 body 会被拒（官方 asar 实测）。
        Map<String, Object> initBody = new LinkedHashMap<>();
        initBody.put("provider", "bigmodel");
        String response = ChannelHttp.postJson(
                AUTH_HOST + OAUTH_PATH_PREFIX + "/init", Json.toJSONString(initBody), authHeaders(deviceMid));
        Map<String, Object> payload = unwrapData(response);
        String flowId = readString(payload, "flow_id");
        String authorizeUrl = readString(payload, "authorize_url");
        if (isBlank(flowId) || isBlank(authorizeUrl)) {
            throw new ApiException(ApiErrorCode.UPSTREAM_ERROR,
                    "ZCode 授权初始化响应缺少 flow_id / authorize_url: " + ChannelHttp.truncate(response));
        }
        Map<String, Object> handle = new LinkedHashMap<>();
        handle.put("handle", flowId);
        handle.put("device_mid", deviceMid);
        handle.put("poll_token", readString(payload, "poll_token"));
        handle.put("verifyUrl", authorizeUrl);
        handle.put("poll_interval_sec", payload.get("poll_interval_sec"));
        handle.put("startedAt", System.currentTimeMillis());
        return handle;
    }

    /**
     * {@inheritDoc}
     *
     * <p>按 2s 间隔轮询 {@code poll/{flow_id}}；status=ready 时取 {@code token}(zcode_jwt)
     * 与 user 返回可持久化凭据；status=pending 返回 null；其它状态抛错。device_mid 由句柄携带。</p>
     */
    @Override
    public Map<String, Object> pollLogin(Map<String, Object> handle) {
        if (handle == null || isBlank(readString(handle, "handle"))) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "ZCode 登录句柄缺失 handle(flow_id)");
        }
        long now = System.currentTimeMillis();
        Long lastPollAt = toLong(handle.get(HANDLE_LAST_POLL_AT));
        if (lastPollAt != null && now - lastPollAt < POLL_INTERVAL_MS) {
            return null;
        }
        String flowId = readString(handle, "handle");
        String deviceMid = readString(handle, "device_mid");
        if (isBlank(deviceMid)) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "ZCode 登录句柄缺少 device_mid");
        }
        String response;
        try {
            response = ChannelHttp.getJson(
                    AUTH_HOST + OAUTH_PATH_PREFIX + "/poll/" + flowId, authHeaders(deviceMid));
        } catch (ApiException e) {
            // ⚠️ 上游限流（429）不是授权失败：用户可能还在授权页操作，
            // 2s 一次的轮询很容易触发限流，直接向上抛会让弹窗显示「失败」并中断绑定。
            // 记下本轮时间戳，等下一轮继续轮询。
            if (ChannelHttp.isRateLimited(e)) {
                handle.put(HANDLE_LAST_POLL_AT, now);
                return null;
            }
            throw e;
        }
        Map<String, Object> payload = unwrapData(response);
        String status = readString(payload, "status");
        if (isBlank(status) || "pending".equalsIgnoreCase(status) || "waiting".equalsIgnoreCase(status)) {
            handle.put(HANDLE_LAST_POLL_AT, now);
            return null;
        }
        if (!"ready".equalsIgnoreCase(status)) {
            throw new ApiException(ApiErrorCode.UPSTREAM_ERROR,
                    "ZCode 授权轮询失败: " + ChannelHttp.truncate(Json.toJSONString(payload)));
        }
        String token = readString(payload, "token");
        if (isBlank(token)) {
            token = readString(payload, "zcode_jwt");
        }
        if (isBlank(token)) {
            throw new ApiException(ApiErrorCode.UPSTREAM_ERROR,
                    "ZCode 授权轮询 ready 但缺少 token: " + ChannelHttp.truncate(Json.toJSONString(payload)));
        }
        // user 对象：user_id 是权威身份（缺失视为失败，与官方解析器一致）。
        Map<String, Object> user = payload.get("user") instanceof Map<?, ?> u ? (Map<String, Object>) u : Map.of();
        String userId = readLooseString(user, "user_id");
        if (isBlank(userId)) {
            userId = readLooseString(user, "id");
        }
        if (isBlank(userId)) {
            throw new ApiException(ApiErrorCode.UPSTREAM_ERROR,
                    "ZCode 授权轮询 ready 但缺少 user.user_id: "
                            + ChannelHttp.truncate(Json.toJSONString(payload)));
        }
        // 第三方凭据块：bigmodel 登录回 data.bigmodel，zai 登录回 data.zai，
        // 二者不可互相替代（字段不参与推理，仅作备用身份，但缺了就是异常响应）。
        String thirdPartyToken = pickAccessToken(payload.get("bigmodel"));
        if (isBlank(thirdPartyToken)) {
            thirdPartyToken = pickAccessToken(payload.get("zai"));
        }
        if (isBlank(thirdPartyToken)) {
            throw new ApiException(ApiErrorCode.UPSTREAM_ERROR,
                    "ZCode 轮询响应缺第三方 access_token（data.zai 与 data.bigmodel 均为空）: "
                            + ChannelHttp.truncate(Json.toJSONString(payload)));
        }
        Map<String, Object> credential = new LinkedHashMap<>();
        // 推理只用 zcode_jwt（见 zcode-adapter 的 Bearer）。
        credential.put("access_token", token);
        credential.put("user_id", userId);
        credential.put("third_party_token", thirdPartyToken);
        String refreshToken = readString(payload.get("bigmodel") instanceof Map<?, ?> bm
                ? (Map<String, Object>) bm : Map.of(), "refresh_token");
        if (isBlank(refreshToken)) {
            refreshToken = readString(payload.get("bigmodel") instanceof Map<?, ?> bm
                    ? (Map<String, Object>) bm : Map.of(), "refreshToken");
        }
        if (!isBlank(refreshToken)) {
            credential.put("third_party_refresh_token", refreshToken);
        }
        // 展示名：user.name → user.email → user_id（与官方解析器一致）。
        String display = readString(user, "name");
        if (isBlank(display)) {
            display = readString(user, "email");
        }
        credential.put("nickname", isBlank(display) ? userId : display);
        return credential;
    }

    /**
     * pick Access Token
     * @param block 对象
     * @return 返回值
    */
    private static String pickAccessToken(Object block) {
        if (!(block instanceof Map<?, ?> map)) {
            return null;
        }
        String snake = readLooseString(map, "access_token");
        return isBlank(snake) ? readLooseString(map, "accessToken") : snake;
    }

    /**
     * {@inheritDoc}
     *
     * <p>⚠️ ZCode 凭据 JWT 无 exp、不可自动续期；refresh 不可用，须重新绑定。</p>
     */
    @Override
    public Map<String, Object> refresh(Map<String, Object> credentials) {
        throw new ApiException(ApiErrorCode.NOT_IMPLEMENTED,
                "ZCode 凭据不可续期（JWT 无 exp），请重新绑定登录");
    }

    /**
     * {@inheritDoc}
     *
     * <p>探活：以 device_mid 不可用，故凭据无有效探测端点时按无到期信息判
     * {@code valid}（zcode_jwt 无 exp，无法本地判定过期）。</p>
     */
    @Override
    public Map<String, Object> status(Map<String, Object> credentials) {
        String accessToken = readString(credentials, "access_token");
        if (isBlank(accessToken)) {
            return status("invalid", "凭据缺少 access_token");
        }
        // ZCode JWT 无 exp，无法本地到期判定；返回 valid（实际调用失败由业务层处理）。
        return status("valid", "ZCode 凭据不可续期，无到期信息，视为有效（调用失败时请重绑）");
    }

    @Override
    public boolean supportsCheckin() {
        // ZCode 额度是每模型固定上限（dailyQuota），非签到发放；无签到端点。
        return false;
    }

    // ── 私有辅助 ──

    /**
     * auth Headers
     * @param deviceMid 字符串
     * @return 返回值
    */
    private static Map<String, String> authHeaders(String deviceMid) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + deviceMid);
        headers.put("Content-Type", "application/json");
        headers.put("Accept", "application/json");
        return headers;
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
     * device Mid
     * @return 返回值
    */
    private static String deviceMid() {
        byte[] raw = new byte[32];
        new SecureRandom().nextBytes(raw);
        return HexFormat.of().formatHex(raw);
    }

    /**
     * 从响应的 {@code data} 信封取实际负载。
     *
     * <p>⚠️ init / poll 均包一层信封 {@code {code, msg, data:{...}}}，
     * 业务字段在 {@code data} 里；只读顶层会永远判缺失（线上实测冒烟时踩到）。</p>
     *
     * @param response 上游响应体
     * @return 实际负载（{@code data} 非对象时回退整个响应）
     */
    private static Map<String, Object> unwrapData(String response) {
        Map<String, Object> payload = parseJson(response);
        Object data = payload.get("data");
        if (data instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            map.forEach((k, v) -> out.put(String.valueOf(k), v));
            return out;
        }
        return payload;
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
}
