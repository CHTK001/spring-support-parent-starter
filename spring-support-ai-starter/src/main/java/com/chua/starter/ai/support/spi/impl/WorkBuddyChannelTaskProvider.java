// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.ai.support.spi.impl;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.common.support.channel.ChannelTaskProvider;

import java.util.List;
import java.util.Map;

/**
 * WorkBuddy 成长任务实现。
 *
 * <p>上游三端点（对齐 {@code workbuddy2api-panel} 的 {@code internal/upstream/tasks.go}）：</p>
 * <ul>
 *   <li>{@code GET {chatBase}/v2/activity/growth/tasks} → {@code data.tasks[]}
 *       （含 {@code accept_status} 与 {@code progress{current,target}}）；</li>
 *   <li>{@code POST {chatBase}/v2/activity/growth/tasks/accept}
 *       （body {@code {"task_codes":[...]}}）— 报名，不产生进度；</li>
 *   <li>{@code POST {webBase}/activity/growth/tasks/{code}/claim} — 领奖
 *       （任务码在路径里，重复领取返回 {@code already_claimed=true}）。</li>
 * </ul>
 *
 * <p>只有 CN 域有这套端点：global 账号没有成长任务体系，{@link #tasks(Map)} 直接返回空列表。</p>
 *
 * @since 4.0.0.52
 * @author CH
 */
@Spi("workbuddy")
public class WorkBuddyChannelTaskProvider implements ChannelTaskProvider {

    /**
     * 渠道标识
     */
    static final String CHANNEL = "workbuddy";

    /**
     * 具体实现（任务端点与凭据解析复用鉴权管理器）
     */
    private final WorkBuddyAccountChannelAuthManager delegate = new WorkBuddyAccountChannelAuthManager();

    @Override
    public String channel() {
        return CHANNEL;
    }

    @Override
    public List<Map<String, Object>> tasks(Map<String, Object> credentials) {
        if (credentials == null || credentials.isEmpty()) {
            return List.of();
        }
        // global 域无成长任务体系（panel 的 D4 门控：CN 任务端点对 global 不发起任何调用）
        if (delegate.isGlobalRealm(credentials)) {
            return List.of();
        }
        return delegate.taskList(credentials, delegate.realmOf(credentials));
    }

    @Override
    public int acceptTasks(Map<String, Object> credentials, List<String> taskCodes) {
        if (credentials == null || credentials.isEmpty()
                || taskCodes == null || taskCodes.isEmpty()) {
            return 0;
        }
        if (delegate.isGlobalRealm(credentials)) {
            return 0;
        }
        return delegate.acceptTaskCodes(credentials, delegate.realmOf(credentials), taskCodes);
    }

    @Override
    public Map<String, Object> claimTask(Map<String, Object> credentials, String taskCode) {
        if (credentials == null || credentials.isEmpty()) {
            return Map.of("ok", false, "claimed", false, "message", "账号未绑定或凭据为空");
        }
        if (delegate.isGlobalRealm(credentials)) {
            return Map.of("ok", false, "claimed", false, "message", "global 账号无成长任务");
        }
        return delegate.claimTaskReward(credentials, delegate.realmOf(credentials), taskCode);
    }
}
