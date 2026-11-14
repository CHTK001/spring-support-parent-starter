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
 * Raccoon（商汤小浣熊）积分查询实现。
 *
 * <p>端点：{@code GET https://xiaohuanxiong.com/api/web/points/v1/balance}（Bearer）。
 * 这是桌面端/网页端同一个积分服务（{@code /api/web/points/v1}）的余额接口，
 * 与推理网关 {@code /api/web/llm/v2} 是两套前缀，不要混用。</p>
 *
 * <pre>
 * {"code":0,"data":{"available_points":1234,"daily_points":300,"monthly_points":10000,
 *                   "reward_points":3000,"topup_points":1500,"topup_frozen":0}}
 * </pre>
 *
 * <p>字段含义（官方《积分规则说明》口径）：每日赠送积分每日 00:00 发放，当天登录可延长至 7 天；
 * 每月赠送积分按订阅周期发放；加油包永久有效（升级版到期期间冻结，见 {@code topup_frozen}）；
 * 奖励积分 1 年有效。四类同时存在时按「每日 → 每月 → 奖励 → 加油包」顺序扣除。</p>
 *
 * <p>⚠️ 易踩点：{@code access_token} 约 2 小时有效、{@code refresh_token} 约 30 天，
 * 且 refresh 会<b>轮换</b> refresh_token；本类只读余额，不触发续期——
 * 续期由鉴权管理器负责，这里 401 只如实报「登录已失效」，避免两次刷新互相顶掉。</p>
 *
 * @since 4.0.0.53
 * @author CH
 */
@Spi("raccoon")
public class RaccoonChannelCreditProvider implements ChannelCreditProvider {

    /**
     * 渠道标识
     */
    static final String CHANNEL = "raccoon";

    /**
     * 积分余额端点
     */
    static final String BALANCE_URL = "https://xiaohuanxiong.com/api/web/points/v1/balance";

    /**
     * 鉴权失败业务码
     */
    static final String AUTH_ERROR_CODE = "200003";

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
        Map<String, Object> result = new LinkedHashMap<>(8);
        String accessToken = readString(credentials, "access_token");
        if (isBlank(accessToken)) {
            result.put("error", "凭据缺少 access_token");
            return result;
        }
        try {
            Map<String, Object> payload = parseJson(
                    ChannelHttp.getJson(BALANCE_URL, authHeaders(accessToken)));
            if (payload.isEmpty()) {
                result.put("error", "积分查询返回空响应");
                return result;
            }
            String code = toCode(payload.get("code"));
            if (AUTH_ERROR_CODE.equals(code)) {
                result.put("error", "Raccoon 登录已失效，请重新扫码登录");
                return result;
            }
            if (!code.isEmpty() && !"0".equals(code)) {
                result.put("error", "积分查询失败: " + ChannelHttp.truncate(businessMessage(payload)));
                return result;
            }
            if (!(payload.get("data") instanceof Map<?, ?> raw)) {
                result.put("error", "上游未返回积分数据");
                return result;
            }
            Map<String, Object> data = asMap(raw);
            Long available = toLong(data.get("available_points"));
            if (available == null) {
                result.put("error", "上游未返回积分字段 available_points");
                return result;
            }
            result.put("remain", Math.max(available, 0L));
            result.put("unit", "credit");
            result.put("updatedAt", System.currentTimeMillis());
            result.put("packages", packagesOf(data));
            return result;
        } catch (Exception e) {
            result.put("error", "积分查询失败，请稍后重试");
            return result;
        }
    }

    /**
     * 明细行：每日 / 每月 / 奖励 / 加油包；加油包另有冻结额度时单列一行。
     *
     * @param data 业务数据
     * @return 明细行
     */
    private static List<Map<String, Object>> packagesOf(Map<String, Object> data) {
        List<Map<String, Object>> rows = new ArrayList<>(5);
        addRow(rows, "每日赠送", data.get("daily_points"));
        addRow(rows, "每月赠送", data.get("monthly_points"));
        addRow(rows, "奖励积分", data.get("reward_points"));
        addRow(rows, "加油包", data.get("topup_points"));
        Long frozen = toLong(data.get("topup_frozen"));
        if (frozen != null && frozen > 0L) {
            // 冻结中的加油包不参与扣除，单列一行说明它「存在但不可用」
            Map<String, Object> row = new LinkedHashMap<>(3);
            row.put("name", "加油包（冻结）");
            row.put("remain", frozen);
            rows.add(row);
        }
        return rows;
    }

    /**
     * 追加一行（值为空则跳过；上游未提供不等于 0）。
     *
     * @param rows  明细行
     * @param name  名称
     * @param value 原始值
     */
    private static void addRow(List<Map<String, Object>> rows, String name, Object value) {
        Long remain = toLong(value);
        if (remain == null) {
            return;
        }
        Map<String, Object> row = new LinkedHashMap<>(2);
        row.put("name", name);
        row.put("remain", Math.max(remain, 0L));
        rows.add(row);
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
     * 取业务错误文案。
     *
     * @param payload 响应体
     * @return 文案；缺失返回「上游返回未知错误」
     */
    private static String businessMessage(Map<String, Object> payload) {
        for (String key : new String[]{"message", "msg", "details"}) {
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
