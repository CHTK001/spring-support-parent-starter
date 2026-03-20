package com.chua.starter.sync.data.test.controller;

import com.chua.starter.sync.data.support.service.sync.MonitorSyncTaskExecutor;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 测试控制器
 *
 * @author CH
 * @since 2026/03/20
 */
@RestController
@RequestMapping("/test")
@RequiredArgsConstructor
public class TestController {

    private final MonitorSyncTaskExecutor executor;

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "UP");
        result.put("message", "Sync Data Test Application is running");
        return result;
    }

    @PostMapping("/task/{taskId}/execute")
    public Map<String, Object> executeTask(@PathVariable Long taskId) {
        try {
            Long logId = executor.executeOnce(taskId, "MANUAL");
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("taskId", taskId);
            result.put("logId", logId);
            result.put("message", "任务执行已触发");
            return result;
        } catch (Exception e) {
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", e.getMessage());
            return result;
        }
    }

    @PostMapping("/task/{taskId}/start")
    public Map<String, Object> startTask(@PathVariable Long taskId) {
        try {
            executor.start(taskId);
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("taskId", taskId);
            result.put("message", "任务已启动");
            return result;
        } catch (Exception e) {
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", e.getMessage());
            return result;
        }
    }

    @PostMapping("/task/{taskId}/stop")
    public Map<String, Object> stopTask(@PathVariable Long taskId) {
        try {
            executor.stop(taskId);
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("taskId", taskId);
            result.put("message", "任务已停止");
            return result;
        } catch (Exception e) {
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", e.getMessage());
            return result;
        }
    }

    @GetMapping("/task/{taskId}/status")
    public Map<String, Object> getTaskStatus(@PathVariable Long taskId) {
        Map<String, Object> result = new HashMap<>();
        result.put("taskId", taskId);
        result.put("running", executor.isRunning(taskId));
        return result;
    }
}
