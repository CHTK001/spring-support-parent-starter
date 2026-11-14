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
 * ZCode（智谱 Z.ai / GLM Coding Plan）额度查询实现。
 *
 * <p>端点：{@code GET https://zcode.z.ai/api/v1/zcode-plan/billing/balance?app_version=3.7.7}
 * （{@code Authorization: Bearer <zcode_jwt>}）。这是 ZCode 客户端自己的套餐账单端点，
 * 只认登录时下发的 <b>zcode_jwt</b>——正是本系统 {@code ZcodeAccountChannelAuthManager}
 * 落库的 {@code access_token}；API key 方式绑的账号没有 jwt，读不了这里。</p>
 *
 * <pre>
 * {"code":0,"msg":"","data":{"server_time":...,"plans":[{"plan_id","name","status",...}],
 *  "balances":[{"show_name":"GLM-5.3","meter":"model_usage","unit_type":"token",
 *    "total_units":100000000,"used_units":0,"remaining_units":100000000,
 *    "available_units":100000000,"period_start":...,"period_end":...,"expires_at":...}]}}
 * </pre>
 *
 * <p>⚠️ 易踩点：</p>
 * <ul>
 *   <li>业务失败也可能是 <b>HTTP 200 + 信封 {@code code≠0}</b>，必须读 {@code code}/{@code msg}；</li>
 *   <li>{@code remaining_units} 缺失时退回 {@code available_units}——两者在正常响应里同值，
 *       但「缺失」不能被当成 0（那会把有余额的号显示成已用尽）；</li>
 *   <li>时间字段是 <b>Unix 秒</b>，本 impl 统一换算成毫秒；</li>
 *   <li>{@code zcode-plan} 网关只对「对话」加验证码风控，<b>读取额度不受影响</b>。</li>
 * </ul>
 *
 * <p>另一条已核对但<b>不采用</b>的路径：{@code https://api.z.ai/api/monitor/usage/quota/limit}
 * 只认 Z.ai 平台 API key（本系统 ZCode 渠道存的是 jwt），且它只回 5 小时 / 每周的
 * {@code percentage}，没有绝对单位量，故不用于本渠道。</p>
 *
 * @since 4.0.0.53
 * @author CH
 */
@Spi("zcode")
public class ZcodeChannelCreditProvider implements ChannelCreditProvider {

    /**
     * 渠道标识
     */
    static final String CHANNEL = "zcode";

    /**
     * 套餐账单余额端点
     */
    static final String BALANCE_URL =
            "https://zcode.z.ai/api/v1/zcode-plan/billing/balance?app_version=3.7.7";

    /**
     * 客户端版本号（上游按它区分客户端形态）
     */
    static final String APP_VERSION = "3.7.7";

    @Override
    public String channel() {
        return CHANNEL;
    }

    @Override
    public String creditUnit() {
        return "token";
    }

    /**
     * {@inheritDoc}
     *
     * <p>永不抛异常：缺凭据、401、业务码失败、结构异常都写 {@code error} 返回。</p>
     */
    @Override
    public Map<String, Object> credit(Map<String, Object> credentials) {
        Map<String, Object> result = new LinkedHashMap<>(8);
        String jwt = readString(credentials, "access_token");
        if (isBlank(jwt)) {
            result.put("error", "凭据缺少 access_token（zcode_jwt）");
            return result;
        }
        try {
            Map<String, Object> payload = parseJson(
                    ChannelHttp.getJson(BALANCE_URL, authHeaders(jwt, credentials)));
            if (payload.isEmpty()) {
                result.put("error", "额度查询返回空响应");
                return result;
            }
            if (!isOk(payload.get("code"))) {
                result.put("error", "额度查询失败: " + ChannelHttp.truncate(businessMessage(payload)));
                return result;
            }
            if (!(payload.get("data") instanceof Map<?, ?> raw)) {
                result.put("error", "上游未返回 data");
                return result;
            }
            Map<String, Object> data = asMap(raw);
            List<Map<String, Object>> balances = mapsOf(data.get("balances"));
            if (balances.isEmpty()) {
                result.put("error", "上游未返回 balances（该账号可能没有生效中的套餐）");
                return result;
            }
            long total = 0L;
            long remain = 0L;
            boolean totalKnown = false;
            for (Map<String, Object> balance : balances) {
                Long totalUnits = toLong(balance.get("total_units"));
                if (totalUnits != null) {
                    total += totalUnits;
                    totalKnown = true;
                }
                Long left = toLong(balance.get("remaining_units"));
                if (left == null) {
                    left = toLong(balance.get("available_units"));
                }
                if (left != null) {
                    remain += left;
                }
            }
            result.put("remain", Math.max(remain, 0L));
            if (totalKnown) {
                result.put("total", Math.max(total, 0L));
            }
            result.put("unit", unitOf(balances));
            result.put("updatedAt", System.currentTimeMillis());
            result.put("packages", packagesOf(balances));
            String plan = planOf(data.get("plans"));
            if (!isBlank(plan)) {
                result.put("plan", plan);
            }
            return result;
        } catch (Exception e) {
            result.put("error", "额度查询失败，请稍后重试");
            return result;
        }
    }

    /**
     * 明细行：每个额度桶一行（GLM 各模型分别计量）。
     *
     * @param balances 额度桶
     * @return 明细行
     */
    private static List<Map<String, Object>> packagesOf(List<Map<String, Object>> balances) {
        List<Map<String, Object>> rows = new ArrayList<>(balances.size());
        for (Map<String, Object> balance : balances) {
            Long left = toLong(balance.get("remaining_units"));
            if (left == null) {
                left = toLong(balance.get("available_units"));
            }
            Long size = toLong(balance.get("total_units"));
            if (left == null && size == null) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>(4);
            String name = readString(balance, "show_name");
            if (isBlank(name)) {
                name = readString(balance, "meter");
            }
            row.put("name", isBlank(name) ? "额度桶" : name);
            if (left != null) {
                row.put("remain", Math.max(left, 0L));
            }
            if (size != null) {
                row.put("size", Math.max(size, 0L));
            }
            Long expiresAt = toMillis(balance.get("expires_at"));
            if (expiresAt == null) {
                expiresAt = toMillis(balance.get("period_end"));
            }
            if (expiresAt != null) {
                row.put("expiresAt", expiresAt);
            }
            rows.add(row);
        }
        return rows;
    }

    /**
     * 套餐名：优先 {@code status=active} 的那一条，否则第一条。
     *
     * @param plans 套餐列表
     * @return 套餐名；没有返回 {@code null}
     */
    private static String planOf(Object plans) {
        List<Map<String, Object>> rows = mapsOf(plans);
        if (rows.isEmpty()) {
            return null;
        }
        String fallback = null;
        for (Map<String, Object> plan : rows) {
            String name = readString(plan, "name");
            if (isBlank(name)) {
                name = readString(plan, "plan_id");
            }
            if (isBlank(name)) {
                continue;
            }
            if ("active".equals(readString(plan, "status"))) {
                return name;
            }
            if (fallback == null) {
                fallback = name;
            }
        }
        return fallback;
    }

    /**
     * 单位：额度桶声明 {@code unit_type=token} 时按 Token，否则按积分。
     *
     * @param balances 额度桶
     * @return 单位名
     */
    private static String unitOf(List<Map<String, Object>> balances) {
        for (Map<String, Object> balance : balances) {
            String unitType = readString(balance, "unit_type");
            if (!isBlank(unitType)) {
                return unitType;
            }
        }
        return "credit";
    }

    /**
     * 鉴权头；凭据里带了 {@code device_mid} 时一并带上（部分网关版本要求 UUID 设备指纹）。
     *
     * @param jwt         访问令牌
     * @param credentials 凭据
     * @return 请求头
     */
    private static Map<String, String> authHeaders(String jwt, Map<String, Object> credentials) {
        Map<String, String> headers = new LinkedHashMap<>(6);
        headers.put("Accept", "application/json");
        headers.put("Authorization", "Bearer " + jwt.trim());
        headers.put("X-ZCode-App-Version", APP_VERSION);
        String deviceMid = readString(credentials, "device_mid");
        if (!isBlank(deviceMid)) {
            headers.put("X-Device-Mid", deviceMid.trim());
        }
        return headers;
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
     * 是否成功（{@code code=0} 或缺失）。
     *
     * @param value 原始业务码
     * @return 成功返回 {@code true}
     */
    private static boolean isOk(Object value) {
        String code = toCode(value);
        return code.isEmpty() || "0".equals(code);
    }

    /**
     * 取业务错误文案。
     *
     * @param payload 响应体
     * @return 文案；缺失返回「上游返回未知错误」
     */
    private static String businessMessage(Map<String, Object> payload) {
        for (String key : new String[]{"msg", "message", "desc"}) {
            String text = readString(payload, key);
            if (text != null) {
                return text;
            }
        }
        return "上游返回未知错误";
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
     * Unix 秒 → 毫秒（容忍毫秒 / 字符串两种形态）。
     *
     * @param value 原始值
     * @return 毫秒时间戳；不可解析返回 {@code null}
     */
    private static Long toMillis(Object value) {
        Long number = toLong(value);
        if (number == null || number <= 0L) {
            return null;
        }
        // 1e11 以下视为秒（当前毫秒时间戳早已越过 1e12）
        return number < 100_000_000_000L ? number * 1000L : number;
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
