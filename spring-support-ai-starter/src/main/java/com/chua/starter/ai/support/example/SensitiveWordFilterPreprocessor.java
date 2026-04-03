package com.chua.starter.ai.support.example;

import com.chua.common.support.ai.mcp.processor.McpContext;
import com.chua.common.support.ai.mcp.processor.McpPreprocessor;
import com.chua.common.support.core.annotation.Spi;

/**
 * 敏感词过滤前置处理器示例。
 */
@Spi
public class SensitiveWordFilterPreprocessor implements McpPreprocessor {

    private static final String[] SENSITIVE_WORDS = {"敏感词1", "敏感词2", "敏感词3"};

    @Override
    public int getPriority() {
        return 5;
    }

    @Override
    public String preprocess(String rawInput, McpContext context) {
        String filteredInput = rawInput;
        for (String word : SENSITIVE_WORDS) {
            filteredInput = filteredInput.replace(word, "***");
        }
        if (!filteredInput.equals(rawInput)) {
            context.setAttribute("sensitive_word_filtered", true);
        }
        return filteredInput;
    }
}
