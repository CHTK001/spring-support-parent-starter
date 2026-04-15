package com.chua.starter.spider.support.controller;

import com.chua.starter.spider.support.domain.SpiderFlowDefinition;
import com.chua.starter.spider.support.engine.SpiderExecutionEngine;
import com.chua.starter.spider.support.repository.SpiderExecutionRecordRepository;
import com.chua.starter.spider.support.repository.SpiderFlowRepository;
import com.chua.starter.spider.support.repository.SpiderRuntimeSnapshotRepository;
import com.chua.starter.spider.support.service.SpiderScheduledJobService;
import com.chua.starter.spider.support.validator.SpiderFlowValidationResult;
import com.chua.starter.spider.support.validator.SpiderFlowValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

    private final SpiderFlowValidator flowValidator = new SpiderFlowValidator();

    // ── 14.1 GET/PUT flow ────────────────────────────────────────────────────

    @GetMapping("/flow")
    public ResponseEntity<?> getFlow(@PathVariable Long taskId) {
        return flowRepository.findByTaskId(taskId)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "任务 [" + taskId + "] 编排不存在")));
    }

    @PutMapping("/flow")
    public ResponseEntity<?> updateFlow(@PathVariable Long taskId,
                                        @RequestBody SpiderFlowDefinition flow) {
        flow.setTaskId(taskId);
        flowRepository.saveFlow(flow);
        return ResponseEntity.ok(Map.of("message", "编排保存成功"));
    }

    // ── 14.2 POST flow/validate ───────────────────────────────────────────────

    @PostMapping("/flow/validate")
    public ResponseEntity<?> validateFlow(@PathVariable Long taskId,
                                          @RequestBody SpiderFlowDefinition flow) {
        SpiderFlowValidationResult result = flowValidator.validate(flow);
        return ResponseEntity.ok(Map.of(
                "valid", result.isValid(),
                "errors", result.getErrors()
        ));
    }

    // ── 14.3 run / pause / resume ────────────────────────────────────────────

    @PostMapping("/run")
    public ResponseEntity<?> run(@PathVariable Long taskId) {
        try {
            executionEngine.execute(taskId);
            return ResponseEntity.ok(Map.of("message", "任务已启动"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "启动失败: " + e.getMessage()));
        }
    }

    @PostMapping("/pause")
    public ResponseEntity<?> pause(@PathVariable Long taskId) {
        try {
            scheduledJobService.pauseJob(taskId);
            return ResponseEntity.ok(Map.of("message", "任务已暂停"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/resume")
    public ResponseEntity<?> resume(@PathVariable Long taskId) {
        try {
            scheduledJobService.resumeJob(taskId);
            return ResponseEntity.ok(Map.of("message", "任务已恢复"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ── 14.4 GET runtime ─────────────────────────────────────────────────────

    @GetMapping("/runtime")
    public ResponseEntity<?> getRuntime(@PathVariable Long taskId) {
        return snapshotRepository.findByTaskId(taskId)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "任务 [" + taskId + "] 运行时快照不存在")));
    }

    // ── 14.5 GET records ─────────────────────────────────────────────────────

    @GetMapping("/records")
    public ResponseEntity<?> getRecords(@PathVariable Long taskId) {
        return ResponseEntity.ok(recordRepository.findByTaskId(taskId));
    }
}
