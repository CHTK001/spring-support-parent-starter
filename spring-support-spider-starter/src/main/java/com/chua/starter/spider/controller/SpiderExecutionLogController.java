package com.chua.starter.spider.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.spider.dto.DefinitionStatsVO;
import com.chua.starter.spider.dto.ExecutionRecordVO;
import com.chua.starter.spider.dto.PageResultVO;
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
 * <p>提供节点级执行日志查询、按 execution_no 聚合的执行历史分页与按爬虫维度的执行统计，
 * 路径前缀 {@code /spider-api/spider/executions}。</p>
 *
 * @since 4.0.0.42
 * @author CH
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
     * @param executionNo 执行号（追踪标识）
     * @return 节点日志列表
     */
    @GetMapping("/logs")
    @Operation(summary = "按执行号查询全链路节点日志")
    public ReturnResult<List<SpiderExecutionLog>> logs(@RequestParam String executionNo) {
        return ReturnResult.ok(executionLogService.listByExecutionNo(executionNo));
    }

    /**
     * 执行历史分页（按 execution_no 聚合）。
     *
     * @param pageNo        页码（默认 1）
     * @param pageSize      每页条数（默认 10）
     * @param definitionCode 爬虫编码（可选）
     * @param status        执行状态（可选）
     * @return 分页结果
     */
    @GetMapping("/records")
    @Operation(summary = "执行历史分页查询")
    public ReturnResult<PageResultVO<ExecutionRecordVO>> records(
            @RequestParam(defaultValue = "1") long pageNo,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) String definitionCode,
            @RequestParam(required = false) String status) {
        return ReturnResult.ok(executionLogService.pageExecutionRecords(pageNo, pageSize, definitionCode, status));
    }

    /**
     * 按爬虫编码聚合执行统计。
     *
     * @return 统计列表
     */
    @GetMapping("/stats")
    @Operation(summary = "执行统计聚合")
    public ReturnResult<List<DefinitionStatsVO>> stats() {
        return ReturnResult.ok(executionLogService.aggregateStats());
    }
}
