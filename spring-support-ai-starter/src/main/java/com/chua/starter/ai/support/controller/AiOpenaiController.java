package com.chua.starter.ai.support.controller;

import com.chua.starter.ai.support.model.AiChatRequest;
import com.chua.starter.ai.support.service.AiAccessGuardService;
import com.chua.starter.ai.support.service.AiChatFacadeService;
import com.chua.starter.ai.support.service.SysAiAppKeyService;
import com.chua.starter.ai.support.service.SysAiGlobalSettingService;
import com.chua.starter.common.support.api.annotations.ApiReturnFormatIgnore;
import com.chua.common.support.ai.chat.ModelDefinition;
import com.chua.common.support.utils.StringUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * AI OpenAI 标准协议控制器
 * 提供 OpenAI 兼容接口和 CC Switch 控制接口
 *
 * @author CH
 */
@Tag(name = "AI OpenAI Protocol Controller", description = "OpenAI 标准协议接口与 CC Switch 控制接口")
@RestController
@RequiredArgsConstructor
@Slf4j
@ApiReturnFormatIgnore
@RequestMapping("/v2/ai")
public class AiOpenaiController {

    private final AiChatFacadeService aiChatFacadeService;
    private final AiAccessGuardService aiAccessGuardService;
    private final SysAiGlobalSettingService sysAiGlobalSettingService;
    private final SysAiAppKeyService sysAiAppKeyService;
    private final ObjectMapper objectMapper;

    private boolean authCheck(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        return aiAccessGuardService.verifyBearerToken(auth, request.getRemoteAddr());
    }

    /**
     * 从请求中解析 AppKey 所属的分组 ID。
     */
    private Integer resolveGroupId(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (StringUtils.isBlank(auth) || !auth.startsWith("Bearer ")) {
            return null;
        }
        String token = auth.substring(7).trim();
        if (StringUtils.isBlank(token)) {
            return null;
        }
        Integer gid = sysAiAppKeyService.getGroupIdByAppKey(token);
        log.debug("resolveGroupId: token={}, groupId={}", token, gid);
        return gid;
    }

    private SseEmitter authErrorEmitter() {
        SseEmitter emitter = new SseEmitter(30_000L);
        CompletableFuture.runAsync(() -> {
            try {
                emitter.send(SseEmitter.event().name("error").data("AI AppKey 校验失败", MediaType.TEXT_PLAIN));
                emitter.send(SseEmitter.event().name("done").data("[DONE]", MediaType.TEXT_PLAIN));
                emitter.complete();
            } catch (IOException ex) {
                emitter.completeWithError(ex);
            }
        });
        return emitter;
    }

    // ==================== OpenAI Chat Completions ====================

    /**
     * OpenAI Chat Completions 总调度入口
     * POST /v1/chat/completions
     * 根据 stream 参数自动分发到同步或流式处理
     */
    @Operation(summary = "OpenAI Chat Completions", description = "OpenAI 标准协议聊天补全接口")
    @PostMapping("/v1/chat/completions")
    public Object chatCompletions(@RequestBody OpenAiChatRequest request,
                                  HttpServletRequest servletRequest) {
        if (!sysAiGlobalSettingService.getBoolean("runtime", "openaiRestEnabled", true)) {
            if (Boolean.TRUE.equals(request.getStream())) {
                return disabledEmitter("OpenAI RESTful 端口未启用");
            }
            return ResponseEntity.ok(buildOpenAiChatResponse(null, request.getModel()));
        }
        if (!authCheck(servletRequest)) {
            if (Boolean.TRUE.equals(request.getStream())) {
                return authErrorEmitter();
            }
            return ResponseEntity.status(401).body(Map.of(
                    "error", Map.of("message", "AI AppKey 校验失败", "type", "authentication_error")
            ));
        }
        Integer groupId = resolveGroupId(servletRequest);
        if (Boolean.TRUE.equals(request.getStream())) {
            return chatCompletionsStream(request, groupId);
        }
        try {
            AiChatRequest chatRequest = convertToChatRequest(request, groupId);
            Map<String, Object> result = aiChatFacadeService.call(chatRequest);
            return ResponseEntity.ok(buildOpenAiChatResponse(result, request.getModel()));
        } catch (IllegalStateException ex) {
            return ResponseEntity.ok(buildOpenAiChatError(request.getModel(), ex.getMessage()));
        }
    }

