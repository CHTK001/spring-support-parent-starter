package com.chua.starter.spider.support.engine;

import com.alibaba.fastjson2.JSON;
import com.chua.spider.support.SpiderToolkit;
import com.chua.spider.support.model.SpiderTaskResult;
import com.chua.starter.spider.support.domain.RetryPolicy;
import com.chua.starter.spider.support.domain.SpiderExecutionPolicy;
import com.chua.starter.spider.support.domain.SpiderFlowDefinition;
import com.chua.starter.spider.support.domain.SpiderFlowEdge;
import com.chua.starter.spider.support.domain.SpiderFlowNode;
import com.chua.starter.spider.support.domain.SpiderRuntimeSnapshot;
import com.chua.starter.spider.support.domain.SpiderTaskDefinition;
import com.chua.starter.spider.support.domain.enums.SpiderExecutionType;
import com.chua.starter.spider.support.domain.enums.SpiderNodeType;
import com.chua.starter.spider.support.domain.enums.SpiderTaskStatus;
import com.chua.starter.spider.support.repository.SpiderExecutionRecordRepository;
import com.chua.starter.spider.support.repository.SpiderFlowRepository;
import com.chua.starter.spider.support.repository.SpiderRuntimeSnapshotRepository;
import com.chua.starter.spider.support.repository.SpiderTaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * SpiderExecutionEngine 单元测试（Mock SpiderToolkit）。
 */
@ExtendWith(MockitoExtension.class)
class SpiderExecutionEngineTest {

    @Mock private SpiderTaskRepository taskRepository;
    @Mock private SpiderFlowRepository flowRepository;
    @Mock private SpiderRuntimeSnapshotRepository snapshotRepository;
    @Mock private SpiderExecutionRecordRepository recordRepository;
    @Mock private SpiderToolkit spiderToolkit;

    private SpiderExecutionEngine engine;

    @BeforeEach
    void setUp() {
        engine = new SpiderExecutionEngine(
                taskRepository, flowRepository, snapshotRepository, recordRepository, spiderToolkit);
    }

    // ── 正常执行 ──────────────────────────────────────────────────────────────

    @Test
    void execute_onceTask_completesWithFinishedStatus() {
        Long taskId = 1L;
        SpiderTaskDefinition task = buildTask(taskId, SpiderExecutionType.ONCE, null);
        SpiderFlowDefinition flow = buildMinimalFlow();
        SpiderTaskResult result = buildResult(5, 0);

        when(taskRepository.getById(taskId)).thenReturn(task);
        when(flowRepository.findByTaskId(taskId)).thenReturn(Optional.of(flow));
        when(snapshotRepository.findByTaskId(taskId)).thenReturn(Optional.empty());
        when(spiderToolkit.run(any())).thenReturn(result);
        when(taskRepository.updateById(any())).thenReturn(true);
        when(snapshotRepository.saveOrUpdate(any())).thenReturn(true);

        engine.execute(taskId);

        // 验证最终状态为 FINISHED
        ArgumentCaptor<SpiderTaskDefinition> taskCaptor = ArgumentCaptor.forClass(SpiderTaskDefinition.class);
        verify(taskRepository, atLeastOnce()).updateById(taskCaptor.capture());
        List<SpiderTaskDefinition> updates = taskCaptor.getAllValues();
        assertThat(updates).anyMatch(t -> t.getStatus() == SpiderTaskStatus.FINISHED);
    }

