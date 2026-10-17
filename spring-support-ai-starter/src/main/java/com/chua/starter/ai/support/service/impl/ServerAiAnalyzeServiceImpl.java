package com.chua.starter.ai.support.service.impl;

import com.chua.starter.ai.support.model.ServerAiStructuredAdvice;
import com.chua.starter.ai.support.model.ServerAlertHistoryAnalyzeRequest;
import com.chua.starter.ai.support.model.ServerHostStabilityAnalyzeRequest;
import com.chua.starter.ai.support.model.ServerMetricHistoryAnalyzeRequest;
import com.chua.starter.ai.support.model.ServerProcessAnalyzeRequest;
import com.chua.starter.ai.support.service.ServerAiAnalyzeService;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * 服务器 AI 分析服务默认实现。
 *
 * <p>当前模块没有可用的模型提供商配置时，返回明确的不可用分析结果，
 * 不伪造分析成功，也不返回空对象。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Service
public class ServerAiAnalyzeServiceImpl implements ServerAiAnalyzeService {

    /**
     * 分析主机稳定性。
     *
     * @param request 分析请求，不能为空
     * @return 当前未配置模型时返回不可用分析结果
     */
    @Override
    public ServerAiStructuredAdvice analyzeHostStability(ServerHostStabilityAnalyzeRequest request) {
        return unavailable(request);
    }

    /**
     * 分析指标历史。
     *
     * @param request 分析请求，不能为空
     * @return 当前未配置模型时返回不可用分析结果
     */
    @Override
    public ServerAiStructuredAdvice analyzeMetricHistory(ServerMetricHistoryAnalyzeRequest request) {
        return unavailable(request);
    }

    /**
     * 分析告警历史。
     *
     * @param request 分析请求，不能为空
     * @return 当前未配置模型时返回不可用分析结果
     */
    @Override
    public ServerAiStructuredAdvice analyzeAlertHistory(ServerAlertHistoryAnalyzeRequest request) {
        return unavailable(request);
    }

    /**
     * 分析进程状态。
     *
     * @param request 分析请求，不能为空
     * @return 当前未配置模型时返回不可用分析结果
     */
    @Override
    public ServerAiStructuredAdvice analyzeProcess(ServerProcessAnalyzeRequest request) {
        return unavailable(request);
    }

    /**
     * 构造模型未配置时的明确结果。
     *
     * @param request 分析请求，不能为空
     * @return 不可用分析结果
     */
    private ServerAiStructuredAdvice unavailable(Object request) {
        Objects.requireNonNull(request, "分析请求不能为空");
        return new ServerAiStructuredAdvice(
                "服务器 AI 分析服务尚未配置可用模型",
                "UNKNOWN",
                "请配置 AI 模型提供商后重试",
                "UNAVAILABLE",
                "UNCONFIGURED");
    }
}