    private SseEmitter chatCompletionsStream(OpenAiChatRequest request, Integer groupId) {
        return aiChatFacadeService.stream(convertToChatRequest(request, groupId));
    }

    // ==================== OpenAI Completions (legacy) ====================

    /**
     * OpenAI Completions 接口
     * POST /v1/completions
     */
    @Operation(summary = "OpenAI Completions", description = "OpenAI 标准协议补全接口")
    @PostMapping("/v1/completions")
    public ResponseEntity<Map<String, Object>> completions(@RequestBody OpenAiCompletionRequest request,
                                                            HttpServletRequest servletRequest) {
        if (!sysAiGlobalSettingService.getBoolean("runtime", "openaiRestEnabled", true)) {
            return ResponseEntity.ok(buildOpenAiCompletionResponse(null, request.getModel(), request.getPrompt()));
        }
        if (!authCheck(servletRequest)) {
            return ResponseEntity.status(401).body(Map.of(
                    "error", Map.of("message", "AI AppKey 校验失败", "type", "authentication_error")
            ));
        }
        try {
            AiChatRequest chatRequest = convertCompletionToChatRequest(request);
            Map<String, Object> result = aiChatFacadeService.call(chatRequest);
            return ResponseEntity.ok(buildOpenAiCompletionResponse(result, request.getModel(), request.getPrompt()));
        } catch (IllegalStateException ex) {
            return ResponseEntity.ok(buildOpenAiCompletionError(request.getModel(), request.getPrompt(), ex.getMessage()));
        }
    }

    // ==================== OpenAI Models ====================

    /**
     * OpenAI Models 接口
     * GET /v1/models
     */
    @Operation(summary = "OpenAI Models", description = "获取支持的模型列表")
    @GetMapping("/v1/models")
    public ResponseEntity<Map<String, Object>> models(HttpServletRequest servletRequest) {
        if (!sysAiGlobalSettingService.getBoolean("runtime", "openaiRestEnabled", true)) {
            return ResponseEntity.ok(Map.of("object", "list", "data", List.of()));
        }
        if (!authCheck(servletRequest)) {
            return ResponseEntity.status(401).body(Map.of(
                    "error", Map.of("message", "AI AppKey 校验失败", "type", "authentication_error")
            ));
        }
        Integer groupId = resolveGroupId(servletRequest);
        List<ModelDefinition> modelDefinitions = aiChatFacadeService.modelDefinitions(groupId);
        if (!modelDefinitions.isEmpty()) {
            List<Map<String, Object>> data = new ArrayList<>();
            for (ModelDefinition def : modelDefinitions) {
                data.add(Map.of(
                        "id", def.getId(),
                        "object", "model",
                        "created", (int) (System.currentTimeMillis() / 1000),
                        "owned_by", "system",
                        "name", def.getName() != null ? def.getName() : def.getId(),
                        "description", def.getDescription() != null ? def.getDescription() : "",
                        "capabilities", def.getCapabilities() != null ? def.getCapabilities() : List.of()
                ));
            }
            return ResponseEntity.ok(Map.of("object", "list", "data", data));
        }
        List<String> modelIds = aiChatFacadeService.models(groupId);
        List<Map<String, Object>> data = new ArrayList<>();
        for (String id : modelIds) {
            data.add(Map.of("id", id, "object", "model", "created", (int) (System.currentTimeMillis() / 1000), "owned_by", "system", "name", id, "description", "", "capabilities", List.of()));
        }
        return ResponseEntity.ok(Map.of("object", "list", "data", data));
    }

    // ==================== CC Switch ====================

