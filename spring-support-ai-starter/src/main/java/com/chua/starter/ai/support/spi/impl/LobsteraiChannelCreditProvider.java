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
 * LobsterAI（有道龙虾）积分 / 额度查询实现。
 *
 * <p>端点：{@code GET https://lobsterai-server.youdao.com/api/user/profile-summary}
 * （Bearer）。上游把额度放在 {@code credits} / {@code balance} / {@code quotas} 等字段，
 * 逐字段宽松解析：能取到「剩余 + 总量」就展示进度，只有单值就只展示数值。</p>
 *
 * <p>签到是「活动 check_in」（{@code /api/client-activities/*}），与额度查询不同源，
 * 本类只读额度。</p>
 *
 * @since 4.0.0.52
 * @author CH
 */
@Spi("lobsterai")
public class LobsteraiChannelCreditProvider implements ChannelCreditProvider {

    /**
     * 渠道标识
     */
    static final String CHANNEL = "lobsterai";

    /**
     * 上游 API 基址
     */
    static final String API_BASE = "https://lobsterai-server.youdao.com";

    /**
     * 账号概览路径
     */
    static final String PROFILE_SUMMARY_PATH = "/api/user/profile-summary";

    @Override
    public String channel() {
        return CHANNEL;
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
            String body = ChannelHttp.getJson(API_BASE + PROFILE_SUMMARY_PATH,
                    authHeaders(accessToken));
            Map<String, Object> payload = parseJson(body);
            if (payload.isEmpty()) {
                result.put("error", "上游返回空响应");
                return result;
            }
            Map<String, Object> root = payload.get("data") instanceof Map<?, ?> raw
                    ? asMap(raw) : payload;
            Long remain = firstNumber(root, "credits", "credit", "balance", "remain",
                    "remaining", "points", "quota");
            Long total = firstNumber(root, "creditsTotal", "credits_total", "total",
                    "totalCredits", "total_credits", "quotaTotal", "quota_total");
            if (remain == null && total == null) {
                result.put("error", "上游未返回可解析的额度字段");
                return result;
            }
            result.put("remain", remain == null ? 0L : Math.max(remain, 0L));
            if (total != null) {
                result.put("total", Math.max(total, 0L));
            }
            result.put("unit", "credit");
            result.put("updatedAt", System.currentTimeMillis());
            List<Map<String, Object>> packages = packagesOf(root);
            if (!packages.isEmpty()) {
                result.put("packages", packages);
            }
            String plan = firstString(root, "plan", "planName", "plan_name", "vipLevel",
                    "vip_level", "tier");
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
     * 提取额度明细列表（上游若有 {@code quotas} / {@code packages} 才返回）。
     *
     * @param root 根节点
     * @return 明细行
     */
    private static List<Map<String, Object>> packagesOf(Map<String, Object> root) {
        List<Map<String, Object>> rows = new ArrayList<>(4);
        for (String key : new String[]{"quotas", "packages", "credits_detail", "details"}) {
            if (!(root.get(key) instanceof List<?> list)) {
                continue;
            }
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> raw)) {
                    continue;
                }
                Map<String, Object> node = asMap(raw);
                Long remain = firstNumber(node, "remain", "remaining", "balance", "credits");
                Long size = firstNumber(node, "size", "total", "quota", "limit");
                if (remain == null && size == null) {
                    continue;
                }
                Map<String, Object> row = new LinkedHashMap<>(3);
                String name = firstString(node, "name", "title", "packageName", "package_name");
                row.put("name", isBlank(name) ? "额度包" : name);
                if (remain != null) {
                    row.put("remain", Math.max(remain, 0L));
                }
                if (size != null) {
                    row.put("size", Math.max(size, 0L));
                }
                rows.add(row);
            }
        }
        return rows;
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
