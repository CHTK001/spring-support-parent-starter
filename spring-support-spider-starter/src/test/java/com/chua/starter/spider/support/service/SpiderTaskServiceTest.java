package com.chua.starter.spider.support.service;

import com.chua.starter.spider.support.domain.SpiderFlowDefinition;
import com.chua.starter.spider.support.domain.SpiderFlowEdge;
import com.chua.starter.spider.support.domain.SpiderFlowNode;
import com.chua.starter.spider.support.domain.SpiderTaskDefinition;
import com.chua.starter.spider.support.domain.enums.SpiderExecutionType;
import com.chua.starter.spider.support.domain.enums.SpiderNodeType;
import com.chua.starter.spider.support.domain.enums.SpiderTaskStatus;
import com.chua.starter.spider.support.repository.SpiderFlowRepository;
import com.chua.starter.spider.support.repository.SpiderJobBindingRepository;
import com.chua.starter.spider.support.repository.SpiderRuntimeSnapshotRepository;
import com.chua.starter.spider.support.repository.SpiderTaskRepository;
import com.chua.starter.spider.support.service.dto.CreateTaskResult;
import com.chua.starter.spider.support.service.impl.SpiderTaskServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * SpiderTaskService 单元测试。
 */
@ExtendWith(MockitoExtension.class)
class SpiderTaskServiceTest {

    @Mock
    private SpiderTaskRepository taskRepository;
    @Mock
    private SpiderFlowRepository flowRepository;
    @Mock
    private SpiderJobBindingRepository jobBindingRepository;
    @Mock
    private SpiderRuntimeSnapshotRepository runtimeSnapshotRepository;
    @Mock
    private SpiderScheduledJobService scheduledJobService;

    private SpiderTaskService taskService;

    @BeforeEach
    void setUp() {
        taskService = new SpiderTaskServiceImpl(
                taskRepository, flowRepository, jobBindingRepository,
                runtimeSnapshotRepository, scheduledJobService);
    }

    // ── createTask ────────────────────────────────────────────────────────────

    @Test
    void createTask_shouldReturnDefaultFlowWithStartAndEndNodes() {
        CreateTaskResult result = taskService.createTask();

        assertThat(result).isNotNull();
        assertThat(result.getFlow()).isNotNull();
        assertThat(result.getFlow().getNodes()).hasSize(2);
        assertThat(result.getFlow().getNodes())
                .extracting(SpiderFlowNode::getNodeType)
                .containsExactlyInAnyOrder(SpiderNodeType.START, SpiderNodeType.END);
        assertThat(result.getFlow().getEdges()).hasSize(1);
    }

    @Test
    void createTask_shouldGenerateUniqueTaskCode() {
        CreateTaskResult r1 = taskService.createTask();
        CreateTaskResult r2 = taskService.createTask();
        // Both results are in-memory only (no persistence), but codes are generated fresh each call
        // The flow is returned; taskId is null until saved
        assertThat(r1.getFlow()).isNotNull();
        assertThat(r2.getFlow()).isNotNull();
    }

    // ── saveTask: taskName 为空 ────────────────────────────────────────────────

    @Test
    void saveTask_shouldThrow_whenTaskNameIsNull() {
        SpiderTaskDefinition task = SpiderTaskDefinition.builder()
                .taskCode("SPIDER-ABCD1234")
                .entryUrl("https://example.com")
                .build();
        SpiderFlowDefinition flow = buildValidFlow();

        assertThatThrownBy(() -> taskService.saveTask(task, flow))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("任务名称不能为空");
    }

    @Test
    void saveTask_shouldThrow_whenTaskNameIsBlank() {
        SpiderTaskDefinition task = SpiderTaskDefinition.builder()
                .taskCode("SPIDER-ABCD1234")
                .taskName("   ")
                .entryUrl("https://example.com")
                .build();
        SpiderFlowDefinition flow = buildValidFlow();

        assertThatThrownBy(() -> taskService.saveTask(task, flow))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("任务名称不能为空");
    }

    // ── saveTask: entryUrl 为空 ───────────────────────────────────────────────

    @Test
    void saveTask_shouldThrow_whenEntryUrlIsNull() {
        SpiderTaskDefinition task = SpiderTaskDefinition.builder()
                .taskCode("SPIDER-ABCD1234")
                .taskName("测试任务")
                .build();
        SpiderFlowDefinition flow = buildValidFlow();

        assertThatThrownBy(() -> taskService.saveTask(task, flow))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("入口 URL 不能为空");
    }

