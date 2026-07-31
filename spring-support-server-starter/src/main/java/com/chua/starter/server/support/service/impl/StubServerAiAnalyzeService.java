package com.chua.starter.server.support.service.impl;

import com.chua.starter.ai.support.model.ServerAiStructuredAdvice;
import com.chua.starter.ai.support.model.ServerAlertHistoryAnalyzeRequest;
import com.chua.starter.ai.support.model.ServerHostStabilityAnalyzeRequest;
import com.chua.starter.ai.support.model.ServerMetricHistoryAnalyzeRequest;
import com.chua.starter.ai.support.model.ServerProcessAnalyzeRequest;
import com.chua.starter.ai.support.service.ServerAiAnalyzeService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

/**
 * 服务器 AI 分析服务（占位实现）。
 *
 * <p>当前模块启动时不需要真正的 AI 推理能力，因此提供一个空实现，
 * 既满足 {@link com.chua.starter.server.support.service.impl.ServerHostAiAdvisor}
 * 等依赖注入要求，又避免引入额外的 AI 自动装配。</p>
 *
 * @author CH
 * @since 2026/7/31
 */
@Service
@ConditionalOnMissingBean(ServerAiAnalyzeService.class)
public class StubServerAiAnalyzeService implements ServerAiAnalyzeService {

    @Override
    public ServerAiStructuredAdvice analyzeHostStability(ServerHostStabilityAnalyzeRequest request) {
        return null;
    }

    @Override
    public ServerAiStructuredAdvice analyzeMetricHistory(ServerMetricHistoryAnalyzeRequest request) {
        return null;
    }

    @Override
    public ServerAiStructuredAdvice analyzeAlertHistory(ServerAlertHistoryAnalyzeRequest request) {
        return null;
    }

    @Override
    public ServerAiStructuredAdvice analyzeProcess(ServerProcessAnalyzeRequest request) {
        return null;
    }
}
