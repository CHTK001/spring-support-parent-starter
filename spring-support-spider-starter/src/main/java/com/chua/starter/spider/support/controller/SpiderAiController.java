package com.chua.starter.spider.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.spider.support.domain.enums.SpiderNodeType;
import com.chua.starter.spider.support.service.SpiderAiService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 接口。
 *
 * @author CH
 */
@RestController
@RequiredArgsConstructor
public class SpiderAiController {

    private final SpiderAiService aiService;

    /** GET /v1/spider/ai/status */
    @GetMapping("/v1/spider/ai/status")
    public ReturnResult<Map<String, Object>> status() {
        return ReturnResult.ok(Map.of("available", aiService.isAvailable()));
    }

    /** POST /v1/spider/tasks/{taskId}/ai/review */
    @PostMapping("/v1/spider/tasks/{taskId}/ai/review")
    public ReturnResult<?> review(@PathVariable Long taskId,
                                  @RequestBody(required = false) AiReviewRequest request) {
        List<String> logs = request != null ? request.recentLogs() : null;
        List<String> fails = request != null ? request.failSamples() : null;
        SpiderAiService.AiReviewResult result = aiService.reviewTask(taskId, logs, fails);
        return ReturnResult.ok(result);
    }

    /** POST /v1/spider/tasks/{taskId}/nodes/{nodeId}/ai/suggest */
    @PostMapping("/v1/spider/tasks/{taskId}/nodes/{nodeId}/ai/suggest")
    public ReturnResult<?> suggest(@PathVariable Long taskId,
                                   @PathVariable String nodeId,
                                   @RequestParam(required = false) String nodeType) {
        SpiderNodeType type = nodeType != null ? SpiderNodeType.valueOf(nodeType.toUpperCase())
                : SpiderNodeType.DOWNLOADER;
        SpiderAiService.AiNodeSuggestion suggestion = aiService.suggestNode(taskId, nodeId, type);
        return ReturnResult.ok(suggestion);
    }

    /** POST /v1/spider/tasks/{taskId}/nodes/{nodeId}/ai/apply */
    @PostMapping("/v1/spider/tasks/{taskId}/nodes/{nodeId}/ai/apply")
    public ReturnResult<?> apply(@PathVariable Long taskId,
                                 @PathVariable String nodeId,
                                 @RequestBody SpiderAiService.AiNodeSuggestion suggestion) {
        try {
            aiService.applyNodeSuggestion(taskId, nodeId, suggestion);
            return ReturnResult.ok(Map.of("message", "建议已应用"));
        } catch (IllegalArgumentException e) {
            return ReturnResult.illegal(e.getMessage());
        }
    }

    /** GET /v1/spider/ai/brain-config — 查询可用 AI 提供商和模型列表 */
    @GetMapping("/v1/spider/ai/brain-config")
    public ReturnResult<?> brainConfig() {
        Map<String, List<String>> providerModels = new LinkedHashMap<>();
        providerModels.put("openai", List.of("gpt-4o", "gpt-4-turbo", "gpt-3.5-turbo"));
        providerModels.put("zhipu", List.of("glm-4", "glm-4-flash", "glm-3-turbo"));
        providerModels.put("deepseek", List.of("deepseek-chat", "deepseek-coder", "deepseek-reasoner"));
        providerModels.put("qwen", List.of("qwen-max", "qwen-plus", "qwen-turbo", "qwen-long"));
        providerModels.put("ollama", List.of("llama3", "llama3.1", "mistral", "qwen2", "deepseek-r1"));

        List<Map<String, Object>> providers = providerModels.entrySet().stream()
                .map(e -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("name", e.getKey());
                    item.put("models", e.getValue());
                    return item;
                })
                .toList();

        return ReturnResult.ok(Map.of("providers", providers));
    }

    /** POST /v1/spider/ai/test — 测试 AI 连通性，返回响应时间 */
    @PostMapping("/v1/spider/ai/test")
    public ReturnResult<?> testAi(@RequestBody AiTestRequest request) {
        if (request.provider() == null || request.provider().isBlank()) {
            return ReturnResult.illegal("provider 不能为空");
        }
        if (request.model() == null || request.model().isBlank()) {
            return ReturnResult.illegal("model 不能为空");
        }

        long start = System.currentTimeMillis();
        try {
            boolean available = aiService.isAvailable();
            long responseTimeMs = System.currentTimeMillis() - start;
            if (available) {
                return ReturnResult.ok(Map.of(
                        "success", true,
                        "responseTimeMs", responseTimeMs,
                        "message", "AI 服务连通正常，提供商: " + request.provider() + "，模型: " + request.model()
                ));
            } else {
                return ReturnResult.ok(Map.of(
                        "success", false,
                        "responseTimeMs", responseTimeMs,
                        "message", "AI 服务当前不可用"
                ));
            }
        } catch (Exception e) {
            long responseTimeMs = System.currentTimeMillis() - start;
            return ReturnResult.ok(Map.of(
                    "success", false,
                    "responseTimeMs", responseTimeMs,
                    "message", "连通性测试失败: " + e.getMessage()
            ));
        }
    }

    record AiReviewRequest(List<String> recentLogs, List<String> failSamples) {}

    record AiTestRequest(String provider, String model, String apiKey) {}
}
