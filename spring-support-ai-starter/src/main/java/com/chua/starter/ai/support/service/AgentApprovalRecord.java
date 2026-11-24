// [P3C A20 豁免] A20/A30/A31: 含状态/风险等级词表等字符串常量场景，豁免。
package com.chua.starter.ai.support.service;

/**
 * Agent 工具审批单（表 {@code sys_ai_agent_approval} 的行视图）。
 *
 * <p>一次「高风险工具调用待人工确认」的持久化单据：编排层在确认转发点创建
 * （{@code PENDING}），用户决议（批准 / 拒绝）或超时后进入终态。字段与
 * {@code SysAiAgentRunSchemaMigrator} 的审批表 DDL 一一对应。</p>
 *
 * <p>状态词表：{@code PENDING} 待决议 / {@code APPROVED} 已批准 /
 * {@code REJECTED} 已拒绝 / {@code EXPIRED} 已过期。</p>
 *
 * @param approvalId     审批单标识（UUID）
 * @param runId          关联运行标识；主聊天链路的确认不经 Run 体系，为 {@code null}
 * @param stepId         关联步骤标识，可为 {@code null}
 * @param sessionId      关联会话主键（resume 回写与列表过滤用），可为 {@code null}
 * @param sessionUid     前端会话标识（SSE 订阅与内转 resume 用），可为 {@code null}
 * @param userId         归属用户（列表按本人过滤），可为 {@code null}
 * @param replyId        确认请求回复 ID（与前端确认卡、{@code /v2/chat/resume} 关联），可为 {@code null}
 * @param toolCallId     工具调用 ID（决策匹配），可为 {@code null}
 * @param action         工具动作名
 * @param resources      资源清单原文（命令行、路径等），可为 {@code null}
 * @param riskLevel      风险分级（{@link com.chua.common.support.ai.agent.ToolRiskPolicy} 词表）
 * @param status         审批状态（PENDING / APPROVED / REJECTED / EXPIRED）
 * @param expireAt       过期时间（epoch 毫秒）
 * @param decidedBy      决议人，可为 {@code null}
 * @param decidedAt      决议时间（epoch 毫秒），可为 {@code null}
 * @param decisionReason 决议说明（拒绝原因等），可为 {@code null}
 * @param resolvedAt     终态时间（epoch 毫秒；过期场景无 decidedAt，以此为准），可为 {@code null}
 * @param createdAt      创建时间（epoch 毫秒）
 * @author CH
 * @since 4.0.0.52
 */
public record AgentApprovalRecord(
        String approvalId,
        String runId,
        String stepId,
        Long sessionId,
        String sessionUid,
        String userId,
        String replyId,
        String toolCallId,
        String action,
        String resources,
        String riskLevel,
        String status,
        long expireAt,
        String decidedBy,
        Long decidedAt,
        String decisionReason,
        Long resolvedAt,
        long createdAt) {

    /**
     * 状态：待决议
     */
    public static final String STATUS_PENDING = "PENDING";

    /**
     * 状态：已批准
     */
    public static final String STATUS_APPROVED = "APPROVED";

    /**
     * 状态：已拒绝
     */
    public static final String STATUS_REJECTED = "REJECTED";

    /**
     * 状态：已过期
     */
    public static final String STATUS_EXPIRED = "EXPIRED";

    /**
     * 是否待决议。
     *
     * @return PENDING 返回 true
     */
    public boolean isPending() {
        return STATUS_PENDING.equals(status);
    }
}
