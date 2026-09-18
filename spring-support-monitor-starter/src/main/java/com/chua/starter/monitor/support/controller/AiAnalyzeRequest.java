package com.chua.starter.monitor.support.controller;

/**
 * 通用 AI 分析请求体。
 *
 * <p>由 {@link MonitorController#aiAnalyze} 接收。未配置 AI 模型时接口返回未启用提示，
 * 请求体字段仅作为透传给 {@code ChatClient} 的对话参数。</p>
 *
 * @param prompt 分析内容，必填，不能为 null 或空白
 * @param systemPrompt 系统提示词，可为 null（使用默认运维专家角色）
 * @param model 指定模型名，可为 null（使用客户端默认模型）
 * @since 2026/8/4
 * @author CH
 * @return 结果值
 */
public record AiAnalyzeRequest(String prompt, String systemPrompt, String model) {
}
