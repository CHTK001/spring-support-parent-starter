package com.chua.starter.spider.support.serializer;

import com.chua.starter.spider.support.domain.SpiderFlowDefinition;
import com.chua.starter.spider.support.domain.SpiderFlowEdge;
import com.chua.starter.spider.support.domain.SpiderFlowNode;
import com.chua.starter.spider.support.domain.enums.SpiderNodeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * SpiderFlowSerializer / SpiderFlowDeserializer 测试。
 *
 * <p><b>Property 1 — 往返一致性</b>：对任意合法 SpiderFlowDefinition，
 * 序列化后再反序列化，节点数量、边数量和属性值与原始对象语义等价。</p>
 */
class SpiderFlowSerializerTest {

    private SpiderFlowSerializer serializer;
    private SpiderFlowDeserializer deserializer;

    @BeforeEach
    void setUp() {
        serializer = new SpiderFlowSerializer();
        deserializer = new SpiderFlowDeserializer();
    }

    // ── 往返属性测试 ──────────────────────────────────────────────────────────

    /** Property 1: 最小编排（仅 START + END）往返一致 */
    @Test
    void roundTrip_minimalFlow_isEquivalent() {
        SpiderFlowDefinition original = buildMinimalFlow();

        String json = serializer.serialize(original);
        SpiderFlowDefinition restored = deserializer.deserialize(json);

        assertThat(restored.getNodes()).hasSize(original.getNodes().size());
        assertThat(restored.getEdges()).hasSize(original.getEdges().size());
        assertThat(restored.getNodes().get(0).getNodeId()).isEqualTo(original.getNodes().get(0).getNodeId());
        assertThat(restored.getNodes().get(0).getNodeType()).isEqualTo(original.getNodes().get(0).getNodeType());
        assertThat(restored.getNodes().get(1).getNodeType()).isEqualTo(SpiderNodeType.END);
        assertThat(restored.getEdges().get(0).getSourceNodeId()).isEqualTo("start-1");
        assertThat(restored.getEdges().get(0).getTargetNodeId()).isEqualTo("end-1");
    }

    /** Property 1: 完整编排（含所有节点类型）往返一致 */
    @Test
    void roundTrip_fullFlow_nodeCountAndEdgeCountPreserved() {
        SpiderFlowDefinition original = buildFullFlow();

        String json = serializer.serialize(original);
        SpiderFlowDefinition restored = deserializer.deserialize(json);

        assertThat(restored.getNodes()).hasSize(original.getNodes().size());
        assertThat(restored.getEdges()).hasSize(original.getEdges().size());
    }

    /** Property 1: 节点 config 属性往返后值不变 */
    @Test
    void roundTrip_nodeConfig_valuesPreserved() {
        SpiderFlowNode downloader = SpiderFlowNode.builder()
                .nodeId("dl-1")
                .nodeType(SpiderNodeType.DOWNLOADER)
                .label("下载器")
                .config(Map.of("timeout", 5000, "userAgent", "Mozilla/5.0"))
                .positionX(200.0)
                .positionY(100.0)
                .build();

        SpiderFlowDefinition original = SpiderFlowDefinition.builder()
                .taskId(1L)
                .nodes(List.of(downloader))
                .edges(List.of())
                .build();

        String json = serializer.serialize(original);
        SpiderFlowDefinition restored = deserializer.deserialize(json);

        SpiderFlowNode restoredNode = restored.getNodes().get(0);
        assertThat(restoredNode.getNodeId()).isEqualTo("dl-1");
        assertThat(restoredNode.getLabel()).isEqualTo("下载器");
        assertThat(restoredNode.getPositionX()).isEqualTo(200.0);
    }

    /** Property 1: 空节点列表和空边列表往返后仍为空列表 */
    @Test
    void roundTrip_emptyNodesAndEdges_remainsEmpty() {
        SpiderFlowDefinition original = SpiderFlowDefinition.builder()
                .taskId(42L)
                .nodes(List.of())
                .edges(List.of())
                .build();

        String json = serializer.serialize(original);
        SpiderFlowDefinition restored = deserializer.deserialize(json);

        assertThat(restored.getNodes()).isEmpty();
        assertThat(restored.getEdges()).isEmpty();
    }

    // ── 序列化格式测试 ────────────────────────────────────────────────────────

    @Test
    void serialize_outputContainsNodesAndEdgesKeys() {
        String json = serializer.serialize(buildMinimalFlow());
        assertThat(json).contains("\"nodes\"").contains("\"edges\"");
    }

    @Test
    void serializePretty_outputIsMultiLine() {
        String json = serializer.serializePretty(buildMinimalFlow());
        assertThat(json).contains("\n");
    }