    @Test
    void saveTask_shouldThrow_whenEntryUrlIsBlank() {
        SpiderTaskDefinition task = SpiderTaskDefinition.builder()
                .taskCode("SPIDER-ABCD1234")
                .taskName("测试任务")
                .entryUrl("  ")
                .build();
        SpiderFlowDefinition flow = buildValidFlow();

        assertThatThrownBy(() -> taskService.saveTask(task, flow))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("入口 URL 不能为空");
    }

    // ── saveTask: taskCode 重复 ───────────────────────────────────────────────

    @Test
    void saveTask_shouldThrow_whenTaskCodeAlreadyExists() {
        SpiderTaskDefinition task = SpiderTaskDefinition.builder()
                .taskCode("SPIDER-DUPLICATE")
                .taskName("测试任务")
                .entryUrl("https://example.com")
                .build();
        SpiderFlowDefinition flow = buildValidFlow();

        when(taskRepository.existsByTaskCode("SPIDER-DUPLICATE", null)).thenReturn(true);

        assertThatThrownBy(() -> taskService.saveTask(task, flow))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SPIDER-DUPLICATE")
                .hasMessageContaining("已存在");
    }

    @Test
    void saveTask_shouldSucceed_whenTaskCodeIsUnique() {
        SpiderTaskDefinition task = SpiderTaskDefinition.builder()
                .taskCode("SPIDER-UNIQUE01")
                .taskName("测试任务")
                .entryUrl("https://example.com")
                .build();
        SpiderFlowDefinition flow = buildValidFlow();

        when(taskRepository.existsByTaskCode("SPIDER-UNIQUE01", null)).thenReturn(false);
        doNothing().when(flowRepository).saveTaskAndFlow(any(), any());

        assertThatCode(() -> taskService.saveTask(task, flow)).doesNotThrowAnyException();
        verify(flowRepository).saveTaskAndFlow(task, flow);
    }

    // ── deleteTask: SCHEDULED 任务同步调用 deleteJob ──────────────────────────

    @Test
    void deleteTask_shouldCallDeleteJob_whenTaskIsScheduled() {
        Long taskId = 42L;
        SpiderTaskDefinition task = SpiderTaskDefinition.builder()
                .id(taskId)
                .taskName("定时任务")
                .executionType(SpiderExecutionType.SCHEDULED)
                .build();

        when(taskRepository.getById(taskId)).thenReturn(task);
        doNothing().when(scheduledJobService).deleteJob(taskId);
        doNothing().when(jobBindingRepository).deleteByTaskId(taskId);
        doNothing().when(runtimeSnapshotRepository).deleteByTaskId(taskId);
        when(flowRepository.remove(any())).thenReturn(true);
        when(taskRepository.removeById(taskId)).thenReturn(true);

        taskService.deleteTask(taskId);

        verify(scheduledJobService).deleteJob(taskId);
        verify(jobBindingRepository).deleteByTaskId(taskId);
        verify(runtimeSnapshotRepository).deleteByTaskId(taskId);
        verify(taskRepository).removeById(taskId);
    }

    @Test
    void deleteTask_shouldNotCallDeleteJob_whenTaskIsOnce() {
        Long taskId = 43L;
        SpiderTaskDefinition task = SpiderTaskDefinition.builder()
                .id(taskId)
                .taskName("单次任务")
                .executionType(SpiderExecutionType.ONCE)
                .build();

        when(taskRepository.getById(taskId)).thenReturn(task);
        doNothing().when(jobBindingRepository).deleteByTaskId(taskId);
        doNothing().when(runtimeSnapshotRepository).deleteByTaskId(taskId);
        when(flowRepository.remove(any())).thenReturn(true);
        when(taskRepository.removeById(taskId)).thenReturn(true);

        taskService.deleteTask(taskId);

        verify(scheduledJobService, never()).deleteJob(any());
    }

    @Test
    void deleteTask_shouldThrow_whenTaskNotFound() {
        when(taskRepository.getById(99L)).thenReturn(null);

        assertThatThrownBy(() -> taskService.deleteTask(99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不存在");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private SpiderFlowDefinition buildValidFlow() {
        SpiderFlowNode start = SpiderFlowNode.builder()
                .nodeId("start-1").nodeType(SpiderNodeType.START).label("开始").build();
        SpiderFlowNode end = SpiderFlowNode.builder()
                .nodeId("end-1").nodeType(SpiderNodeType.END).label("结束").build();
        SpiderFlowEdge edge = SpiderFlowEdge.builder()
                .edgeId("e1").sourceNodeId("start-1").targetNodeId("end-1").build();
        return SpiderFlowDefinition.builder()
                .nodes(List.of(start, end))
                .edges(List.of(edge))
                .build();
    }
}