    @Test
    void execute_taskNotFound_throwsIllegalArgument() {
        when(taskRepository.getById(99L)).thenReturn(null);
        assertThatThrownBy(() -> engine.execute(99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不存在");
    }

    // ── 重试耗尽后状态变更为 FAILED ───────────────────────────────────────────

    @Test
    void execute_toolkitThrows_statusBecomeFailed() {
        Long taskId = 2L;
        SpiderExecutionPolicy policy = SpiderExecutionPolicy.builder()
                .retryPolicy(RetryPolicy.builder().maxRetries(2).retryIntervalMs(0).build())
                .build();
        SpiderTaskDefinition task = buildTask(taskId, SpiderExecutionType.ONCE,
                JSON.toJSONString(policy));
        SpiderFlowDefinition flow = buildMinimalFlow();

        when(taskRepository.getById(taskId)).thenReturn(task);
        when(flowRepository.findByTaskId(taskId)).thenReturn(Optional.of(flow));
        when(snapshotRepository.findByTaskId(taskId)).thenReturn(Optional.empty());
        when(spiderToolkit.run(any())).thenThrow(new RuntimeException("网络超时"));
        when(taskRepository.updateById(any())).thenReturn(true);
        when(snapshotRepository.saveOrUpdate(any())).thenReturn(true);

        engine.execute(taskId);

        ArgumentCaptor<SpiderTaskDefinition> taskCaptor = ArgumentCaptor.forClass(SpiderTaskDefinition.class);
        verify(taskRepository, atLeastOnce()).updateById(taskCaptor.capture());
        assertThat(taskCaptor.getAllValues())
                .anyMatch(t -> t.getStatus() == SpiderTaskStatus.FAILED);
    }

    @Test
    void execute_toolkitThrows_snapshotContainsErrorSummary() {
        Long taskId = 3L;
        SpiderTaskDefinition task = buildTask(taskId, SpiderExecutionType.ONCE, null);
        SpiderFlowDefinition flow = buildMinimalFlow();

        when(taskRepository.getById(taskId)).thenReturn(task);
        when(flowRepository.findByTaskId(taskId)).thenReturn(Optional.of(flow));
        when(snapshotRepository.findByTaskId(taskId)).thenReturn(Optional.empty());
        when(spiderToolkit.run(any())).thenThrow(new RuntimeException("连接被拒绝"));
        when(taskRepository.updateById(any())).thenReturn(true);
        when(snapshotRepository.saveOrUpdate(any())).thenReturn(true);

        engine.execute(taskId);

        ArgumentCaptor<SpiderRuntimeSnapshot> snapshotCaptor = ArgumentCaptor.forClass(SpiderRuntimeSnapshot.class);
        verify(snapshotRepository, atLeastOnce()).saveOrUpdate(snapshotCaptor.capture());
        assertThat(snapshotCaptor.getAllValues())
                .anyMatch(s -> s.getLastErrorSummary() != null
                        && s.getLastErrorSummary().contains("连接被拒绝"));
    }

    // ── REPEAT_N 达到次数后状态变更为 FINISHED ────────────────────────────────

    @Test
    void execute_repeatN_completesAfterRepeatTimes() {
        Long taskId = 4L;
        SpiderExecutionPolicy policy = SpiderExecutionPolicy.builder()
                .repeatTimes(3)
                .repeatInterval(0L)
                .build();
        SpiderTaskDefinition task = buildTask(taskId, SpiderExecutionType.REPEAT_N,
                JSON.toJSONString(policy));
        SpiderFlowDefinition flow = buildMinimalFlow();
        SpiderTaskResult result = buildResult(2, 0);

        when(taskRepository.getById(taskId)).thenReturn(task);
        when(flowRepository.findByTaskId(taskId)).thenReturn(Optional.of(flow));
        when(snapshotRepository.findByTaskId(taskId)).thenReturn(Optional.empty());
        when(spiderToolkit.run(any())).thenReturn(result);
        when(taskRepository.updateById(any())).thenReturn(true);
        when(snapshotRepository.saveOrUpdate(any())).thenReturn(true);

        engine.execute(taskId);

        // SpiderToolkit.run 应被调用 3 次（repeatTimes=3）
        verify(spiderToolkit, times(3)).run(any());

        // 最终状态为 FINISHED
        ArgumentCaptor<SpiderTaskDefinition> taskCaptor = ArgumentCaptor.forClass(SpiderTaskDefinition.class);
        verify(taskRepository, atLeastOnce()).updateById(taskCaptor.capture());
        assertThat(taskCaptor.getAllValues())
                .anyMatch(t -> t.getStatus() == SpiderTaskStatus.FINISHED);
    }

    // ── 底层定义转换 ──────────────────────────────────────────────────────────

    @Test
    void toUtilsDefinition_mapsEntryUrlAndTaskName() {
        SpiderTaskDefinition task = buildTask(1L, SpiderExecutionType.ONCE, null);
        task.setEntryUrl("https://gitee.com/explore");
        task.setTaskName("Gitee 爬虫");

        com.chua.spider.support.model.SpiderTaskDefinition def =
                engine.toUtilsDefinition(task, null, null);

        assertThat(def.getUrl()).isEqualTo("https://gitee.com/explore");
        assertThat(def.getTaskName()).isEqualTo("Gitee 爬虫");
    }

    @Test
    void toUtilsDefinition_mapsRetryPolicy() {
        SpiderExecutionPolicy policy = SpiderExecutionPolicy.builder()
                .retryPolicy(RetryPolicy.builder().maxRetries(5).retryIntervalMs(2000).build())
                .threadCount(4)
                .build();
        SpiderTaskDefinition task = buildTask(1L, SpiderExecutionType.ONCE,
                JSON.toJSONString(policy));

        com.chua.spider.support.model.SpiderTaskDefinition def =
                engine.toUtilsDefinition(task, policy, null);

        assertThat(def.getRetryTimes()).isEqualTo(5);
        assertThat(def.getRetrySleepTime()).isEqualTo(2000);
        assertThat(def.getThreadNum()).isEqualTo(4);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private SpiderTaskDefinition buildTask(Long id, SpiderExecutionType type, String policyJson) {
        return SpiderTaskDefinition.builder()
                .id(id)
                .taskCode("SPIDER-TEST-" + id)
                .taskName("测试任务-" + id)
                .entryUrl("https://example.com")
                .executionType(type)
                .executionPolicy(policyJson)
                .status(SpiderTaskStatus.READY)
                .version(0)
                .build();
    }

    private SpiderFlowDefinition buildMinimalFlow() {
        return SpiderFlowDefinition.builder()
                .nodes(List.of(
                        SpiderFlowNode.builder().nodeId("s").nodeType(SpiderNodeType.START).build(),
                        SpiderFlowNode.builder().nodeId("e").nodeType(SpiderNodeType.END).build()
                ))
                .edges(List.of(
                        SpiderFlowEdge.builder().edgeId("e1").sourceNodeId("s").targetNodeId("e").build()
                ))
                .build();
    }

    private SpiderTaskResult buildResult(long success, long failure) {
        SpiderTaskResult r = new SpiderTaskResult();
        r.setSuccessRequests(success);
        r.setFailureRequests(failure);
        r.setTotalRequests(success + failure);
        return r;
    }
}
