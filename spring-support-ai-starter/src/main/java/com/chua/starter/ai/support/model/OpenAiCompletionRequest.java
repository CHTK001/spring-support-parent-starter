package com.chua.starter.ai.support.model;

import lombok.Data;

@Data
public class OpenAiCompletionRequest {

    private String model;
    private String prompt;
    private Double temperature;
    private Integer maxTokens;
    private Integer topP;
    private Boolean stream;
}
