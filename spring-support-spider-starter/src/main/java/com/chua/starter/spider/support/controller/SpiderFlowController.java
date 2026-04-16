package com.chua.starter.spider.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.spider.support.domain.SpiderFlowDefinition;
import com.chua.starter.spider.support.engine.SpiderExecutionEngine;
import com.chua.starter.spider.support.engine.SpiderRuntimePushService;
import com.chua.starter.spider.support.repository.SpiderExecutionRecordRepository;
import com.chua.starter.spider.support.repository.SpiderFlowRepository;
import com.chua.starter.spider.support.repository.SpiderNodeExecutionLogRepository;
import com.chua.starter.spider.support.repository.SpiderRuntimeSnapshotRepository;
import com.chua.starter.spider.support.service.SpiderScheduledJobService;
import com.chua.starter.spider.support.validator.SpiderFlowValidationResult;
import com.chua.starter.spider.support.validator.SpiderFlowValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

/**
 * Flow 接口 + Runtime 接口。
 *
 * @author CH
 */
@RestController
@RequestMapping("/v1/spider/tasks/{taskId}")
@RequiredArgsConstructor
public class SpiderFlowController {

    private final SpiderFlowRepository flowRepository;
    private final SpiderRuntimeSnapshotRepository snapshotRepository;
    private final SpiderExecutionRecordRepository recordRepository;
    private final SpiderExecutionEngine executionEngine;
    private final SpiderScheduledJobService scheduledJobService;
    private final com.chua.starter.spider.support.repository.SpiderTaskRepository taskRepository;

    private final SpiderRuntimePushService runtimePushService;

    @Autowired(required = false)
    private SpiderNodeExecutionLogRepository nodeLogRepository;

    private final SpiderFlowValidator flowValidator = new SpiderFlowValidator();

    // ── 14.1 GET/PUT flow ────────────────────────────────────────────────────

    @GetMapping("/flow")
    public ReturnResult<?> getFlow(@PathVariable Long taskId) {
        return flowRepository.findByTaskId(taskId)
                .<ReturnResult<?>>map(ReturnResult::ok)
                .orElse(ReturnResult.illegal("任务 [" + taskId + "] 编排不存在"));
    }

    @PutMapping("/flow")
    public ReturnResult<?> updateFlow(@PathVariable Long taskId,
                                      @RequestBody SpiderFlowDefinition flow) {
        flow.setTaskId(taskId);
        flowRepository.saveFlow(flow);
        return ReturnResult.ok(Map.of("message", "编排保存成功"));
    }

    // ── 14.2 POST flow/validate ───────────────────────────────────────────────

    @PostMapping("/flow/validate")
    public ReturnResult<?> validateFlow(@PathVariable Long taskId,
                                        @RequestBody SpiderFlowDefinition flow) {
        SpiderFlowValidationResult result = flowValidator.validate(flow);
        return ReturnResult.ok(Map.of(
                "valid", result.isValid(),
                "errors", result.getErrors()
        ));
    }

    // ── 14.3 run / pause / resume ────────────────────────────────────────────

    @PostMapping("/run")
    public ReturnResult<?> run(@PathVariable Long taskId) {
        try {
            executionEngine.execute(taskId);
            return ReturnResult.ok(Map.of("message", "任务已启动"));
        } catch (IllegalArgumentException e) {
            return ReturnResult.illegal(e.getMessage());
        } catch (Exception e) {
            return ReturnResult.illegal("启动失败: " + e.getMessage());
        }
    }

    @PostMapping("/pause")
    public ReturnResult<?> pause(@PathVariable Long taskId) {
        try {
            scheduledJobService.pauseJob(taskId);
            return ReturnResult.ok(Map.of("message", "任务已暂停"));
        } catch (Exception e) {
            return ReturnResult.illegal(e.getMessage());
        }
    }

    @PostMapping("/resume")
    public ReturnResult<?> resume(@PathVariable Long taskId) {
        try {
            scheduledJobService.resumeJob(taskId);
            return ReturnResult.ok(Map.of("message", "任务已恢复"));
        } catch (Exception e) {
            return ReturnResult.illegal(e.getMessage());
        }
    }

