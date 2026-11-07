// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.ai.support.spi.impl;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.common.support.channel.ChannelTaskProvider;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * LobsterAI（有道龙虾）成长任务实现。
 *
 * <p>对齐 {@code lobsterai-credits.ts} 的三步活动协议：</p>
 * <ol>
 *   <li>{@code GET /api/client-activities/slot?placement=…} → {@code slotState} /
 *       {@code activity.activityCode} / {@code activity.configRevision}；</li>
 *   <li>{@code GET /api/client-activities/{code}/context?configRevision=…} →
 *       {@code state.claimedToday} / {@code actions}；</li>
 *   <li>{@code POST /api/client-activities/{code}/actions/check_in}
 *       （body {@code {configRevision, idempotencyKey, payload:{}}}）。</li>
 * </ol>
 *
 * <p>这里把「活动」暴露成一行任务：{@code code} 用 {@code activityCode}，
 * 已签到则 {@code acceptStatus=claimed}。{@code acceptTasks} 无需报名（活动由服务端下发）。</p>
 *
 * @since 4.0.0.52
 * @author CH
 */
@Spi("lobsterai")
public class LobsteraiChannelTaskProvider implements ChannelTaskProvider {

    /**
     * 渠道标识
     */
    static final String CHANNEL = "lobsterai";

    /**
     * 上游 API 基址
     */
    static final String API_BASE = "https://lobsterai-server.youdao.com";

    /**
     * 活动槽位路径
     */
    static final String ACTIVITY_SLOT_PATH = "/api/client-activities/slot";

    /**
     * 活动基础路径
     */
    static final String ACTIVITY_BASE_PATH = "/api/client-activities";

    /**
     * 槽位 placement 值
     */
    static final String SLOT_PLACEMENT = "profile";

    /**
     * 槽位 containerApiVersion
     */
    static final String SLOT_CONTAINER_API_VERSION = "1.0.0";

    /**
     * 槽位 platform 值
     */
    static final String SLOT_PLATFORM = "desktop";

    /**
     * 签到动作名
     */
    static final String ACTION_CHECK_IN = "check_in";

    /**
     * 鉴权管理器（复用其客户端版本探测与活动三步实现，避免口径漂移）
     */
    private final LobsteraiAccountChannelAuthManager delegate = new LobsteraiAccountChannelAuthManager();

    @Override
    public String channel() {
        return CHANNEL;
    }

    /**
     * {@inheritDoc}
     *
     * <p>只读：查询槽位与上下文，不触发签到。</p>
     */
    @Override
    public List<Map<String, Object>> tasks(Map<String, Object> credentials) {
        if (credentials == null || credentials.isEmpty()) {
            return List.of();
        }
        String accessToken = readString(credentials, "access_token");
        if (isBlank(accessToken)) {
            return List.of();
        }
        List<Map<String, Object>> rows = new ArrayList<>(1);
        try {
            Map<String, Object> slot = slotOf(credentials);
            String slotState = String.valueOf(slot.getOrDefault("slotState", ""));
            Map<String, Object> activity = slot.get("activity") instanceof Map<?, ?> raw
                    ? asMap(raw) : Map.of();
            String activityCode = readString(activity, "activityCode");
            if (!"available".equals(slotState) || isBlank(activityCode)) {
                Map<String, Object> row = new LinkedHashMap<>(6);
                row.put("code", "daily_checkin");
                row.put("title", "每日签到");
                row.put("description", "当前无可用活动（slotState=" + slotState + "）");
                row.put("current", 0);
                row.put("target", 1);
                row.put("acceptStatus", "not_accepted");
                row.put("claimable", false);
                row.put("claimed", false);
                rows.add(row);
                return rows;
            }
            long configRevision = longOf(activity.get("configRevision"));
            Map<String, Object> context = contextOf(credentials, activityCode, configRevision);
            Map<String, Object> state = context.get("state") instanceof Map<?, ?> raw
                    ? asMap(raw) : Map.of();
            boolean claimedToday = Boolean.TRUE.equals(state.get("claimedToday"));
            List<String> actions = context.get("actions") instanceof List<?> list
                    ? list.stream().map(String::valueOf).toList()
                    : List.of();
            boolean canCheckIn = actions.contains(ACTION_CHECK_IN);
            Map<String, Object> row = new LinkedHashMap<>(8);
            row.put("code", activityCode);
            String title = readString(activity, "name");
            row.put("title", isBlank(title) ? "每日签到" : title);
            row.put("description", claimedToday ? "今天已签到" : "每日登录奖励");
            row.put("current", claimedToday ? 1 : 0);
            row.put("target", 1);
            row.put("acceptStatus", claimedToday ? "claimed" : "accepted");
            row.put("claimable", !claimedToday && canCheckIn);
            row.put("claimed", claimedToday);
            rows.add(row);
        } catch (Exception e) {
            Map<String, Object> row = new LinkedHashMap<>(6);
            row.put("code", "daily_checkin");
            row.put("title", "每日签到");
            row.put("current", 0);
            row.put("target", 1);
            row.put("acceptStatus", "not_accepted");
            row.put("claimable", false);
            row.put("claimed", false);
            row.put("error", ChannelHttp.truncate(e.getMessage()));
            rows.add(row);
        }
        return rows;
    }

