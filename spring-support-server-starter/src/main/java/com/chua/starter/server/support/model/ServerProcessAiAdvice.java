package com.chua.starter.server.support.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServerProcessAiAdvice {

    private Long pid;
    private String summary;
    private String riskLevel;
    private String suggestion;
    private String provider;
    private String model;
}
