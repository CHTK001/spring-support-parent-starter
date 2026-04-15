package com.chua.starter.spider.support.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.starter.spider.support.domain.SpiderExecutionRecord;
import com.chua.starter.spider.support.domain.SpiderJobBinding;
import com.chua.starter.spider.support.domain.SpiderRuntimeSnapshot;
import com.chua.starter.spider.support.domain.SpiderTaskDefinition;
import com.chua.starter.spider.support.domain.enums.SpiderTaskStatus;
import com.chua.starter.spider.support.repository.SpiderExecutionRecordRepository;
import com.chua.starter.spider.support.repository.SpiderJobBindingRepository;
import com.chua.starter.spider.support.repository.SpiderRuntimeSnapshotRepository;
import com.chua.starter.spider.support.repository.SpiderTaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Dashboard 接口。
 *
 * @author CH
 */
@RestController
@RequestMapping("/v1/spider/dashboard")
@RequiredArgsConstructor
public class SpiderDashboardController {

    private final SpiderTaskRepository taskRepository;
    private final SpiderJobBindingRepository jobBindingRepository;
    private final SpiderExecutionRecordRepository executionRecordRepository;
    private final SpiderRuntimeSnapshotRepository snapshotRepository;

    /**
     * GET /v1/spider/dashboard/summary
     * 返回 KPI 汇总：定时器数量、最新执行任务、任务总数。
     */
    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> summary() {
        // 定时器数量 = 有效 job 绑定数
        long timerCount = jobBindingRepository.count(
                new LambdaQueryWrapper<SpiderJobBinding>().eq(SpiderJobBinding::getActive, true));

        // 任务总数（不含 DRAFT）
        long taskTotal = taskRepository.count(
                new LambdaQueryWrapper<SpiderTaskDefinition>()
                        .ne(SpiderTaskDefinition::getStatus, SpiderTaskStatus.DRAFT));

        // 最新执行任务
        SpiderExecutionRecord latest = executionRecordRepository.findLatestByTaskId(null);
        Map<String, Object> latestTask = null;
        if (latest != null) {
            SpiderTaskDefinition task = taskRepository.getById(latest.getTaskId());
            latestTask = Map.of(
                    "taskId", latest.getTaskId(),
                    "taskName", task != null ? task.getTaskName() : "",
                    "status", task != null && task.getStatus() != null ? task.getStatus().name() : "",
                    "startTime", latest.getStartTime() != null ? latest.getStartTime().toString() : ""
            );
        }

        return ResponseEntity.ok(Map.of(
                "timerCount", timerCount,
                "taskTotal", taskTotal,
                "latestTask", latestTask != null ? latestTask : Map.of()
        ));
    }

    /**
     * GET /v1/spider/dashboard/running-cards
     * 返回运行中或需要关注的任务卡片列表。
     */
    @GetMapping("/running-cards")
    public ResponseEntity<List<Map<String, Object>>> runningCards() {
        // 查询 RUNNING / FAILED / PAUSED 状态的任务
        List<SpiderTaskDefinition> tasks = taskRepository.list(
                new LambdaQueryWrapper<SpiderTaskDefinition>()
                        .in(SpiderTaskDefinition::getStatus,
                                SpiderTaskStatus.RUNNING, SpiderTaskStatus.FAILED, SpiderTaskStatus.PAUSED));

        List<Map<String, Object>> cards = new ArrayList<>();
        for (SpiderTaskDefinition task : tasks) {
            SpiderRuntimeSnapshot snapshot = snapshotRepository.findByTaskId(task.getId()).orElse(null);
            boolean jobBound = snapshot != null && Boolean.TRUE.equals(snapshot.getJobBound());

            cards.add(Map.of(
                    "taskId", task.getId(),
                    "taskName", task.getTaskName() != null ? task.getTaskName() : "",
                    "status", task.getStatus().name(),
                    "executionType", task.getExecutionType() != null ? task.getExecutionType().name() : "",
                    "lastExecuteTime", snapshot != null && snapshot.getLastExecuteTime() != null
                            ? snapshot.getLastExecuteTime().toString() : "",
                    "jobBound", jobBound,
                    "aiEnabled", isAiEnabled(task),
                    "successCount", snapshot != null && snapshot.getSuccessCount() != null
                            ? snapshot.getSuccessCount() : 0L,
                    "failureCount", snapshot != null && snapshot.getFailureCount() != null
                            ? snapshot.getFailureCount() : 0L
            ));
        }
        return ResponseEntity.ok(cards);
    }

    private boolean isAiEnabled(SpiderTaskDefinition task) {
        if (task.getAiProfile() == null || task.getAiProfile().isBlank()) return false;
        return task.getAiProfile().contains("\"enabled\":true");
    }
}
