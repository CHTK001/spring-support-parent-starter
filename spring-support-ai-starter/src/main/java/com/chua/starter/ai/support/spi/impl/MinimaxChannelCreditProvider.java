// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.ai.support.spi.impl;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.common.support.channel.ChannelCreditProvider;
import com.chua.starter.common.support.text.json.Json;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * MiniMax 免费额度 / 余额查询实现。
 *
 * <p>端点：{@code GET https://agent.minimax.cn/v1/user/balance}（Bearer + 客户端标识头）。
 * 上游余额字段可能是 {@code balance} 或 {@code amount}，两种都取。</p>
 *
 * <p>MiniMax 没有每日签到端点（源码全量扫描无 checkin），额度按窗口自动重置，
 * 所以只有「积分」标签。</p>
 *
 * @since 4.0.0.52
 * @author CH
 */
@Spi("minimax")
public class MinimaxChannelCreditProvider implements ChannelCreditProvider {

    /**
     * 渠道标识
     */
    static final String CHANNEL = "minimax";

    /**
     * 推理 / 账号 API 基址
     */
    static final String API_HOST = "https://agent.minimax.cn";

    /**
     * 余额查询路径
     */
    static final String BALANCE_PATH = "/v1/user/balance";

    @Override
    public String channel() {
        return CHANNEL;
    }

    @Override
    public String creditUnit() {
        return "currency";
    }

    /**
     * {@inheritDoc}
     *
     * <p>永不抛异常：失败写 {@code error}。</p>
     */
    @Override
    public Map<String, Object> credit(Map<String, Object> credentials) {
        Map<String, Object> result = new LinkedHashMap<>(6);
        String accessToken = readString(credentials, "access_token");
        if (isBlank(accessToken)) {
            result.put("error", "凭据缺少 access_token");
            return result;
        }
        try {
            String body = ChannelHttp.getJson(API_HOST + BALANCE_PATH, authHeaders(accessToken));
            Map<String, Object> payload = parseJson(body);
            Map<String, Object> data = payload.get("data") instanceof Map<?, ?> raw
                    ? asMap(raw) : payload;
            Object balance = data.get("balance") != null ? data.get("balance") : data.get("amount");
            if (!(balance instanceof Number number)) {
                result.put("error", "上游未返回 balance / amount 字段");
                return result;
            }
            result.put("remain", number.doubleValue());
            result.put("unit", "currency");
            result.put("currency", "CNY");
            result.put("updatedAt", System.currentTimeMillis());
            return result;
        } catch (Exception e) {
            result.put("error", "余额查询失败: " + ChannelHttp.truncate(e.getMessage()));
            return result;
        }
    }

    /**
     * 鉴权头（Bearer + 客户端标识，缺标识头上游会拒）。
     *
     * @param accessToken 访问令牌
     * @return 请求头
     */
    private static Map<String, String> authHeaders(String accessToken) {
        Map<String, String> headers = new LinkedHashMap<>(4);
        headers.put("Accept", "application/json");
        headers.put("Authorization", "Bearer " + accessToken.trim());
        headers.put("MM-API-Source", "Minimax-Java-SDK");
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
}
