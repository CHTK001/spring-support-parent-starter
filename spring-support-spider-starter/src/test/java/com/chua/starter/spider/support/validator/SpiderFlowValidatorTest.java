package com.chua.starter.spider.support.validator;

import com.chua.starter.spider.support.domain.SpiderFlowDefinition;
import com.chua.starter.spider.support.domain.SpiderFlowEdge;
import com.chua.starter.spider.support.domain.SpiderFlowNode;
import com.chua.starter.spider.support.domain.enums.SpiderNodeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * SpiderFlowValidator 测试。
 *
 * <p><b>Property 2 — 合法编排必通过校验</b>：满足 START→END 可达、无悬空节点、无非法回环的编排，校验结果为通过。</p>
 * <p><b>Property 3 — 悬空节点必报错</b>：包含悬空节点的编排，校验结果包含该节点的错误标记。</p>
 */
class SpiderFlowValidatorTest {

    private SpiderFlowValidator validator;

    @BeforeEach
    void setUp() {
        validator = new SpiderFlowValidator();
    }

    // ── Property 2: 合法编排必通过校验 ────────────────────────────────────────

    /** Property 2: 最小合法编排 START→END 通过 */
    @Test
    void validate_minimalValidFlow_passes() {
        SpiderFlowDefinition flow = buildLinearFlow(
                SpiderNodeType.START, SpiderNodeType.END);
        assertThat(validator.validate(flow).isValid()).isTrue();
    }

    /** Property 2: 完整主链路 START→DOWNLOADER→PARSER→FILTER→PIPELINE→END 通过 */
    @Test
    void validate_fullMainChain_passes() {
        SpiderFlowDefinition flow = buildLinearFlow(
                SpiderNodeType.START,
                SpiderNodeType.DOWNLOADER,
                SpiderNodeType.PARSER,
                SpiderNodeType.FILTER,
                SpiderNodeType.PIPELINE,
                SpiderNodeType.END);
        SpiderFlowValidationResult result = validator.validate(flow);
        assertThat(result.isValid()).isTrue();
    }

    /** Property 2: 含 ERROR_HANDLER 的合法编排通过 */
    @Test
    void validate_flowWithErrorHandler_passes() {
        // START → DOWNLOADER → END
        //              ↓
        //         ERROR_HANDLER
        List<SpiderFlowNode> nodes = List.of(
                node("s", SpiderNodeType.START),
                node("d", SpiderNodeType.DOWNLOADER),
                node("eh", SpiderNodeType.ERROR_HANDLER),
                node("e", SpiderNodeType.END)
        );
        List<SpiderFlowEdge> edges = List.of(
                edge("e1", "s", "d"),
                edge("e2", "d", "e"),
                edge("e3", "d", "eh")
        );
        SpiderFlowDefinition flow = SpiderFlowDefinition.builder()
                .nodes(nodes).edges(edges).build();
        assertThat(validator.validate(flow).isValid()).isTrue();
    }

    // ── Property 3: 悬空节点必报错 ────────────────────────────────────────────

    /** Property 3: 悬空 DOWNLOADER 节点被标记为错误 */
    @Test
    void validate_danglingNode_containsNodeIdInError() {
        List<SpiderFlowNode> nodes = List.of(
                node("s", SpiderNodeType.START),
                node("dangling", SpiderNodeType.DOWNLOADER),  // 无任何连线
                node("e", SpiderNodeType.END)
        );
        List<SpiderFlowEdge> edges = List.of(edge("e1", "s", "e"));
        SpiderFlowDefinition flow = SpiderFlowDefinition.builder()
                .nodes(nodes).edges(edges).build();

        SpiderFlowValidationResult result = validator.validate(flow);
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(err -> err.contains("dangling"));
    }

    /** Property 3: 多个悬空节点各自被标记 */
    @Test
    void validate_multipleDanglingNodes_allMarked() {
        List<SpiderFlowNode> nodes = List.of(
                node("s", SpiderNodeType.START),
                node("d1", SpiderNodeType.DOWNLOADER),
                node("d2", SpiderNodeType.PARSER),
                node("e", SpiderNodeType.END)
        );
        List<SpiderFlowEdge> edges = List.of(edge("e1", "s", "e"));
        SpiderFlowDefinition flow = SpiderFlowDefinition.builder()
                .nodes(nodes).edges(edges).build();

        SpiderFlowValidationResult result = validator.validate(flow);
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(err -> err.contains("d1"));
        assertThat(result.getErrors()).anyMatch(err -> err.contains("d2"));
    }

