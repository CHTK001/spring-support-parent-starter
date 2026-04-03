package com.chua.starter.ai.support.example;

import com.chua.common.support.ai.mcp.processor.McpContext;
import com.chua.common.support.ai.mcp.processor.McpPostprocessor;
import com.chua.common.support.core.annotation.Spi;

/**
 * 自定义后置处理器示例。
 */
@Spi
public class CustomMcpPostprocessor implements McpPostprocessor {

    @Override
    public int getPriority() {
        return 10;
    }

    @Override
    public String postprocess(String rawOutput, McpContext context) {
        String originalInput = (String) context.getAttribute("original_input");
        String preprocessor = (String) context.getAttribute("preprocessor");
        StringBuilder enhancedOutput = new StringBuilder();
        enhancedOutput.append(rawOutput);
        enhancedOutput.append("\n\n---\n");
        enhancedOutput.append("处理信息:\n");
        enhancedOutput.append("- 原始输入: ").append(originalInput).append("\n");
        enhancedOutput.append("- 预处理器: ").append(preprocessor).append("\n");
        enhancedOutput.append("- 会话ID: ").append(context.getSessionId()).append("\n");
        enhancedOutput.append("- 时间戳: ").append(context.getTimestamp()).append("\n");
        return enhancedOutput.toString();
    }
}
