package com.chua.starter.ai.support.provider;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.ai.support.model.WebPerformanceAnalyzeRequest;
import com.chua.starter.ai.support.model.WebPerformanceAnalyzeResult;
import com.chua.starter.ai.support.service.WebPerformanceAnalyzeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 网站性能分析接口。
 *
 * @author CH
 * @since 2026/04/14
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/ai/performance")
public class WebPerformanceAnalyzeProvider {

    private final WebPerformanceAnalyzeService webPerformanceAnalyzeService;

    @PostMapping("/website/analyze")
    public ReturnResult<WebPerformanceAnalyzeResult> analyze(@RequestBody WebPerformanceAnalyzeRequest request) {
        try {
            return ReturnResult.ok(webPerformanceAnalyzeService.analyze(request));
        } catch (IllegalArgumentException e) {
            return ReturnResult.error(e.getMessage());
        } catch (Exception e) {
            log.error("[AI][性能分析]执行失败", e);
            return ReturnResult.error("website performance analyze failed: " + e.getMessage());
        }
    }
}
