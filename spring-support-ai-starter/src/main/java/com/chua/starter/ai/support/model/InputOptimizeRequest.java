package com.chua.starter.ai.support.model;

import lombok.Data;

/**
* 输入优化请求模型。
*
* @since 4.0.0.42
* @author CH
 */
@Data
public class InputOptimizeRequest {

    /**
    * 待优化的原始输入
     */
    private String input;

    /**
    * 指定优化模型（可选，空=使用 输入 分组 优化器_模型，仍为空则用全局默认对话模型）
     */
    private String model;

    /**
    * 指定优化系统提示词（可选，空=使用 输入 分组 优化器_提示符，仍为空则用内置默认提示词）
     */
    private String systemPrompt;
}
