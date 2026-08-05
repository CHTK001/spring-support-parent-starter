package com.chua.starter.spider.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.spider.entity.SpiderExecutionLog;
import com.chua.starter.spider.service.SpiderExecutionLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 执行日志查询接口。
 *
 * <p>提供节点级执行日志查询，支持按执行号（traceId）查看一次执行的完整链路，
 * 路径前缀 {@code /spider-api/spider/executions}。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Tag(name = "执行日志")
@RestController
@RequestMapping("${spider.path:/spider-api/spider}/executions")
public class SpiderExecutionLogController {

    /**
     * 执行日志服务
     */
    private final SpiderExecutionLogService executionLogService;

    /**
     * 构造方法。
     *
     * @param executionLogService 执行日志服务
     */
    public SpiderExecutionLogController(SpiderExecutionLogService executionLogService) {
        this.executionLogService = executionLogService;
    }

    /**
     * 按执行号查询全链路节点日志。
     *
     * @param executionNo 执行号（traceId）
     * @return 节点日志列表
     */
    @GetMapping("/logs")
    @Operation(summary = "按执行号查询全链路节点日志")
    public ReturnResult<List<SpiderExecutionLog>> logs(@RequestParam String executionNo) {
        return ReturnResult.ok(executionLogService.listByExecutionNo(executionNo));
    }
}