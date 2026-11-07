package com.chua.starter.ai.support.spi.impl;

import com.chua.common.support.ai.chat.route.RouteTarget;
import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.ai.support.manage.common.ApiErrorCode;
import com.chua.starter.ai.support.manage.common.ApiException;
import com.chua.starter.common.support.channel.AccountChannelAuthManager;
import com.chua.starter.common.support.text.json.Json;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * OpenCode（Zen 免费推理）账号渠道鉴权管理器实现。
 *
 * <p>协议（对齐 {@code dsh-our-free-model} 的 {@code opencode.ts} / {@code opencode-product.ts} /
 * {@code opencode-adapter.ts}，<b>API key 渠道</b>——身份只有三个维度：
 * 出口 IP、API key（账号）、随机会话 id，<b>没有</b>机器指纹）：</p>
 * <ol>
 *   <li>{@code startLogin} 无参：返回含 {@code loginMethod=api-key} 的句柄，
 *       前端据此渲染「粘贴 Zen API key」输入框；匿名通道 key 是字面量 {@code public}。</li>
 *   <li>{@code pollLogin}：入参 {@code apiKey} 缺失时返回 null（等待粘贴）；
 *       携带 key 时以 {@code GET {base}/v1/models}（Bearer key）做一次探活，
 *       成功返回可持久化凭据 Map，401 抛 {@code UNAUTHORIZED}。</li>
 *   <li>{@code refresh}：API key 无续期机制（{@link #refresh} 抛 {@code NOT_IMPLEMENTED}）。</li>
 *   <li>{@code status}：探活 {@code GET /v1/models}（401 判 invalid，网络失败按本地判 valid）。</li>
 *   <li>{@code checkin}：Zen 无每日签到/额度领取机制（免费额度按窗口重置），不支持。</li>
 *   <li>{@code listModels}：{@code GET /v1/models}（Bearer），远端只回
 *       {@code id/object/created/owned_by} 四个字段（<b>不带</b> contextWindow 与免费标记），
 *       免费判定按 id 后缀 {@code -free}（与 our free model 插件的口径一致）。</li>
 * </ol>
 *
 * <p>⚠️ 易踩点（源码注释原样保留）：身份 = key + 出口 IP + 随机会话 id，
 * 额度错误可能带 400/401/403/429 任一状态码，唯一稳定信号是<b>响应体里的错误类型名</b>
 * （{@code FreeUsageLimitError} 等）——故 {@link #status} 探活时 429/402 类响应
 * <b>不判 invalid</b>（那是额度而非凭据失效）；只有 401/403 无额度类型名才判 invalid。</p>
 *
 * <p>凭据落库：{@code access_token}(Zen API key，匿名通道为 {@code public}) +
 * {@code nickname}（脱敏 key 尾号）+ 可选 {@code proxy}（出口代理 URL，空=直连）。</p>
 *
 * @since 4.0.0.43
 * @author CH
 */
@Spi("opencode")
public class OpencodeAccountChannelAuthManager implements AccountChannelAuthManager {

    /** 上游推理基址（免费模型新入口统一挂 /inference/v1）。 */
    static final String API_BASE = "https://opencode.ai/inference";
    /** 模型目录端点（探活 + 拉目录共用；匿名 {@code public} 也能用）。 */
    static final String MODELS_PATH = "/v1/models";
    /** 匿名通道字面量 key（opencode 官方 CLI 无 key 时自己就用它）。 */
    static final String ANONYMOUS_KEY = "public";

    /** 轮询句柄：用户粘贴的 API key。 */
    static final String HANDLE_API_KEY = "apiKey";

    @Override
    public String channel() {
        return "opencode";
    }

    @Override
    public String displayName() {
        return "OpenCode";
    }

    @Override
    public String loginMethod() {
        return "api-key";
    }

    /**
     * {@inheritDoc}
     *
     * <p>API key 渠道没有可打开的 URL，{@code startLogin} 直接返回带
     * {@code loginMethod=api-key} 标记的句柄，前端渲染 key 输入框；
     * 粘贴与探活都在 {@link #pollLogin} 完成。匿名通道直接粘 {@code public}。</p>
     */
    @Override
    public Map<String, Object> startLogin() {
        Map<String, Object> handle = new LinkedHashMap<>();
        handle.put("handle", "opencode-api-key");
        handle.put("loginMethod", "api-key");
        handle.put("startedAt", System.currentTimeMillis());
        return handle;
    }

    /**
     * {@inheritDoc}
     *
     * <p>入参 {@code apiKey} 缺失时返回 null（等待粘贴）；携带 key 时探活一次
     * {@code GET /v1/models}（Bearer key）——成功返回凭据 Map，401 抛 UNAUTHORIZED。
     * 探活失败（网络）不阻断绑定：凭据本身可用（额度/网络问题由业务层处理），
     * 仅在 401/403 <b>且</b> 响应不含额度类型名时判凭据失效。</p>
     */
    @Override
    public Map<String, Object> pollLogin(Map<String, Object> handle) {
        if (handle == null || isBlank(readString(handle, "handle"))) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "OpenCode 登录句柄缺失 handle");
        }
        String apiKey = readString(handle, HANDLE_API_KEY);
        if (isBlank(apiKey)) {
            // 前端尚未粘贴 key → 继续等待。
            return null;
        }
        apiKey = apiKey.trim();
        // 探活：401（不含额度类型名）= key 无效；额度类响应与网络失败不阻断绑定。
        ProbeResult probe = probe(apiKey);
        if (!probe.ok() && !probe.networkFailure()
                && !containsUsageLimitType(probe.detail())) {
            throw new ApiException(ApiErrorCode.UNAUTHORIZED,
                    "OpenCode API key 探活失败（401/403）: " + ChannelHttp.truncate(probe.detail()));
        }
        Map<String, Object> credential = new LinkedHashMap<>();
        credential.put("access_token", apiKey);
        credential.put("nickname", maskKey(apiKey));
        return credential;
    }

    /**
     * {@inheritDoc}
     *
     * <p>API key 无 refresh 机制（不返回 refresh_token，也无续期端点）。</p>
     */
    @Override
    public Map<String, Object> refresh(Map<String, Object> credentials) {
        throw new ApiException(ApiErrorCode.NOT_IMPLEMENTED,
                "OpenCode API key 不可续期（无 refresh 机制），如失效请更换 key");
    }

    /**
     * 探活 {@code GET /v1/models}（Bearer key）：2xx 判 valid；
     * 401/403 且响应<b>不含</b>额度类型名（{@code FreeUsageLimitError} /
     * {@code GoUsageLimitError} / {@code FreeTierError}）才判 invalid ——
     * 额度错误可能带 401/403 状态码，必须按响应体类型名区分，
     * 把「额度用尽」误判成「key 失效」会让用户去做无效的重绑动作。</p>
     */
    @Override
    public Map<String, Object> status(Map<String, Object> credentials) {
        String apiKey = readString(credentials, "access_token");
        if (isBlank(apiKey)) {
            return status("invalid", "凭据缺少 access_token（API key）");
        }
        ProbeResult probe = probe(apiKey);
        if (probe.ok()) {
            return status("valid", "探活成功");
        }
        if (probe.networkFailure()) {
            return status("valid", "探活网络失败（按凭据本身可用处理）: " + probe.detail());
        }
        boolean usageLimited = containsUsageLimitType(probe.detail());
        if (usageLimited) {
            return status("valid", "额度受限（" + ChannelHttp.truncate(probe.detail()) + "），凭据仍有效，等待额度窗口恢复");
        }
        return status("invalid", "探活 401/403（凭据失效）: " + ChannelHttp.truncate(probe.detail()));
    }

    @Override
    public boolean supportsCheckin() {
        // Zen 免费额度按时间窗口重置，无签到/领取端点，不支持。
        return false;
    }

    /**
     * {@inheritDoc}
     *
     * <p>远端目录：{@code GET /v1/models}（Bearer key；匿名 {@code public} 只返回
     * 匿名可见的模型集）。远端只回 {@code id/object/created/owned_by} 四字段，
     * <b>不带</b> contextWindow 与免费标记——免费判定按 id 后缀 {@code -free}
     * （与 our free model 插件口径一致）；失败返回空列表不抛错。</p>
     */
    @Override
    public List<Map<String, Object>> listModels(Map<String, Object> credentials) {
        String apiKey = readString(credentials, "access_token");
        if (isBlank(apiKey)) {
            return List.of();
        }
        List<Map<String, Object>> result = new java.util.ArrayList<>();
        Set<String> seen = new java.util.LinkedHashSet<>();
        try {
            String response = ChannelHttp.getJson(API_BASE + MODELS_PATH, authHeaders(apiKey));
            Map<String, Object> payload = parseJson(response);
            Object data = payload.get("data");
            if (data instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Map<?, ?> entry) {
                        Object idRaw = entry.get("id");
                        if (idRaw == null || !seen.add(String.valueOf(idRaw))) {
                            continue;
                        }
                        String id = String.valueOf(idRaw);
                        Map<String, Object> model = new LinkedHashMap<>();
                        model.put("id", id);
                        model.put("name", entry.get("name") instanceof String n && !n.isBlank()
                                ? n : id);
                        // 远端不下发窗口，不编造（缺省即不写该键）。
                        model.put("isFree", id.endsWith("-free") || ANONYMOUS_KEY.equals(apiKey.trim()));
                        result.add(model);
                    }
                }
            }
        } catch (ApiException ignored) {
            // 远端目录拉取失败不致命（返回空列表，上层可走兜底模型表）。
        }
        return result;
    }

    // ── 私有辅助 ──

    /** 探活结果：三态（成功 / 网络失败 / 上游 401-403 带响应体）。 */
    private record ProbeResult(boolean ok, boolean networkFailure, String detail) {
    }

    /**
     * 探活 {@code GET /v1/models}（Bearer key）。 <p>{@code ChannelHttp} 对非 2xx 抛的 {@code ApiException} 消息形态为 {@code "渠道上游返回非 2xx: ... -> " + 状态码}，据此区分「401/403 凭据/额度」 与「网络失败」（网络失败消息形态 {@code "渠道 HTTP 请求失败: ..."}， 不含状态码，见 {@code ChannelHttp.execute}）。</p>
     * @param apiKey 字符串
     * @return 返回值
    */
    private ProbeResult probe(String apiKey) {
        try {
            ChannelHttp.getJson(API_BASE + MODELS_PATH, authHeaders(apiKey));
            return new ProbeResult(true, false, null);
        } catch (ApiException e) {
            String message = e.getMessage() == null ? "" : e.getMessage();
            if (message.contains("-> 401") || message.contains("-> 403")) {
                return new ProbeResult(false, false, message);
            }
            return new ProbeResult(false, true, message);
        }
    }

    /**
     * contains Usage Limit Type
     * @param detail 字符串
     * @return 返回值
    */
    private static boolean containsUsageLimitType(String detail) {
        if (detail == null) {
            return false;
        }
        return detail.contains("FreeUsageLimitError")
                || detail.contains("GoUsageLimitError")
                || detail.contains("FreeTierError");
    }

    /**
     * auth Headers
     * @param apiKey 字符串
     * @return 返回值
    */
    private static Map<String, String> authHeaders(String apiKey) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Authorization", "Bearer " + apiKey);
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
     * mask Key
     * @param key 字符串
     * @return 返回值
    */
    private static String maskKey(String key) {
        String trimmed = key == null ? "" : key.trim();
        if (trimmed.length() <= 4 || ANONYMOUS_KEY.equals(trimmed)) {
            return trimmed;
        }
        return "..." + trimmed.substring(trimmed.length() - 4);
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

    @Override
    public RouteTarget toRouteTarget(Map<String, Object> credentials) {
        String accessToken = readString(credentials, "access_token");
        if (isBlank(accessToken)) {
            return null;
        }
        return RouteTarget.builder()
                .provider("openai")
                .baseUrl(API_BASE + "/v1")
                .apiKey(accessToken)
                .authScheme("api_key")
                .autoSwitchOnRateLimit(true)
                .build();
    }
}
