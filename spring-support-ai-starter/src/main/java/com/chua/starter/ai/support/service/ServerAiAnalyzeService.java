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
    * @param request 请求
    * @return 分析主机stability的结果
     */
    ServerAiStructuredAdvice analyzeHostStability(ServerHostStabilityAnalyzeRequest request);

    /**
    * 分析指标历史趋势
    * @param request 请求
    * @return 分析指标历史的结果
     */
    ServerAiStructuredAdvice analyzeMetricHistory(ServerMetricHistoryAnalyzeRequest request);

    /**
    * 分析告警历史
    * @param request 请求
    * @return 分析alert历史的结果
     */
    ServerAiStructuredAdvice analyzeAlertHistory(ServerAlertHistoryAnalyzeRequest request);

    /**
    * 分析进程状态
    * @param request 请求
    * @return 分析处理的结果
     */
    ServerAiStructuredAdvice analyzeProcess(ServerProcessAnalyzeRequest request);
}
