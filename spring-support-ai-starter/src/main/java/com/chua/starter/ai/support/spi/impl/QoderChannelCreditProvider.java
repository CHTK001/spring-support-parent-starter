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
 * Qoder（国际版）积分 / 额度查询实现。
 *
 * <p>端点：{@code GET {openApi}/sash/api/v2/me/usage}（Bearer + Sash 客户端头）。
 * 上游把额度放在 {@code usage} / {@code quotas} / {@code limits} 等字段下，逐模型给出
 * 已用 / 上限；这里按「上限优先、缺失不计」的口径聚合，并把逐条明细原样透出。</p>
 *
 * <p>Qoder 的签到是「活动领奖」（{@code /sash/api/v1/me/campaigns}），与额度查询不同源，
 * 因此本类只读额度，不触发领取。</p>
 *
 * @since 4.0.0.52
 * @author CH
 */
@Spi("qoder")
public class QoderChannelCreditProvider implements ChannelCreditProvider {

    /**
     * 渠道标识
     */
    static final String CHANNEL = "qoder";

    /**
     * OpenAPI 站点
     */
    static final String OPEN_API_BASE = "https://openapi.qoder.sh";

    /**
     * 用量查询路径
     */
    static final String USAGE_PATH = "/sash/api/v2/me/usage";

    /**
     * Sash 客户端头（缺 {@code Cosy-ClientType} 时上游返回空）
     */
    static final String COSY_CLIENT_TYPE = "10";

    /**
     * 覆盖 {@code channel()} 的子类用（国内版继承本类，只换站点与标识）
     */
    private final String channel;
    private final String openApiBase;

    /**
     * 默认构造（国际版）。
     */
    public QoderChannelCreditProvider() {
        this(CHANNEL, OPEN_API_BASE);
    }

    /**
     * 指定渠道与站点的构造（供国内版子类使用）。
     *
     * @param channel     渠道标识
     * @param openApiBase OpenAPI 站点
     */
    protected QoderChannelCreditProvider(String channel, String openApiBase) {
        this.channel = channel;
        this.openApiBase = openApiBase;
    }

    @Override
    public String channel() {
        return channel;
    }

    @Override
    public String creditUnit() {
        return "credit";
    }

    /**
     * {@inheritDoc}
     *
     * <p>永不抛异常：失败写 {@code error}。</p>
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
            String body = ChannelHttp.getJson(openApiBase + USAGE_PATH, sashHeaders(accessToken));
            Map<String, Object> payload = parseJson(body);
            if (payload.isEmpty()) {
                result.put("error", "上游返回空响应");
                return result;
            }
            Map<String, Object> root = payload.get("data") instanceof Map<?, ?> raw
                    ? asMap(raw) : payload;
            List<Map<String, Object>> packages = new ArrayList<>(4);
            long remain = 0L;
            long total = 0L;
            for (Object item : quotaCandidates(root)) {
                if (!(item instanceof Map<?, ?> raw)) {
                    continue;
                }
                Map<String, Object> node = asMap(raw);
                Long limit = firstNumber(node, "limit", "quota", "total", "max",
                        "totalQuota", "total_quota");
                Long used = firstNumber(node, "used", "usage", "consumed", "usedQuota",
                        "used_quota");
                Long explicitRemain = firstNumber(node, "remain", "remaining", "left",
                        "remainQuota", "remain_quota");
                if (limit == null && explicitRemain == null) {
                    continue;
                }
                long rowRemain = explicitRemain != null ? explicitRemain
                        : Math.max(limit - (used == null ? 0L : used), 0L);
                long rowTotal = limit != null ? limit : rowRemain;
                remain += Math.max(rowRemain, 0L);
                total += Math.max(rowTotal, rowRemain);
                Map<String, Object> row = new LinkedHashMap<>(3);
                String name = firstString(node, "name", "model", "modelName", "model_name",
                        "title", "quotaName", "quota_name");
                row.put("name", isBlank(name) ? "额度" : name);
                row.put("remain", Math.max(rowRemain, 0L));
                row.put("size", Math.max(rowTotal, rowRemain));
                packages.add(row);
            }
            if (packages.isEmpty()) {
                result.put("error", "上游未返回可解析的额度字段");
                return result;
            }
            result.put("remain", remain);
            result.put("total", total);
            result.put("unit", "credit");
            result.put("exceeded", remain <= 0 && total > 0);
            result.put("updatedAt", System.currentTimeMillis());
            result.put("packages", packages);
            String plan = firstString(root, "plan", "planName", "plan_name", "tier",
                    "subscription", "productName", "product_name");
            if (!isBlank(plan)) {
                result.put("plan", plan);
            }
            return result;
        } catch (Exception e) {
            result.put("error", "额度查询失败: " + ChannelHttp.truncate(e.getMessage()));
            return result;
        }
    }

    /**
     * 从根节点挑出所有可能承载额度的列表。
     *
     * @param root 根节点
     * @return 额度条目集合
     */
    private static List<Object> quotaCandidates(Map<String, Object> root) {
        List<Object> items = new ArrayList<>(4);
        for (String key : new String[]{"usage", "usages", "quotas", "limits", "items",
                "models", "modelUsages", "model_usages", "details"}) {
            Object value = root.get(key);
            if (value instanceof List<?> list) {
                items.addAll(list);
            } else if (value instanceof Map<?, ?> map) {
                items.add(map);
            }
        }
        if (items.isEmpty()) {
            // 根节点自身可能就是单条额度
            items.add(root);
        }
        return items;
    }

    /**
     * Sash 域请求头。
     *
     * @param accessToken 访问令牌
     * @return 请求头
     */
    static Map<String, String> sashHeaders(String accessToken) {
        Map<String, String> headers = new LinkedHashMap<>(4);
        headers.put("Content-Type", "application/json");
        headers.put("Accept", "application/json");
        headers.put("Cosy-ClientType", COSY_CLIENT_TYPE);
        headers.put("Authorization", "Bearer " + accessToken.trim());
        return headers;
    }

    /**
     * 取首个数字字段。
     *
     * @param node 节点
     * @param keys 候选键
     * @return 数值；全空返回 {@code null}
     */
    private static Long firstNumber(Map<String, Object> node, String... keys) {
        for (String key : keys) {
            Long value = toLong(node.get(key));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    /**
     * 取首个非空字符串字段。
     *
     * @param node 节点
     * @param keys 候选键
     * @return 字符串；全空返回 {@code null}
     */
    private static String firstString(Map<String, Object> node, String... keys) {
        for (String key : keys) {
            Object value = node.get(key);
            if (value instanceof String text && !text.isBlank()) {
                return text;
            }
        }
        return null;
    }

    /**
     * 数字归一。
     *
     * @param value 原始值
     * @return 长整数；不可解析返回 {@code null}
     */
    private static Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
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
