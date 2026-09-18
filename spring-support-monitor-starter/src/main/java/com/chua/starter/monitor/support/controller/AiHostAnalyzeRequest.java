package com.chua.starter.monitor.support.controller;

/**
 * 主机指标 AI 分析请求体。
 *
 * <p>由 {@link MonitorController#aiAnalyzeHost} 接收。请求体可为 null（不带 body 时仅按主机默认指标分析）。</p>
 *
 * @param metrics 最近指标数据（JSON 对象或列表），可为 null（null 时仅按主机静态信息分析）
 * @since 2026/8/4
 * @author CH
 * @return 结果值
 */
public record AiHostAnalyzeRequest(Object metrics) {
}