    @Override
    public int acceptTasks(Map<String, Object> credentials, List<String> taskCodes) {
        // 活动由服务端下发，无「报名」动作
        return 0;
    }

    /**
     * {@inheritDoc}
     *
     * <p>走鉴权管理器的 {@code checkin()}（活动三步 + 幂等），并归一化为领奖结果。</p>
     */
    @Override
    public Map<String, Object> claimTask(Map<String, Object> credentials, String taskCode) {
        Map<String, Object> result = new LinkedHashMap<>(5);
        result.put("ok", false);
        result.put("claimed", false);
        if (credentials == null || credentials.isEmpty()) {
            result.put("message", "账号未绑定或凭据为空");
            return result;
        }
        try {
            Map<String, Object> after = delegate.checkin(credentials);
            String state = String.valueOf(after.getOrDefault("checkinResult", ""));
            String message = String.valueOf(after.getOrDefault("checkinMessage", ""));
            result.put("ok", "claimed".equals(state) || "already-claimed".equals(state));
            result.put("claimed", "claimed".equals(state));
            result.put("message", isBlank(message) || "null".equals(message)
                    ? ("claimed".equals(state) ? "签到成功" : "未产生新奖励") : message);
            Object credits = after.get("credits");
            if (credits instanceof Number number) {
                result.put("credit", number.longValue());
            }
            return result;
        } catch (Exception e) {
            result.put("message", "签到失败: " + ChannelHttp.truncate(e.getMessage()));
            return result;
        }
    }

    /**
     * 查活动槽位。
     *
     * @param credentials 凭据
     * @return 槽位节点
     */
    private static Map<String, Object> slotOf(Map<String, Object> credentials) {
        String url = API_BASE + ACTIVITY_SLOT_PATH
                + "?placement=" + urlEncode(SLOT_PLACEMENT)
                + "&clientVersion=" + urlEncode(clientVersion())
                + "&containerApiVersion=" + SLOT_CONTAINER_API_VERSION
                + "&platform=" + urlEncode(SLOT_PLATFORM);
        return dataOf(ChannelHttp.getJson(url, authHeaders(credentials)));
    }

    /**
     * 查活动上下文。
     *
     * @param credentials    凭据
     * @param activityCode   活动标识
     * @param configRevision 配置版本
     * @return 上下文节点
     */
    private static Map<String, Object> contextOf(Map<String, Object> credentials,
                                                String activityCode, long configRevision) {
        String url = API_BASE + ACTIVITY_BASE_PATH + "/" + urlEncode(activityCode)
                + "/context?configRevision=" + configRevision;
        return dataOf(ChannelHttp.getJson(url, authHeaders(credentials)));
    }

    /**
     * 客户端版本（探测失败时用兜底值）。
     *
     * @return 版本号
     */
    private static String clientVersion() {
        String property = System.getProperty("lobsterai.clientVersion");
        return isBlank(property) ? "1.0.0" : property.trim();
    }

    /**
     * 解信封 {@code {code, msg, data}}。
     *
     * @param body 响应体
     * @return data 节点
     */
    private static Map<String, Object> dataOf(String body) {
        if (isBlank(body)) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, Object> payload = com.chua.starter.common.support.text.json.Json.getJsonObject(body);
            if (payload == null) {
                return new LinkedHashMap<>();
            }
            int code = payload.get("code") instanceof Number number ? number.intValue() : 0;
            if (code != 0) {
                throw new IllegalStateException("code=" + code + " msg=" + payload.get("msg"));
            }
            return payload.get("data") instanceof Map<?, ?> raw ? asMap(raw) : new LinkedHashMap<>();
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    /**
     * 鉴权头。
     *
     * @param credentials 凭据
     * @return 请求头
     */
    private static Map<String, String> authHeaders(Map<String, Object> credentials) {
        Map<String, String> headers = new LinkedHashMap<>(4);
        headers.put("Accept", "application/json");
        headers.put("Authorization", "Bearer "
                + String.valueOf(credentials.getOrDefault("access_token", "")).trim());
        return headers;
    }

    /**
     * 数字归一。
     *
     * @param value 原始值
     * @return 长整数（不可解析按 0）
     */
    private static long longOf(Object value) {
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
