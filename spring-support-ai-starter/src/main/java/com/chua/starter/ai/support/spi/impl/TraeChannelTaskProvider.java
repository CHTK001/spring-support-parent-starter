// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.ai.support.spi.impl;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.common.support.channel.ChannelTaskProvider;
import com.chua.starter.common.support.text.json.Json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * TRAE 任务实现。
 *
 * <p>对齐 {@code trae2api-hub} 的 {@code trae_tasks.TASK_DEFS} 口径，但**只暴露真正能做的**：</p>
 * <ul>
 *   <li>{@code daily_checkin} — 国内版每日签到（{@code /trae/api/v2/ug/checkin_credits/*}），
 *       国际版无此端点；</li>
 *   <li>{@code usage_refresh} — 刷新权益 / 用量（trae2api-hub 里 {@code /tasks/travel}
 *       实际就映射到它：Trae 没有福利包可领，动作等价于刷新权益）。</li>
 * </ul>
 *
 * <p>trae2api-hub 里另外四个任务（{@code token_keepalive} / {@code catalog_refresh} /
 * {@code model_probe} / {@code expiry_reminder}）是<b>网关自身的运维动作</b>，不是用户可点的
 * 成长任务，因此不进入本接口——它们在本模块分别由 refresh()、listModels() 与探针任务承担。</p>
 *
 * <p>签到类任务在「报名」语义上映射为 {@code acceptTasks}：Trae 的签到无需报名，
 * 因此 {@code acceptTasks} 返回 0，{@code claimTask} 才真正发请求。</p>
 *
 * @since 4.0.0.52
 * @author CH
 */
@Spi("trae")
public class TraeChannelTaskProvider implements ChannelTaskProvider {

    /**
     * 渠道标识
     */
    static final String CHANNEL = "trae";

    /**
     * 每日签到任务码（与 trae2api-hub 的 {@code daily_checkin} 一致）
     */
    static final String TASK_DAILY_CHECKIN = "daily_checkin";

    /**
     * 权益刷新任务码（对应 trae2api-hub 的 {@code usage_refresh}）
     */
    static final String TASK_USAGE_REFRESH = "usage_refresh";

    /**
     * 签到实现（复用鉴权管理器的签到头与端点）
     */
    private final TraeAccountChannelAuthManager delegate = new TraeAccountChannelAuthManager();

    /**
     * 积分实现（{@code usage_refresh} 领奖即刷新权益）
     */
    private final TraeChannelCreditProvider creditProvider = new TraeChannelCreditProvider();

    @Override
    public String channel() {
        return CHANNEL;
    }

    /**
     * {@inheritDoc}
     *
     * <p>返回两行：每日签到（仅国内版）与权益刷新。签到行会先查一次状态，
     * 已签到时 {@code acceptStatus=claimed} 且 {@code claimable=false}。</p>
     */
    @Override
    public List<Map<String, Object>> tasks(Map<String, Object> credentials) {
        if (credentials == null || credentials.isEmpty()) {
            return List.of();
        }
        boolean intl = TraeChannelCreditProvider.isIntl(credentials);
        List<Map<String, Object>> rows = new ArrayList<>(2);
        if (!intl) {
            rows.add(checkinRow(credentials));
        }
        rows.add(usageRefreshRow(credentials));
        return rows;
    }

    @Override
    public int acceptTasks(Map<String, Object> credentials, List<String> taskCodes) {
        // Trae 的两个任务都不需要「报名」这一步：签到是直接领取，权益刷新是无副作用读。
        return 0;
    }

    /**
     * {@inheritDoc}
     *
     * <p>{@code daily_checkin} 走 SPI 的 {@code checkin()}（含幂等与积分差额）；
     * {@code usage_refresh} 走积分查询，成功即视为「已刷新」。</p>
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
        if (TASK_DAILY_CHECKIN.equals(taskCode)) {
            return claimCheckin(credentials);
        }
        if (TASK_USAGE_REFRESH.equals(taskCode)) {
            return claimUsageRefresh(credentials);
        }
        result.put("message", "未知任务: " + taskCode);
        return result;
    }

    // ==================== 单任务 ====================

    /**
     * 领取每日签到（幂等；已签到返回 claimed=false + already=true）。
     *
     * @param credentials 凭据
     * @return 结果
     */
    private Map<String, Object> claimCheckin(Map<String, Object> credentials) {
        Map<String, Object> result = new LinkedHashMap<>(6);
        try {
            Map<String, Object> after = delegate.checkin(credentials);
            String state = String.valueOf(after.getOrDefault("checkin", ""));
            String detail = String.valueOf(after.getOrDefault("checkinDetail", ""));
            result.put("ok", !"failed".equalsIgnoreCase(state));
            result.put("claimed", "claimed".equalsIgnoreCase(state));
            result.put("message", detail.isEmpty() || "null".equals(detail) ? state : detail);
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
     * 刷新权益 / 用量（无副作用读，成功即算完成）。
     *
     * @param credentials 凭据
     * @return 结果
     */
    private Map<String, Object> claimUsageRefresh(Map<String, Object> credentials) {
        Map<String, Object> result = new LinkedHashMap<>(6);
        Map<String, Object> credit = creditProvider.credit(credentials);
        if (credit.get("error") != null) {
            result.put("ok", false);
            result.put("claimed", false);
            result.put("message", "权益刷新失败: " + credit.get("error"));
            return result;
        }
        result.put("ok", true);
        // 刷新不是「领取」，不产生新增额度，因此 claimed=false
        result.put("claimed", false);
        result.put("credit", credit.get("remain"));
        result.put("message", "权益已刷新（剩余 " + credit.get("remain") + "）");
        return result;
    }

    /**
     * 签到任务行：先查状态，再决定 claimable。
     *
     * @param credentials 凭据
     * @return 任务行
     */
    private Map<String, Object> checkinRow(Map<String, Object> credentials) {
        Map<String, Object> row = new LinkedHashMap<>(8);
        row.put("code", TASK_DAILY_CHECKIN);
        row.put("title", "每日签到");
        row.put("description", "国内版每日签到领取积分（国际版无此接口）");
        boolean checkedIn = false;
        long credits = 0L;
        String error = null;
        try {
            Map<String, Object> status = checkinStatus(credentials);
            checkedIn = Boolean.TRUE.equals(status.get("checked_in"));
            Long value = toLong(status.get("credits"));
            if (value != null) {
                credits = value;
            }
        } catch (Exception e) {
            error = ChannelHttp.truncate(e.getMessage());
        }
        row.put("current", checkedIn ? 1 : 0);
        row.put("target", 1);
        row.put("acceptStatus", checkedIn ? "claimed" : "accepted");
        row.put("claimable", !checkedIn && error == null);
        row.put("claimed", checkedIn);
        if (credits > 0) {
            row.put("credit", credits);
        }
        if (error != null) {
            row.put("error", error);
        }
        return row;
    }

    /**
     * 权益刷新任务行。
     *
     * @param credentials 凭据
     * @return 任务行
     */
    private Map<String, Object> usageRefreshRow(Map<String, Object> credentials) {
        Map<String, Object> row = new LinkedHashMap<>(8);
        row.put("code", TASK_USAGE_REFRESH);
        row.put("title", "权益 / 用量刷新");
        row.put("description", "拉取账号权益包与会话用量（Trae 无福利包领取，动作等价于刷新）");
        row.put("current", 0);
        row.put("target", 0);
        row.put("acceptStatus", "accepted");
        row.put("claimable", true);
        row.put("claimed", false);
        return row;
    }

    /**
     * 查签到状态（{@code POST /trae/api/v2/ug/checkin_credits/status}）。
     *
     * @param credentials 凭据
     * @return 状态节点（{@code checked_in} / {@code credits} / {@code enable}）
     */
    private static Map<String, Object> checkinStatus(Map<String, Object> credentials) {
        String accessToken = String.valueOf(credentials.getOrDefault("access_token", ""));
        String uid = String.valueOf(credentials.getOrDefault("uid", ""));
        Map<String, String> headers = new LinkedHashMap<>(6);
        headers.put("Content-Type", "application/json");
        headers.put("Cloud-IDE-JWT", accessToken);
        headers.put("X-Device-Id", TraeAccountChannelAuthManager.checkinDeviceId(uid));
        headers.put("X-Market-User-Id", TraeAccountChannelAuthManager.checkinMarketUserId(uid));
        headers.put("X-Session-Id", TraeAccountChannelAuthManager.checkinSessionId(uid));
        String body = ChannelHttp.postJson(
                "https://api.trae.cn/trae/api/v2/ug/checkin_credits/status",
                Json.toJson(Map.of("req_source", 1)), headers);
        Map<String, Object> payload = Json.getJsonObject(body);
        if (payload == null) {
            return new LinkedHashMap<>();
        }
        // 有些响应把状态套在 data 里
        if (payload.get("data") instanceof Map<?, ?> data) {
            Map<String, Object> out = new LinkedHashMap<>();
            data.forEach((k, v) -> out.put(String.valueOf(k), v));
            // 保留外层 code/msg，便于错误判定
            payload.forEach(out::putIfAbsent);
            return out;
        }
        return payload;
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
}
