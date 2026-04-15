package com.chua.starter.spider.support.repository;

import com.chua.starter.spider.support.domain.SpiderFlowDefinition;
import com.chua.starter.spider.support.domain.SpiderFlowEdge;
import com.chua.starter.spider.support.domain.SpiderFlowNode;
import com.chua.starter.spider.support.domain.SpiderTaskDefinition;
import com.chua.starter.spider.support.domain.enums.SpiderExecutionType;
import com.chua.starter.spider.support.domain.enums.SpiderNodeType;
import com.chua.starter.spider.support.domain.enums.SpiderTaskStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * Repository 层集成测试（H2 内存数据库）。
 *
 * <p>测试乐观锁并发冲突和任务与编排原子性持久化。</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SpiderRepositoryTest {

    @Autowired
    private SpiderTaskRepository taskRepository;

    @Autowired
    private SpiderFlowRepository flowRepository;

    // ── 乐观锁冲突测试 ────────────────────────────────────────────────────────

    @Test
    void saveOrUpdateWithLock_concurrentUpdate_throwsOptimisticLockException() {
        // 保存初始任务（version=0）
        SpiderTaskDefinition task = buildTask("lock-test-001");
        taskRepository.save(task);
        Long taskId = task.getId();

        // 模拟两个并发读取同一版本
        SpiderTaskDefinition v1 = taskRepository.getById(taskId);
        SpiderTaskDefinition v2 = taskRepository.getById(taskId);

        // 第一次更新成功（version 0 → 1）
        v1.setTaskName("更新1");
        taskRepository.saveOrUpdateWithLock(v1);

        // 第二次更新应失败（version 仍为 0，但数据库已是 1）
        v2.setTaskName("更新2");
        assertThatThrownBy(() -> taskRepository.saveOrUpdateWithLock(v2))
                .isInstanceOf(SpiderOptimisticLockException.class)
                .hasMessageContaining("版本冲突");
    }

    // ── 原子性持久化测试 ──────────────────────────────────────────────────────

    @Test
    void saveTaskAndFlow_success_bothPersisted() {
        SpiderTaskDefinition task = buildTask("atomic-test-001");
        SpiderFlowDefinition flow = buildFlow();

        flowRepository.saveTaskAndFlow(task, flow);

        assertThat(taskRepository.getById(task.getId())).isNotNull();
        Optional<SpiderFlowDefinition> savedFlow = flowRepository.findByTaskId(task.getId());
        assertThat(savedFlow).isPresent();
        assertThat(savedFlow.get().getNodes()).hasSize(2);
        assertThat(savedFlow.get().getEdges()).hasSize(1);
    }

    @Test
    void findByTaskId_afterSave_nodesAndEdgesDeserialized() {
        SpiderTaskDefinition task = buildTask("deser-test-001");
        SpiderFlowDefinition flow = buildFlow();
        flowRepository.saveTaskAndFlow(task, flow);

        Optional<SpiderFlowDefinition> found = flowRepository.findByTaskId(task.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getNodes()).isNotNull().hasSize(2);
        assertThat(found.get().getNodes().get(0).getNodeType()).isEqualTo(SpiderNodeType.START);
    }

    // ── 辅助方法 ──────────────────────────────────────────────────────────────

    private SpiderTaskDefinition buildTask(String code) {
        return SpiderTaskDefinition.builder()
                .taskCode(code)
                .taskName("测试任务-" + code)
                .entryUrl("https://example.com")
                .executionType(SpiderExecutionType.ONCE)
                .status(SpiderTaskStatus.DRAFT)
                .version(0)
                .build();
    }

    private SpiderFlowDefinition buildFlow() {
        SpiderFlowNode start = SpiderFlowNode.builder()
                .nodeId("s").nodeType(SpiderNodeType.START).build();
        SpiderFlowNode end = SpiderFlowNode.builder()
                .nodeId("e").nodeType(SpiderNodeType.END).build();
        SpiderFlowEdge edge = SpiderFlowEdge.builder()
                .edgeId("e1").sourceNodeId("s").targetNodeId("e").build();
        return SpiderFlowDefinition.builder()
                .nodes(List.of(start, end))
                .edges(List.of(edge))
                .version(0)
                .build();
    }
}
