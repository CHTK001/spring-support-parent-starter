package com.chua.starter.spider.support.service.impl;

import com.alibaba.fastjson2.JSON;
import com.chua.common.support.ai.brain.Brain;
import com.chua.common.support.ai.brain.BrainRequest;
import com.chua.common.support.ai.brain.BrainResponse;
import com.chua.starter.spider.support.domain.SpiderFlowDefinition;
import com.chua.starter.spider.support.domain.SpiderFlowNode;
import com.chua.starter.spider.support.domain.SpiderTaskDefinition;
import com.chua.starter.spider.support.domain.enums.SpiderNodeType;
import com.chua.starter.spider.support.repository.SpiderFlowRepository;
import com.chua.starter.spider.support.repository.SpiderTaskRepository;
import com.chua.starter.spider.support.service.SpiderAiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 爬虫 AI 集成服务实现。
 *
 * <p>通过 Spring {@link ObjectProvider} 懒加载 {@link Brain}，
 * 若 ai-starter 不可用则自动降级，不影响任务编辑和执行功能。</p>
 *
 * @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SpiderAiServiceImpl implements SpiderAiService {

    private final SpiderTaskRepository taskRepository;
    private final SpiderFlowRepository flowRepository;
    private final ObjectProvider<Brain> brainProvider;

    // ── 11.1 reviewTask ───────────────────────────────────────────────────────

    @Override
    public AiReviewResult reviewTask(Long taskId, List<String> recentLogs, List<String> failSamples) {
        Brain brain = getBrain().orElse(null);
        if (brain == null) {
            return AiReviewResult.degrade("AI 服务不可用");
        }

        SpiderTaskDefinition task = taskRepository.getById(taskId);
        if (task == null) {
            return AiReviewResult.degrade("任务 [" + taskId + "] 不存在");
        }

        SpiderFlowDefinition flow = flowRepository.findByTaskId(taskId).orElse(null);

        try {
            String prompt = buildReviewPrompt(task, flow, recentLogs, failSamples);
            BrainResponse response = brain.ask(BrainRequest.builder().prompt(prompt).build());
            String report = response != null ? response.getContent() : "AI 未返回内容";
            return new AiReviewResult(true, report, null);
        } catch (Exception e) {
            log.warn("[Spider][AI] 任务审查失败, taskId={}", taskId, e);
            return AiReviewResult.degrade("AI 审查调用失败: " + e.getMessage());
        }
    }

    // ── 11.2 suggestNode ──────────────────────────────────────────────────────

    @Override
    public AiNodeSuggestion suggestNode(Long taskId, String nodeId, SpiderNodeType nodeType) {
        Brain brain = getBrain().orElse(null);
        if (brain == null) {
            return AiNodeSuggestion.degrade(nodeId, nodeType, "AI 服务不可用");
        }

        try {
            String prompt = buildNodeSuggestionPrompt(nodeType);
            BrainResponse response = brain.ask(BrainRequest.builder().prompt(prompt).build());
            String content = response != null ? response.getContent() : "";

            // 尝试解析 AI 返回的 JSON 配置建议
            Map<String, Object> suggestedConfig = tryParseConfig(content);
            return new AiNodeSuggestion(nodeId, nodeType, suggestedConfig, content);
        } catch (Exception e) {
            log.warn("[Spider][AI] 节点建议失败, taskId={}, nodeId={}", taskId, nodeId, e);
            return AiNodeSuggestion.degrade(nodeId, nodeType, "AI 建议调用失败: " + e.getMessage());
        }
    }

    // ── 11.3 applyNodeSuggestion ──────────────────────────────────────────────

    @Override
    public void applyNodeSuggestion(Long taskId, String nodeId, AiNodeSuggestion suggestion) {
        if (suggestion == null || suggestion.suggestedConfig().isEmpty()) {
            return;
        }

        SpiderFlowDefinition flow = flowRepository.findByTaskId(taskId)
                .orElseThrow(() -> new IllegalArgumentException("任务 [" + taskId + "] 编排不存在"));

        if (flow.getNodes() == null) return;

        for (SpiderFlowNode node : flow.getNodes()) {
            if (nodeId.equals(node.getNodeId())) {
                // 合并建议配置到节点配置
                if (node.getConfig() == null) {
                    node.setConfig(new java.util.HashMap<>(suggestion.suggestedConfig()));
                } else {
                    node.getConfig().putAll(suggestion.suggestedConfig());
                }
                break;
            }
        }

        flowRepository.saveFlow(flow);
        log.info("[Spider][AI] 节点建议已应用, taskId={}, nodeId={}", taskId, nodeId);
    }

    // ── 11.4 isAvailable ─────────────────────────────────────────────────────

    @Override
    public boolean isAvailable() {
        return getBrain().isPresent();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Optional<Brain> getBrain() {
        try {
            Brain brain = brainProvider.getIfAvailable();
            return Optional.ofNullable(brain);
        } catch (Exception e) {
            log.debug("[Spider][AI] Brain 不可用: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private String buildReviewPrompt(SpiderTaskDefinition task, SpiderFlowDefinition flow,
                                     List<String> recentLogs, List<String> failSamples) {
        StringBuilder sb = new StringBuilder();
        sb.append("请对以下爬虫任务编排进行完整性审查，指出缺失节点、脆弱节点和高风险规则：\n\n");
        sb.append("任务名称：").append(task.getTaskName()).append("\n");
        sb.append("入口 URL：").append(task.getEntryUrl()).append("\n");

        if (flow != null && flow.getNodes() != null) {
            sb.append("编排节点：").append(JSON.toJSONString(flow.getNodes())).append("\n");
        }
        if (recentLogs != null && !recentLogs.isEmpty()) {
            sb.append("最近日志：\n");
            recentLogs.stream().limit(10).forEach(l -> sb.append("  ").append(l).append("\n"));
        }
        if (failSamples != null && !failSamples.isEmpty()) {
            sb.append("失败样本：\n");
            failSamples.stream().limit(5).forEach(s -> sb.append("  ").append(s).append("\n"));
        }
        return sb.toString();
    }

    private String buildNodeSuggestionPrompt(SpiderNodeType nodeType) {
        return switch (nodeType) {
            case DOWNLOADER -> "请为爬虫下载器节点提供建议，包括：请求头建议、反爬建议、动态渲染建议、重试策略建议。以 JSON 格式返回配置建议。";
            case PARSER -> "请为爬虫解析器节点提供建议，包括：XPath/CSS 选择器建议、字段映射建议、结构化结果建议。以 JSON 格式返回配置建议。";
            case FILTER -> "请为爬虫过滤器节点提供建议，包括：脏数据诊断、去重规则建议、异常样本建议。以 JSON 格式返回配置建议。";
            case PIPELINE -> "请为爬虫管道节点提供建议，包括：字段类型推断、DDL 建议、输出介质建议。以 JSON 格式返回配置建议。";
            default -> "请为爬虫节点类型 " + nodeType + " 提供配置建议。";
        };
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> tryParseConfig(String content) {
        if (content == null || content.isBlank()) return Map.of();
        try {
            // 尝试从 AI 响应中提取 JSON 块
            int start = content.indexOf('{');
            int end = content.lastIndexOf('}');
            if (start >= 0 && end > start) {
                String jsonPart = content.substring(start, end + 1);
                return JSON.parseObject(jsonPart, Map.class);
            }
        } catch (Exception ignored) {
        }
        return Map.of();
    }
}
