package com.chua.starter.ai.support.model;

import lombok.Data;

/**
 * 输入优化请求模型。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
public class InputOptimizeRequest {

    /**
     * 待优化的原始输入
     */
    private String input;

    /**
     * 指定优化模型（可选，空=使用 input 分组 optimizer_model，仍为空则用全局默认对话模型）
     */
    private String model;

    /**
     * 指定优化系统提示词（可选，空=使用 input 分组 optimizer_prompt，仍为空则用内置默认提示词）
     */
    private String systemPrompt;
}
