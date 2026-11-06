// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.ai.support.spi.impl;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.ai.support.manage.common.ApiErrorCode;
import com.chua.starter.ai.support.manage.common.ApiException;
import com.chua.starter.ai.support.spi.AccountChannelAuthManager;
import com.chua.starter.common.support.text.json.Json;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Raccoon Work（商汤小浣熊）账号渠道鉴权管理器实现。
 *
 * <p>协议（对齐 {@code dsh-our-free-model} 的 {@code raccoon-oauth.ts} / {@code raccoon-login-page.ts}，
 * 微信扫码登录）：</p>
 * <ol>
 *   <li>{@code code} 由<b>插件本地随机生成</b>（16 字节 hex，服务端接受任意自造 code 进入
 *       {@code pending}，绕开收不到的自定义协议回调）；二维码 URL 形如
 *       {@code {apiBase}/login/mp?code=...&appname=商汤小浣熊官网}；</li>
 *   <li>轮询 {@code POST {apiBase}{authApiPrefix}/login_with_qrcode_code}（body
 *       {@code {qrcode_code: code}}）→ 信封 {@code {code:0, data:{status, access_token, refresh_token, expired_at}}}；
 *       仅 {@code code===0 && status==="success" && access_token 非空} 才完成；
 *       {@code logging}=已扫码待确认、{@code canceled}=换码重扫、其余视为 pending。</li>
 * </ol>
 *
 * <p>⚠️ 易踩点（源码注释原样保留）：任何轮询异常一律降级为 pending（不中断 2s 循环）；
 * 缺 token 的 success 视为未完成；nickname 是服务端默认名（RaccoonAva），多账号消歧靠 phone。
 * 每日 300 积分是服务端自动发放（无签到端点），登录奖励是一次性 onboarding，故 {@link #checkin} 为 no-op。</p>
 *
 * <p>凭据落库 {@code access_token}/{@code refresh_token}/{@code expire_time}/{@code nickname}。</p>
 *
 * @since 4.0.0.43
 * @author CH
 */
@Spi("raccoon")
public class RaccoonAccountChannelAuthManager implements AccountChannelAuthManager {

    /** Raccoon API 基址。 */
    static final String API_BASE = "https://xiaohuanxiong.com";
    /** 认证 API 前缀。 */
    static final String AUTH_API_PREFIX = "/api/web/auth/v1";
    /** 二维码落地页 appname。 */
    static final String QR_APPNAME = "商汤小浣熊官网";

    /** 轮询间隔（毫秒，约 2s）。 */
    static final long POLL_INTERVAL_MS = 2_000L;
    /** 轮询句柄记录的最后一次轮询时刻（毫秒）。 */
    static final String HANDLE_LAST_POLL_AT = "__lastPollAt";
    /** 轮询句柄中存放的轮询间隔键名（毫秒），测试可覆写以加速轮询。 */
    public static final String HANDLE_POLL_INTERVAL_MS = "__pollIntervalMs";

    /**
     * 站点基址（可覆写指向 mock 测试服务器）。
     *
     * @return 站点基址
     */
    protected String siteOrigin() {
        return API_BASE;
    }

    @Override
    public String channel() {
        return "raccoon";
    }

    @Override
    public String displayName() {
        return "Raccoon Work";
    }

    @Override
    public String loginMethod() {
        return "qr-code";
    }

    /**
     * {@inheritDoc}
     *
     * <p>本地随机生成扫码 code，返回 {@code verifyUrl}（二维码 URL）供前端渲染/打开，
     * code 一并放进句柄供后续轮询。无需上游授权（code 自造即进入 pending）。</p>
     */
    @Override
    public Map<String, Object> startLogin() {
        String code = qrCode();
        String qrUrl = siteOrigin() + "/login/mp?code=" + code + "&appname=" + qrAppnameEncoded();
        Map<String, Object> handle = new LinkedHashMap<>();
        handle.put("handle", code);
        handle.put("qrUrl", qrUrl);
        handle.put("verifyUrl", qrUrl);
        handle.put("startedAt", System.currentTimeMillis());
        handle.put(HANDLE_POLL_INTERVAL_MS, POLL_INTERVAL_MS);
        return handle;
    }

    /**
     * {@inheritDoc}
     *
     * <p>按 2s 间隔轮询 {@code login_with_qrcode_code}。code===0 且 status=success 且
     * access_token 非空时取回凭据（expires_at 由 JWT exp 推算）；logging/canceled/pending
     * 返回 null；网络或未知形态一律降级 pending（不中断循环）。</p>
     */
    @Override
    public Map<String, Object> pollLogin(Map<String, Object> handle) {
        if (handle == null || isBlank(readString(handle, "handle"))) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "Raccoon 登录句柄缺失 handle(qrcode_code)");
        }
        long now = System.currentTimeMillis();
        Long lastPollAt = toLong(handle.get(HANDLE_LAST_POLL_AT));
        Long pollInterval = toLong(handle.get(HANDLE_POLL_INTERVAL_MS));
        if (pollInterval == null) {
            pollInterval = POLL_INTERVAL_MS;
        }
        if (lastPollAt != null && now - lastPollAt < pollInterval) {
            return null;
        }
        String code = readString(handle, "handle");

        // ⚠️ 任何异常降级为 pending。
        Map<String, Object> data;
        try {
            String body = Json.toJSONString(Map.of("qrcode_code", code));
            String response = ChannelHttp.postJson(
                    siteOrigin() + AUTH_API_PREFIX + "/login_with_qrcode_code", body, jsonHeaders());
            Map<String, Object> envelope = parseJson(response);
            Object codeVal = envelope.get("code");
            if (!(codeVal instanceof Number n) || n.intValue() != 0) {
                handle.put(HANDLE_LAST_POLL_AT, now);
                return null;
            }
            data = envelope.get("data") instanceof Map<?, ?> m
                    ? new LinkedHashMap<>((Map<String, Object>) m) : Map.of();
        } catch (ApiException e) {
            // ⚠️ 网络抖动/响应畸形一律降级 pending（不中断 2s 循环）。
            handle.put(HANDLE_LAST_POLL_AT, now);
            return null;
        }

        String status = readString(data, "status");
        if ("canceled".equalsIgnoreCase(status)) {
            // 换一个新 code，让二维码重新可用（否则用户卡死）。
            String newCode = qrCode();
            handle.put("handle", newCode);
            handle.put("qrUrl", siteOrigin() + "/login/mp?code=" + newCode + "&appname=" + qrAppnameEncoded());
            handle.put(HANDLE_LAST_POLL_AT, now);
            return null;
        }
        if (!"success".equalsIgnoreCase(status)) {
            // logging / pending / 未知 → 继续等。
            handle.put(HANDLE_LAST_POLL_AT, now);
            return null;
        }
        String accessToken = readString(data, "access_token");
        if (isBlank(accessToken)) {
            // ⚠️ 缺 token 的 success 视为未完成。
            handle.put(HANDLE_LAST_POLL_AT, now);
            return null;
        }
        Map<String, Object> credential = new LinkedHashMap<>();
        credential.put("access_token", accessToken);
        String refreshToken = readString(data, "refresh_token");
        if (refreshToken != null) {
            credential.put("refresh_token", refreshToken);
        }
        Long expireMs = toMillis(data.get("expired_at"));
        if (expireMs != null) {
            credential.put("expire_time", expireMs);
        }
        credential.put("nickname", "Raccoon");
        return credential;
    }

    /**
     * {@inheritDoc}
     *
     * <p>用 {@code refresh_token} 走 {@code /refresh}；401/200003 视为登录态过期须重绑。</p>
     */
    @Override
    public Map<String, Object> refresh(Map<String, Object> credentials) {
        String refreshToken = readString(credentials, "refresh_token");
        if (isBlank(refreshToken)) {
            throw new ApiException(ApiErrorCode.UNAUTHORIZED, "Raccoon 凭据缺少 refresh_token，无法续期");
        }
        String body = Json.toJSONString(Map.of("refresh_token", refreshToken));
        String response;
        try {
            response = ChannelHttp.postJson(siteOrigin() + AUTH_API_PREFIX + "/refresh", body, jsonHeaders());
        } catch (ApiException e) {
            if (e.getMessage() != null && e.getMessage().contains("401")) {
                throw new ApiException(ApiErrorCode.UNAUTHORIZED, "Raccoon 登录态已过期，请重新登录", e);
            }
            throw e;
        }
        Map<String, Object> envelope = parseJson(response);
        Object codeVal = envelope.get("code");
        if (codeVal instanceof Number n && (n.intValue() == 200003 || n.intValue() != 0)) {
            throw new ApiException(ApiErrorCode.UNAUTHORIZED,
                    n.intValue() == 200003 ? "Raccoon 登录态已过期，请重新登录" : "Raccoon 续期失败: " + n.intValue());
        }
        Map<String, Object> data = envelope.get("data") instanceof Map<?, ?> m
                ? new LinkedHashMap<>((Map<String, Object>) m) : Map.of();
        String accessToken = readString(data, "access_token");
        if (isBlank(accessToken)) {
            throw new ApiException(ApiErrorCode.UPSTREAM_ERROR, "Raccoon 续期响应缺少 access_token");
        }
        Map<String, Object> refreshed = new LinkedHashMap<>();
        refreshed.put("access_token", accessToken);
        String newRefresh = readString(data, "refresh_token");
        refreshed.put("refresh_token", isBlank(newRefresh) ? refreshToken : newRefresh);
        Long expireMs = toMillis(data.get("expired_at"));
        if (expireMs != null) {
            refreshed.put("expire_time", expireMs);
        }
        return refreshed;
    }

    /**
     * {@inheritDoc}
     *
     * <p>本地到期优先：凭据带 {@code expire_time} 且已过期判 {@code expired}；
     * 否则探活 {@code /user_info}（带 Bearer），401 判 {@code invalid}，2xx 判 {@code valid}。</p>
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
            ChannelHttp.getJson(siteOrigin() + AUTH_API_PREFIX + "/user_info", authHeaders(accessToken));
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
        // 每日 300 积分是服务端自动发放（无签到端点）；登录奖励是一次性 onboarding。
        return false;
    }

    // ── 私有辅助 ──

    /**
     * json Headers
     * @return 返回值
    */
    private static Map<String, String> jsonHeaders() {
        return Map.of("Content-Type", "application/json", "Accept", "application/json");
    }

    /**
     * auth Headers
     * @param accessToken 字符串
     * @return 返回值
    */
    private static Map<String, String> authHeaders(String accessToken) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Authorization", "Bearer " + (accessToken == null ? "" : accessToken.trim()));
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
     * qr Code
     * @return 返回值
    */
    private static String qrCode() {
        byte[] raw = new byte[16];
        new SecureRandom().nextBytes(raw);
        return HexFormat.of().formatHex(raw);
    }

    /**
     * qr Appname Encoded
     * @return 返回值
    */
    private static String qrAppnameEncoded() {
        try {
            return java.net.URLEncoder.encode(QR_APPNAME, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return QR_APPNAME;
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