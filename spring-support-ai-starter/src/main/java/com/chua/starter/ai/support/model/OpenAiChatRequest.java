package com.chua.starter.ai.support.model;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class OpenAiChatRequest {

    private String model;
    private List<Map<String, String>> messages;
    private Double temperature;
    private Integer maxTokens;
    private Boolean stream;
    private Map<String, Object> responseFormat;
    private List<String> stop;
    private Double topP;
    private Integer topK;
}
