package com.chua.starter.ai.support.service;

import com.chua.starter.ai.support.manage.common.ApiException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 编排图数据丢失和非法引用回归。
 * @author CH
 */
class AgentOrchestrationGraphTest {
    /** 缺失及类型错误的数组均不得被当成清空。 */
    @Test
    void rejectsMalformedGraph() {
        for (Map<String, Object> body : List.<Map<String, Object>>of(
                Map.of("raw", "broken json"), Map.of("nodes", List.of()),
                Map.of("nodes", "[]", "edges", List.of()))) {
            assertThrows(ApiException.class, () -> AgentOrchestrationGraph.validate(body, Set.of("a")));
        }
    }

    /** 环路不能用追加剩余节点的方式继续执行。 */
    @Test
    void rejectsCycle() {
        assertThrows(ApiException.class, () -> AgentOrchestrationGraph.validate(
                Map.of("nodes", List.of(Map.of("id", "a"), Map.of("id", "b")),
                        "edges", List.of(Map.of("source", "a", "target", "b"),
                                Map.of("source", "b", "target", "a"))), Set.of("a", "b")));
    }

    /** 外来 Agent 与悬空边均被拒绝。 */
    @Test
    void rejectsForeignAgentAndDanglingEdge() {
        assertThrows(ApiException.class, () -> AgentOrchestrationGraph.validate(
                Map.of("nodes", List.of(Map.of("id", "other")), "edges", List.of()), Set.of("a")));
        assertThrows(ApiException.class, () -> AgentOrchestrationGraph.validate(
                Map.of("nodes", List.of(Map.of("id", "a")),
                        "edges", List.of(Map.of("source", "a", "target", "missing"))), Set.of("a")));
    }

    /** 节点显示标识可以不同于业务 Agent 标识。 */
    @Test
    void acceptsNodeAliasesAndExplicitEmptyGraph() {
        var graph = AgentOrchestrationGraph.validate(Map.of("nodes",
                List.of(Map.of("id", "visual-a", "agent_id", "a"), Map.of("id", "b")),
                "edges", List.of(Map.of("source", "visual-a", "target", "b"))), Set.of("a", "b"));
        assertEquals(2, graph.nodes().size());
        assertEquals(List.of(), AgentOrchestrationGraph.validate(
                Map.of("nodes", List.of(), "edges", List.of()), Set.of("a")).nodes());
    }
}
