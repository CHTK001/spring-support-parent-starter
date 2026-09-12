package com.chua.starter.ai.support.model;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
* 打开AI 兼容对话请求模型。
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
    * 最大生成 令牌
     */
    private Integer maxTokens;

    /**
    * 是否流式输出
     */
    private Boolean stream;

    /**
    * 响应格式（如 json_对象）
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
