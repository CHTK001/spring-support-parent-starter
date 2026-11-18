package com.chua.starter.ai.support.spi.impl;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.ai.support.manage.common.ApiErrorCode;
import com.chua.starter.ai.support.manage.common.ApiException;
import com.chua.starter.common.support.channel.AccountChannelAuthManager;
import com.chua.starter.common.support.text.json.Json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * SCNet（超算互联网）账号渠道鉴权管理器。
 *
 * <p>协议（对齐 SCNet 官方 OpenAI 兼容入口 {@code https://api.scnet.cn/api/llm/v1}，
 * 本机 SCNet 客户端 {@code models.json} 的 {@code scnet} 提供商即此配置）：
 * 模型目录 {@code GET /models}，Bearer API Key，无 OAuth、无 refresh、无签到。</p>
 *
 * <ol>
 *   <li>{@code startLogin} 返回 {@code loginMethod=api-key} 句柄，并带默认基址
 *       {@link #DEFAULT_API_BASE}；前端据此渲染「粘贴 API Key」输入框（可改基址，
 *       私有网关/代理场景用）。</li>
 *   <li>{@code pollLogin}：{@code apiKey} 缺失返回 {@code null}（继续等待粘贴）；
 *       否则以 {@code GET {base}/models} 探活，2xx 返回可持久化凭据，
 *       401/403 抛 {@code UNAUTHORIZED}；网络失败不阻断绑定（凭据本身可用）。</li>
 *   <li>{@code refresh}：API Key 无续期机制，抛 {@code NOT_IMPLEMENTED}。</li>
 *   <li>{@code status}：同探活口径；401/403 判 {@code invalid}，网络失败按本地判 {@code valid}。</li>
 *   <li>{@code checkin}：SCNet 无每日签到/额度领取端点，不支持。</li>
 *   <li>{@code listModels}：{@code GET /models}，映射 {@code data[].id/name}；
 *       远端未下发 contextWindow 时不编造。</li>
 * </ol>
 *
 * <p>凭据落库：{@code access_token}（API Key）+ {@code base_url}（默认官方基址）+
 * {@code nickname}（脱敏 key 尾号）。{@code base_url} 落库是为了后续探活/拉模型
 * 不依赖部署环境变量，私有网关换址只需改连接。</p>
 *
 * <p><b>如何再接入其它 agent 软件</b>：复制本类即可——把 {@code @Spi} 换成新渠道标识、
 * 改 {@link #displayName()} 与基址常量；若上游是 OpenAI 兼容 + API Key，
 * 其余逻辑（探活、状态、拉目录）无需改动。新实现必须同步登记到
 * {@code META-INF/extensions/com.chua.starter.common.support.channel.AccountChannelAuthManager}，
 * 否则 SPI 枚举不到，渠道管理页不会出现该渠道。</p>
 *
 * @since 4.0.0.60
 * @author CH
 */
@Spi("scnet")
public class ScnetAccountChannelAuthManager implements AccountChannelAuthManager {

    /** SCNet 官方 OpenAI 兼容基址（末尾不带斜杠）。 */
    static final String DEFAULT_API_BASE = "https://api.scnet.cn/api/llm/v1";

    /** 模型目录端点（探活与拉目录共用）。 */
    static final String MODELS_PATH = "/models";

    /** 轮询句柄：用户粘贴的 API Key。 */
    static final String HANDLE_API_KEY = "apiKey";

    /** 轮询句柄：可覆盖的基址。 */
    static final String HANDLE_BASE_URL = "baseUrl";

    @Override
    public String channel() {
        return "scnet";
    }

    @Override
    public String displayName() {
        return "SCNet 超算互联网";
    }

    @Override
    public String loginMethod() {
        return "api-key";
    }

    /**
     * {@inheritDoc}
     *
     * <p>API Key 渠道没有可打开的授权页，直接返回句柄让前端渲染 key 输入框；
     * 同时带默认基址，用户不改就是官方入口。</p>
     */
    @Override
    public Map<String, Object> startLogin() {
        Map<String, Object> handle = new LinkedHashMap<>();
        handle.put("handle", "scnet-api-key");
        handle.put("loginMethod", "api-key");
        handle.put("baseUrl", DEFAULT_API_BASE);
        handle.put("startedAt", System.currentTimeMillis());
        return handle;
    }

    /**
     * {@inheritDoc}
     *
     * <p>{@code apiKey} 缺失时返回 {@code null} 等待前端粘贴；携带 key 时探活一次
     * {@code GET {base}/models}。网络失败不阻断绑定，仅 401/403 判 key 无效。</p>
     */
    @Override
    public Map<String, Object> pollLogin(Map<String, Object> handle) {
        if (handle == null || isBlank(readString(handle, "handle"))) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "SCNet 登录句柄缺失 handle");
        }
        String apiKey = readString(handle, HANDLE_API_KEY);
        if (isBlank(apiKey)) {
            // 前端尚未粘贴 key → 继续等待。
            return null;
        }
        apiKey = apiKey.trim();
        String baseUrl = normalizeBase(readString(handle, HANDLE_BASE_URL));
        ProbeResult probe = probe(baseUrl, apiKey);
        if (!probe.ok() && !probe.networkFailure()) {
            throw new ApiException(ApiErrorCode.UNAUTHORIZED,
                    "SCNet API Key 探活失败（401/403）: " + ChannelHttp.truncate(probe.detail()));
        }
        Map<String, Object> credential = new LinkedHashMap<>();
        credential.put("access_token", apiKey);
        credential.put("base_url", baseUrl);
        credential.put("nickname", maskKey(apiKey));
        return credential;
    }

    /**
     * {@inheritDoc}
     *
     * <p>API Key 无 refresh 机制（不返回 refresh_token，也无续期端点）。</p>
     */
    @Override
    public Map<String, Object> refresh(Map<String, Object> credentials) {
        throw new ApiException(ApiErrorCode.NOT_IMPLEMENTED,
                "SCNet API Key 不可续期（无 refresh 机制），如失效请更换 Key");
    }

    /**
     * {@inheritDoc}
     *
     * <p>探活 {@code GET {base}/models}：2xx 判 {@code valid}；401/403 判 {@code invalid}；
     * 网络失败按凭据本身可用处理（避免把断网误判成 key 失效，逼用户做无效重绑）。</p>
     */
    @Override
    public Map<String, Object> status(Map<String, Object> credentials) {
        String apiKey = readString(credentials, "access_token");
        if (isBlank(apiKey)) {
            return status("invalid", "凭据缺少 access_token（API Key）");
        }
        String baseUrl = normalizeBase(readString(credentials, "base_url"));
        ProbeResult probe = probe(baseUrl, apiKey.trim());
        if (probe.ok()) {
            return status("valid", "探活成功");
        }
        if (probe.networkFailure()) {
            return status("valid", "探活网络失败（按凭据本身可用处理）: " + probe.detail());
        }
        return status("invalid", "探活 401/403（凭据失效）: " + ChannelHttp.truncate(probe.detail()));
    }

    @Override
    public boolean supportsCheckin() {
        // SCNet 无每日签到/额度领取端点。
        return false;
    }

    /**
     * {@inheritDoc}
     *
     * <p>远端目录只保证 {@code id}；{@code name} 缺失时回退 id，
     * {@code contextWindow} 远端未下发则不写该键，不编造窗口大小。</p>
     */
    @Override
    public List<Map<String, Object>> listModels(Map<String, Object> credentials) {
        String apiKey = readString(credentials, "access_token");
        if (isBlank(apiKey)) {
            return List.of();
        }
        String baseUrl = normalizeBase(readString(credentials, "base_url"));
        List<Map<String, Object>> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        try {
            String response = ChannelHttp.getJson(modelsUrl(baseUrl), authHeaders(apiKey.trim()));
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
                        model.put("name", entry.get("name") instanceof String n && !n.isBlank() ? n : id);
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

    /**
     * 探活结果：三态（成功 / 网络失败 / 上游 401-403 带响应体）。
     *
     * @param ok             是否 2xx
     * @param networkFailure 是否网络层失败
     * @param detail         失败详情
     */
    private record ProbeResult(boolean ok, boolean networkFailure, String detail) {
    }

    /**
     * 探活 {@code GET {base}/models}（Bearer key）。
     *
     * <p>{@code ChannelHttp} 对非 2xx 抛的 {@code ApiException} 消息形如
     * {@code "渠道上游返回非 2xx: ... -> 401"}，据此区分凭据失效与网络失败。</p>
     *
     * @param baseUrl 基址
     * @param apiKey  API Key
     * @return 探活结果
     */
    private ProbeResult probe(String baseUrl, String apiKey) {
        try {
            ChannelHttp.getJson(modelsUrl(baseUrl), authHeaders(apiKey));
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
     * 规范化基址：空值回退官方入口，去掉末尾斜杠与误粘的 {@code /models}。
     *
     * @param raw 原始基址
     * @return 可拼接 {@link #MODELS_PATH} 的基址
     */
    private static String normalizeBase(String raw) {
        String base = isBlank(raw) ? DEFAULT_API_BASE : raw.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (base.endsWith(MODELS_PATH)) {
            base = base.substring(0, base.length() - MODELS_PATH.length());
        }
        return base;
    }

    /**
     * 拼接模型目录地址。
     *
     * @param baseUrl 基址
     * @return 目录 URL
     */
    private static String modelsUrl(String baseUrl) {
        return normalizeBase(baseUrl) + MODELS_PATH;
    }

    /**
     * 构造鉴权头。
     *
     * @param apiKey API Key
     * @return 请求头
     */
    private static Map<String, String> authHeaders(String apiKey) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Authorization", "Bearer " + apiKey);
        headers.put("Accept", "application/json");
        return headers;
    }

    /**
     * 构造状态 Map。
     *
     * @param state  状态
     * @param detail 详情
     * @return 状态 Map
     */
    private static Map<String, Object> status(String state, String detail) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("state", state);
        result.put("detail", detail);
        return result;
    }

    /**
     * 脱敏 key（只留末 4 位）。
     *
     * @param key API Key
     * @return 脱敏值
     */
    private static String maskKey(String key) {
        String trimmed = key == null ? "" : key.trim();
        if (trimmed.length() <= 4) {
            return trimmed;
        }
        return "..." + trimmed.substring(trimmed.length() - 4);
    }

    /**
     * 解析 JSON 对象。
     *
     * @param response 响应体
     * @return Map（解析失败返回空 Map）
     */
    private static Map<String, Object> parseJson(String response) {
        Map<String, Object> map = Json.getJsonObject(response);
        return map != null ? map : new LinkedHashMap<>();
    }

    /**
     * 读取非空字符串。
     *
     * @param source 来源 Map
     * @param key    键
     * @return 非空字符串或 null
     */
    private static String readString(Map<String, Object> source, String key) {
        Object value = source == null ? null : source.get(key);
        if (value instanceof String s && !s.isBlank()) {
            return s;
        }
        return null;
    }

    /**
     * 判空。
     *
     * @param value 字符串
     * @return 是否为空
     */
    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
