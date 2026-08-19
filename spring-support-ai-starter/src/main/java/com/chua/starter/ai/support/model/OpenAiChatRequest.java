package com.chua.starter.ai.support.model;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容对话请求模型。
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
public class OpenAiChatRequest {

    /**
     * 模型名称
     */
    private String model;

    /**
     * 消息列表
     */
    private List<Map<String, String>> messages;

    /**
     * 采样温度
     */
    private Double temperature;

    /**
     * 最大生成 tokens
     */
    private Integer maxTokens;

    /**
     * 是否流式输出
     */
    private Boolean stream;

    /**
     * 响应格式（如 json_object）
     */
    private Map<String, Object> responseFormat;

    /**
     * 停止序列
     */
    private List<String> stop;

    /**
     * 核采样参数
     */
    private Double topP;

    /**
     * 候选数量
     */
    private Integer topK;
}
