package com.chua.starter.monitor.support.model;

import com.chua.common.support.ai.AiUsage;

/**
 * 通用 AI 分析结果
 *
 * @param content 模型输出文本
 * @param model   实际使用的模型名
 * @param usage   Token 用量，模型未返回时省略
 * @author CH
 * @since 4.0.0.42
 */
public record AiAnalyzeVO(
        String content,
        String model,
        AiUsage usage
) {
}
