package com.chua.starter.monitor.support.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 主机指标智能分析请求
 *
 * <p>指标快照不再由前端回传：分析所需的 CPU/内存数据由服务端从采集库取数，
 * 这里只接受分析重点与可逐项覆盖的系统提示词、模型。</p>
 *
 * @param dimension 分析重点，可为空
 * @param systemPrompt 系统提示词，可为空（为空时依次回退全局 AI 设置、内置运维专家角色）
 * @param model 指定模型，可为空（为空时用全局默认模型）
 * @author CH
 * @since 4.0.0.42
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AiHostAnalyzeDTO(
        String dimension,
        String systemPrompt,
        String model
) {
}
