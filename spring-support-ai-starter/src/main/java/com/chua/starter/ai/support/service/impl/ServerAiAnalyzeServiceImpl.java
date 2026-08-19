package com.chua.starter.ai.support.service.impl;

import com.chua.starter.ai.support.model.ServerAiStructuredAdvice;
import com.chua.starter.ai.support.model.ServerAlertHistoryAnalyzeRequest;
import com.chua.starter.ai.support.model.ServerHostStabilityAnalyzeRequest;
import com.chua.starter.ai.support.model.ServerMetricHistoryAnalyzeRequest;
import com.chua.starter.ai.support.model.ServerProcessAnalyzeRequest;
import com.chua.starter.ai.support.service.ServerAiAnalyzeService;
import org.springframework.stereotype.Service;

/**
 * 服务器 AI 分析服务空实现
 *
 * @since 2026/7/30
 * @author CH
 */
@Service
public class ServerAiAnalyzeServiceImpl implements ServerAiAnalyzeService {

    @Override
    /** AnalyzeHostStability */
    public ServerAiStructuredAdvice analyzeHostStability(ServerHostStabilityAnalyzeRequest request) {
        return null;
    }

    @Override
    /** AnalyzeMetricHistory */
    public ServerAiStructuredAdvice analyzeMetricHistory(ServerMetricHistoryAnalyzeRequest request) {
        return null;
    }

    @Override
    /** AnalyzeAlertHistory */
    public ServerAiStructuredAdvice analyzeAlertHistory(ServerAlertHistoryAnalyzeRequest request) {
        return null;
    }

    @Override
    /** Analyze处理 */
    public ServerAiStructuredAdvice analyzeProcess(ServerProcessAnalyzeRequest request) {
        return null;
    }
}
