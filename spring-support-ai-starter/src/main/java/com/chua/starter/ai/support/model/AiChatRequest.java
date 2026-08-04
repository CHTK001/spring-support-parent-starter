package com.chua.starter.ai.support.model;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class AiChatRequest {

    private String model;
    private String prompt;
    private String systemPrompt;
    private Double temperature;
    private Integer maxTokens;
    private List<Map<String, Object>> messages;
    private List<String> imageUrls;
    private Map<String, Object> parameters;
    private Boolean stream;
    private String factory;
    private String provider;
    /**
     * 深度思考开关（由前端 ChatClient 透传，供支持该能力的模型开启推理）
     */
    private Boolean think;
    /**
     * 智能搜索开关（由前端 ChatClient 透传，供支持联网搜索的模型检索外部知识）
     */
    private Boolean search;
}
