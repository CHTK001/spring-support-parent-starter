package com.chua.starter.ai.support.model;

import lombok.Data;

/**
 * OpenAI 兼容补全请求模型。
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
public class OpenAiCompletionRequest {

    /**
     * 模型名称
     */
    private String model;

    /**
     * 输入提示
     */
    private String prompt;

    /**
     * 采样温度
     */
    private Double temperature;

    /**
     * 最大生成 tokens
     */
    private Integer maxTokens;

    /**
     * 核采样参数
     */
    private Integer topP;

    /**
     * 是否流式输出
     */
    private Boolean stream;
}
