// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.ai.support.spi.impl;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.common.support.channel.ChannelTaskProvider;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Qoder 成长任务实现（活动领奖，国际版 / 国内版共用协议）。
 *
 * <p>Qoder 的「任务」是活动制：{@code GET {openApi}/sash/api/v1/me/campaigns} 返回
 * {@code campaigns[]}，每条含 {@code actionType}（{@code CLAIM_BENEFIT} 才可领）与
 * {@code claimStatus}（{@code CLAIMABLE} / {@code CLAIMED}）。领奖走
 * {@code POST .../campaigns/{id}/claim}。</p>
 *
 * <p>与签到（{@link QoderAccountChannelAuthManager#checkin(Map)}）的关系：签到就是
 * 「把所有 CLAIMABLE 的活动都领一遍」；本接口把同一批活动按任务行暴露出来，
 * 支持逐条领取与查看进度。</p>
 *
 * @since 4.0.0.52
 * @author CH
 */
@Spi("qoder")
public class QoderChannelTaskProvider implements ChannelTaskProvider {

    /**
     * 渠道标识
     */
    static final String CHANNEL = "qoder";

    /**
     * OpenAPI 站点
     */
    static final String OPEN_API_BASE = "https://openapi.qoder.sh";

    /**
     * 活动列表路径
     */
    static final String CAMPAIGNS_PATH = "/sash/api/v1/me/campaigns";

    /**
     * 可领取的动作类型
     */
    static final String ACTION_CLAIM_BENEFIT = "CLAIM_BENEFIT";

    /**
     * 已领取状态
     */
    static final String STATUS_CLAIMED = "CLAIMED";

    /**
     * 可领取状态
     */
    static final String STATUS_CLAIMABLE = "CLAIMABLE";

    private final String channel;
    private final String openApiBase;

    /**
     * 默认构造（国际版）。
     */
    public QoderChannelTaskProvider() {
        this(CHANNEL, OPEN_API_BASE);
    }

    /**
     * 指定渠道与站点的构造（供国内版子类使用）。
     *
     * @param channel     渠道标识
     * @param openApiBase OpenAPI 站点
     */
    protected QoderChannelTaskProvider(String channel, String openApiBase) {
        this.channel = channel;
        this.openApiBase = openApiBase;
    }

    @Override
    public String channel() {
        return channel;
    }

    /**
     * {@inheritDoc}
     *
     * <p>只读：把活动列表归一化为任务行，不产生领取副作用。</p>
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
        try {
            String body = ChannelHttp.getJson(openApiBase + CAMPAIGNS_PATH,
                    QoderChannelCreditProvider.sashHeaders(accessToken));
            Map<String, Object> payload = parseJson(body);
            Object raw = payload.get("campaigns");
            if (!(raw instanceof List<?>)) {
                raw = payload.get("data") instanceof Map<?, ?> data ? data.get("campaigns") : null;
            }
            List<?> list = raw instanceof List<?> inner ? inner : List.of();
            List<Map<String, Object>> rows = new ArrayList<>(list.size());
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> rawItem)) {
                    continue;
                }
                Map<String, Object> campaign = asMap(rawItem);
                String campaignId = readString(campaign, "campaignId");
                if (isBlank(campaignId)) {
                    campaignId = readString(campaign, "campaign_id");
                }
                if (isBlank(campaignId)) {
                    continue;
                }
                String actionType = readString(campaign, "actionType");
                if (!ACTION_CLAIM_BENEFIT.equals(actionType)) {
                    continue;
                }
                String claimStatus = readString(campaign, "claimStatus");
                boolean claimed = STATUS_CLAIMED.equals(claimStatus);
                boolean claimable = STATUS_CLAIMABLE.equals(claimStatus);
                Map<String, Object> row = new LinkedHashMap<>(8);
                row.put("code", campaignId);
                String title = readString(campaign, "name");
                if (isBlank(title)) {
                    title = readString(campaign, "title");
                }
                row.put("title", isBlank(title) ? campaignId : title);
                String description = readString(campaign, "description");
                if (!isBlank(description)) {
                    row.put("description", description);
                }
                row.put("current", claimed ? 1 : (claimable ? 1 : 0));
                row.put("target", 1);
                row.put("acceptStatus", claimed ? "claimed" : (claimable ? "accepted" : "not_accepted"));
                row.put("claimable", claimable);
                row.put("claimed", claimed);
                Long amount = toLong(campaign.get("amount"));
                if (amount != null && amount > 0) {
                    row.put("credit", amount);
                }
                rows.add(row);
            }
            return rows;
        } catch (Exception e) {
            return List.of();
        }
    }

    @Override
    public int acceptTasks(Map<String, Object> credentials, List<String> taskCodes) {
        // Qoder 的活动无需报名：CLAIMABLE 即已具备领取资格
        return 0;
    }

    /**
     * {@inheritDoc}
     *
     * <p>逐条领奖：{@code POST .../campaigns/{id}/claim}；{@code replayed=true}
     * 表示重复领取（幂等成功，但不算新增额度）。</p>
     */
    @Override
    public Map<String, Object> claimTask(Map<String, Object> credentials, String taskCode) {
        Map<String, Object> result = new LinkedHashMap<>(5);
        result.put("ok", false);
        result.put("claimed", false);
        String accessToken = credentials == null ? null : readString(credentials, "access_token");
        if (isBlank(accessToken)) {
            result.put("message", "凭据缺少 access_token");
            return result;
        }
        if (isBlank(taskCode)) {
            result.put("message", "任务标识为空");
            return result;
        }
        try {
            String body = ChannelHttp.postJson(
                    openApiBase + CAMPAIGNS_PATH + "/" + urlEncode(taskCode) + "/claim",
                    "{}", QoderChannelCreditProvider.sashHeaders(accessToken));
            Map<String, Object> payload = parseJson(body);
            if (Boolean.TRUE.equals(payload.get("replayed"))) {
                result.put("ok", true);
                result.put("message", "该活动奖励已领取过");
                return result;
            }
            String status = readString(payload, "status");
            if (!isBlank(status) && !STATUS_CLAIMED.equals(status)) {
                result.put("message", "领奖失败: status=" + status);
                return result;
            }
            result.put("ok", true);
            result.put("claimed", true);
            Long amount = toLong(payload.get("amount"));
            if (amount != null) {
                result.put("credit", amount);
            }
            result.put("message", "领奖成功");
            return result;
        } catch (Exception e) {
            result.put("message", "领奖失败: " + ChannelHttp.truncate(e.getMessage()));
            return result;
        }
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
            Map<String, Object> map = com.chua.starter.common.support.text.json.Json.getJsonObject(text);
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
