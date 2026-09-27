package com.chua.starter.monitor.support.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 通用 AI 分析请求
 *
 * @param prompt       用户提示词，不能为空
 * @param systemPrompt 系统提示词，可为空
 * @param model        指定模型，为空时使用客户端默认模型
 * @author CH
 * @since 4.0.0.42
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AiAnalyzeDTO(
        String prompt,
        String systemPrompt,
        String model
) {
}
