package com.chua.starter.ai.support.example;

import com.chua.common.support.ai.mcp.processor.McpContext;
import com.chua.common.support.ai.mcp.processor.McpPostprocessor;
import com.chua.common.support.core.annotation.Spi;

/**
 * 日志后置处理器示例。
 */
@Spi
public class LoggingMcpPostprocessor implements McpPostprocessor {

    @Override
    public int getPriority() {
        return 100;
    }

    @Override
    public String postprocess(String rawOutput, McpContext context) {
        System.out.println("=== MCP 处理日志 ===");
        System.out.println("会话ID: " + context.getSessionId());
        System.out.println("时间戳: " + context.getTimestamp());
        System.out.println("响应长度: " + rawOutput.length());
        System.out.println("是否过滤敏感词: " + context.getAttribute("sensitive_word_filtered"));
        System.out.println("==================");
        return rawOutput;
    }
}
