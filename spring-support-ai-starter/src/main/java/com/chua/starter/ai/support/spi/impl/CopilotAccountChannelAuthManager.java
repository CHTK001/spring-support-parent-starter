package com.chua.starter.ai.support.spi.impl;

import com.chua.common.support.ai.chat.route.RouteTarget;
import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.ai.support.manage.common.ApiErrorCode;
import com.chua.starter.ai.support.manage.common.ApiException;
import com.chua.starter.common.support.channel.AccountChannelAuthManager;
import com.chua.starter.common.support.text.json.Json;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * GitHub Copilot 账号渠道鉴权管理器实现。
 *
 * <p>GitHub Copilot 不接受 Personal Access Token 调用对话 / 配额接口（实测一律
 * 400 {@code Personal Access Tokens are not supported}），唯一可用路径是 OAuth
 * 设备码授权，共三步：</p>
 * <ol>
 *   <li>{@code POST https://github.com/login/device/code}（表单 {@code client_id}）
 *       → {@code {device_code, user_code, verification_uri, expires_in, interval}}。
 *       注意响应没有 {@code verification_uri_complete}，用户须打开
 *       {@code verification_uri} 手动输入 {@code user_code}；</li>
 *   <li>轮询 {@code POST https://github.com/login/oauth/access_token}（表单
 *       {@code grant_type=urn:ietf:params:oauth:grant-type:device_code} +
 *       {@code device_code} + {@code client_id}）→ 成功返回
 *       {@code {access_token: "ghu_*"}}。⚠️ 与 WorkOS 不同：用户尚未授权时 GitHub 回
 *       <b>HTTP 200</b> + 响应体 {@code {"error": "authorization_pending"}}，必须解析
 *       响应体的 {@code error} 字段判断等待态，不能依赖 HTTP 状态码；</li>
 *   <li>用 {@code ghu_} 长效令牌换短时效 Copilot 令牌：
 *       {@code GET https://api.github.com/copilot_internal/v2/token}
 *       （Bearer {@code ghu_*} + Copilot 客户端 User-Agent / Editor-Version 头）
 *       → {@code {token: "tid=...", expires_at, endpoints.api, sku, ...}}。
 *       短时效令牌实测约 24 小时，到期用同一 {@code ghu_} 重新换取即完成续期。</li>
 * </ol>
 *
 * <p>凭据 Map 契约：{@code access_token}=短时效 Copilot 令牌（路由与上游调用用）、
 * {@code refresh_token}={@code ghu_} 长效令牌（续期与余额采样的根凭据）、
 * {@code expire_time}=短时效令牌到期毫秒、{@code endpoints_api}=Copilot 网关地址、
 * {@code sku}=套餐标识、{@code nickname}=GitHub 登录名（尽力获取）。
 * 余额配额采样由 datasearch 的 {@code copilot} 余额实现完成，它消费
 * {@code refresh_token}（{@code ghu_}）查配额接口，本类不重复实现。</p>
 *
 * @since 4.0.0.62
 * @author CH
 */
@Spi("github-copilot")
public class CopilotAccountChannelAuthManager implements AccountChannelAuthManager {

    /** GitHub 主站基址（设备码授权与 token 轮询走它）。 */
    static final String GITHUB_BASE = "https://github.com";
    /** 设备码授权路径（挂 GitHub 主站）。 */
    static final String DEVICE_CODE_PATH = "/login/device/code";
    /** OAuth token 轮询路径（挂 GitHub 主站）。 */
    static final String TOKEN_PATH = "/login/oauth/access_token";
    /** Copilot 内部令牌 / 配额接口（挂 api.github.com，Bearer ghu_ 鉴权）。 */
    static final String COPILOT_TOKEN_URL = "https://api.github.com/copilot_internal/v2/token";
    /** GitHub 账号资料接口（绑定成功后取登录名做展示昵称，尽力而为）。 */
    static final String GITHUB_USER_URL = "https://api.github.com/user";
    /** GitHub OAuth App client id（Copilot 官方设备码客户端）。 */
    static final String GITHUB_CLIENT_ID = "Iv1.b507a08c87ecfe98";
    /** OAuth 设备码授权的 grant_type 常量。 */
    static final String OAUTH_DEVICE_GRANT = "urn:ietf:params:oauth:grant-type:device_code";
    /** Copilot 客户端 User-Agent（上游按客户端身份校验）。 */
    static final String COPILOT_USER_AGENT = "GitHubCopilotChat/0.30.0";
    /** Editor-Version 请求头（与 User-Agent 配套的客户端标识）。 */
    static final String EDITOR_VERSION = "vscode/1.99.0";
    /** Copilot 个人版网关默认地址（凭据缺 endpoints_api 时兜底）。 */
    static final String DEFAULT_API_BASE = "https://api.individual.githubcopilot.com";
    /** 轮询间隔（毫秒）。 */
    static final long POLL_INTERVAL_MS = 5_000L;
    /** 设备码默认有效期（毫秒；GitHub 实测 899 秒）。 */
    static final long DEVICE_EXPIRES_MS = 900_000L;
    /** 收到 slow_down 时轮询间隔的递增步长（毫秒，GitHub 规范要求 +5 秒）。 */
    static final long SLOW_DOWN_STEP_MS = 5_000L;
    /** 轮询句柄里记录的最后一次轮询时刻（毫秒），用于控制服务端调用频率。 */
    static final String HANDLE_LAST_POLL_AT = "__lastPollAt";

    @Override
    public String channel() {
        return "github-copilot";
    }

    @Override
    public String displayName() {
        return "GitHub Copilot";
    }

    /**
     * {@inheritDoc}
     *
     * <p>向 GitHub 申请设备码，返回 {@code verifyUrl}（{@code verification_uri}，
     * 用户打开后输入 {@code user_code} 完成授权）。</p>
     */
    @Override
    public Map<String, Object> startLogin() {
        String body = "client_id=" + encode(GITHUB_CLIENT_ID);
        String response = ChannelHttp.postForm(
                GITHUB_BASE + DEVICE_CODE_PATH, body, formHeaders());

        Map<String, Object> payload = parseJson(response);
        String deviceCode = readString(payload, "device_code");
        String userCode = readString(payload, "user_code");
        String verificationUri = readString(payload, "verification_uri");
        if (isBlank(deviceCode) || isBlank(userCode) || isBlank(verificationUri)) {
            throw new ApiException(ApiErrorCode.UPSTREAM_ERROR,
                    "GitHub 设备码授权响应缺少 device_code / user_code / verification_uri: "
                            + ChannelHttp.truncate(response));
        }

        Map<String, Object> handle = new LinkedHashMap<>();
        handle.put("handle", deviceCode);
        handle.put("user_code", userCode);
        handle.put("verifyUrl", verificationUri);
        handle.put("expires_in", pollExpiryMs(payload));
        handle.put("interval", pollIntervalMs(payload));
        handle.put("startedAt", System.currentTimeMillis());
        return handle;
    }

    /**
     * {@inheritDoc}
     *
     * <p>按 GitHub 给出的间隔轮询 token 端点；拿到 {@code ghu_} 长效令牌后立即换出
     * 短时效 Copilot 令牌，组装成可持久化凭据。轮询间隔短于前端轮询周期时直接返回
     * {@code null}（待确认），不重复打上游。</p>
     */
    @Override
    public Map<String, Object> pollLogin(Map<String, Object> handle) {
        if (handle == null || isBlank(readString(handle, "handle"))) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "Copilot 登录句柄缺失 handle");
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
        String body = "grant_type=" + encode(OAUTH_DEVICE_GRANT)
                + "&device_code=" + encode(deviceCode)
                + "&client_id=" + encode(GITHUB_CLIENT_ID);
        String response;
        try {
            response = ChannelHttp.postForm(GITHUB_BASE + TOKEN_PATH, body, formHeaders());
        } catch (ApiException e) {
            // 上游限流同样是「继续等」，不是失败。
            if (ChannelHttp.isRateLimited(e)) {
                handle.put(HANDLE_LAST_POLL_AT, now);
                return null;
            }
            throw e;
        }

        Map<String, Object> payload = parseJson(response);
        String githubToken = readString(payload, "access_token");
        if (isBlank(githubToken)) {
            // ⚠️ GitHub 与 WorkOS 不同：用户未完成授权时回 HTTP 200 + error 体
            //（不是 HTTP 400），必须按响应体的 error 字段区分等待态与真失败。
            String error = readString(payload, "error");
            if ("expired_token".equals(error)) {
                throw new ApiException(ApiErrorCode.UPSTREAM_ERROR, "GitHub 设备码已过期，请重新发起绑定");
            }
            if ("access_denied".equals(error)) {
                throw new ApiException(ApiErrorCode.UPSTREAM_ERROR, "用户拒绝了 GitHub 授权");
            }
            if ("slow_down".equals(error)) {
                // GitHub 规范：收到 slow_down 后轮询间隔加 5 秒
                handle.put("interval", interval + SLOW_DOWN_STEP_MS);
            }
            // authorization_pending 与未知错误体：继续等待（句柄自身带过期时间兜底）
            handle.put(HANDLE_LAST_POLL_AT, now);
            return null;
        }
        handle.put(HANDLE_LAST_POLL_AT, now);

        // 步骤 3：用 ghu_ 长效令牌换短时效 Copilot 令牌，组装可持久化凭据。
        Map<String, Object> tokenPayload = exchangeCopilotToken(githubToken);
        Map<String, Object> credentials = buildCredentials(tokenPayload, githubToken);
        applyGithubProfile(githubToken, credentials);
        return credentials;
    }

    /**
     * {@inheritDoc}
     *
     * <p>用凭据里的 {@code ghu_} 长效令牌（{@code refresh_token}）重新换取短时效
     * Copilot 令牌；GitHub 的 {@code ghu_} 长效令牌不轮换，续期即重换短时效令牌。
     * 保留原凭据里的展示与账号字段。</p>
     */
    @Override
    public Map<String, Object> refresh(Map<String, Object> credentials) {
        String githubToken = readString(credentials, "refresh_token");
        if (isBlank(githubToken)) {
            throw new ApiException(ApiErrorCode.UNAUTHORIZED, "Copilot 凭据缺少 refresh_token（ghu_），无法续期");
        }
        Map<String, Object> tokenPayload = exchangeCopilotToken(githubToken);
        Map<String, Object> refreshed = buildCredentials(tokenPayload, githubToken);
        // 保留不在续期响应里的展示与账号字段。
        copyIfPresent(credentials, refreshed, "nickname");
        copyIfPresent(credentials, refreshed, "account_id");
        copyIfPresent(credentials, refreshed, "github_id");
        return refreshed;
    }

    /**
     * {@inheritDoc}
     *
     * <p>本地到期优先：凭据带 {@code expire_time} 且已过期直接判 {@code expired}
     * （调用方可用 {@code refresh_token} 续期）；短时效令牌与长效令牌都缺失才判
     * {@code invalid}。不主动探测上游——配额接口以 {@code ghu_} 换令牌，探活等于
     * 白白生成一个短时效令牌。</p>
     */
    @Override
    public Map<String, Object> status(Map<String, Object> credentials) {
        String accessToken = readString(credentials, "access_token");
        String refreshToken = readString(credentials, "refresh_token");
        if (isBlank(accessToken) && isBlank(refreshToken)) {
            return status("invalid", "凭据缺少 access_token / refresh_token");
        }
        Long expireTime = toLong(credentials.get("expire_time"));
        if (expireTime != null && expireTime <= System.currentTimeMillis()) {
            return status("expired", "短时效 Copilot 令牌已到期，可用长效 ghu_ 令牌续期");
        }
        return status("valid", "本地有效期校验通过");
    }

    /**
     * {@inheritDoc}
     *
     * <p>拉取 Copilot 网关模型目录（{@code GET {endpoints_api}/models}，Bearer 短时效
     * 令牌）。只保留支持 {@code /chat/completions} 的模型（仅 {@code /responses} 的
     * 实验模型不进目录），并带上架状态（{@code policy.state == enabled}）与厂商。
     * 远端失败时返回空列表不抛错。</p>
     */
    @Override
    public List<Map<String, Object>> listModels(Map<String, Object> credentials) {
        String accessToken = readString(credentials, "access_token");
        if (isBlank(accessToken)) {
            return List.of();
        }
        String base = readString(credentials, "endpoints_api");
        String url = (isBlank(base) ? DEFAULT_API_BASE : base) + "/models";
        List<Map<String, Object>> models = new ArrayList<>();
        try {
            String response = ChannelHttp.getJson(url, copilotHeaders(accessToken));
            Map<String, Object> payload = parseJson(response);
            Object data = payload.get("data");
            if (data instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Map<?, ?> entry) {
                        Map<String, Object> model = modelEntry((Map<String, Object>) entry);
                        if (model != null) {
                            models.add(model);
                        }
                    }
                }
            }
        } catch (ApiException ignored) {
            // 目录拉取失败不致命：返回已收集部分（通常为空）。
        }
        return models;
    }

    @Override
    public RouteTarget toRouteTarget(Map<String, Object> credentials) {
        String accessToken = readString(credentials, "access_token");
        if (isBlank(accessToken)) {
            return null;
        }
        String base = readString(credentials, "endpoints_api");
        return RouteTarget.builder()
                .provider("openai")
                .baseUrl(isBlank(base) ? DEFAULT_API_BASE : base)
                .apiKey(accessToken)
                .authScheme("api_key")
                .autoSwitchOnRateLimit(true)
                .build();
    }

    // ── 私有辅助 ──

    /**
     * 用 {@code ghu_} 长效令牌换取短时效 Copilot 令牌，返回完整响应体。
     *
     * @param githubToken ghu_ 长效令牌
     * @return Copilot 令牌响应（含 token / expires_at / endpoints / sku 等）
     */
    private static Map<String, Object> exchangeCopilotToken(String githubToken) {
        String response = ChannelHttp.getJson(COPILOT_TOKEN_URL, copilotHeaders(githubToken));
        Map<String, Object> payload = parseJson(response);
        if (isBlank(readString(payload, "token"))) {
            throw new ApiException(ApiErrorCode.UPSTREAM_ERROR,
                    "Copilot 令牌交换响应缺少 token: " + ChannelHttp.truncate(response));
        }
        return payload;
    }

    /**
     * 按凭据契约把 Copilot 令牌响应组装成可持久化凭据 Map。
     *
     * @param payload     Copilot 令牌响应
     * @param githubToken ghu_ 长效令牌（写入 refresh_token）
     * @return 凭据 Map
     */
    private static Map<String, Object> buildCredentials(Map<String, Object> payload, String githubToken) {
        Map<String, Object> credentials = new LinkedHashMap<>();
        credentials.put("access_token", readString(payload, "token"));
        credentials.put("refresh_token", githubToken);
        Long expireTime = toMillis(payload.get("expires_at"));
        if (expireTime != null) {
            credentials.put("expire_time", expireTime);
        }
        String endpointsApi = readString(endpointsOf(payload), "api");
        credentials.put("endpoints_api", endpointsApi != null ? endpointsApi : DEFAULT_API_BASE);
        String sku = readString(payload, "sku");
        if (sku != null) {
            credentials.put("sku", sku);
        }
        return credentials;
    }

    /**
     * 尽力补齐 GitHub 账号资料（登录名作展示昵称）；失败静默跳过，不影响绑定。
     *
     * @param githubToken ghu_ 长效令牌
     * @param credentials 凭据 Map（原地补充 nickname / account_id / github_id）
     */
    private static void applyGithubProfile(String githubToken, Map<String, Object> credentials) {
        try {
            String response = ChannelHttp.getJson(GITHUB_USER_URL, copilotHeaders(githubToken));
            Map<String, Object> payload = parseJson(response);
            String login = readString(payload, "login");
            if (login != null) {
                credentials.put("nickname", login);
                credentials.putIfAbsent("account_id", login);
            }
            if (payload.get("id") != null) {
                credentials.put("github_id", String.valueOf(payload.get("id")));
            }
        } catch (Exception ignored) {
            // 资料拉取失败不影响绑定（GitHub 用户接口偶发限流），凭据仍可用。
        }
    }

    /**
     * 把目录条目转成模型 Map；不支持 chat/completions 的条目返回 {@code null} 跳过。
     *
     * @param entry 目录条目
     * @return 模型 Map（id / name / enabled / vendor），不可用返回 null
     */
    private static Map<String, Object> modelEntry(Map<String, Object> entry) {
        String id = readString(entry, "id");
        if (id == null) {
            return null;
        }
        Object endpoints = entry.get("supported_endpoints");
        if (endpoints instanceof List<?> list && !list.isEmpty() && !list.contains("/chat/completions")) {
            // 仅支持 /responses 等非对话端点的模型不进目录。
            return null;
        }
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("id", id);
        String name = readString(entry, "name");
        model.put("name", name != null ? name : id);
        Map<?, ?> policy = entry.get("policy") instanceof Map<?, ?> p ? p : Map.of();
        model.put("enabled", "enabled".equals(policy.get("state")));
        String vendor = readString(entry, "vendor");
        if (vendor != null) {
            model.put("vendor", vendor);
        }
        return model;
    }

    /**
     * 读响应体里的 {@code endpoints} 对象；缺失时返回空 Map。
     *
     * @param payload Copilot 令牌响应
     * @return endpoints 对象（可能为空）
     */
    private static Map<String, Object> endpointsOf(Map<String, Object> payload) {
        return payload.get("endpoints") instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    /**
     * Copilot 上游请求头：Bearer 鉴权 + 客户端身份标识（User-Agent / Editor-Version）。
     *
     * @param token Bearer 令牌
     * @return 请求头
     */
    private static Map<String, String> copilotHeaders(String token) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + (token == null ? "" : token.trim()));
        headers.put("Accept", "application/json");
        headers.put("User-Agent", COPILOT_USER_AGENT);
        headers.put("Editor-Version", EDITOR_VERSION);
        return headers;
    }

    /**
     * 表单请求头：GitHub 的 OAuth 端点缺省回 form 编码体，必须带 Accept: application/json
     * 才能拿到 JSON。
     *
     * @return 请求头
     */
    private static Map<String, String> formHeaders() {
        return Map.of("Content-Type", "application/x-www-form-urlencoded", "Accept", "application/json");
    }

    /**
     * status
     * @param state 字符串
     * @param detail 字符串
     * @return 返回值
     */
    private static Map<String, Object> status(String state, String detail) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("state", state);
        result.put("detail", detail);
        return result;
    }

    /**
     * poll Expiry Ms
     * @param payload 参数
     * @return 返回值
     */
    private static long pollExpiryMs(Map<String, Object> payload) {
        Long ms = toMillis(payload.get("expires_in"));
        return ms != null ? ms : DEVICE_EXPIRES_MS;
    }

    /**
     * poll Interval Ms
     * @param payload 参数
     * @return 返回值
     */
    private static long pollIntervalMs(Map<String, Object> payload) {
        Long ms = toMillis(payload.get("interval"));
        return ms != null ? ms : POLL_INTERVAL_MS;
    }

    /**
     * 秒级 / 毫秒级时间戳统一转毫秒（小于 1e12 视为秒）。
     *
     * @param value 时间戳（数值）
     * @return 毫秒值；无法解析返回 null
     */
    private static Long toMillis(Object value) {
        if (value instanceof Number number && number.doubleValue() > 0) {
            double v = number.doubleValue();
            return v < 1e12 ? Math.round(v * 1000d) : Math.round(v);
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
     * copy If Present
     * @param from 参数
     * @param to 参数
     * @param key 字符串
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
        Map<String, Object> map = Json.getJsonObject(response);
        return map != null ? map : new LinkedHashMap<>();
    }

    /**
     * read String
     * @param source 参数
     * @param key 字符串
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

}
