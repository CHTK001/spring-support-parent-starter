package com.chua.starter.ai.support.example;

import com.chua.common.support.ai.mcp.processor.McpContext;
import com.chua.common.support.ai.mcp.processor.McpPreprocessor;
import com.chua.common.support.core.annotation.Spi;

/**
 * 自定义前置处理器示例。
 */
@Spi
public class CustomMcpPreprocessor implements McpPreprocessor {

    @Override
    public int getPriority() {
        return 10;
    }

    @Override
    public String preprocess(String rawInput, McpContext context) {
        String systemPrompt = "你是一个专业的AI助手，请用简洁专业的语言回答问题。\n\n";
        context.setAttribute("original_input", rawInput);
        context.setAttribute("preprocessor", "CustomMcpPreprocessor");
        return systemPrompt + rawInput;
    }
}
