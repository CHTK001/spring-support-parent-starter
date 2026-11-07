package com.chua.starter.common.support.channel;

import java.util.List;
import java.util.Map;

/**
 * 账号类 AI 渠道的<b>成长任务</b> SPI（列表 / 报名 / 领奖）。
 *
 * <p>与 {@link AccountChannelAuthManager#checkin(Map)} 的分工：签到是「每天一次的动作」，
 * 任务是「一串带进度的目标」（首次对话、绑定手机、邀请好友…），进度由服务端行为事件点亮，
 * 达标后需要显式领取。二者不能混为一谈——签到成功不代表任务可领，任务也不该每天只跑一次。</p>
 *
 * <p>对齐 WorkBuddy 的 {@code /v2/activity/growth/tasks*} 与 trae2api-hub 的
 * {@code TASK_DEFS} 口径：</p>
 * <ul>
 *   <li>{@link #tasks(Map)}：只读，返回任务清单与进度；</li>
 *   <li>{@link #acceptTasks(Map, List)}：报名（幂等，不产生进度）；</li>
 *   <li>{@link #claimTask(Map, String)}：领奖（仅进度达标可领，重复领返回「已领过」）。</li>
 * </ul>
 *
 * <p><b>任务行契约</b>（字段全部可空，缺省表示上游未提供）：</p>
 * <ul>
 *   <li>{@code code}：任务标识（领奖寻址键）；</li>
 *   <li>{@code title} / {@code description}：展示文案；</li>
 *   <li>{@code current} / {@code target}：进度（{@code target=0} 表示「非计数型」）；</li>
 *   <li>{@code acceptStatus}：{@code not_accepted} / {@code accepted} / {@code claimed}；</li>
 *   <li>{@code claimable}：进度达标且未领（本地推算，供前端直接置灰按钮）；</li>
 *   <li>{@code credit} / {@code energy}：奖励额度；</li>
 *   <li>{@code jumpUrl}：客户端跳转协议（如 {@code workbuddy://...}）；</li>
 *   <li>{@code error}：单条解析失败原因。</li>
 * </ul>
 *
 * <p>实现类应<b>永不抛异常</b>：失败时返回空列表或在行里带 {@code error}，
 * 因为任务面板是增强信息，失败不该阻断渠道管理页。</p>
 *
 * @since 4.0.0.52
 * @author CH
 */
public interface ChannelTaskProvider {

    /**
     * 渠道标识（与 {@link AccountChannelAuthManager#channel()} 一致）。
     *
     * @return 渠道标识
     */
    String channel();

    /**
     * 是否支持成长任务。
     *
     * @return true 表示支持
     */
    default boolean supportsTask() {
        return true;
    }

    /**
     * 拉取任务清单（只读，不产生任何上游副作用）。
     *
     * @param credentials 账号凭据
     * @return 任务行列表；不支持或失败时返回空列表
     */
    List<Map<String, Object>> tasks(Map<String, Object> credentials);

    /**
     * 报名任务（幂等）。默认 no-op，支持批量的渠道覆盖。
     *
     * @param credentials 账号凭据
     * @param taskCodes   任务标识
     * @return 实际受理条数
     */
    default int acceptTasks(Map<String, Object> credentials, List<String> taskCodes) {
        return 0;
    }

    /**
     * 领取单个任务奖励。
     *
     * @param credentials 账号凭据
     * @param taskCode    任务标识
     * @return 结果：{@code {ok, claimed, credit, energy, message}}
     */
    Map<String, Object> claimTask(Map<String, Object> credentials, String taskCode);

    /**
     * 一键做任务：报名未报名的 + 领取已达标的。默认实现基于前三个方法组合。
     *
     * @param credentials 账号凭据
     * @return 汇总：{@code {accepted, claimed, credit, messages}}
     */
    default Map<String, Object> runTasks(Map<String, Object> credentials) {
        List<Map<String, Object>> rows = tasks(credentials);
        List<String> pending = new java.util.ArrayList<>();
        List<String> claimable = new java.util.ArrayList<>();
        for (Map<String, Object> row : rows) {
            Object code = row.get("code");
            if (code == null || String.valueOf(code).isBlank()) {
                continue;
            }
            String taskCode = String.valueOf(code);
            String status = String.valueOf(row.getOrDefault("acceptStatus", ""));
            if ("claimed".equalsIgnoreCase(status)) {
                continue;
            }
            if ("not_accepted".equalsIgnoreCase(status) || status.isBlank()) {
                pending.add(taskCode);
                continue;
            }
            if (Boolean.TRUE.equals(row.get("claimable"))) {
                claimable.add(taskCode);
            }
        }
        int accepted = pending.isEmpty() ? 0 : acceptTasks(credentials, pending);
        int claimed = 0;
        double credit = 0;
        List<String> messages = new java.util.ArrayList<>(2);
        for (String taskCode : claimable) {
            Map<String, Object> result = claimTask(credentials, taskCode);
            if (Boolean.TRUE.equals(result.get("claimed"))) {
                claimed++;
                Object value = result.get("credit");
                if (value instanceof Number number) {
                    credit += number.doubleValue();
                }
            }
        }
        if (accepted > 0) {
            messages.add("已报名 " + accepted + " 个任务");
        }
        if (claimed > 0) {
            messages.add("已领取 " + claimed + " 个奖励");
        }
        Map<String, Object> summary = new java.util.LinkedHashMap<>(4);
        summary.put("accepted", accepted);
        summary.put("claimed", claimed);
        summary.put("credit", credit);
        summary.put("messages", messages);
        return summary;
    }
}
