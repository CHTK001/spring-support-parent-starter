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
}
