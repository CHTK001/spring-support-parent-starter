package com.chua.starter.ai.support.chat;

import com.chua.common.support.ai.agent.AgentUsage;
import com.chua.common.support.ai.chat.ChatResponse;

/**
* AI 用量统计工具类。
*
* @author CH
* @since 4.0.0
 */
public abstract class ChatUsageSupport {

    /**
    * 从 对话响应 提取 Agentusage。
    *
    * @param response 对话响应
    * @return AgentUsage 实例
    */
    public static AgentUsage from(ChatResponse response) {
        if (response == null || response.getUsage() == null) {
            return AgentUsage.builder()
                    .promptTokens(0)
                    .completionTokens(0)
                    .totalTokens(0)
                    .build();
        }
        return AgentUsage.builder()
                .promptTokens(response.getUsage().getInputTokens())
                .completionTokens(response.getUsage().getOutputTokens())
                .totalTokens(response.getUsage().getTotalTokens())
                .build();
    }

    /**
    * 合并
    *
    * @param a a
    * @param b b
    * @return 合并的结果
    */
    public static AgentUsage merge(AgentUsage a, AgentUsage b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        int pt = (a.getPromptTokens() != null ? a.getPromptTokens() : 0) + (b.getPromptTokens() != null ? b.getPromptTokens() : 0);
        int ct = (a.getCompletionTokens() != null ? a.getCompletionTokens() : 0) + (b.getCompletionTokens() != null ? b.getCompletionTokens() : 0);
        int tt = (a.getTotalTokens() != null ? a.getTotalTokens() : 0) + (b.getTotalTokens() != null ? b.getTotalTokens() : 0);
        double cost = (a.getCost() != null ? a.getCost() : 0.0) + (b.getCost() != null ? b.getCost() : 0.0);
        return AgentUsage.builder()
                .promptTokens(pt)
                .completionTokens(ct)
                .totalTokens(tt)
                .cost(cost)
                .model(b.getModel() != null ? b.getModel() : a.getModel())
                .build();
    }
}
