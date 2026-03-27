package com.chua.starter.sync.data.demo.controller;

import com.chua.starter.sync.data.support.service.sync.MonitorSyncTaskExecutor;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Demo 应用辅助接口，便于本地联调和验收。
 */
@RestController
@RequestMapping("/test")
@RequiredArgsConstructor
public class DemoController {

    private final MonitorSyncTaskExecutor executor;

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "UP");
        result.put("message", "Sync Data Demo Application is running");
        return result;
    }

    @PostMapping("/task/{taskId}/execute")
    public Map<String, Object> executeTask(@PathVariable Long taskId) {
        Map<String, Object> result = new HashMap<>();
        try {
            Long logId = executor.executeOnce(taskId, "MANUAL");
            result.put("success", true);
            result.put("taskId", taskId);
            result.put("logId", logId);
            result.put("message", "任务执行已触发");
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return result;
    }

    @PostMapping("/task/{taskId}/start")
    public Map<String, Object> startTask(@PathVariable Long taskId) {
        Map<String, Object> result = new HashMap<>();
        try {
            executor.start(taskId);
            result.put("success", true);
            result.put("taskId", taskId);
            result.put("message", "任务已启动");
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return result;
    }

    @PostMapping("/task/{taskId}/stop")
    public Map<String, Object> stopTask(@PathVariable Long taskId) {
        Map<String, Object> result = new HashMap<>();
        try {
            executor.stop(taskId);
            result.put("success", true);
            result.put("taskId", taskId);
            result.put("message", "任务已停止");
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return result;
    }

    @GetMapping("/task/{taskId}/status")
    public Map<String, Object> getTaskStatus(@PathVariable Long taskId) {
        Map<String, Object> result = new HashMap<>();
        result.put("taskId", taskId);
        result.put("running", executor.isRunning(taskId));
        return result;
    }
}
