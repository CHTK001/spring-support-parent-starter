package com.chua.starter.ai.support.mcp;

import com.chua.deeplearning.support.ml.bigmodel.mcp.model.ChatContext;

/**
 * MCP后置处理器接口（Spring层适配）
 * <p>
 * 用于在LLM处理后对输出进行后处理，如结果格式化、工具调用结果合并等
 * <p>
 * 此接口适配Spring层的ChatContext，内部会转换为通用ChatContext后调用底层处理器
 *
 * @author CH
 * @since 2024-01-01
 */
public interface McpPostprocessor extends com.chua.deeplearning.support.ml.bigmodel.mcp.McpPostprocessor {

    /**
     * 后处理LLM输出（Spring层）
     *
     * @param rawOutput 原始输出
     * @param context   Spring层上下文信息
     * @return 处理后的输出
     */
    default String postprocess(String rawOutput, com.chua.starter.ai.support.chat.ChatContext context) {
        ChatContext deepLearningContext = ChatContextAdapter.toDeepLearningContext(context);
        return postprocess(rawOutput, deepLearningContext);
    }
}

