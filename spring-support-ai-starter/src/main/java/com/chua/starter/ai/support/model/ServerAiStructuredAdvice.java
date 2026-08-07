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

    /**
     * 分析摘要
     */
    private String summary;

    /**
     * 风险等级
     */
    private String riskLevel;

    /**
     * 处置建议
     */
    private String suggestion;

    /**
     * 提供者
     */
    private String provider;

    /**
     * 模型名称
     */
    private String model;
}
