package com.chua.starter.ai.support.service;

import com.chua.starter.ai.support.model.WebPerformanceAnalyzeRequest;
import com.chua.starter.ai.support.model.WebPerformanceAnalyzeResult;

/**
 * 网站性能分析服务。
 *
 * @author CH
 * @since 2026/04/14
 */
public interface WebPerformanceAnalyzeService {

    /**
     * 执行网站性能分析。
     *
     * @param request 分析请求
     * @return 分析结果
     */
    WebPerformanceAnalyzeResult analyze(WebPerformanceAnalyzeRequest request);
}
