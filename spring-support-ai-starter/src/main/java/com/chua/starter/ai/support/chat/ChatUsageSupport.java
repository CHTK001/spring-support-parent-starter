package com.chua.starter.ai.support.chat;

import com.chua.common.support.ai.agent.AgentUsage;
import com.chua.common.support.ai.bigmodel.BigModelResponse;

import java.math.BigDecimal;

/**
 * 聊天 usage 适配工具。
 *
 * @author CH
 * @since 2026/04/03
 */
final class ChatUsageSupport {

    private ChatUsageSupport() {
    }

    /**
     * 将单次大模型响应转换为 AgentUsage。
     *
     * @param response 大模型响应
     * @return usage，若响应不包含 usage 则返回 null
     */
    static AgentUsage from(BigModelResponse response) {
        if (response == null) {
            return null;
        }
        if (response.getPromptTokens() == null
                && response.getCompletionTokens() == null
                && response.getTotalTokens() == null
                && response.getInputCost() == null
                && response.getOutputCost() == null
                && response.getTotalCost() == null
                && response.getCurrency() == null) {
            return null;
        }
        return AgentUsage.builder()
                .promptTokens(response.getPromptTokens())
                .completionTokens(response.getCompletionTokens())
                .totalTokens(response.getTotalTokens())
                .inputCost(response.getInputCost())
                .outputCost(response.getOutputCost())
                .totalCost(response.getTotalCost())
                .currency(response.getCurrency())
                .estimated(Boolean.TRUE.equals(response.getEstimated()))
                .build();
    }

    /**
     * 累加两次 usage。
     *
     * @param current 当前值
     * @param next    新值
     * @return 合并后的 usage
     */
    static AgentUsage merge(AgentUsage current, AgentUsage next) {
        if (current == null) {
            return next;
        }
        if (next == null) {
            return current;
        }
        return AgentUsage.builder()
                .promptTokens(sum(current.getPromptTokens(), next.getPromptTokens()))
                .completionTokens(sum(current.getCompletionTokens(), next.getCompletionTokens()))
                .totalTokens(sum(current.getTotalTokens(), next.getTotalTokens()))
                .inputCost(sum(current.getInputCost(), next.getInputCost()))
                .outputCost(sum(current.getOutputCost(), next.getOutputCost()))
                .totalCost(sum(current.getTotalCost(), next.getTotalCost()))
                .currency(current.getCurrency() == null ? next.getCurrency() : current.getCurrency())
                .estimated(current.isEstimated() || next.isEstimated())
                .build();
    }

    private static Integer sum(Integer left, Integer right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left + right;
    }

    private static BigDecimal sum(BigDecimal left, BigDecimal right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left.add(right);
    }
}
