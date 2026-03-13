package com.chua.starter.ai.support.example;

import com.chua.common.support.core.annotation.Spi;
import com.chua.deeplearning.support.mcp.McpContext;
import com.chua.deeplearning.support.mcp.McpPostprocessor;
import com.chua.deeplearning.support.mcp.McpPreprocessor;

/**
 * MCP 处理器自定义示例
 * 
 * @author CH
 * @since 2024-01-01
 */
public class McpProcessorExample {

    /**
     * 自定义前置处理器示例
     * 
     * 在 LLM 请求前对输入进行预处理
     */
    @Spi
    public static class CustomPreprocessor implements McpPreprocessor {

        @Override
        public int getPriority() {
            // 优先级，数字越小优先级越高
            return 10;
        }

        @Override
        public String preprocess(String rawInput, McpContext context) {
            // 示例：添加系统提示词
            String systemPrompt = "你是一个专业的AI助手，请用简洁专业的语言回答问题。\n\n";
            
            // 记录原始输入到上下文
            context.setAttribute("original_input", rawInput);
            context.setAttribute("preprocessor", "CustomPreprocessor");
            
            // 返回处理后的输入
            return systemPrompt + rawInput;
        }
    }

    /**
     * 自定义后置处理器示例
     * 
     * 在 LLM 响应后对输出进行后处理
     */
    @Spi
    public static class CustomPostprocessor implements McpPostprocessor {

        @Override
        public int getPriority() {
            // 优先级，数字越小优先级越高
            return 10;
        }

        @Override
        public String postprocess(String rawOutput, McpContext context) {
            // 示例：添加响应元数据
            String originalInput = (String) context.getAttribute("original_input");
            String preprocessor = (String) context.getAttribute("preprocessor");
            
            // 构建增强响应
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

    /**
     * 敏感词过滤前置处理器示例
     */
    @Spi
    public static class SensitiveWordFilterPreprocessor implements McpPreprocessor {

        private static final String[] SENSITIVE_WORDS = {"敏感词1", "敏感词2", "敏感词3"};

        @Override
        public int getPriority() {
            return 5; // 高优先级，优先执行
        }

        @Override
        public String preprocess(String rawInput, McpContext context) {
            String filteredInput = rawInput;
            
            // 过滤敏感词
            for (String word : SENSITIVE_WORDS) {
                filteredInput = filteredInput.replace(word, "***");
            }
            
            // 记录是否进行了过滤
            if (!filteredInput.equals(rawInput)) {
                context.setAttribute("sensitive_word_filtered", true);
            }
            
            return filteredInput;
        }
    }

    /**
     * 日志记录后置处理器示例
     */
    @Spi
    public static class LoggingPostprocessor implements McpPostprocessor {

        @Override
        public int getPriority() {
            return 100; // 低优先级，最后执行
        }

        @Override
        public String postprocess(String rawOutput, McpContext context) {
            // 记录日志
            System.out.println("=== MCP 处理日志 ===");
            System.out.println("会话ID: " + context.getSessionId());
            System.out.println("时间戳: " + context.getTimestamp());
            System.out.println("响应长度: " + rawOutput.length());
            System.out.println("是否过滤敏感词: " + context.getAttribute("sensitive_word_filtered"));
            System.out.println("==================");
            
            // 不修改输出，直接返回
            return rawOutput;
        }
    }
}
