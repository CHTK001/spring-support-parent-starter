package com.chua.starter.ai.support.spi.impl;

import com.chua.common.support.ai.chat.route.RouteTarget;
import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.ai.support.manage.common.ApiErrorCode;
import com.chua.starter.ai.support.manage.common.ApiException;
import com.chua.starter.ai.support.spi.AccountChannelAuthManager;
import com.chua.starter.common.support.text.json.Json;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * EAC（Extended Access Co-paid）协付渠道鉴权管理器实现。
 *
 * <p>协议（对齐 {@code dsh-our-free-model} 的 {@code src/eac.js} /
 * {@code src/eac-login.js} / {@code worker/auth-github.mjs}，<b>GitHub OAuth + Star 验证 +
 * HMAC-SHA256 签名网关</b>）：</p>
 * <ol>
 *   <li>{@code startLogin}：生成随机 link code，返回含 {@code verifyUrl} 的句柄，
 *       前端据此渲染「打开 GitHub 授权」按钮。</li>
 *   <li>{@code pollLogin}：{@code GET {gateway}/auth/poll?link={link}}，
 *       返回 {@code {status:"ok", token, login}} 时完成，{@code pending} 返回 null。</li>
 *   <li>{@code refresh}：{@code GET {gateway}/auth/status}（带 {@code x-ofm-user} 头），
 *       state=ok 时凭据仍有效，返回原凭据。</li>
 *   <li>{@code status}：同 refresh，返回 {@code {state, detail}}。</li>
 *   <li>{@code checkin}：无签到机制。</li>
 *   <li>{@code listModels}：内置模型清单（与 our free model 插件口径一致）。</li>
 *   <li>{@code toRouteTarget}：返回路由目标，baseUrl 为 EAC 网关，authScheme=none，
 *       headers 含 {@code x-ofm-user} 头。</li>
 * </ol>
 *
 * <p>⚠️ HMAC 签名说明：EAC 网关的 {@code /chat/completions} 需要 HMAC-SHA256 签名
 * （{@code x-ofm-signature} / {@code x-ofm-timestamp} 头，签名覆盖 body 哈希）。
 * 签名密钥通过 {@link #SIGNING_SECRET} 读取（环境变量 {@code EAC_SIGNING_SECRET}
 * 或系统属性 {@code eac.signing.secret}），由 {@code EacChatClient} 在构建 HTTP
 * 客户端时注入 {@code HttpRequestAuthenticator}，每请求计算一次。</p>
 *
 * <p>凭据落库：{@code access_token}（GitHub OAuth 签发的 per-user token）、
 * {@code login}（GitHub 用户名）。provider 配置中 platform=openai，
 * baseUrl=https://ourfreemodel.beichenapi.com/eac/v1，appKey 为空。</p>
 *
 * @since 4.0.0.43
 * @author CH
 */
@Spi("eac")
public class EacAccountChannelAuthManager implements AccountChannelAuthManager {

    /** EAC 网关基础地址（chat completions / models）。 */
    static final String GATEWAY_BASE = "https://ourfreemodel.beichenapi.com/eac/v1";

    /** EAC 认证基址（/auth/* 路由，不带 /v1 后缀）。 */
    static final String AUTH_BASE = "https://ourfreemodel.beichenapi.com/eac";

    /**
     * EAC HMAC 签名密钥。
     *
     * <p>与网关 {@code SIGNING_SECRETS} 环境变量一致。共享密钥，所有用户共用。
     * 部署时需在网关与后端两侧配置相同值。优先读环境变量
     * {@code EAC_SIGNING_SECRET}（可在 {@code config/app.env} 中配置），
     * 其次读 JVM 系统属性 {@code eac.signing.secret}，都没有时为空串。</p>
     */
    static final String SIGNING_SECRET =
            System.getenv("EAC_SIGNING_SECRET") != null
                    ? System.getenv("EAC_SIGNING_SECRET")
                    : System.getProperty("eac.signing.secret", "");

    /** link code 字符集。 */
    static final String LINK_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789_-";

    /** link code 长度。 */
    static final int LINK_LENGTH = 32;

    /** SecureRandom 单例（线程安全）。 */
    static final SecureRandom RANDOM = new SecureRandom();

    /** 内置模型清单（与 our free model 插件 EAC_DISPLAY_NAMES 口径一致）。 */
    static final List<String[]> BUILTIN_MODELS = List.of(
            new String[]{"deepseek-v4.1-flash-free", "DeepSeek V4.1 Flash Free"},
            new String[]{"glm-5.3-flash-free", "GLM 5.3 Flash Free"}
    );

    @Override
    public String channel() {
        return "eac";
    }

    @Override
    public String displayName() {
        return "EAC 协付渠道";
    }

    @Override
    public String loginMethod() {
        return "browser-device";
    }

    /**
     * 生成随机 link code，返回含 {@code verifyUrl} 的句柄。
     *
     * <p>前端据此渲染「打开 GitHub 授权」按钮，用户点击后浏览器打开
     * {@code verifyUrl}，完成 GitHub OAuth → Star 验证 → 网关签发 token。</p>
     */
    @Override
    public Map<String, Object> startLogin() {
        String link = generateLink();
        Map<String, Object> handle = new LinkedHashMap<>();
        handle.put("handle", link);
        handle.put("verifyUrl", AUTH_BASE + "/auth/github/start?link=" + link);
        handle.put("loginMethod", "browser-device");
        handle.put("startedAt", System.currentTimeMillis());
        return handle;
    }

    /**
     * 轮询登录进度：{@code GET {gateway}/auth/poll?link={link}}。
     *
     * <p>返回 {@code {status:"ok", token, login}} 时完成，返回凭据 Map；
     * {@code status:"pending"} 返回 null（继续等）；
     * {@code status:"expired" / "unstarred"} 抛出异常。</p>
     */
    @Override
    public Map<String, Object> pollLogin(Map<String, Object> handle) {
        if (handle == null) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "EAC 登录句柄为空");
        }
        String link = readString(handle, "handle");
        if (link == null || link.isBlank()) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "EAC 登录句柄无效");
        }
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Accept", "application/json");
        headers.put("User-Agent", "dsh-our-free-model");
        String url = AUTH_BASE + "/auth/poll?link=" + link;
        String response;
        try {
            response = ChannelHttp.getJson(url, headers);
        } catch (ApiException e) {
            // 429 限流时按 ChannelHttp 内部退避逻辑已处理；此处透传
            if (ChannelHttp.isRateLimited(e)) {
                throw e;
            }
            throw new ApiException(ApiErrorCode.UPSTREAM_ERROR,
                    "EAC 网关 poll 请求失败: " + e.getMessage(), e);
        }
        Map<String, Object> payload = parseJson(response);
        String status = readString(payload, "status");
        if ("ok".equals(status)) {
            String token = readString(payload, "token");
            String login = readString(payload, "login");
            if (token == null || token.isBlank()) {
                throw new ApiException(ApiErrorCode.UPSTREAM_ERROR,
                        "EAC 网关返回 ok 但无 token");
            }
            Map<String, Object> credential = new LinkedHashMap<>();
            credential.put("access_token", token);
            credential.put("login", login != null ? login : "");
            return credential;
        }
        if ("pending".equals(status)) {
            return null;
        }
        // expired / unstarred / other → 明确失败
        String detail = readString(payload, "detail");
        throw new ApiException(ApiErrorCode.UPSTREAM_ERROR,
                "EAC 登录失败: " + (status != null ? status : "unknown")
                        + (detail != null ? " - " + detail : ""));
    }

    /**
     * 续期：{@code GET {gateway}/auth/status}（带 {@code x-ofm-user} 头），
     * state=ok 时凭据仍有效，返回原凭据。
     */
    @Override
    public Map<String, Object> refresh(Map<String, Object> credentials) {
        Map<String, Object> status = checkStatus(credentials);
        String state = readString(status, "state");
        if ("ok".equals(state)) {
            return credentials;
        }
        String detail = readString(status, "detail");
        throw new ApiException(ApiErrorCode.UNAUTHORIZED,
                "EAC 凭据已失效: " + (state != null ? state : "unknown")
                        + (detail != null ? " - " + detail : ""));
    }

    /**
     * 凭据有效性检查：{@code GET {gateway}/auth/status}（带 {@code x-ofm-user} 头）。
     *
     * <p>返回 {@code {state, detail}}，state 取值：
     * ok / expired / unstarred / revoked / no-token / gateway-down。</p>
     */
    @Override
    public Map<String, Object> status(Map<String, Object> credentials) {
        return checkStatus(credentials);
    }

    @Override
    public boolean supportsCheckin() {
        return false;
    }

    /**
     * 内置模型清单（与 our free model 插件口径一致）。
     */
    @Override
    public List<Map<String, Object>> listModels(Map<String, Object> credentials) {
        List<Map<String, Object>> result = new ArrayList<>(BUILTIN_MODELS.size());
        for (String[] entry : BUILTIN_MODELS) {
            Map<String, Object> model = new LinkedHashMap<>();
            model.put("id", entry[0]);
            model.put("name", entry[1]);
            model.put("isFree", true);
            result.add(model);
        }
        return result;
    }

    /**
     * 转路由目标：baseUrl 为 EAC 网关，authScheme=none，
     * headers 含 {@code x-ofm-user} 头，apiKey 为 HMAC 签名密钥
     * （{@code EacChatClient} 从 {@code getSetting().getAppKey()} 读取）。
     */
    @Override
    public RouteTarget toRouteTarget(Map<String, Object> credentials) {
        // EAC 网关对 chat completions 只验 HMAC 签名，不检查 x-ofm-user 头。
        // 因此即使无用户令牌（未完成 GitHub OAuth），路由仍可注册并工作。
        // 用户令牌存在时通过 x-ofm-user 头传递，供 /auth/* 端点使用。
        Map<String, String> headers = new LinkedHashMap<>();
        String token = readString(credentials, "access_token");
        if (token != null) {
            headers.put("x-ofm-user", token);
        }
        return RouteTarget.builder()
                .provider("eac")
                .baseUrl(GATEWAY_BASE)
                .apiKey(SIGNING_SECRET)
                .authScheme("none")
                .headers(headers)
                .autoSwitchOnRateLimit(true)
                .build();
    }

    // ── 私有辅助 ──

    /**
     * generate Link
     * @return 返回值
    */
    private static String generateLink() {
        StringBuilder sb = new StringBuilder(LINK_LENGTH);
        for (int i = 0; i < LINK_LENGTH; i++) {
            sb.append(LINK_CHARS.charAt(RANDOM.nextInt(LINK_CHARS.length())));
        }
        return sb.toString();
    }

    /**
     * check Status
     * @param Map<String 参数
     * @param credentials 参数
     * @return 返回值
    */
    private static Map<String, Object> checkStatus(Map<String, Object> credentials) {
        String token = readString(credentials, "access_token");
        if (token == null || token.isBlank()) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("state", "no-token");
            result.put("detail", "EAC 凭据无 access_token");
            return result;
        }
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Accept", "application/json");
        headers.put("User-Agent", "dsh-our-free-model");
        headers.put("x-ofm-user", token);
        String url = AUTH_BASE + "/auth/status";
        String response;
        try {
            response = ChannelHttp.getJson(url, headers);
        } catch (ApiException e) {
            if (ChannelHttp.isRateLimited(e)) {
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("state", "gateway-down");
                result.put("detail", "EAC 网关限流: " + e.getMessage());
                return result;
            }
            throw new ApiException(ApiErrorCode.UPSTREAM_ERROR,
                    "EAC 网关 status 请求失败: " + e.getMessage(), e);
        }
        Map<String, Object> payload = parseJson(response);
        Map<String, Object> result = new LinkedHashMap<>();
        String state = readString(payload, "state");
        result.put("state", state != null ? state : "error");
        Object detail = payload.get("detail");
        if (detail instanceof String s && !s.isBlank()) {
            result.put("detail", s);
        } else {
            String error = readString(payload, "error");
            if (error != null) {
                result.put("detail", error);
            }
        }
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
}
