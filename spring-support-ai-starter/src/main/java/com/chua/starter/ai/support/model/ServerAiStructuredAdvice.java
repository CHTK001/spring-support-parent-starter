package com.chua.starter.ai.support.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 分析结构化建议
 *
 * @author CH
 * @since 2026/7/30
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServerAiStructuredAdvice {

    private String summary;
    private String riskLevel;
    private String suggestion;
    private String provider;
    private String model;
}
