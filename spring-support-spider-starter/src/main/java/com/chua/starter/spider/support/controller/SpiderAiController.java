package com.chua.starter.spider.support.controller;

import com.chua.starter.spider.support.domain.enums.SpiderNodeType;
import com.chua.starter.spider.support.service.SpiderAiService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(Map.of("available", aiService.isAvailable()));
    }

    /** POST /v1/spider/tasks/{taskId}/ai/review */
    @PostMapping("/v1/spider/tasks/{taskId}/ai/review")
    public ResponseEntity<?> review(@PathVariable Long taskId,
                                    @RequestBody(required = false) AiReviewRequest request) {
        List<String> logs = request != null ? request.recentLogs() : null;
        List<String> fails = request != null ? request.failSamples() : null;
        SpiderAiService.AiReviewResult result = aiService.reviewTask(taskId, logs, fails);
        return ResponseEntity.ok(result);
    }

    /** POST /v1/spider/tasks/{taskId}/nodes/{nodeId}/ai/suggest */
    @PostMapping("/v1/spider/tasks/{taskId}/nodes/{nodeId}/ai/suggest")
    public ResponseEntity<?> suggest(@PathVariable Long taskId,
                                     @PathVariable String nodeId,
                                     @RequestParam(required = false) String nodeType) {
        SpiderNodeType type = nodeType != null ? SpiderNodeType.valueOf(nodeType.toUpperCase())
                : SpiderNodeType.DOWNLOADER;
        SpiderAiService.AiNodeSuggestion suggestion = aiService.suggestNode(taskId, nodeId, type);
        return ResponseEntity.ok(suggestion);
    }

    /** POST /v1/spider/tasks/{taskId}/nodes/{nodeId}/ai/apply */
    @PostMapping("/v1/spider/tasks/{taskId}/nodes/{nodeId}/ai/apply")
    public ResponseEntity<?> apply(@PathVariable Long taskId,
                                   @PathVariable String nodeId,
                                   @RequestBody SpiderAiService.AiNodeSuggestion suggestion) {
        try {
            aiService.applyNodeSuggestion(taskId, nodeId, suggestion);
            return ResponseEntity.ok(Map.of("message", "建议已应用"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    record AiReviewRequest(List<String> recentLogs, List<String> failSamples) {}
}