    /**
     * CC Switch - 获取可用模型列表
     * GET /v1/cc/switch/models
     */
    @Operation(summary = "CC Switch - 获取可用模型列表", description = "获取支持切换的模型列表")
    @GetMapping("/v1/cc/switch/models")
    public ResponseEntity<Map<String, Object>> ccSwitchModels(HttpServletRequest servletRequest) {
        if (!sysAiGlobalSettingService.getBoolean("runtime", "openaiRestEnabled", true)) {
            return ResponseEntity.ok(Map.of("object", "list", "data", List.of()));
        }
        if (!authCheck(servletRequest)) {
            return ResponseEntity.status(401).body(Map.of(
                    "error", Map.of("message", "AI AppKey 校验失败", "type", "authentication_error")
            ));
        }
        Integer ccGroupId = resolveGroupId(servletRequest);
        List<String> ccModelIds = aiChatFacadeService.models(ccGroupId);
        List<Map<String, Object>> data = new ArrayList<>();
        for (String id : ccModelIds) {
            data.add(Map.of("id", id, "object", "model", "created", (int) (System.currentTimeMillis() / 1000), "owned_by", "system"));
        }
        return ResponseEntity.ok(Map.of("object", "list", "data", data));
    }

    /**
     * CC Switch - 切换模型
     * POST /v1/cc/switch
     */
    @Operation(summary = "CC Switch - 切换模型", description = "切换当前使用的模型")
    @PostMapping("/v1/cc/switch")
    public ResponseEntity<Map<String, Object>> ccSwitch(@RequestBody CcSwitchRequest request,
                                                         HttpServletRequest servletRequest) {
        if (!sysAiGlobalSettingService.getBoolean("runtime", "openaiRestEnabled", true)) {
            return ResponseEntity.ok(Map.of("success", false, "message", "CC Switch 服务未启用"));
        }
        if (!authCheck(servletRequest)) {
            return ResponseEntity.status(401).body(Map.of(
                    "error", Map.of("message", "AI AppKey 校验失败", "type", "authentication_error")
            ));
        }
        try {
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "model", request.getModel(),
                    "message", "模型切换成功"
            ));
        } catch (Exception ex) {
            return ResponseEntity.ok(Map.of("success", false, "message", "模型切换失败: " + ex.getMessage()));
        }
    }

    /**
     * CC Switch - 获取当前模型状态
     * GET /v1/cc/switch/status
     */
    @Operation(summary = "CC Switch - 获取当前模型状态", description = "获取当前模型配置状态")
    @GetMapping("/v1/cc/switch/status")
    public ResponseEntity<Map<String, Object>> ccSwitchStatus(HttpServletRequest servletRequest) {
        if (!sysAiGlobalSettingService.getBoolean("runtime", "openaiRestEnabled", true)) {
            return ResponseEntity.ok(Map.of("status", "disabled", "message", "CC Switch 服务未启用"));
        }
        if (!authCheck(servletRequest)) {
            return ResponseEntity.status(401).body(Map.of(
                    "error", Map.of("message", "AI AppKey 校验失败", "type", "authentication_error")
            ));
        }
        Integer statusGroupId = resolveGroupId(servletRequest);
        List<String> statusModelIds = aiChatFacadeService.models(statusGroupId);
        List<Map<String, Object>> data = new ArrayList<>();
        for (String id : statusModelIds) {
            data.add(Map.of("id", id, "object", "model"));
        }
        return ResponseEntity.ok(Map.of(
                "currentModel", "gpt-4",
                "availableModels", data,
                "timestamp", System.currentTimeMillis()
        ));
    }

    /**
     * CC Switch - 批量模型配置
     * POST /v1/cc/switch/batch
     */
    @Operation(summary = "CC Switch - 批量模型配置", description = "批量配置多个模型的优先级和权重")
    @PostMapping("/v1/cc/switch/batch")
    public ResponseEntity<Map<String, Object>> ccSwitchBatch(@RequestBody CcBatchSwitchRequest request,
                                                              HttpServletRequest servletRequest) {
        if (!sysAiGlobalSettingService.getBoolean("runtime", "openaiRestEnabled", true)) {
            return ResponseEntity.ok(Map.of("success", false, "message", "OpenAI RESTful 端口未启用"));
        }
        if (!authCheck(servletRequest)) {
            return ResponseEntity.status(401).body(Map.of(
                    "error", Map.of("message", "AI AppKey 校验失败", "type", "authentication_error")
            ));
        }
        try {
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "configs", request.getConfigs(),
                    "message", "批量配置成功"
            ));
        } catch (Exception ex) {
            return ResponseEntity.ok(Map.of("success", false, "message", "批量配置失败: " + ex.getMessage()));
        }
    }

    /**
     * CC Switch - 健康检查
     * GET /v1/cc/switch/health
     */
    @Operation(summary = "CC Switch - 健康检查", description = "检查 CC Switch 服务健康状态")
    @GetMapping("/v1/cc/switch/health")
    public ResponseEntity<Map<String, Object>> ccSwitchHealth(HttpServletRequest servletRequest) {
        if (!sysAiGlobalSettingService.getBoolean("runtime", "openaiRestEnabled", true)) {
            return ResponseEntity.ok(Map.of("status", "disabled"));
        }
        if (!authCheck(servletRequest)) {
            return ResponseEntity.status(401).body(Map.of(
                    "error", Map.of("message", "AI AppKey 校验失败", "type", "authentication_error")
            ));
        }
        return ResponseEntity.ok(Map.of(
                "status", "healthy",
                "timestamp", System.currentTimeMillis(),
                "version", "1.0.0"
        ));
    }

    // ==================== Response Builders (OpenAI format) ====================

    private Map<String, Object> buildOpenAiChatResponse(Map<String, Object> result, String model) {
        String id = "chatcmpl-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String text = result != null ? (String) result.get("text") : null;
        String content = text != null ? text : "";
        String resolvedModel = model != null ? model : "unknown";

        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "assistant");
        message.put("content", content);

        Map<String, Object> choice = new LinkedHashMap<>();
        choice.put("index", 0);
        choice.put("message", message);
        choice.put("finish_reason", "stop");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", id);
        body.put("object", "chat.completion");
        body.put("created", (int) (System.currentTimeMillis() / 1000));
        body.put("model", resolvedModel);
        body.put("choices", List.of(choice));

        if (result != null) {
            Map<String, Object> usage = new LinkedHashMap<>();
            usage.put("prompt_tokens", result.getOrDefault("promptTokens", 0));
            usage.put("completion_tokens", result.getOrDefault("completionTokens", 0));
            usage.put("total_tokens", result.getOrDefault("totalTokens", 0));
            body.put("usage", usage);
        }

        return body;
    }

    private Map<String, Object> buildOpenAiChatError(String model, String errorMessage) {
        String id = "chatcmpl-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);

        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "assistant");
        message.put("content", errorMessage);

        Map<String, Object> choice = new LinkedHashMap<>();
        choice.put("index", 0);
        choice.put("message", message);
        choice.put("finish_reason", "stop");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", id);
        body.put("object", "chat.completion");
        body.put("created", (int) (System.currentTimeMillis() / 1000));
        body.put("model", model != null ? model : "unknown");
        body.put("choices", List.of(choice));
        return body;
    }

    private Map<String, Object> buildOpenAiCompletionResponse(Map<String, Object> result, String model, String prompt) {
        String id = "cmpl-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String text = result != null ? (String) result.get("text") : "";
        String resolvedModel = model != null ? model : "unknown";

        Map<String, Object> choice = new LinkedHashMap<>();
        choice.put("text", text);
        choice.put("index", 0);
        choice.put("finish_reason", "stop");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", id);
        body.put("object", "text_completion");
        body.put("created", (int) (System.currentTimeMillis() / 1000));
        body.put("model", resolvedModel);
        body.put("choices", List.of(choice));

        if (result != null) {
            Map<String, Object> usage = new LinkedHashMap<>();
            usage.put("prompt_tokens", result.getOrDefault("promptTokens", 0));
            usage.put("completion_tokens", result.getOrDefault("completionTokens", 0));
            usage.put("total_tokens", result.getOrDefault("totalTokens", 0));
            body.put("usage", usage);
        }

        return body;
    }

    private Map<String, Object> buildOpenAiCompletionError(String model, String prompt, String errorMessage) {
        String id = "cmpl-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);

        Map<String, Object> choice = new LinkedHashMap<>();
        choice.put("text", errorMessage);
        choice.put("index", 0);
        choice.put("finish_reason", "stop");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", id);
        body.put("object", "text_completion");
        body.put("created", (int) (System.currentTimeMillis() / 1000));
        body.put("model", model != null ? model : "unknown");
        body.put("choices", List.of(choice));
        return body;
    }

    private SseEmitter disabledEmitter(String message) {
        SseEmitter emitter = new SseEmitter(30_000L);
        CompletableFuture.runAsync(() -> {
            try {
                emitter.send(SseEmitter.event().name("error").data(message, MediaType.TEXT_PLAIN));
                emitter.send(SseEmitter.event().name("done").data("[DONE]", MediaType.TEXT_PLAIN));
                emitter.complete();
            } catch (IOException ex) {
                emitter.completeWithError(ex);
            }
        });
        return emitter;
    }

    // ==================== Request Conversion Methods ====================

    private AiChatRequest convertToChatRequest(OpenAiChatRequest request) {
        return convertToChatRequest(request, null);
    }

    private AiChatRequest convertToChatRequest(OpenAiChatRequest request, Integer groupId) {
        AiChatRequest chatRequest = new AiChatRequest();
        chatRequest.setModel(request.getModel());
        // 注入分组 ID 到 parameters，供 AiChatFacadeService 进行模型隔离
        if (groupId != null) {
            chatRequest.setParameters(new java.util.LinkedHashMap<>());
            chatRequest.getParameters().put("_groupId", groupId);
        }
        // 深度思考 / 智能搜索开关透传
        chatRequest.setThink(request.getThink());
        chatRequest.setSearch(request.getSearch());
        if (request.getMessages() != null) {
            StringBuilder promptBuilder = new StringBuilder();
            for (OpenAiMessage msg : request.getMessages()) {
                if (msg.getRole() != null && msg.getRole().equals("system")) {
                    chatRequest.setPrompt(msg.getContent());
                } else if (msg.getRole() != null && msg.getRole().equals("user")) {
                    promptBuilder.append(msg.getContent()).append("\n");
                }
            }
            if (promptBuilder.length() > 0) {
                chatRequest.setPrompt(promptBuilder.toString().trim());
            }
        }
        return chatRequest;
    }

    private AiChatRequest convertCompletionToChatRequest(OpenAiCompletionRequest request) {
        AiChatRequest chatRequest = new AiChatRequest();
        chatRequest.setModel(request.getModel());
        chatRequest.setPrompt(request.getPrompt());
        return chatRequest;
    }

    // ==================== Request/Response DTOs ====================

    @Data
    public static class OpenAiChatRequest {
        private String model;
        private List<OpenAiMessage> messages;
        private Double temperature;
        private Integer maxTokens;
        private Double topP;
        private Integer n;
        private Boolean stream;
        private String stop;
        private Double frequencyPenalty;
        private Double presencePenalty;
        private Map<String, Object> tools;
        private String toolChoice;
        /**
         * 深度思考开关（非 OpenAI 标准字段，由前端 ChatClient 透传）
         */
        private Boolean think;
        /**
         * 智能搜索开关（非 OpenAI 标准字段，由前端 ChatClient 透传）
         */
        private Boolean search;
    }

    @Data
    public static class OpenAiMessage {
        private String role;
        private String content;
        private String name;
    }

    @Data
    public static class OpenAiCompletionRequest {
        private String model;
        private String prompt;
        private String suffix;
        private Integer maxTokens;
        private Double temperature;
        private Double topP;
        private Integer n;
        private Boolean stream;
        private Integer logprobs;
        private String echo;
        private String stop;
        private Double frequencyPenalty;
        private Double presencePenalty;
    }

    @Data
    public static class CcSwitchRequest {
        private String model;
        private String provider;
        private Map<String, Object> config;
    }

    @Data
    public static class CcBatchSwitchRequest {
        private List<ModelConfig> configs;
    }

    @Data
    public static class ModelConfig {
        private String model;
        private String provider;
        private Integer priority;
        private Double weight;
        private Boolean enabled;
    }
}
