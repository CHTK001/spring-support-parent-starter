// [P3C A20 豁免] A20/A30/A31: 含 SQL 字符串字面量、状态词表等非业务魔法值场景，豁免。
package com.chua.starter.ai.support.service;

import com.chua.common.support.ai.agent.ConfirmationDecision;
import com.chua.common.support.ai.agent.ConfirmationItem;
import com.chua.common.support.ai.agent.ConfirmationRequest;
import com.chua.common.support.ai.agent.ToolRiskPolicy;
import com.chua.common.support.utils.IdUtils;
import com.chua.starter.ai.support.manage.common.ApiErrorCode;
import com.chua.starter.ai.support.manage.common.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.chua.common.support.utils.CollectionUtils;

/**
 * Agent 工具审批单服务（切片四：审批闭环的持久化与广播）。
 *
 * <p>职责与边界（与 {@code RuntimeApprovalGate} 的无状态裁决区分）：</p>
 * <ul>
 *   <li><b>创建</b>：编排层确认转发点（收到 CONFIRMATION_REQUEST）调用
 *       {@link #createForConfirmation}，把待确认项落库为 PENDING 审批单，
 *       并向归属用户的会话 SSE 通道广播 {@code agent_approval_required}；</li>
 *   <li><b>决议</b>：两条通道——① 用户经 {@code /v2/chat/resume} 决议时
 *       {@link #resolveByReplyId} 按 replyId + toolCallId 回写匹配的 PENDING 单；
 *       ② 审批列表页经 {@code POST /api/agents/approvals/{id}/resolve} 调
 *       {@link #resolve} 程序化决议。两者都以 CAS（仅 PENDING 可变）保证幂等，
 *       成功后广播 {@code agent_approval_resolved}（多标签页同步）；</li>
 *   <li><b>过期</b>：{@link #expireOverdue} 定时把超时未决议的单置 EXPIRED
 *       （审批时效内未决策 = 拒绝执行，fail-close 口径）。</li>
 * </ul>
 *
 * <p>审批单不等于 Run：主聊天链路的确认不经 Run 体系（runId 为空），
 * Run 体系的审批（future）再带 runId 落库。</p>
 *
 * @author CH
 * @since 4.0.0.52
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentApprovalService {

    /**
     * 审批单表名（与 {@code SysAiAgentRunSchemaMigrator} 的 DDL 同构）
     */
    private static final String TABLE = "sys_ai_agent_approval";

    /**
     * 查询列清单（与 DDL 列序一致）
     */
    private static final String COLS = "sys_ai_approval_id, sys_ai_run_id, sys_ai_step_id, sys_ai_session_id, "
            + "session_uid, sys_user_id, reply_id, tool_call_id, action, resources, risk_level, status, "
            + "expire_at, decided_by, decided_at, decision_reason, resolved_at, created_at";

    /**
     * 审批时效（毫秒）：超时未决议自动关闭。待确认项长期挂起会阻塞 Agent 轮次，
     * 默认 10 分钟；后续可按需配置化。
     */
    private static final long DEFAULT_TTL_MILLIS = 10 * 60 * 1000L;

    /**
     * 过期清扫单批上限（防止一次性刷屏广播）
     */
    private static final int EXPIRE_BATCH = 200;

    /**
     * 新建审批单
     */
    private static final String INSERT_SQL = "INSERT INTO " + TABLE + " (" + COLS + ") "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    /**
     * 按单号 CAS 决议（仅 PENDING 可变）
     */
    private static final String RESOLVE_BY_ID_SQL = "UPDATE " + TABLE + " SET status = ?, decided_by = ?, "
            + "decided_at = ?, decision_reason = ?, resolved_at = ? WHERE sys_ai_approval_id = ? "
            + "AND sys_user_id = ? AND status = 'PENDING' AND expire_at > ?";

    /**
     * 按 会话 + replyId + toolCallId CAS 回写（仅 PENDING 可变）
     */
    private static final String RESOLVE_BY_REPLY_SQL = "UPDATE " + TABLE + " SET status = ?, decided_by = ?, "
            + "decided_at = ?, resolved_at = ? WHERE sys_ai_session_id = ? AND reply_id = ? AND tool_call_id = ? "
            + "AND sys_user_id = ? AND status = 'PENDING' AND expire_at > ?";

    /**
     * 数据库访问
     */
    private final JdbcTemplate jdbcTemplate;

    /**
     * 会话事件广播器（agent_approval_required / agent_approval_resolved）
     */
    private final AiSessionEventBroadcaster eventBroadcaster;

    /**
     * 把一次确认请求的各待确认项落库为 PENDING 审批单，并广播 {@code agent_approval_required}。
     *
     * <p>调用方是流式编排的确认转发点（可能在 reactor 线程）：本方法只做落库与广播，
     * 异常由调用方兜底（不阻断 Agent 主链路）。无 toolCallId 的项跳过（无法与决策匹配）。</p>
     *
     * @param dialogId   后端会话主键
     * @param sessionUid 前端会话标识（SSE 订阅与内转 resume 用）
     * @param userId     归属用户
     * @param confirmation 确认请求
     * @return 落库成功的审批单列表
     */
    public List<AgentApprovalRecord> createForConfirmation(Long dialogId, String sessionUid, String userId,
                                                           ConfirmationRequest confirmation) {
        if (confirmation == null || confirmation.getItems() == null || confirmation.getItems().isEmpty()) {
            return List.of();
        }
        long now = System.currentTimeMillis();
        long expireAt = now + DEFAULT_TTL_MILLIS;
        List<AgentApprovalRecord> created = new ArrayList<>(confirmation.getItems().size());
        for (ConfirmationItem item : confirmation.getItems()) {
            if (item == null || item.getToolCallId() == null || item.getToolCallId().isBlank()) {
                continue;
            }
            String riskLevel = item.getRiskLevel() != null && !item.getRiskLevel().isBlank()
                    ? item.getRiskLevel()
                    : ToolRiskPolicy.classify(item.getToolName(), item.getDescription());
            AgentApprovalRecord rec = new AgentApprovalRecord(IdUtils.uuidv7(), null, null,
                    dialogId, sessionUid, userId, confirmation.getReplyId(), item.getToolCallId(),
                    item.getToolName(), item.getDescription(), riskLevel,
                    AgentApprovalRecord.STATUS_PENDING, expireAt, null, null, null, null, now);
            jdbcTemplate.update(INSERT_SQL,
                    rec.approvalId(), rec.runId(), rec.stepId(), rec.sessionId(), rec.sessionUid(),
                    rec.userId(), rec.replyId(), rec.toolCallId(), rec.action(), rec.resources(),
                    rec.riskLevel(), rec.status(), rec.expireAt(), rec.decidedBy(), rec.decidedAt(),
                    rec.decisionReason(), rec.resolvedAt(), rec.createdAt());
            created.add(rec);
            publishApprovalEvent(AiSessionEventBroadcaster.TYPE_AGENT_APPROVAL_REQUIRED, rec, null);
        }
        log.info("[AgentApproval] 审批单已落库 dialogId={} replyId={} count={}",
                dialogId, confirmation.getReplyId(), created.size());
        return created;
    }

    /**
     * 决议结果：单据现状 + 本次调用是否完成「PENDING → 终态」的 CAS 落定。
     *
     * @param record       决议后的审批单现状（幂等重入时为已决议的旧单据）
     * @param transitioned true 表示本次调用完成了状态迁移（CAS 胜出）；false 表示单据已被决议/过期，本次为幂等重入
     * @author CH
     * @since 4.0.0.52
     */
    public record ApprovalResolveResult(AgentApprovalRecord record, boolean transitioned) {
    }

    /**
     * 按单号程序化决议（审批列表入口），CAS 保证仅 PENDING 可变、重复决议幂等返回现状。
     *
     * @param approvalId 审批单号
     * @param approved   true 批准 / false 拒绝
     * @param decidedBy  决议人
     * @param reason     决议说明，可为 null
     * @return 决议结果（单据现状 + 是否本次落定）
     */
    public ApprovalResolveResult resolve(String approvalId, boolean approved, String decidedBy, String reason) {
        AgentApprovalRecord rec = find(approvalId);
        requireOwner(rec, decidedBy);
        if (!rec.isPending()) {
            // 幂等：已决议/已过期直接返回现状，未发生状态迁移
            return new ApprovalResolveResult(rec, false);
        }
        String target = approved ? AgentApprovalRecord.STATUS_APPROVED : AgentApprovalRecord.STATUS_REJECTED;
        long now = System.currentTimeMillis();
        if (rec.expireAt() <= now) {
            return new ApprovalResolveResult(expire(rec, now), false);
        }
        int updated = jdbcTemplate.update(RESOLVE_BY_ID_SQL, target, decidedBy, now, reason, now,
                approvalId, decidedBy, now);
        if (updated == 0) {
            // 并发决议被抢先，返回最新现状（未落定）
            AgentApprovalRecord latest = find(approvalId);
            if (latest == null) {
                throw new ApiException(ApiErrorCode.NOT_FOUND, "审批单不存在: " + approvalId);
            }
            if (latest.isPending() && latest.expireAt() <= System.currentTimeMillis()) {
                latest = expire(latest, System.currentTimeMillis());
            }
            return new ApprovalResolveResult(latest, false);
        }
        AgentApprovalRecord after = find(approvalId);
        Map<String, Object> extra = new LinkedHashMap<>(4);
        extra.put("decidedBy", decidedBy == null ? "" : decidedBy);
        extra.put("reason", reason == null ? "" : reason);
        publishApprovalEvent(AiSessionEventBroadcaster.TYPE_AGENT_APPROVAL_RESOLVED, after, extra);
        log.info("[AgentApproval] 审批单已决议 approvalId={} -> {} by={}", approvalId, target, decidedBy);
        return new ApprovalResolveResult(after, true);
    }

    /**
     * 用户经 {@code /v2/chat/resume} 决议时，按 replyId + toolCallId 回写匹配的 PENDING 审批单。
     *
     * <p>逐条 CAS：某个 toolCallId 没有匹配的 PENDING 单（例如审批单已过期、或本就
     * 不经审批体系）时静默跳过，不影响 resume 主链路。</p>
     *
     * @param dialogId  后端会话主键
     * @param replyId   确认请求回复 ID
     * @param decisions 用户决策列表
     * @param decidedBy 决议人
     * @return 成功回写的单据数
     */
    public int resolveByReplyId(Long dialogId, String replyId, List<ConfirmationDecision> decisions,
                                String decidedBy) {
        if (dialogId == null || replyId == null || replyId.isBlank()
                || CollectionUtils.isEmpty(decisions)) {
            return 0;
        }
        int count = 0;
        long now = System.currentTimeMillis();
        // 先核对整批，避免后面的越权/过期项导致前面的单据已经被修改。
        for (ConfirmationDecision decision : decisions) {
            if (decision == null || decision.getToolCallId() == null || decision.getToolCallId().isBlank()) continue;
            AgentApprovalRecord existing = findByReplyAndToolCall(dialogId, replyId, decision.getToolCallId());
            if (existing != null) requireResumable(existing, decidedBy,
                    decision.isConfirmed() ? AgentApprovalRecord.STATUS_APPROVED : AgentApprovalRecord.STATUS_REJECTED, now);
        }
        for (ConfirmationDecision decision : decisions) {
            if (decision == null || decision.getToolCallId() == null || decision.getToolCallId().isBlank()) {
                continue;
            }
            String target = decision.isConfirmed()
                    ? AgentApprovalRecord.STATUS_APPROVED : AgentApprovalRecord.STATUS_REJECTED;
            AgentApprovalRecord existing = findByReplyAndToolCall(dialogId, replyId, decision.getToolCallId());
            if (existing != null) {
                requireResumable(existing, decidedBy, target, now);
            }
            int updated = jdbcTemplate.update(RESOLVE_BY_REPLY_SQL, target, decidedBy, now, now,
                    dialogId, replyId, decision.getToolCallId(), decidedBy, now);
            if (updated > 0) {
                count++;
                AgentApprovalRecord rec = findByReplyAndToolCall(dialogId, replyId, decision.getToolCallId());
                if (rec != null) {
                    Map<String, Object> extra = new LinkedHashMap<>(2);
                    extra.put("decidedBy", decidedBy == null ? "" : decidedBy);
                    publishApprovalEvent(AiSessionEventBroadcaster.TYPE_AGENT_APPROVAL_RESOLVED, rec, extra);
                }
            } else if (existing != null) {
                // CAS 失败后重新核对，避免另一标签页的拒绝/过期被恢复执行覆盖。
                requireResumable(findByReplyAndToolCall(dialogId, replyId, decision.getToolCallId()),
                        decidedBy, target, System.currentTimeMillis());
            }
        }
        if (count > 0) {
            log.info("[AgentApproval] 审批单已随 resume 回写 dialogId={} replyId={} count={}",
                    dialogId, replyId, count);
        }
        return count;
    }

    /**
     * 按单号查询审批单。
     *
     * @param approvalId 审批单号
     * @return 审批单；不存在返回 null
     */
    public AgentApprovalRecord find(String approvalId) {
        if (approvalId == null || approvalId.isBlank()) {
            return null;
        }
        List<AgentApprovalRecord> list = jdbcTemplate.query(
                "SELECT " + COLS + " FROM " + TABLE + " WHERE sys_ai_approval_id = ?",
                (rs, rowNum) -> mapApproval(rs), approvalId);
        return list.isEmpty() ? null : list.get(0);
    }

    /**
     * 归属用户的审批单分页列表（按创建时间倒序）。
     *
     * @param userId   归属用户（不允许为空，调用方保证）
     * @param status   状态过滤（PENDING/APPROVED/REJECTED/EXPIRED），空为全部
     * @param page     页码（1 起）
     * @param pageSize 页大小（1~200）
     * @return { total, page, pageSize, items }
     */
    public Map<String, Object> listForUser(String userId, String status, int page, int pageSize) {
        if (userId == null || userId.isBlank()) {
            throw new ApiException(ApiErrorCode.UNAUTHORIZED, "请先登录");
        }
        int safePage = Math.max(page, 1);
        int size = pageSize < 1 ? 20 : Math.min(pageSize, 200);
        StringBuilder where = new StringBuilder(" WHERE sys_user_id = ?");
        List<Object> args = new ArrayList<>(4);
        args.add(userId);
        if (status != null && !status.isBlank()) {
            where.append(" AND status = ?");
            args.add(status);
        }
        Integer total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + TABLE + where,
                Integer.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add((long) (safePage - 1) * size);
        List<AgentApprovalRecord> items = jdbcTemplate.query(
                "SELECT " + COLS + " FROM " + TABLE + where + " ORDER BY created_at DESC LIMIT ? OFFSET ?",
                (rs, rowNum) -> mapApproval(rs), pageArgs.toArray());
        Map<String, Object> result = new LinkedHashMap<>(6);
        result.put("total", total == null ? 0L : total.longValue());
        result.put("page", safePage);
        result.put("pageSize", size);
        result.put("items", items);
        return result;
    }

    /**
     * 过期清扫：把超时未决议的审批单置 EXPIRED 并广播（fail-close 口径：
     * 时效内未决策 = 不执行）。每 30 秒一轮，单批上限 {@value #EXPIRE_BATCH} 条。
     */
    @Scheduled(fixedDelay = 30_000)
    public void expireOverdue() {
        try {
            long now = System.currentTimeMillis();
            List<AgentApprovalRecord> overdue = jdbcTemplate.query(
                    "SELECT " + COLS + " FROM " + TABLE
                            + " WHERE status = 'PENDING' AND expire_at <= ? ORDER BY created_at LIMIT "
                            + EXPIRE_BATCH,
                    (rs, rowNum) -> mapApproval(rs), now);
            for (AgentApprovalRecord rec : overdue) {
                expire(rec, now);
            }
            if (!overdue.isEmpty()) {
                log.info("[AgentApproval] 过期清扫完成 count={}", overdue.size());
            }
        } catch (Exception e) {
            log.warn("[AgentApproval] 过期清扫失败（下一轮重试）: {}", e.getMessage());
        }
    }

    /**
     * 所有决议入口先校验归属；不存在与越权使用相同响应，避免泄露单据。
     *
     * @param rec 审批单
     * @param userId 当前决议用户
     */
    private static void requireOwner(AgentApprovalRecord rec, String userId) {
        if (userId == null || userId.isBlank()) {
            throw new ApiException(ApiErrorCode.UNAUTHORIZED, "请先登录");
        }
        if (rec == null || !userId.equals(rec.userId())) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "审批单不存在");
        }
    }

    /**
     * 校验聊天恢复决策与持久化审批一致，超时和相反决策均禁止恢复。
     *
     * @param rec 审批单
     * @param userId 当前用户
     * @param target 请求的决议状态
     * @param now 当前时间
     */
    private void requireResumable(AgentApprovalRecord rec, String userId, String target, long now) {
        requireOwner(rec, userId);
        if (rec.isPending() && rec.expireAt() <= now) {
            expire(rec, now);
            throw new ApiException(ApiErrorCode.CONFLICT, "审批已过期，请重新发起请求");
        }
        if (!rec.isPending() && !target.equals(rec.status())) {
            throw new ApiException(ApiErrorCode.CONFLICT, "审批已结束，不能更改决策");
        }
    }

    /**
     * 原子关闭过期单据，只有状态迁移胜出者广播一次。
     *
     * @param rec 原单据
     * @param now 当前时间
     * @return 数据库中的最新单据
     */
    private AgentApprovalRecord expire(AgentApprovalRecord rec, long now) {
        int updated = jdbcTemplate.update("UPDATE " + TABLE + " SET status = ?, resolved_at = ? "
                        + "WHERE sys_ai_approval_id = ? AND status = 'PENDING' AND expire_at <= ?",
                AgentApprovalRecord.STATUS_EXPIRED, now, rec.approvalId(), now);
        if (updated > 0) {
            publishApprovalEvent(AiSessionEventBroadcaster.TYPE_AGENT_APPROVAL_RESOLVED,
                    withStatus(rec, AgentApprovalRecord.STATUS_EXPIRED, now), Map.of("reason", "审批超时自动关闭"));
        }
        return find(rec.approvalId());
    }

    /**
     * 按会话 + replyId + toolCallId 查询审批单（决议广播用）。
     *
     * @param dialogId   后端会话主键
     * @param replyId    确认请求回复 ID
     * @param toolCallId 工具调用 ID
     * @return 审批单；不存在返回 null
     */
    private AgentApprovalRecord findByReplyAndToolCall(Long dialogId, String replyId, String toolCallId) {
        List<AgentApprovalRecord> list = jdbcTemplate.query(
                "SELECT " + COLS + " FROM " + TABLE
                        + " WHERE sys_ai_session_id = ? AND reply_id = ? AND tool_call_id = ?",
                (rs, rowNum) -> mapApproval(rs), dialogId, replyId, toolCallId);
        return list.isEmpty() ? null : list.get(0);
    }

    /**
     * 向归属用户广播审批单事件（payload 为审批单关键字段；失败只记日志不阻断）。
     *
     * @param type 事件类型（TYPE_AGENT_APPROVAL_*）
     * @param rec  审批单
     * @param extra 附加字段（decidedBy/reason 等），可为 null
     */
    private void publishApprovalEvent(String type, AgentApprovalRecord rec, Map<String, Object> extra) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>(12);
            payload.put("approvalId", rec.approvalId());
            payload.put("replyId", rec.replyId());
            payload.put("toolCallId", rec.toolCallId());
            payload.put("action", rec.action());
            payload.put("riskLevel", rec.riskLevel());
            payload.put("resources", rec.resources());
            payload.put("status", rec.status());
            payload.put("sessionId", rec.sessionUid());
            payload.put("dialogId", rec.sessionId());
            payload.put("expireAt", rec.expireAt());
            payload.put("createdAt", rec.createdAt());
            if (extra != null) {
                payload.putAll(extra);
            }
            eventBroadcaster.publish(type, rec.userId(), rec.sessionUid(), payload);
        } catch (Exception e) {
            log.warn("[AgentApproval] 广播 {} 失败 approvalId={}: {}", type, rec.approvalId(), e.getMessage());
        }
    }

    /**
     * 重建一个改了状态/终态时间的审批单副本（广播过期事件用）。
     *
     * @param rec    原审批单
     * @param status 新状态
     * @param resolvedAt 终态时间
     * @return 新副本
     */
    private AgentApprovalRecord withStatus(AgentApprovalRecord rec, String status, long resolvedAt) {
        return new AgentApprovalRecord(rec.approvalId(), rec.runId(), rec.stepId(), rec.sessionId(),
                rec.sessionUid(), rec.userId(), rec.replyId(), rec.toolCallId(), rec.action(), rec.resources(),
                rec.riskLevel(), status, rec.expireAt(), rec.decidedBy(), rec.decidedAt(), rec.decisionReason(),
                resolvedAt, rec.createdAt());
    }

    /**
     * 结果集 → 审批单（可空 BIGINT 用 getObject 判空，避免 getLong 把 null 读成 0）。
     *
     * @param rs 结果集（游标已定位）
     * @return 审批单
     * @throws SQLException 读取失败
     */
    private AgentApprovalRecord mapApproval(ResultSet rs) throws SQLException {
        return new AgentApprovalRecord(
                rs.getString("sys_ai_approval_id"),
                rs.getString("sys_ai_run_id"),
                rs.getString("sys_ai_step_id"),
                nullableLong(rs, "sys_ai_session_id"),
                rs.getString("session_uid"),
                rs.getString("sys_user_id"),
                rs.getString("reply_id"),
                rs.getString("tool_call_id"),
                rs.getString("action"),
                rs.getString("resources"),
                rs.getString("risk_level"),
                rs.getString("status"),
                rs.getLong("expire_at"),
                rs.getString("decided_by"),
                nullableLong(rs, "decided_at"),
                rs.getString("decision_reason"),
                nullableLong(rs, "resolved_at"),
                rs.getLong("created_at"));
    }

    /**
     * 读取可空 BIGINT 列。
     *
     * @param rs  结果集
     * @param col 列名
     * @return null 或列值
     * @throws SQLException 读取失败
     */
    private static Long nullableLong(ResultSet rs, String col) throws SQLException {
        Object value = rs.getObject(col);
        return value == null ? null : rs.getLong(col);
    }
}
