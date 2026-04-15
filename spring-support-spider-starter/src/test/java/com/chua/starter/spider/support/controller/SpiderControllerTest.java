package com.chua.starter.spider.support.controller;

import com.chua.starter.spider.support.domain.SpiderFlowDefinition;
import com.chua.starter.spider.support.domain.SpiderFlowEdge;
import com.chua.starter.spider.support.domain.SpiderFlowNode;
import com.chua.starter.spider.support.domain.SpiderRuntimeSnapshot;
import com.chua.starter.spider.support.domain.SpiderTaskDefinition;
import com.chua.starter.spider.support.domain.enums.SpiderExecutionType;
import com.chua.starter.spider.support.domain.enums.SpiderNodeType;
import com.chua.starter.spider.support.domain.enums.SpiderTaskStatus;
import com.chua.starter.spider.support.engine.SpiderExecutionEngine;
import com.chua.starter.spider.support.repository.SpiderExecutionRecordRepository;
import com.chua.starter.spider.support.repository.SpiderFlowRepository;
import com.chua.starter.spider.support.repository.SpiderJobBindingRepository;
import com.chua.starter.spider.support.repository.SpiderRuntimeSnapshotRepository;
import com.chua.starter.spider.support.repository.SpiderTaskRepository;
import com.chua.starter.spider.support.service.SpiderScheduledJobService;
import com.chua.starter.spider.support.service.SpiderTaskService;
import com.chua.starter.spider.support.service.dto.CreateTaskResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Controller 层单元测试（不启动 Spring 容器）。
 */
@ExtendWith(MockitoExtension.class)
class SpiderControllerTest {

    // ── Task Controller ───────────────────────────────────────────────────────

    @Mock private SpiderTaskService taskService;
    @Mock private SpiderTaskRepository taskRepository;
    @Mock private SpiderFlowRepository flowRepository;

    @InjectMocks private SpiderTaskController taskController;

    @Test
    void createTask_returns201WithResult() {
        CreateTaskResult result = new CreateTaskResult(null, buildFlow());
        when(taskService.createTask()).thenReturn(result);

        ResponseEntity<?> response = taskController.createTask();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isEqualTo(result);
    }

    @Test
    void getTask_whenNotFound_returns404() {
        when(taskRepository.getById(99L)).thenReturn(null);

        ResponseEntity<?> response = taskController.getTask(99L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void getTask_whenFound_returns200() {
        SpiderTaskDefinition task = buildTask(1L);
        when(taskRepository.getById(1L)).thenReturn(task);

        ResponseEntity<?> response = taskController.getTask(1L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(task);
    }

    @Test
    void updateTask_whenIllegalArgument_returns400() {
        SpiderTaskDefinition task = buildTask(1L);
        task.setTaskName(null); // 触发校验失败
        doThrow(new IllegalArgumentException("任务名称不能为空"))
                .when(taskService).saveTask(any(), any());

        var request = new SpiderTaskController.UpdateTaskRequest(task, buildFlow());
        ResponseEntity<?> response = taskController.updateTask(1L, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body).containsKey("error");
    }

    @Test
    void deleteTask_whenNotFound_returns404() {
        doThrow(new IllegalArgumentException("任务不存在"))
                .when(taskService).deleteTask(99L);

        ResponseEntity<?> response = taskController.deleteTask(99L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void deleteTask_whenFound_returns204() {
        doNothing().when(taskService).deleteTask(1L);

        ResponseEntity<?> response = taskController.deleteTask(1L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    // ── Flow Controller ───────────────────────────────────────────────────────

    @Mock private SpiderRuntimeSnapshotRepository snapshotRepository;
    @Mock private SpiderExecutionRecordRepository recordRepository;
    @Mock private SpiderExecutionEngine executionEngine;
    @Mock private SpiderScheduledJobService scheduledJobService;

    @InjectMocks private SpiderFlowController flowController;

    @Test
    void validateFlow_withDanglingNode_returnsInvalidResult() {
        // 悬空节点：DOWNLOADER 无连线
        SpiderFlowDefinition flow = SpiderFlowDefinition.builder()
                .nodes(List.of(
                        SpiderFlowNode.builder().nodeId("s").nodeType(SpiderNodeType.START).build(),
                        SpiderFlowNode.builder().nodeId("d").nodeType(SpiderNodeType.DOWNLOADER).build(),
                        SpiderFlowNode.builder().nodeId("e").nodeType(SpiderNodeType.END).build()
                ))
                .edges(List.of(
                        SpiderFlowEdge.builder().edgeId("e1").sourceNodeId("s").targetNodeId("e").build()
                ))
                .build();

        ResponseEntity<?> response = flowController.validateFlow(1L, flow);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body.get("valid")).isEqualTo(false);
        @SuppressWarnings("unchecked")
        List<String> errors = (List<String>) body.get("errors");
        assertThat(errors).anyMatch(e -> e.contains("d"));
    }

    @Test
    void validateFlow_withValidFlow_returnsValid() {
        SpiderFlowDefinition flow = buildFlow();

        ResponseEntity<?> response = flowController.validateFlow(1L, flow);

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body.get("valid")).isEqualTo(true);
    }

    @Test
    void getRuntime_whenNotFound_returns404() {
        when(snapshotRepository.findByTaskId(1L)).thenReturn(Optional.empty());

        ResponseEntity<?> response = flowController.getRuntime(1L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void getRuntime_whenFound_returns200() {
        SpiderRuntimeSnapshot snapshot = SpiderRuntimeSnapshot.builder()
                .taskId(1L).status(SpiderTaskStatus.RUNNING).build();
        when(snapshotRepository.findByTaskId(1L)).thenReturn(Optional.of(snapshot));

        ResponseEntity<?> response = flowController.getRuntime(1L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void run_pauseResume_statusTransitions() {
        doNothing().when(executionEngine).execute(1L);
        doNothing().when(scheduledJobService).pauseJob(1L);
        doNothing().when(scheduledJobService).resumeJob(1L);

        assertThat(flowController.run(1L).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(flowController.pause(1L).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(flowController.resume(1L).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private SpiderTaskDefinition buildTask(Long id) {
        return SpiderTaskDefinition.builder()
                .id(id).taskCode("SPIDER-TEST").taskName("测试任务")
                .entryUrl("https://example.com")
                .executionType(SpiderExecutionType.ONCE)
                .status(SpiderTaskStatus.DRAFT).version(0).build();
    }

    private SpiderFlowDefinition buildFlow() {
        return SpiderFlowDefinition.builder()
                .nodes(List.of(
                        SpiderFlowNode.builder().nodeId("s").nodeType(SpiderNodeType.START).build(),
                        SpiderFlowNode.builder().nodeId("e").nodeType(SpiderNodeType.END).build()))
                .edges(List.of(
                        SpiderFlowEdge.builder().edgeId("e1").sourceNodeId("s").targetNodeId("e").build()))
                .build();
    }
}
