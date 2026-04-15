package com.chua.starter.spider.support.controller;

import com.chua.starter.spider.support.domain.SpiderFlowDefinition;
import com.chua.starter.spider.support.domain.SpiderTaskDefinition;
import com.chua.starter.spider.support.repository.SpiderOptimisticLockException;
import com.chua.starter.spider.support.service.SpiderTaskService;
import com.chua.starter.spider.support.service.dto.CreateTaskResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Task CRUD 接口。
 *
 * @author CH
 */
@RestController
@RequestMapping("/v1/spider/tasks")
@RequiredArgsConstructor
public class SpiderTaskController {

    private final SpiderTaskService taskService;
    private final com.chua.starter.spider.support.repository.SpiderTaskRepository taskRepository;
    private final com.chua.starter.spider.support.repository.SpiderFlowRepository flowRepository;

    /** POST /v1/spider/tasks — 创建任务 */
    @PostMapping
    public ResponseEntity<?> createTask() {
        CreateTaskResult result = taskService.createTask();
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    /** GET /v1/spider/tasks/{taskId} — 查询任务详情 */
    @GetMapping("/{taskId}")
    public ResponseEntity<?> getTask(@PathVariable Long taskId) {
        SpiderTaskDefinition task = taskRepository.getById(taskId);
        if (task == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "任务 [" + taskId + "] 不存在"));
        }
        return ResponseEntity.ok(task);
    }

    /** PUT /v1/spider/tasks/{taskId} — 更新任务（含完整校验链） */
    @PutMapping("/{taskId}")
    public ResponseEntity<?> updateTask(@PathVariable Long taskId,
                                        @RequestBody UpdateTaskRequest request) {
        SpiderTaskDefinition task = request.task();
        SpiderFlowDefinition flow = request.flow();
        if (task == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "请求体中 task 不能为空"));
        }
        task.setId(taskId);
        try {
            taskService.saveTask(task, flow);
            return ResponseEntity.ok(Map.of("message", "保存成功"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (SpiderOptimisticLockException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** DELETE /v1/spider/tasks/{taskId} — 删除任务 */
    @DeleteMapping("/{taskId}")
    public ResponseEntity<?> deleteTask(@PathVariable Long taskId) {
        try {
            taskService.deleteTask(taskId);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    record UpdateTaskRequest(SpiderTaskDefinition task, SpiderFlowDefinition flow) {}
}
