package com.chua.starter.spider.support.service;

import com.chua.common.support.ai.brain.Brain;
import com.chua.common.support.ai.brain.BrainResponse;
import com.chua.starter.spider.support.domain.SpiderFlowDefinition;
import com.chua.starter.spider.support.domain.SpiderFlowEdge;
import com.chua.starter.spider.support.domain.SpiderFlowNode;
import com.chua.starter.spider.support.domain.SpiderTaskDefinition;
import com.chua.starter.spider.support.domain.enums.SpiderExecutionType;
import com.chua.starter.spider.support.domain.enums.SpiderNodeType;
import com.chua.starter.spider.support.domain.enums.SpiderTaskStatus;
import com.chua.starter.spider.support.repository.SpiderFlowRepository;
import com.chua.starter.spider.support.repository.SpiderTaskRepository;
import com.chua.starter.spider.support.service.impl.SpiderAiServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * SpiderAiService 单元测试（Mock ai-starter）。
 */
@ExtendWith(MockitoExtension.class)
class SpiderAiServiceTest {

    @Mock private SpiderTaskRepository taskRepository;
    @Mock private SpiderFlowRepository flowRepository;
    @Mock private ObjectProvider<Brain> brainProvider;
    @Mock private Brain brain;

    private SpiderAiService aiService;

    @BeforeEach
    void setUp() {
        aiService = new SpiderAiServiceImpl(taskRepository, flowRepository, brainProvider);
    }

    // ── 11.4 AI 不可用时降级 ──────────────────────────────────────────────────

    @Test
    void reviewTask_whenBrainUnavailable_returnsDegradeResult() {
        when(brainProvider.getIfAvailable()).thenReturn(null);

        SpiderAiService.AiReviewResult result = aiService.reviewTask(1L, null, null);

        assertThat(result.success()).isFalse();
        assertThat(result.degradeReason()).contains("AI 服务不可用");
    }

    @Test
    void suggestNode_whenBrainUnavailable_returnsDegradeResult() {
        when(brainProvider.getIfAvailable()).thenReturn(null);

        SpiderAiService.AiNodeSuggestion suggestion =
                aiService.suggestNode(1L, "dl-1", SpiderNodeType.DOWNLOADER);

        assertThat(suggestion.explanation()).contains("AI 服务不可用");
        assertThat(suggestion.suggestedConfig()).isEmpty();
    }

    @Test
    void isAvailable_whenBrainNull_returnsFalse() {
        when(brainProvider.getIfAvailable()).thenReturn(null);
        assertThat(aiService.isAvailable()).isFalse();
    }

    @Test
    void isAvailable_whenBrainPresent_returnsTrue() {
        when(brainProvider.getIfAvailable()).thenReturn(brain);
        assertThat(aiService.isAvailable()).isTrue();
    }

    // ── AI 可用时正常调用 ─────────────────────────────────────────────────────

    @Test
    void reviewTask_whenBrainAvailable_returnsReport() {
        Long taskId = 1L;
        SpiderTaskDefinition task = buildTask(taskId);
        SpiderFlowDefinition flow = buildFlow();
        BrainResponse response = mock(BrainResponse.class);

        when(brainProvider.getIfAvailable()).thenReturn(brain);
        when(taskRepository.getById(taskId)).thenReturn(task);
        when(flowRepository.findByTaskId(taskId)).thenReturn(Optional.of(flow));
        when(brain.ask(any())).thenReturn(response);
        when(response.getContent()).thenReturn("编排完整，无明显风险");

        SpiderAiService.AiReviewResult result = aiService.reviewTask(taskId, null, null);

        assertThat(result.success()).isTrue();
        assertThat(result.report()).isEqualTo("编排完整，无明显风险");
    }

    @Test
    void reviewTask_withRecentLogsAndFailSamples_callsBrainWithContext() {
        Long taskId = 2L;
        SpiderTaskDefinition task = buildTask(taskId);
        BrainResponse response = mock(BrainResponse.class);

        when(brainProvider.getIfAvailable()).thenReturn(brain);
        when(taskRepository.getById(taskId)).thenReturn(task);
        when(flowRepository.findByTaskId(taskId)).thenReturn(Optional.empty());
        when(brain.ask(any())).thenReturn(response);
        when(response.getContent()).thenReturn("发现潜在问题");

        List<String> logs = List.of("ERROR: timeout at 2026-04-15");
        List<String> fails = List.of("URL: https://example.com/page/1 - 404");

        SpiderAiService.AiReviewResult result = aiService.reviewTask(taskId, logs, fails);

        assertThat(result.success()).isTrue();
        verify(brain).ask(argThat(req ->
                req.getPrompt().contains("最近日志") && req.getPrompt().contains("失败样本")));
    }

    @Test
    void suggestNode_downloader_callsBrainWithDownloaderPrompt() {
        Long taskId = 3L;
        BrainResponse response = mock(BrainResponse.class);

        when(brainProvider.getIfAvailable()).thenReturn(brain);
        when(brain.ask(any())).thenReturn(response);
        when(response.getContent()).thenReturn("{\"userAgent\":\"Mozilla/5.0\"}");

        SpiderAiService.AiNodeSuggestion suggestion =
                aiService.suggestNode(taskId, "dl-1", SpiderNodeType.DOWNLOADER);

        assertThat(suggestion.nodeType()).isEqualTo(SpiderNodeType.DOWNLOADER);
        verify(brain).ask(argThat(req -> req.getPrompt().contains("下载器")));
    }

    @Test
    void reviewTask_whenBrainThrows_returnsDegradeResult() {
        Long taskId = 4L;
        SpiderTaskDefinition task = buildTask(taskId);

        when(brainProvider.getIfAvailable()).thenReturn(brain);
        when(taskRepository.getById(taskId)).thenReturn(task);
        when(flowRepository.findByTaskId(taskId)).thenReturn(Optional.empty());
        when(brain.ask(any())).thenThrow(new RuntimeException("AI 服务超时"));

        SpiderAiService.AiReviewResult result = aiService.reviewTask(taskId, null, null);

        assertThat(result.success()).isFalse();
        assertThat(result.degradeReason()).contains("AI 审查调用失败");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private SpiderTaskDefinition buildTask(Long id) {
        return SpiderTaskDefinition.builder()
                .id(id).taskName("测试任务").entryUrl("https://example.com")
                .executionType(SpiderExecutionType.ONCE).status(SpiderTaskStatus.READY).build();
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
