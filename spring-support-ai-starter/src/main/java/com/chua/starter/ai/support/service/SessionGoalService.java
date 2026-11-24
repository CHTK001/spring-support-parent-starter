// [P3C A20 豁免] A20/A30/A31: 含日志/状态字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.ai.support.service;

import com.chua.starter.ai.support.entity.SysAiSession;
import com.chua.starter.ai.support.manage.common.ApiErrorCode;
import com.chua.starter.ai.support.manage.common.ApiException;
import com.chua.starter.ai.support.mapper.SysAiSessionMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 会话目标（Goal / Issue）服务。
 *
 * <p>目标持久化在会话自身的 {@code settings} JSON（{@code settings.goal} 节点），
 * 与 {@code aiNextTask} 同一存储位；不新建表——目标是会话级单例，随会话读写，
 * JSON 节点足够，且前端 settings 本就整体序列化在该列。</p>
 *
 * <p><b>状态机</b>（与前端 {@code GoalState} 词表一一对应）：</p>
 * <ul>
 *   <li>{@code save}（PUT）：新建或覆盖目标，状态置 {@code RUNNING}；
 *       标题不变 = 同一目标（保留令牌/轮次/基线/预算），标题变了 = 新目标（计数清零）；</li>
 *   <li>{@code pause}：{@code PENDING/RUNNING → PAUSED}；已暂停幂等；终态 409；</li>
 *   <li>{@code resume}：{@code PAUSED → RUNNING}；进行中幂等；终态 409；</li>
 *   <li>{@code cancel}：<b>彻底销毁</b>——从 settings 移除该节点（前后端都不保留），
 *       幂等（无目标时返回 null），响应里带回被销毁目标并标 {@code CANCELED}。</li>
 * </ul>
 *
 * <p>令牌计数（{@code tokensUsed/tokensBase}）与轮次（{@code rounds}）是前端
 * 本地同步的展示口径（见 stores/goal 的 syncTokens），后端只原样保存与回带，
 * 不参与计算；{@code tokenBudget} 前端未随 PUT 上送，后端仅保留既有值。</p>
 *
 * @since 4.0.0.52
 * @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionGoalService {

    /**
     * 会话设置里的节点名（{@code settings.goal}）
     */
    public static final String SETTINGS_KEY = "goal";

    /**
     * 状态：待开始
     */
    public static final String STATE_PENDING = "PENDING";

    /**
     * 状态：进行中
     */
    public static final String STATE_RUNNING = "RUNNING";

    /**
     * 状态：已暂停
     */
    public static final String STATE_PAUSED = "PAUSED";

    /**
     * 状态：已完成
     */
    public static final String STATE_COMPLETED = "COMPLETED";

    /**
     * 状态：失败
     */
    public static final String STATE_FAILED = "FAILED";

    /**
     * 状态：已取消
     */
    public static final String STATE_CANCELED = "CANCELED";

    /**
     * 合法状态集合（读取到未知值时按 RUNNING 处理）
     */
    private static final Set<String> VALID_STATES =
            Set.of(STATE_PENDING, STATE_RUNNING, STATE_PAUSED, STATE_COMPLETED, STATE_FAILED, STATE_CANCELED);

    /**
     * 允许暂停的状态
     */
    private static final Set<String> PAUSABLE = Set.of(STATE_PENDING, STATE_RUNNING);

    /**
     * 允许恢复的状态
     */
    private static final Set<String> RESUMABLE = Set.of(STATE_PAUSED);

    /**
     * 目标标题长度上限（超出截断）
     */
    private static final int TITLE_LIMIT = 200;

    /**
     * 目标说明长度上限（超出截断）
     */
    private static final int DETAIL_LIMIT = 4000;

    /**
     * 会话映射器（settings 读写）
     */
    private final SysAiSessionMapper sessionMapper;

    /**
     * 项目统一 ObjectMapper
     */
    private final ObjectMapper objectMapper;

    /**
     * 目标响应视图（对齐前端 {@code GoalDto} 字段名）。
     *
     * @param id          目标标识
     * @param sessionId   主会话标识
     * @param title       目标标题
     * @param detail      目标说明
     * @param state       状态（GoalState 词表）
     * @param tokensUsed  累计消耗令牌（前端本地同步口径，后端只保存回带）
     * @param rounds      已执行轮次
     * @param tokenBudget 令牌预算（前端未上送时保留既有值，可为空）
     * @param tokensBase  目标创建时的会话令牌基线（前端口径，可为空）
     * @param updatedAt   更新时间（epoch 毫秒）
     */
    public record GoalView(String id, String sessionId, String title, String detail, String state,
                           long tokensUsed, long rounds, Long tokenBudget, Long tokensBase, long updatedAt) {
    }

    /**
     * 读取会话目标。
     *
     * @param sessionId 主会话标识
     * @return 目标视图；会话未设目标时 {@code null}
     */
    public GoalView getGoal(Long sessionId) {
        SysAiSession session = requireSession(sessionId);
        Map<String, Object> goal = readGoal(session);
        return goal == null ? null : toView(goal, sessionId);
    }

    /**
     * 新建或覆盖会话目标（状态置 RUNNING）。
     *
     * @param sessionId 主会话标识
     * @param title     目标标题（必填）
     * @param detail    目标说明（可空）
     * @return 落库后的目标视图
     */
    public GoalView saveGoal(Long sessionId, String title, String detail) {
        if (title == null || title.isBlank()) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "目标标题不能为空");
        }
        String safeTitle = truncate(title.trim(), TITLE_LIMIT);
        String safeDetail = detail == null ? "" : truncate(detail.trim(), DETAIL_LIMIT);
        SysAiSession session = requireSession(sessionId);
        Map<String, Object> settings = readSettings(session);
        Map<String, Object> existing = readGoal(session);
        long now = System.currentTimeMillis();

        Map<String, Object> goal = new HashMap<>();
        // 标题不变 = 同一目标：保留计数与基线，避免覆盖说明时把用量清零
        boolean sameGoal = existing != null && safeTitle.equals(asString(existing.get("title")));
        if (sameGoal) {
            goal.put("id", firstNonBlank(asString(existing.get("id")), defaultGoalId(sessionId, now)));
            goal.put("tokensUsed", asLong(existing.get("tokensUsed"), 0L));
            goal.put("rounds", asLong(existing.get("rounds"), 0L));
            goal.put("tokenBudget", asLongOrNull(existing.get("tokenBudget")));
            goal.put("tokensBase", asLongOrNull(existing.get("tokensBase")));
        } else {
            goal.put("id", defaultGoalId(sessionId, now));
            goal.put("tokensUsed", 0L);
            goal.put("rounds", 0L);
            goal.put("tokenBudget", null);
            goal.put("tokensBase", null);
        }
        goal.put("title", safeTitle);
        goal.put("detail", safeDetail);
        goal.put("state", STATE_RUNNING);
        goal.put("updatedAt", now);

        settings.put(SETTINGS_KEY, goal);
        persist(session, settings);
        log.info("[SessionGoal] 目标已保存 sessionId={} goalId={} sameGoal={}", sessionId, goal.get("id"), sameGoal);
        return toView(goal, sessionId);
    }

    /**
     * 暂停目标（PENDING/RUNNING → PAUSED；已暂停幂等；终态 409）。
     *
     * @param sessionId 主会话标识
     * @return 暂停后的目标视图
     */
    public GoalView pauseGoal(Long sessionId) {
        return transition(sessionId, PAUSABLE, STATE_PAUSED, "暂停");
    }

    /**
     * 恢复目标（PAUSED → RUNNING；进行中幂等；终态 409）。
     *
     * @param sessionId 主会话标识
     * @return 恢复后的目标视图
     */
    public GoalView resumeGoal(Long sessionId) {
        return transition(sessionId, RESUMABLE, STATE_RUNNING, "恢复");
    }

    /**
     * 取消并<b>彻底销毁</b>目标（从 settings 移除，前后端都不保留）。
     *
     * @param sessionId 主会话标识
     * @param reason    取消原因（仅记日志，目标已销毁无处落库）
     * @return 被销毁目标的视图（state=CANCELED）；本就无目标时 {@code null}（幂等）
     */
    public GoalView cancelGoal(Long sessionId, String reason) {
        SysAiSession session = requireSession(sessionId);
        Map<String, Object> settings = readSettings(session);
        Map<String, Object> existing = readGoal(session);
        if (existing == null) {
            return null;
        }
        settings.remove(SETTINGS_KEY);
        persist(session, settings);
        Map<String, Object> destroyed = new HashMap<>(existing);
        destroyed.put("state", STATE_CANCELED);
        destroyed.put("updatedAt", System.currentTimeMillis());
        log.info("[SessionGoal] 目标已销毁 sessionId={} reason={}", sessionId, reason);
        return toView(destroyed, sessionId);
    }

    /**
     * 状态迁移的公共实现（幂等 + 终态拒绝）。
     *
     * @param sessionId 主会话标识
     * @param allowed   允许迁出的状态集合
     * @param target    目标状态
     * @param action    动作名（用于错误文案）
     * @return 迁移后的目标视图
     */
    private GoalView transition(Long sessionId, Set<String> allowed, String target, String action) {
        SysAiSession session = requireSession(sessionId);
        Map<String, Object> settings = readSettings(session);
        Map<String, Object> existing = readGoal(session);
        if (existing == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "会话暂无目标");
        }
        String current = normalizeState(asString(existing.get("state")));
        if (target.equals(current)) {
            // 幂等：重复暂停 / 重复恢复直接返回当前态
            return toView(existing, sessionId);
        }
        if (!allowed.contains(current)) {
            throw new ApiException(ApiErrorCode.CONFLICT, "目标当前状态不可" + action + ": " + current);
        }
        existing.put("state", target);
        existing.put("updatedAt", System.currentTimeMillis());
        settings.put(SETTINGS_KEY, existing);
        persist(session, settings);
        log.info("[SessionGoal] 目标{} sessionId={} {} -> {}", action, sessionId, current, target);
        return toView(existing, sessionId);
    }

    /**
     * 读取会话 settings.goal 节点。
     *
     * @param session 会话
     * @return 目标节点；未设置或形不符时 {@code null}
     */
    private Map<String, Object> readGoal(SysAiSession session) {
        Map<String, Object> settings = readSettings(session);
        Object node = settings.get(SETTINGS_KEY);
        if (!(node instanceof Map)) {
            return null;
        }
        Map<String, Object> goal = new HashMap<>((Map<String, Object>) node);
        // 形守卫：没有标题的节点视为损坏，按无目标处理
        String title = asString(goal.get("title"));
        if (title == null || title.isBlank()) {
            return null;
        }
        goal.put("state", normalizeState(asString(goal.get("state"))));
        return goal;
    }

    /**
     * 解析会话 settings JSON（仿 {@code SessionNextTaskService}）。
     *
     * @param session 会话
     * @return settings 映射；为空或损坏时返回空 Map
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> readSettings(SysAiSession session) {
        String json = session.getSettings();
        if (json == null || json.isBlank()) {
            return new HashMap<>();
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(json, Map.class);
            return parsed != null ? new HashMap<>(parsed) : new HashMap<>();
        } catch (Exception e) {
            log.warn("[SessionGoal] 解析会话 settings 失败: {}", e.getMessage());
            return new HashMap<>();
        }
    }

    /**
     * 持久化会话设置。
     *
     * @param session  会话
     * @param settings 会话设置
     */
    private void persist(SysAiSession session, Map<String, Object> settings) {
        try {
            session.setSettings(objectMapper.writeValueAsString(settings));
        } catch (Exception e) {
            // 序列化失败时保持原值不被清空
            log.warn("[SessionGoal] 序列化会话 settings 失败: {}", e.getMessage());
            return;
        }
        session.setUpdateTime(LocalDateTime.now());
        sessionMapper.updateById(session);
    }

    /**
     * 取会话，不存在时 404。
     *
     * @param sessionId 会话 id
     * @return 会话
     */
    private SysAiSession requireSession(Long sessionId) {
        SysAiSession session = sessionId == null ? null : sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "会话不存在");
        }
        return session;
    }

    /**
     * 节点转响应视图。
     *
     * @param goal      目标节点
     * @param sessionId 主会话标识
     * @return 响应视图
     */
    private GoalView toView(Map<String, Object> goal, Long sessionId) {
        return new GoalView(
                firstNonBlank(asString(goal.get("id")), defaultGoalId(sessionId, System.currentTimeMillis())),
                String.valueOf(sessionId),
                asString(goal.get("title")),
                firstNonBlank(asString(goal.get("detail")), ""),
                normalizeState(asString(goal.get("state"))),
                asLong(goal.get("tokensUsed"), 0L),
                asLong(goal.get("rounds"), 0L),
                asLongOrNull(goal.get("tokenBudget")),
                asLongOrNull(goal.get("tokensBase")),
                asLong(goal.get("updatedAt"), System.currentTimeMillis()));
    }

    /**
     * 归一化状态词表（未知值回落 RUNNING）。
     *
     * @param state 原始状态
     * @return 合法状态
     */
    private String normalizeState(String state) {
        return VALID_STATES.contains(state) ? state : STATE_RUNNING;
    }

    /**
     * 默认目标标识：goal-会话-毫秒（与前端 localGoal 的形一致）。
     *
     * @param sessionId 会话 id
     * @param now       当前毫秒
     * @return 目标标识
     */
    private String defaultGoalId(Long sessionId, long now) {
        return "goal-" + sessionId + "-" + now;
    }

    /**
     * 截断到上限。
     *
     * @param value 原文
     * @param limit 上限
     * @return 截断后的文本
     */
    private String truncate(String value, int limit) {
        return value.length() <= limit ? value : value.substring(0, limit);
    }

    /**
     * 空安全取字符串。
     *
     * @param value 原值
     * @return 字符串；null 时返回 null
     */
    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 取长整型（兼容 Integer/Long/Double 等数字形态）。
     *
     * @param value        原值
     * @param defaultValue 缺省
     * @return 长整型
     */
    private long asLong(Object value, long defaultValue) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return value == null ? defaultValue : Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 取可空长整型。
     *
     * @param value 原值
     * @return 长整型；无法解析时 {@code null}
     */
    private Long asLongOrNull(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 取第一个非空文本。
     *
     * @param first  首选
     * @param second 兜底
     * @return 非空文本
     */
    private String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }
}
