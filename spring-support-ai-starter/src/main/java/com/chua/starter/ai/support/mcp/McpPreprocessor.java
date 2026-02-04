package com.chua.starter.ai.support.mcp;

import com.chua.deeplearning.support.ml.mcp.model.ChatContext;

/**
 * MCP前置处理器接口（Spring层适配）
 * <p>
 * 用于在LLM处理前对用户输入进行预处理，如文本清理、意图识别等
 * <p>
 * 此接口适配Spring层的ChatContext，内部会转换为通用ChatContext后调用底层处理器
 *
 * @author CH
 * @since 2024-01-01
 */
public interface McpPreprocessor extends com.chua.deeplearning.support.ml.mcp.McpPreprocessor {

    /**
     * 预处理用户输入（Spring层）
     *
     * @param rawInput 原始输入
     * @param context  Spring层上下文信息
     * @return 处理后的输入
     */
    default String preprocess(String rawInput, com.chua.starter.ai.support.chat.ChatContext context) {
        ChatContext deepLearningContext = ChatContextAdapter.toDeepLearningContext(context);
        return preprocess(rawInput, deepLearningContext);
    }
}

