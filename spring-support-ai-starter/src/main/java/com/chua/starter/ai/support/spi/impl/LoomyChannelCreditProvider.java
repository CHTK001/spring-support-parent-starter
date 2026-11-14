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
 * Loomy（讯飞）积分查询实现。
 *
 * <p>端点：{@code GET https://loomyad.xunfei.cn/api/v1/points/balance}。这是官方客户端
 * 读余额的<b>最小端点</b>（实测 61~233ms，只回三个数，不拉流水），鉴权是<b>双头</b>——
 * {@code token: <session>} 与 {@code Authorization: Bearer <session>} 必须同时带，
 * 只给一个上游回鉴权失败。</p>
 *
 * <pre>
 * {"code":"000000","desc":"","data":{"balance":25000,"dailyBalance":5000,"availableBalance":30000}}
 * </pre>
 *
 * <p>三个数的含义：{@code balance} = 永久积分（一次性，用完不再有）；
 * {@code dailyBalance} = 今日池剩余（每天 5000，北京 0 点重置）；
 * {@code availableBalance} = 两池合计（上游自己算的总数，缺失时本实现按前两者相加）。</p>
 *
 * <p>⚠️ 易踩点：上游有部分错误是 <b>HTTP 200 + 信封 {@code code:"100002"}</b>
 * （登录已失效），只看状态码会把失效误判成成功，故一律读业务码判断。
 * 另：{@code first-login}（签到）是<b>写</b>端点，余额查询绝不能调它——
 * 「看一眼余额」不能等于「签到一次」。</p>
 *
 * @since 4.0.0.53
 * @author CH
 */
@Spi("loomy")
public class LoomyChannelCreditProvider implements ChannelCreditProvider {

    /**
     * 渠道标识
     */
    static final String CHANNEL = "loomy";

    /**
     * 积分查询端点
     */
    static final String BALANCE_URL = "https://loomyad.xunfei.cn/api/v1/points/balance";

    /**
     * 业务成功码
     */
    static final String OK_CODE = "000000";

    /**
     * 登录失效业务码
     */
    static final String AUTH_EXPIRED_CODE = "100002";

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
     * <p>永不抛异常：缺凭据、登录失效、结构异常都写 {@code error} 返回。</p>
     */
    @Override
    public Map<String, Object> credit(Map<String, Object> credentials) {
        Map<String, Object> result = new LinkedHashMap<>(8);
        String session = readString(credentials, "access_token");
        if (isBlank(session)) {
            result.put("error", "凭据缺少 access_token（Loomy session）");
            return result;
        }
        try {
            Map<String, Object> payload = parseJson(
                    ChannelHttp.getJson(BALANCE_URL, authHeaders(session)));
            if (payload.isEmpty()) {
                result.put("error", "积分查询返回空响应");
                return result;
            }
            String code = toCode(payload.get("code"));
            if (AUTH_EXPIRED_CODE.equals(code)) {
                result.put("error", "Loomy 登录已失效，请重新扫码登录");
                return result;
            }
            if (!isOk(code)) {
                result.put("error", "积分查询失败: " + ChannelHttp.truncate(businessMessage(payload)));
                return result;
            }
            Map<String, Object> data = unwrap(payload);
            Long permanent = toLong(data.get("balance"));
            if (permanent == null) {
                // balance 是核心字段：读不到说明形状变了，不编造成 0
                result.put("error", "上游未返回积分字段 balance");
                return result;
            }
            Long daily = toLong(data.get("dailyBalance"));
            Long available = toLong(data.get("availableBalance"));
            long remain = available != null ? available : permanent + (daily == null ? 0L : daily);
            result.put("remain", Math.max(remain, 0L));
            result.put("unit", "credit");
            result.put("updatedAt", System.currentTimeMillis());
            result.put("packages", packagesOf(permanent, daily));
            return result;
        } catch (Exception e) {
            result.put("error", "积分查询失败，请稍后重试");
            return result;
        }
    }

    /**
     * 明细行：永久池 + 今日池。两池都没有上限（今日池的上限在写端点里），故不写 {@code size}。
     *
     * @param permanent 永久积分
     * @param daily     今日池剩余（可空）
     * @return 明细行
     */
    private static List<Map<String, Object>> packagesOf(long permanent, Long daily) {
        List<Map<String, Object>> rows = new ArrayList<>(2);
        rows.add(row("永久积分", permanent));
        if (daily != null) {
            rows.add(row("今日积分", daily));
        }
        return rows;
    }

    /**
     * 明细行。
     *
     * @param name   名称
     * @param remain 剩余
     * @return 明细行
     */
    private static Map<String, Object> row(String name, long remain) {
        Map<String, Object> row = new LinkedHashMap<>(2);
        row.put("name", name);
        row.put("remain", Math.max(remain, 0L));
        return row;
    }

    /**
     * 鉴权头：{@code token} 与 {@code Authorization} 双写（只给一个上游回鉴权失败）。
     *
     * @param session Loomy session
     * @return 请求头
     */
    private static Map<String, String> authHeaders(String session) {
        Map<String, String> headers = new LinkedHashMap<>(4);
        headers.put("Accept", "application/json");
        headers.put("token", session.trim());
        headers.put("Authorization", "Bearer " + session.trim());
        return headers;
    }

    /**
     * 取业务数据：信封形状出现过 {@code data} 与 {@code data.data} 两种，都认。
     *
     * @param payload 响应体
     * @return 业务数据
     */
    private static Map<String, Object> unwrap(Map<String, Object> payload) {
        if (payload.get("data") instanceof Map<?, ?> outer) {
            Map<String, Object> data = asMap(outer);
            if (data.get("data") instanceof Map<?, ?> inner) {
                return asMap(inner);
            }
            return data;
        }
        return payload;
    }

    /**
     * 业务码归一（容忍数字 / 字符串两种形态）。
     *
     * @param value 原始值
     * @return 业务码文本
     */
    private static String toCode(Object value) {
        if (value instanceof Number number) {
            return String.valueOf(number.longValue());
        }
        return value instanceof String text ? text.trim() : "";
    }

    /**
     * 是否成功码（{@code 0} / {@code 000000}，缺失视为成功）。
     *
     * @param code 业务码
     * @return 成功返回 {@code true}
     */
    private static boolean isOk(String code) {
        return code.isEmpty() || "0".equals(code) || OK_CODE.equals(code);
    }

    /**
     * 取业务错误文案。
     *
     * @param payload 响应体
     * @return 文案；缺失返回「上游返回未知错误」
     */
    private static String businessMessage(Map<String, Object> payload) {
        for (String key : new String[]{"desc", "message", "msg"}) {
            String text = readString(payload, key);
            if (text != null) {
                return text;
            }
        }
        return "上游返回未知错误";
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
