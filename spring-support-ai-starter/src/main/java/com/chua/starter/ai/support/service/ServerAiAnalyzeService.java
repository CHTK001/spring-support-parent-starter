package com.chua.starter.ai.support.service;

import com.chua.starter.ai.support.model.ServerAiStructuredAdvice;
import com.chua.starter.ai.support.model.ServerAlertHistoryAnalyzeRequest;
import com.chua.starter.ai.support.model.ServerHostStabilityAnalyzeRequest;
import com.chua.starter.ai.support.model.ServerMetricHistoryAnalyzeRequest;
import com.chua.starter.ai.support.model.ServerProcessAnalyzeRequest;

/**
 * 服务器 AI 分析服务
 *
 * @since 2026/7/30
 * @author CH
 */
public interface ServerAiAnalyzeService {

    /**
     * 分析主机稳定性
     */
    ServerAiStructuredAdvice analyzeHostStability(ServerHostStabilityAnalyzeRequest request);

    /**
     * 分析指标历史趋势
     */
    ServerAiStructuredAdvice analyzeMetricHistory(ServerMetricHistoryAnalyzeRequest request);

    /**
     * 分析告警历史
     */
    ServerAiStructuredAdvice analyzeAlertHistory(ServerAlertHistoryAnalyzeRequest request);

    /**
     * 分析进程状态
     */
    ServerAiStructuredAdvice analyzeProcess(ServerProcessAnalyzeRequest request);
}