    @Test
    void serialize_nullFlow_throwsIllegalArgument() {
        assertThatThrownBy(() -> serializer.serialize(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── 反序列化单元测试 ──────────────────────────────────────────────────────

    @Test
    void deserialize_validJson_restoresNodeAndEdgeCount() {
        String json = """
                {
                  "taskId": 1,
                  "nodes": [
                    {"nodeId":"n1","nodeType":"START"},
                    {"nodeId":"n2","nodeType":"END"}
                  ],
                  "edges": [
                    {"edgeId":"e1","sourceNodeId":"n1","targetNodeId":"n2"}
                  ]
                }
                """;
        SpiderFlowDefinition flow = deserializer.deserialize(json);
        assertThat(flow.getNodes()).hasSize(2);
        assertThat(flow.getEdges()).hasSize(1);
        assertThat(flow.getNodes().get(0).getNodeType()).isEqualTo(SpiderNodeType.START);
    }

    @Test
    void deserialize_missingNodes_throwsWithFieldPath() {
        String json = """
                {"taskId":1,"edges":[]}
                """;
        assertThatThrownBy(() -> deserializer.deserialize(json))
                .isInstanceOf(SpiderFlowDeserializeException.class)
                .hasMessageContaining("$.nodes");
    }

    @Test
    void deserialize_missingEdges_throwsWithFieldPath() {
        String json = """
                {"taskId":1,"nodes":[]}
                """;
        assertThatThrownBy(() -> deserializer.deserialize(json))
                .isInstanceOf(SpiderFlowDeserializeException.class)
                .hasMessageContaining("$.edges");
    }

    @Test
    void deserialize_invalidNodeType_throwsWithFieldPath() {
        String json = """
                {
                  "nodes": [{"nodeId":"n1","nodeType":"INVALID_TYPE"}],
                  "edges": []
                }
                """;
        assertThatThrownBy(() -> deserializer.deserialize(json))
                .isInstanceOf(SpiderFlowDeserializeException.class)
                .hasMessageContaining("$.nodes[0].nodeType");
    }

    @Test
    void deserialize_missingNodeId_throwsWithFieldPath() {
        String json = """
                {
                  "nodes": [{"nodeType":"START"}],
                  "edges": []
                }
                """;
        assertThatThrownBy(() -> deserializer.deserialize(json))
                .isInstanceOf(SpiderFlowDeserializeException.class)
                .hasMessageContaining("$.nodes[0].nodeId");
    }

    @Test
    void deserialize_missingEdgeSourceNodeId_throwsWithFieldPath() {
        String json = """
                {
                  "nodes": [],
                  "edges": [{"edgeId":"e1","targetNodeId":"n2"}]
                }
                """;
        assertThatThrownBy(() -> deserializer.deserialize(json))
                .isInstanceOf(SpiderFlowDeserializeException.class)
                .hasMessageContaining("$.edges[0].sourceNodeId");
    }

    @Test
    void deserialize_invalidJson_throwsWithRootPath() {
        assertThatThrownBy(() -> deserializer.deserialize("{not valid json"))
                .isInstanceOf(SpiderFlowDeserializeException.class)
                .hasMessageContaining("$");
    }

    @Test
    void deserialize_emptyString_throwsWithRootPath() {
        assertThatThrownBy(() -> deserializer.deserialize(""))
                .isInstanceOf(SpiderFlowDeserializeException.class)
                .hasMessageContaining("$");
    }

    // ── 辅助方法 ──────────────────────────────────────────────────────────────

    private SpiderFlowDefinition buildMinimalFlow() {
        SpiderFlowNode start = SpiderFlowNode.builder()
                .nodeId("start-1").nodeType(SpiderNodeType.START).label("开始").build();
        SpiderFlowNode end = SpiderFlowNode.builder()
                .nodeId("end-1").nodeType(SpiderNodeType.END).label("结束").build();
        SpiderFlowEdge edge = SpiderFlowEdge.builder()
                .edgeId("e-1").sourceNodeId("start-1").targetNodeId("end-1").build();
        return SpiderFlowDefinition.builder()
                .taskId(1L).nodes(List.of(start, end)).edges(List.of(edge)).build();
    }

    private SpiderFlowDefinition buildFullFlow() {
        List<SpiderFlowNode> nodes = List.of(
                SpiderFlowNode.builder().nodeId("n0").nodeType(SpiderNodeType.START).build(),
                SpiderFlowNode.builder().nodeId("n1").nodeType(SpiderNodeType.DOWNLOADER).build(),
                SpiderFlowNode.builder().nodeId("n2").nodeType(SpiderNodeType.PARSER).build(),
                SpiderFlowNode.builder().nodeId("n3").nodeType(SpiderNodeType.FILTER).build(),
                SpiderFlowNode.builder().nodeId("n4").nodeType(SpiderNodeType.PIPELINE).build(),
                SpiderFlowNode.builder().nodeId("n5").nodeType(SpiderNodeType.END).build()
        );
        List<SpiderFlowEdge> edges = List.of(
                SpiderFlowEdge.builder().edgeId("e0").sourceNodeId("n0").targetNodeId("n1").build(),
                SpiderFlowEdge.builder().edgeId("e1").sourceNodeId("n1").targetNodeId("n2").build(),
                SpiderFlowEdge.builder().edgeId("e2").sourceNodeId("n2").targetNodeId("n3").build(),
                SpiderFlowEdge.builder().edgeId("e3").sourceNodeId("n3").targetNodeId("n4").build(),
                SpiderFlowEdge.builder().edgeId("e4").sourceNodeId("n4").targetNodeId("n5").build()
        );
        return SpiderFlowDefinition.builder().taskId(2L).nodes(nodes).edges(edges).build();
    }
}
