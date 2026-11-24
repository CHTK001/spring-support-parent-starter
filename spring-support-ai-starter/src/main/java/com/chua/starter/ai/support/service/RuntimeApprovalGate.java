// [P3C A20 豁免] A20/A30/A31: 含日志/风险等级词表等字符串字面量场景，豁免。
package com.chua.starter.ai.support.service;

import com.chua.common.support.ai.agent.AgentApprovalGate;
import com.chua.common.support.ai.agent.AgentRiskDecision;
import com.chua.common.support.ai.agent.AgentRiskRequest;
import com.chua.common.support.ai.agent.ToolRiskPolicy;
import com.chua.starter.ai.support.util.AiUserContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 运行时人工审批门（{@link AgentApprovalGate} 的 ai-starter 实现）。
 *
 * <p><b>无状态裁决</b>：只回答「这个工具调用要不要转人工审批」，不落库、不广播。
 * 审批单持久化、SSE 通知与决议回写由编排层在确认转发点调用
 * {@link AgentApprovalService} 完成——裁决器运行在流式 reactor 线程，
 * 拿不到完整会话上下文（userId / replyId / toolCallId），不能承担持久化职责。</p>
 *
 * <p>裁决口径（fail-close）：</p>
 * <ul>
 *   <li>低/中风险 → {@code ALLOW}（审批闭环只收口高危操作）；</li>
 *   <li>高/危险级 → {@code CONFIRM_REQUIRED}（引擎侧暂停本轮、发确认卡）；</li>
 *   <li>shell 类高危工具尽力收紧：非管理员 {@code DENY}；取不到用户上下文时
 *       （reactor 线程 {@code AiUserContext} 无请求信息）不静默放行、
 *       也不误判为非管理员，回落 {@code CONFIRM_REQUIRED} 交人工把关；</li>
 *   <li>裁决过程抛异常 → {@code CONFIRM_REQUIRED}（宁可多问人，不可放过）。</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.52
 */
@Slf4j
@Component
public class RuntimeApprovalGate implements AgentApprovalGate {

    /**
     * 裁决一次工具调用是否转人工审批。
     *
     * @param request 裁决请求（工具名 + 入参 + 预分级），可为 null（按需确认兜底）
     * @return 裁决结论，永不为 null
     */
    @Override
    public AgentRiskDecision adjudicate(AgentRiskRequest request) {
        try {
            if (request == null) {
                return AgentRiskDecision.confirmRequired("裁决请求为空，转人工确认");
            }
            String riskLevel = request.effectiveRiskLevel();
            String toolName = request.toolName() == null ? "" : request.toolName();
            boolean highOrDangerous = ToolRiskPolicy.isDangerous(riskLevel)
                    || ToolRiskPolicy.RISK_HIGH.equalsIgnoreCase(riskLevel);
            if (!highOrDangerous) {
                // 低/中风险放行：审批闭环只收口高危操作
                return AgentRiskDecision.allow("风险等级 " + riskLevel + "，无需人工审批");
            }
            if (ToolRiskPolicy.isShellAction(toolName)) {
                String userId = AiUserContext.currentUserId();
                if (userId == null || userId.isBlank()) {
                    // reactor 线程无请求上下文：isAdmin() 恒 false 不可作为判据，
                    // 不静默放行，转人工确认兜底
                    log.info("[AgentApprovalGate] shell 高危操作无用户上下文，转人工确认 tool={}", toolName);
                    return AgentRiskDecision.confirmRequired("shell 类高危操作等待人工审批");
                }
                if (!AiUserContext.isAdmin()) {
                    log.info("[AgentApprovalGate] 非管理员 shell 高危操作已拒绝 tool={} user={}", toolName, userId);
                    return AgentRiskDecision.deny("shell 类高危操作仅管理员可执行");
                }
            }
            log.info("[AgentApprovalGate] 高危操作转人工审批 tool={} risk={}", toolName, riskLevel);
            return AgentRiskDecision.confirmRequired("高风险操作等待人工审批");
        } catch (Exception e) {
            log.warn("[AgentApprovalGate] 裁决异常，fail-close 转人工确认: {}", e.getMessage());
            return AgentRiskDecision.confirmRequired("风险裁决异常，转人工确认");
        }
    }
}
