package com.chua.starter.taskdistribution.support.controller;

import com.chua.common.support.taskdistribution.dispatcher.DispatcherProvider;
import com.chua.common.support.taskdistribution.manager.ResultBuffer;
import com.chua.common.support.taskdistribution.manager.TaskManager;
import com.chua.common.support.taskdistribution.node.NodeMeta;
import com.chua.common.support.taskdistribution.node.NodeTable;
import com.chua.common.support.taskdistribution.store.TaskStore;
import com.chua.common.support.taskdistribution.task.Task;
import com.chua.common.support.taskdistribution.task.TaskResult;
import com.chua.common.support.taskdistribution.task.TaskStatus;
import com.chua.starter.common.support.annotations.Permission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 任务分发管理接口。
 *
 * <p>提供任务清单、节点清单、任务状态查询、暂停/恢复/取消等管理能力。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@RestController
@RequestMapping("/v2/taskdistribution")
@RequiredArgsConstructor
@Tag(name = "任务分发管理")
public class TaskDistributionController {

    /**
     * 任务管理器
     */
    private final TaskManager taskManager;

    /**
     * 节点注册表
     */
    private final NodeTable nodeTable;

    /**
     * 派发提供者
     */
    private final DispatcherProvider dispatcherProvider;

    /**
     * 结果缓冲器
     */
    private final ResultBuffer resultBuffer;

    /**
     * 查询任务清单。
     *
     * @return 任务列表
     */
    @GetMapping("/task/list")
    @Operation(summary = "查询任务清单")
    @Permission(value = "taskdistribution:task:list")
    public List<Map<String, Object>> listTasks() {
        List<TaskManager.TaskView> views = taskManager.getAllTasks();
        List<Map<String, Object>> result = new ArrayList<>();
        for (TaskManager.TaskView view : views) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("taskId", view.taskId());
            item.put("taskType", view.taskType());
            item.put("status", view.status().name());
            item.put("retryCount", view.retryCount());
            item.put("createdAt", view.createdAt());
            item.put("completedAt", view.completedAt());
            result.add(item);
        }
        return result;
    }

    /**
     * 查询单个任务详情。
     *
     * @param taskId 任务 标识
     * @return 任务详情
     */
    @GetMapping("/task/{taskId}")
    @Operation(summary = "查询任务详情")
    @Permission(value = "taskdistribution:task:get")
    public Map<String, Object> getTask(@PathVariable String taskId) {
        Map<String, Object> result = new LinkedHashMap<>();
        Task<?> task = taskManager.getTask(taskId);
        TaskStatus status = taskManager.getStatus(taskId);
        TaskResult<?> taskResult = taskManager.getResult(taskId);

        result.put("taskId", taskId);
        result.put("task", task);
        result.put("status", status != null ? status.name() : null);
        result.put("result", taskResult);
        return result;
    }

    /**
     * 取消任务。
     *
     * @param taskId 任务 标识
     * @return 操作结果
     */
    @PostMapping("/task/{taskId}/cancel")
    @Operation(summary = "取消任务")
    @Permission(value = "taskdistribution:task:cancel")
    public Map<String, Object> cancelTask(@PathVariable String taskId) {
        boolean ok = taskManager.cancel(taskId);
        dispatcherProvider.cancel(taskId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", ok);
        result.put("taskId", taskId);
        return result;
    }

    /**
     * 暂停任务。
     *
     * @param taskId 任务 标识
     * @return 操作结果
     */
    @PostMapping("/task/{taskId}/pause")
    @Operation(summary = "暂停任务")
    @Permission(value = "taskdistribution:task:pause")
    public Map<String, Object> pauseTask(@PathVariable String taskId) {
        boolean ok = taskManager.pause(taskId);
        dispatcherProvider.pause(taskId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", ok);
        result.put("taskId", taskId);
        return result;
    }

    /**
     * 恢复暂停的任务。
     *
     * @param taskId 任务 标识
     * @return 操作结果
     */
    @PostMapping("/task/{taskId}/resume")
    @Operation(summary = "恢复任务")
    @Permission(value = "taskdistribution:task:resume")
    public Map<String, Object> resumeTask(@PathVariable String taskId) {
        boolean ok = taskManager.resume(taskId);
        dispatcherProvider.resume(taskId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", ok);
        result.put("taskId", taskId);
        return result;
    }

    /**
     * 删除任务记录。
     *
     * @param taskId 任务 标识
     * @return 操作结果
     */
    @DeleteMapping("/task/{taskId}")
    @Operation(summary = "删除任务记录")
    @Permission(value = "taskdistribution:task:delete")
    public Map<String, Object> deleteTask(@PathVariable String taskId) {
        resultBuffer.remove(taskId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("taskId", taskId);
        return result;
    }

    /**
     * 查询节点清单。
     *
     * @return 节点列表
     */
    @GetMapping("/node/list")
    @Operation(summary = "查询节点清单")
    @Permission(value = "taskdistribution:node:list")
    public List<Map<String, Object>> listNodes() {
        List<NodeMeta> nodes = nodeTable.getAllNodesIncludingOffline();
        List<Map<String, Object>> result = new ArrayList<>();
        for (NodeMeta meta : nodes) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("nodeId", meta.getNodeId());
            item.put("tags", meta.getTags());
            item.put("weight", meta.getWeight());
            item.put("host", meta.getHost());
            item.put("port", meta.getPort());
            item.put("online", meta.isOnline());
            item.put("lastHeartbeat", meta.getLastHeartbeat());
            item.put("homeMiddlewareId", meta.getHomeMiddlewareId());
            result.add(item);
        }
        return result;
    }

    /**
     * 注销节点。
     *
     * @param nodeId 节点 标识
     * @return 操作结果
     */
    @DeleteMapping("/node/{nodeId}")
    @Operation(summary = "注销节点")
    @Permission(value = "taskdistribution:node:delete")
    public Map<String, Object> removeNode(@PathVariable String nodeId) {
        nodeTable.unregister(nodeId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("nodeId", nodeId);
        return result;
    }
}
