// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.ai.support.spi.impl;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.common.support.channel.ChannelCreditProvider;
import com.chua.starter.common.support.text.json.Json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Gemini（Google Code Assist）配额查询实现。
 *
 * <p>Gemini 没有「余额」，只有<b>按模型的配额桶</b>。官方 Gemini CLI 的 {@code /stats}
 * 就调这两个内部端点（与 Antigravity 同后端，不同 OAuth 凭据）：</p>
 * <ol>
 *   <li>{@code POST https://cloudcode-pa.googleapis.com/v1internal:loadCodeAssist}
 *       体 {@code {"metadata":{"ideType":"GEMINI_CLI","pluginType":"GEMINI"}}}
 *       → {@code cloudaicompanionProject}、{@code paidTier/currentTier.name}；</li>
 *   <li>{@code POST .../v1internal:retrieveUserQuota} 体 {@code {"project":"<id>"}}
 *       → {@code {"buckets":[{"modelId","tokenType","remainingFraction","resetTime"}]}}。</li>
 * </ol>
 *
 * <p>⚠️ 易踩点（源码注释原样保留）：<b>不带 project 直接查配额会回 403
 * {@code SUBSCRIPTION_REQUIRED}</b>——项目 id 要先从 {@code loadCodeAssist} 拿；
 * 反过来 {@code loadCodeAssist} 失败时也要继续用空体查一次，因为配额本身属于账号。
 * 另：{@code userAgent} 放进请求体是 400（{@code Unknown name "userAgent"}），
 * 只能留在请求头，故本实现不往体里塞。</p>
 *
 * <p>本实现把「所有桶里最低的剩余比例」作为 {@code remain}（单位 %，{@code total=100}），
 * 每个模型一行明细；这与官方 CLI 的状态栏口径一致——最紧的那个窗口决定还剩多少可用。</p>
 *
 * <p>过期处理：{@code expiry} 是 RFC3339 文本，临期 60 秒内先用 {@code refresh_token}
 * 换新令牌再查（Google 偶尔轮换 refresh_token，新值不落库，只用于本次查询）。</p>
 *
 * @since 4.0.0.53
 * @author CH
 */
@Spi("gemini")
public class GeminiChannelCreditProvider implements ChannelCreditProvider {

    /**
     * 渠道标识
     */
    static final String CHANNEL = "gemini";

    /**
     * Code Assist 层级 / 项目发现端点
     */
    static final String CODE_ASSIST_URL = "https://cloudcode-pa.googleapis.com/v1internal:loadCodeAssist";

    /**
     * 配额桶端点
     */
    static final String QUOTA_URL = "https://cloudcode-pa.googleapis.com/v1internal:retrieveUserQuota";

    /**
     * 令牌续期端点
     */
    static final String TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token";

    /**
     * 到期前余量（毫秒）：临期就先换令牌，避免踩在上游判过期的那一刻
     */
    static final long EXPIRY_SKEW_MS = 60_000L;

    /**
     * 满配额百分比
     */
    static final long FULL_PERCENT = 100L;

    @Override
    public String channel() {
        return CHANNEL;
    }

    @Override
    public String creditUnit() {
        return "%";
    }

    /**
     * {@inheritDoc}
     *
     * <p>永不抛异常：缺凭据、401/403、结构异常都写 {@code error} 返回。</p>
     */
    @Override
    public Map<String, Object> credit(Map<String, Object> credentials) {
        Map<String, Object> result = new LinkedHashMap<>(8);
        String accessToken = readString(credentials, "access_token");
        if (isBlank(accessToken)) {
            result.put("error", "凭据缺少 access_token");
            return result;
        }
        try {
            if (isExpiring(credentials)) {
                String refreshed = refresh(credentials);
                if (isBlank(refreshed)) {
                    result.put("error", "Gemini 登录已过期且续期失败，请重新授权");
                    return result;
                }
                accessToken = refreshed;
            }
            String project = null;
            String plan = null;
            try {
                Map<String, Object> codeAssist = parseJson(ChannelHttp.postJson(CODE_ASSIST_URL,
                        "{\"metadata\":{\"ideType\":\"GEMINI_CLI\",\"pluginType\":\"GEMINI\"}}",
                        authHeaders(accessToken)));
                project = projectOf(codeAssist);
                plan = tierOf(codeAssist);
            } catch (Exception ignored) {
                // 项目发现失败不致命：配额属于账号，空体再查一次
            }
            Map<String, Object> quota = parseJson(ChannelHttp.postJson(QUOTA_URL,
                    project == null ? "{}" : Json.toJSONString(Map.of("project", project)),
                    authHeaders(accessToken)));
            List<Map<String, Object>> buckets = mapsOf(quota.get("buckets"));
            if (buckets.isEmpty()) {
                result.put("error", "上游未返回配额桶（该账号可能没有 Gemini Code Assist 套餐）");
                return result;
            }
            List<Map<String, Object>> packages = packagesOf(buckets);
            if (packages.isEmpty()) {
                result.put("error", "上游配额桶缺少 remainingFraction");
                return result;
            }
            long remain = FULL_PERCENT;
            for (Map<String, Object> row : packages) {
                remain = Math.min(remain, toLong(row.get("remain")));
            }
            result.put("remain", Math.max(remain, 0L));
            result.put("total", FULL_PERCENT);
            result.put("unit", "%");
            result.put("updatedAt", System.currentTimeMillis());
            result.put("packages", packages);
            if (!isBlank(plan)) {
                result.put("plan", plan);
            }
            return result;
        } catch (Exception e) {
            result.put("error", failureOf(e));
            return result;
        }
    }

    /**
     * 明细行：每个 {@code modelId} 一行，同一个模型多个桶时取<b>最低</b>的剩余比例。
     *
     * @param buckets 配额桶
     * @return 明细行
     */
    private static List<Map<String, Object>> packagesOf(List<Map<String, Object>> buckets) {
        Map<String, Long> least = new LinkedHashMap<>(buckets.size());
        for (Map<String, Object> bucket : buckets) {
            String model = readString(bucket, "modelId");
            Long percent = percentOf(bucket.get("remainingFraction"));
            if (isBlank(model) || percent == null) {
                continue;
            }
            Long known = least.get(model);
            if (known == null || percent < known) {
                least.put(model, percent);
            }
        }
        List<Map<String, Object>> rows = new ArrayList<>(least.size());
        least.forEach((model, percent) -> {
            Map<String, Object> row = new LinkedHashMap<>(3);
            row.put("name", model);
            row.put("remain", percent);
            row.put("size", FULL_PERCENT);
            rows.add(row);
        });
        return rows;
    }

    /**
     * {@code remainingFraction}（0~1）→ 剩余百分比。
     *
     * @param value 原始值
     * @return 0~100 的整数；缺失或越界返回 {@code null}
     */
    private static Long percentOf(Object value) {
        if (!(value instanceof Number number)) {
            return null;
        }
        double fraction = number.doubleValue();
        if (Double.isNaN(fraction) || fraction < 0d || fraction > 1d) {
            return null;
        }
        return Math.round(fraction * FULL_PERCENT);
    }

    /**
     * 取项目 id：{@code cloudaicompanionProject} 可能是字符串，也可能是 {@code {id|projectId}}。
     *
     * @param codeAssist 层级响应
     * @return 项目 id；拿不到返回 {@code null}
     */
    private static String projectOf(Map<String, Object> codeAssist) {
        Object raw = codeAssist.get("cloudaicompanionProject");
        if (raw instanceof String text) {
            return isBlank(text) ? null : text.trim();
        }
        if (raw instanceof Map<?, ?> map) {
            Map<String, Object> node = asMap(map);
            String id = readString(node, "id");
            if (isBlank(id)) {
                id = readString(node, "projectId");
            }
            return isBlank(id) ? null : id;
        }
        return null;
    }

    /**
     * 取套餐名：优先 {@code paidTier}，其次 {@code currentTier}。
     *
     * @param codeAssist 层级响应
     * @return 套餐名；拿不到返回 {@code null}
     */
    private static String tierOf(Map<String, Object> codeAssist) {
        for (String key : new String[]{"paidTier", "currentTier"}) {
            if (codeAssist.get(key) instanceof Map<?, ?> map) {
                String name = readString(asMap(map), "name");
                if (!isBlank(name)) {
                    return name;
                }
            }
        }
        return null;
    }

    /**
     * 令牌是否临期（{@code expiry} 支持 RFC3339 文本与毫秒时间戳）。
     *
     * @param credentials 凭据
     * @return 临期或无 expiry 却无 access_token 时返回 {@code true}
     */
    private static boolean isExpiring(Map<String, Object> credentials) {
        Object raw = credentials == null ? null : credentials.get("expiry");
        Long expireAt = null;
        if (raw instanceof Number number) {
            expireAt = number.longValue();
            // 秒级时间戳（旧版本可能落的是秒）
            if (expireAt < 100_000_000_000L) {
                expireAt *= 1000L;
            }
        } else if (raw instanceof String text && !text.isBlank()) {
            try {
                expireAt = java.time.Instant.parse(text.trim()).toEpochMilli();
            } catch (Exception ignored) {
                expireAt = null;
            }
        }
        return expireAt == null || expireAt - EXPIRY_SKEW_MS <= System.currentTimeMillis();
    }

    /**
     * 用 {@code refresh_token} 换新访问令牌（失败返回 {@code null}）。
     *
     * @param credentials 凭据
     * @return 新 access_token；拿不到返回 {@code null}
     */
    private static String refresh(Map<String, Object> credentials) {
        String refreshToken = readString(credentials, "refresh_token");
        if (isBlank(refreshToken)) {
            return null;
        }
        String clientId = readString(credentials, "client_id");
        String clientSecret = readString(credentials, "client_secret");
        Map<String, String> form = new LinkedHashMap<>(4);
        form.put("client_id", isBlank(clientId) ? GeminiAccountChannelAuthManager.DEFAULT_CLIENT_ID : clientId);
        form.put("client_secret",
                isBlank(clientSecret) ? GeminiAccountChannelAuthManager.DEFAULT_CLIENT_SECRET : clientSecret);
        form.put("refresh_token", refreshToken.trim());
        form.put("grant_type", "refresh_token");
        try {
            Map<String, Object> payload = parseJson(
                    ChannelHttp.postForm(TOKEN_ENDPOINT, formString(form), null));
            return readString(payload, "access_token");
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 表单序列化。
     *
     * @param form 表单
     * @return 表单串
     */
    private static String formString(Map<String, String> form) {
        StringBuilder builder = new StringBuilder(form.size() * 32);
        form.forEach((key, value) -> {
            if (builder.length() > 0) {
                builder.append('&');
            }
            builder.append(urlEncode(key)).append('=').append(urlEncode(value));
        });
        return builder.toString();
    }

    /**
     * URL 编码。
     *
     * @param value 原文
     * @return 编码结果；失败返回原文
     */
    private static String urlEncode(String value) {
        try {
            return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return value;
        }
    }

    /**
     * 失败归类：把 {@code -> 401} / {@code -> 403} 翻成可操作提示，其余给通用文案
     * （上游报错原文含 URL，不进界面）。
     *
     * @param e 异常
     * @return 展示文案
     */
    private static String failureOf(Exception e) {
        String message = e.getMessage() == null ? "" : e.getMessage();
        if (message.contains("-> 401")) {
            return "Gemini 登录已过期，请重新授权";
        }
        if (message.contains("-> 403")) {
            return "该 Google 账号没有 Gemini Code Assist 配额（未开通或所在地区不可用）";
        }
        return "配额查询失败，请稍后重试";
    }

    /**
     * 鉴权头。
     *
     * @param accessToken 访问令牌
     * @return 请求头
     */
    private static Map<String, String> authHeaders(String accessToken) {
        Map<String, String> headers = new LinkedHashMap<>(4);
        headers.put("Accept", "application/json");
        headers.put("Content-Type", "application/json");
        headers.put("Authorization", "Bearer " + accessToken.trim());
        return headers;
    }

    /**
     * 取 Map 列表（非列表返回空）。
     *
     * @param value 原始值
     * @return 元素为字符串键 Map 的列表
     */
    private static List<Map<String, Object>> mapsOf(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> rows = new ArrayList<>(list.size());
        for (Object item : list) {
            if (item instanceof Map<?, ?> raw) {
                rows.add(asMap(raw));
            }
        }
        return rows;
    }

    /**
     * Map 键归一化。
     *
     * @param raw 原始 Map
     * @return 字符串键 Map
     */
    private static Map<String, Object> asMap(Map<?, ?> raw) {
        Map<String, Object> out = new LinkedHashMap<>(raw.size());
        raw.forEach((k, v) -> {
            if (k != null) {
                out.put(String.valueOf(k), v);
            }
        });
        return out;
    }

    /**
     * 解析 JSON（失败返回空 Map）。
     *
     * @param text JSON 文本
     * @return 解析结果
     */
    private static Map<String, Object> parseJson(String text) {
        if (isBlank(text)) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, Object> map = Json.getJsonObject(text);
            return map == null ? new LinkedHashMap<>() : map;
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    /**
     * 数字归一。
     *
     * @param value 原始值
     * @return 长整数；不可解析返回 {@code 0}
     */
    private static long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ignored) {
                return 0L;
            }
        }
        return 0L;
    }

    /**
     * 读取非空字符串。
     *
     * @param source 源 Map
     * @param key    键
     * @return 值；空串返回 {@code null}
     */
    private static String readString(Map<String, Object> source, String key) {
        Object value = source == null ? null : source.get(key);
        return value instanceof String text && !text.isBlank() ? text : null;
    }

    /**
     * 判断空串。
     *
     * @param value 字符串
     * @return 空或 null 返回 {@code true}
     */
    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
