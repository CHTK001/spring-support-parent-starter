// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.ai.support.spi.impl;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.common.support.channel.ChannelCreditProvider;
import com.chua.starter.common.support.text.json.Json;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Cline 积分 / 余额查询实现。
 *
 * <p>端点：{@code GET https://api.cline.bot/api/v1/users/{account_id}/balance}（Bearer）。
 * 上游 {@code balance} 是<b>原始最小单位</b>（实测 ÷100000 后与页面一致，但单位未在上游文档中
 * 明确），因此这里同时回传 {@code rawRemain} 供核对，不把换算值当作唯一事实。</p>
 *
 * <p>Cline 没有签到端点（源码全量扫描无 checkin / campaign 命中），所以它只出现在
 * 「积分」标签下，不出现「任务」标签。</p>
 *
 * @since 4.0.0.52
 * @author CH
 */
@Spi("cline")
public class ClineChannelCreditProvider implements ChannelCreditProvider {

    /**
     * 渠道标识
     */
    static final String CHANNEL = "cline";

    /**
     * 上游 API 基址
     */
    static final String API_BASE = "https://api.cline.bot";

    /**
     * 余额原始值与展示值的换算系数（上游未文档化，保留原始值核对）
     */
    static final double BALANCE_SCALE = 100_000d;

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
     * <p>永不抛异常：缺凭据、401、结构异常都写 {@code error} 返回。</p>
     */
    @Override
    public Map<String, Object> credit(Map<String, Object> credentials) {
        Map<String, Object> result = new LinkedHashMap<>(6);
        String accessToken = readString(credentials, "access_token");
        String accountId = readString(credentials, "account_id");
        if (isBlank(accessToken) || isBlank(accountId)) {
            result.put("error", "凭据缺少 access_token 或 account_id");
            return result;
        }
        try {
            String body = ChannelHttp.getJson(
                    API_BASE + "/api/v1/users/" + urlEncode(accountId.trim()) + "/balance",
                    authHeaders(accessToken));
            Map<String, Object> payload = parseJson(body);
            Map<String, Object> data = payload.get("data") instanceof Map<?, ?> raw
                    ? asMap(raw) : payload;
            Object balance = data.get("balance");
            if (!(balance instanceof Number number)) {
                result.put("error", "上游未返回 balance 字段");
                return result;
            }
            result.put("remain", Math.round(number.doubleValue() / BALANCE_SCALE));
            result.put("rawRemain", number.longValue());
            result.put("unit", "credit");
            result.put("updatedAt", System.currentTimeMillis());
            return result;
        } catch (Exception e) {
            result.put("error", "余额查询失败: " + ChannelHttp.truncate(e.getMessage()));
            return result;
        }
    }

    /**
     * 鉴权头（与鉴权管理器同口径的 Bearer）。
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
}
