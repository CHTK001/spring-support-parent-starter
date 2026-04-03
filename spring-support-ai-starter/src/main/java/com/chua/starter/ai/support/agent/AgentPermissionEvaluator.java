package com.chua.starter.ai.support.agent;

import com.chua.common.support.ai.config.AgentPermissionProperties;
import com.chua.common.support.ai.policy.AgentPolicyDecision;

import java.util.List;

/**
 * Agent 权限评估器。
 *
 * @author CH
 * @since 2026/04/03
 */
public class AgentPermissionEvaluator {

    private final AgentPermissionProperties permissions;

    /**
     * 创建权限评估器。
     *
     * @param permissions 权限配置
     */
    public AgentPermissionEvaluator(AgentPermissionProperties permissions) {
        this.permissions = permissions == null ? new AgentPermissionProperties() : permissions;
    }

    /**
     * 校验动作权限。
     *
     * @param actionType 动作类型
     * @return 权限决策
     */
    public AgentPolicyDecision evaluateAction(String actionType) {
        if (!permissions.isEnabled()) {
            return AgentPolicyDecision.allow();
        }
        return evaluate(actionType,
                permissions.getAllowedActionTypes(),
                permissions.getBlockedActionTypes(),
                permissions.getApprovalActionTypes(),
                "action");
    }

    /**
     * 校验本次执行是否允许启用 MCP。
     *
     * @param enabled 本次执行是否启用 MCP
     * @return 权限决策
     */
    public AgentPolicyDecision evaluateMcp(boolean enabled) {
        if (!enabled || !permissions.isEnabled()) {
            return AgentPolicyDecision.allow();
        }
        if (containsAny(permissions.getBlockedMcps())) {
            return AgentPolicyDecision.deny("当前 Agent 权限阻止 MCP 执行");
        }
        if (containsAny(permissions.getApprovalMcps())) {
            return AgentPolicyDecision.requireApproval("当前 Agent 执行 MCP 需要审批");
        }
        if (!permissions.getAllowedMcps().isEmpty()) {
            return AgentPolicyDecision.allow();
        }
        return permissions.isDefaultAllow() ? AgentPolicyDecision.allow() : AgentPolicyDecision.deny("当前 Agent 默认不允许 MCP");
    }

    /**
     * 统一计算 allow / block / approval 三种权限列表的最终决策。
     *
     * @param candidate 当前待评估对象
     * @param allowed   白名单
     * @param blocked   黑名单
     * @param approval  审批列表
     * @param category  类别名称
     * @return 决策结果
     */
    private AgentPolicyDecision evaluate(String candidate,
                                         List<String> allowed,
                                         List<String> blocked,
                                         List<String> approval,
                                         String category) {
        if (matches(blocked, candidate)) {
            return AgentPolicyDecision.deny("当前 Agent 权限阻止 " + category + ": " + candidate);
        }
        if (matches(approval, candidate)) {
            return AgentPolicyDecision.requireApproval("当前 Agent 执行 " + category + " 需要审批: " + candidate);
        }
        if (!allowed.isEmpty() && !matches(allowed, candidate)) {
            return AgentPolicyDecision.deny("当前 Agent 未放行 " + category + ": " + candidate);
        }
        if (!permissions.isDefaultAllow() && allowed.isEmpty()) {
            return AgentPolicyDecision.deny("当前 Agent 默认拒绝 " + category + ": " + candidate);
        }
        return AgentPolicyDecision.allow();
    }

    /**
     * 判断权限列表是否配置了任意值。
     *
     * @param values 权限列表
     * @return 是否存在任意值
     */
    private boolean containsAny(List<String> values) {
        return values != null && !values.isEmpty();
    }

    /**
     * 判断候选值是否命中权限列表。
     *
     * 支持 `*` 通配整个类别。
     *
     * @param values     权限列表
     * @param candidate  候选值
     * @return 是否命中
     */
    private boolean matches(List<String> values, String candidate) {
        if (values == null || values.isEmpty()) {
            return false;
        }
        return values.stream().anyMatch(value ->
                "*".equals(value) || (candidate != null && candidate.equalsIgnoreCase(value)));
    }
}