    // ── 非法回环检测 ──────────────────────────────────────────────────────────

    @Test
    void validate_illegalCycleWithoutErrorHandler_fails() {
        // START → A → B → A (非法回环，无 ERROR_HANDLER)
        List<SpiderFlowNode> nodes = List.of(
                node("s", SpiderNodeType.START),
                node("a", SpiderNodeType.DOWNLOADER),
                node("b", SpiderNodeType.PARSER),
                node("e", SpiderNodeType.END)
        );
        List<SpiderFlowEdge> edges = List.of(
                edge("e1", "s", "a"),
                edge("e2", "a", "b"),
                edge("e3", "b", "a"),  // 回环
                edge("e4", "b", "e")
        );
        SpiderFlowDefinition flow = SpiderFlowDefinition.builder()
                .nodes(nodes).edges(edges).build();

        SpiderFlowValidationResult result = validator.validate(flow);
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(err -> err.contains("非法回环") || err.contains("cycle") || err.contains("回环"));
    }

    @Test
    void validate_cycleViaErrorHandler_passes() {
        // START → DOWNLOADER → ERROR_HANDLER → DOWNLOADER (经过 ERROR_HANDLER 的回环，合法)
        //              ↓
        //             END
        List<SpiderFlowNode> nodes = List.of(
                node("s", SpiderNodeType.START),
                node("d", SpiderNodeType.DOWNLOADER),
                node("eh", SpiderNodeType.ERROR_HANDLER),
                node("e", SpiderNodeType.END)
        );
        List<SpiderFlowEdge> edges = List.of(
                edge("e1", "s", "d"),
                edge("e2", "d", "eh"),
                edge("e3", "eh", "d"),  // 经过 ERROR_HANDLER 的回环
                edge("e4", "d", "e")
        );
        SpiderFlowDefinition flow = SpiderFlowDefinition.builder()
                .nodes(nodes).edges(edges).build();

        // 经过 ERROR_HANDLER 的回环不应报非法回环错误
        SpiderFlowValidationResult result = validator.validate(flow);
        boolean hasIllegalCycleError = result.getErrors().stream()
                .anyMatch(err -> err.contains("非法回环") || err.contains("回环"));
        assertThat(hasIllegalCycleError).isFalse();
    }

    // ── START/END 缺失 ────────────────────────────────────────────────────────

    @Test
    void validate_missingStart_fails() {
        List<SpiderFlowNode> nodes = List.of(node("e", SpiderNodeType.END));
        SpiderFlowDefinition flow = SpiderFlowDefinition.builder()
                .nodes(nodes).edges(List.of()).build();
        assertThat(validator.validate(flow).isValid()).isFalse();
    }

    @Test
    void validate_missingEnd_fails() {
        List<SpiderFlowNode> nodes = List.of(node("s", SpiderNodeType.START));
        SpiderFlowDefinition flow = SpiderFlowDefinition.builder()
                .nodes(nodes).edges(List.of()).build();
        assertThat(validator.validate(flow).isValid()).isFalse();
    }

    @Test
    void validate_startNotReachableToEnd_fails() {
        // START 和 END 存在但没有连线
        List<SpiderFlowNode> nodes = List.of(
                node("s", SpiderNodeType.START),
                node("e", SpiderNodeType.END)
        );
        SpiderFlowDefinition flow = SpiderFlowDefinition.builder()
                .nodes(nodes).edges(List.of()).build();
        assertThat(validator.validate(flow).isValid()).isFalse();
    }

    // ── 辅助方法 ──────────────────────────────────────────────────────────────

    private SpiderFlowDefinition buildLinearFlow(SpiderNodeType... types) {
        List<SpiderFlowNode> nodes = new ArrayList<>();
        List<SpiderFlowEdge> edges = new ArrayList<>();
        for (int i = 0; i < types.length; i++) {
            nodes.add(node("n" + i, types[i]));
        }
        for (int i = 0; i < types.length - 1; i++) {
            edges.add(edge("e" + i, "n" + i, "n" + (i + 1)));
        }
        return SpiderFlowDefinition.builder().nodes(nodes).edges(edges).build();
    }

    private SpiderFlowNode node(String id, SpiderNodeType type) {
        return SpiderFlowNode.builder().nodeId(id).nodeType(type).build();
    }

    private SpiderFlowEdge edge(String id, String from, String to) {
        return SpiderFlowEdge.builder().edgeId(id).sourceNodeId(from).targetNodeId(to).build();
    }
}
