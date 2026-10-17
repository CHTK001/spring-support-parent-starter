package com.chua.starter.ai.support.model;

/**
 * AI 分析结构化建议
 *
 * @param summary    分析摘要
 * @param riskLevel  风险等级
 * @param suggestion 处置建议
 * @param provider   提供者
 * @param model      模型名称
 * @since 2026/7/30
 * @author CH
 */
public record ServerAiStructuredAdvice(
        String summary,
        String riskLevel,
        String suggestion,
        String provider,
        String model) {
}