    // ── 14.4 GET runtime ─────────────────────────────────────────────────────

    @GetMapping("/runtime")
    public ReturnResult<?> getRuntime(@PathVariable Long taskId) {
        return snapshotRepository.findByTaskId(taskId)
                .<ReturnResult<?>>map(ReturnResult::ok)
                .orElse(ReturnResult.illegal("任务 [" + taskId + "] 运行时快照不存在"));
    }

    // ── B46 GET runtime/stream ────────────────────────────────────────────────

    /**
     * GET /v1/spider/tasks/{taskId}/runtime/stream
     * SSE 实时推送节点状态变更。
     */
    @GetMapping(value = "/runtime/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamRuntime(@PathVariable Long taskId) {
        return runtimePushService.subscribe(taskId);
    }

    // ── 14.5 GET records ─────────────────────────────────────────────────────
    @GetMapping("/records")
    public ReturnResult<?> getRecords(@PathVariable Long taskId) {
        return ReturnResult.ok(recordRepository.findByTaskId(taskId));
    }

    // ── B78/B79 GET records/{recordId}/nodes ──────────────────────────────────

    /**
     * GET /v1/spider/tasks/{taskId}/records/{recordId}/nodes
     * 查询节点执行日志列表。
     */
    @GetMapping("/records/{recordId}/nodes")
    public ReturnResult<?> getNodeLogs(@PathVariable Long taskId, @PathVariable Long recordId) {
        if (nodeLogRepository == null) {
            return ReturnResult.ok(java.util.List.of());
        }
        return ReturnResult.ok(nodeLogRepository.findByRecordId(recordId));
    }

    /**
     * GET /v1/spider/tasks/{taskId}/records/{recordId}/nodes/{nodeId}
     * 查询单节点执行日志详情。
     */
    @GetMapping("/records/{recordId}/nodes/{nodeId}")
    public ReturnResult<?> getNodeLog(@PathVariable Long taskId,
                                      @PathVariable Long recordId,
                                      @PathVariable String nodeId) {
        if (nodeLogRepository == null) {
            return ReturnResult.illegal("节点日志服务不可用");
        }
        return nodeLogRepository.findByRecordIdAndNodeId(recordId, nodeId)
                .<ReturnResult<?>>map(ReturnResult::ok)
                .orElse(ReturnResult.illegal("节点 [" + nodeId + "] 日志不存在"));
    }

    // ── B70 GET crawled-urls ──────────────────────────────────────────────────
    /**
     * GET /v1/spider/tasks/{taskId}/crawled-urls
     * 查询历史爬取 URL 列表（从执行记录中汇总）。
     */
    @GetMapping("/crawled-urls")
    public ReturnResult<?> getCrawledUrls(@PathVariable Long taskId) {
        // 从执行记录中提取 URL 信息（当前实现返回执行记录摘要，B96 引入 spider_url_store 后可精确查询）
        var records = recordRepository.findByTaskId(taskId);
        var urls = records.stream()
                .map(r -> Map.of(
                        "recordId", r.getId(),
                        "startTime", r.getStartTime() != null ? r.getStartTime().toString() : "",
                        "totalRequests", r.getTotalRequests() != null ? r.getTotalRequests() : 0L
                ))
                .toList();
        return ReturnResult.ok(urls);
    }

    // ── B45 POST nodes/{nodeId}/input ─────────────────────────────────────────    /**
     * POST /v1/spider/tasks/{taskId}/nodes/{nodeId}/input
     * 提交人工介入输入，恢复被 HUMAN_INPUT 节点挂起的任务执行。
     */
    @PostMapping("/nodes/{nodeId}/input")
    public ReturnResult<?> submitHumanInput(@PathVariable Long taskId,
                                            @PathVariable String nodeId,
                                            @RequestBody HumanInputRequest request) {
        boolean resumed = executionEngine.resumeHumanInput(taskId, nodeId, request.value());
        if (resumed) {
            return ReturnResult.ok();
        }
        return ReturnResult.illegal("节点 [" + nodeId + "] 不存在或不处于等待输入状态");
    }

    /**
     * 人工介入输入请求体。
     *
     * @param value 用户输入的字符串值
     */
    record HumanInputRequest(String value) {}
}
