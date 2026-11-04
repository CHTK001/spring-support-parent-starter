// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.ai.support.controller;

import com.chua.common.support.utils.StringUtils;
import com.chua.starter.ai.support.configuration.client.EffectiveChatModelResolver;
import com.chua.starter.ai.support.model.AiChatRequest;
import com.chua.starter.ai.support.service.AiChatFacadeService;
import com.chua.starter.ai.support.service.AppKeyModelScope;
import com.chua.starter.common.support.api.annotations.ApiReturnFormatIgnore;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * AI 多协议标准网关控制器。
 *
 * <p>提供与 {@code AiProtocolServerFilter} 等价的 Spring MVC 路由映射，
 * 将 Claude、打开AI 响应、Gemini 等外部协议请求收敛到统一的 appKey 鉴权与对话链路。</p>
 *
 * <h3>架构</h3>
 * <p>三个入口（Claude Messages、Responses、Gemini）含义不同，但前置校验与对话执行流是完全相同
 * 的：{@code restEnabled() → authCheck() → 提示词提取 → aiChatFacadeService.call() → 协议化表达}。
 * 公共部分收归 {@link #route(HttpServletRequest, Object, ProtocolAdapter)} 与统一的工具方法，
 * 协议相关的请求/响应形态由各 {@link ProtocolAdapter} 实现。</p>
 *
 * <h3>接口列表</h3>
 * <ul>
 *   <li>Claude Messages — {@code POST /v2/v1/messages}</li>
 *   <li>OpenAI Responses — {@code POST /v2/v1/responses}</li>
 *   <li>Gemini — {@code POST /v2/v1beta/models/...}</li>
 * </ul>
 *
 * <p>继承 {@link BaseAiController} 统一使用 Bearer Token 鉴权与分组解析。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Tag(name = "AI Protocol Gateway Controller", description = "Claude / Responses / Gemini 等第三方协议兼容入口")
@RestController
@RequiredArgsConstructor
@Slf4j
@ApiReturnFormatIgnore
@RequestMapping("/v2")
public class AiProtocolController extends BaseAiController {

    /**
     * AI 对话门面服务，负责模型路由与对话执行
     */
    private final AiChatFacadeService aiChatFacadeService;

    /**
     * 生效对话模型解析器（连接器级 → Bot 默认 → 配置中心默认）
     */
    private final EffectiveChatModelResolver effectiveChatModelResolver;

    /**
     * 协议适配器：描述一个协议族全部的形状（请求/响应/流式），支撑 {@link #route} 统一分发。
     *
     * <p>协议相关的不确定性（提示词提取、流式事件排列、响应体结构）各协议之间不可能通用，
     * 所以由各协议单独实现；鉴权、分组、分组参数、前置开关等流动保证只写一次。</p>
     */
    interface ProtocolAdapter {

        /**
         * 协议族标识，用于日志与监控区分。
         *
         * @return 协议族名
         */
        String family();

        /**
         * 请求体中的模型字段，用于在响应中回显（优先请求携带，其次配置中心默认）。
         *
         * @param body 原始请求体
         * @return 模型名，未携带时为空
         */
        String modelOf(Map<String, Object> body);

        /**
         * 是否为流式调用（语义由协议定义：Claude / Responses 走 body.stream，Gemini 走路径后缀）。
         *
         * @param servletRequest 原始 HTTP 请求
         * @param body           原始请求体，可为 null（Gemini 允许空体）
         * @return 是否流式
         */
        boolean stream(HttpServletRequest servletRequest, Object body);

        /**
         * 是否强制要求请求体非空（Gemini 允许空体）。
         *
         * @return 强制时返回 true
         */
        boolean requestBodyRequired();

        /**
         * 协议化组装对话请求（提示词提取、系统提示词、分组注入）。
         *
         * @param servletRequest HTTP 请求
         * @param body           原始请求体
         * @return 对话请求
         */
        AiChatRequest request(HttpServletRequest servletRequest, Object body);

        /**
         * 同步（非流式）对话成功响应。
         *
         * @param result 对话结果
         * @param model 模型名
         * @return 协议化响应体
         */
        Object success(Map<String, Object> result, String model);

        /**
         * 流式 SSE 响应。
         *
         * @param body           原始请求体
         * @param model          模型名
         * @param servletRequest HTTP 请求
         * @return SSE 发射器或协议化响应体
         */
        Object stream(Object body, String model, HttpServletRequest servletRequest);

        /**
         * 模型调用失败时的协议化响应。
         *
         * @param model        模型名，可为空
         * @param errorMessage 失败原因
         * @return 协议化响应体
         */
        Object failure(String model, String errorMessage);

        /**
         * REST 协议未启用时的协议化响应。
         *
         * @return 协议化响应体
         */
        Object disabled();

        /**
         * 鉴权失败时的协议化响应。
         *
         * @return 协议化响应体
         */
        Object authFailed();
    }

    /**
     * 统一分发入口：鉴权 → 提示词提取 → 对话执行 → 协议化表达，全部由 {@link ProtocolAdapter} 描述。
     *
     * @param servletRequest HTTP 请求
     * @param body           原始请求体
     * @param adapter        协议适配器
     * @return {@link ResponseEntity} / SSE 发射器 / 协议化响应体
     */
    private Object route(HttpServletRequest servletRequest, Object body, ProtocolAdapter adapter) {
        // 检查 REST 开关
        if (!restEnabled()) {
            log.info("[ai] {} 协议被 REST 开关禁用", adapter.family());
            return ResponseEntity.ok(adapter.disabled());
        }
        // 鉴权校验
        if (!authCheck(servletRequest)) {
            log.info("[ai] {} 协议鉴权失败", adapter.family());
            return ResponseEntity.status(401).body(adapter.authFailed());
        }
        // 请求体判空（Gemini 允许空体，其余协议要求非空）
        if (adapter.requestBodyRequired() && body == null) {
            log.warn("[ai] {} 协议请求体为空", adapter.family());
            return ResponseEntity.badRequest().body(adapter.failure(null, "请求体不能为空"));
        }
        boolean stream = adapter.stream(servletRequest, body);
        String model = modelOf(adapter, servletRequest, body);
        // 模型范围闸门：appKey 只能用「分组清单 ∩ 限定模型」内的模型；流式也必须在开流前拦
        String violation = modelScopeViolation(servletRequest, model);
        if (violation != null) {
            log.warn("[ai] {} 协议模型越权: model={}", adapter.family(), model);
            if (stream) {
                return modelNotAllowedEmitter(violation);
            }
            return ResponseEntity.status(403).body(adapter.failure(model, violation));
        }
        // 流式模式
        if (stream) {
            return adapter.stream(body, model, servletRequest);
        }
        // 同步模式
        try {
            AiChatRequest chatRequest = adapter.request(servletRequest, body);
            log.info("[ai] {} 协议调用对话,model={}", adapter.family(), model);
            Map<String, Object> result = aiChatFacadeService.call(chatRequest);
            return ResponseEntity.ok(adapter.success(result, model));
        } catch (IllegalStateException ex) {
            // 解析失败时透传错误信息（模型配置缺失/冲突由门面的 IllegalStateException 表达）
            log.warn("[ai] {} 协议对话请求失败: {}", adapter.family(), ex.getMessage());
            return ResponseEntity.ok(adapter.failure(model, ex.getMessage()));
        }
    }

    /**
     * 从协议适配器取模型名：请求体优先，取不到再看 URL 路径。
     *
     * <p>为什么必须看路径：Gemini 的模型名<b>不在请求体里</b>，
     * 而在 {@code /v1beta/models/{model}:generateContent} 上。只看 body 会得到 null，
     * 于是「有模型范围的 Key 调 Gemini 一律 403」，请求本身也没带对模型。</p>
     *
     * @param adapter        协议适配器
     * @param servletRequest HTTP 请求
     * @param body           原始请求体
     * @return 模型名，都取不到时返回 null
     */
    private String modelOf(ProtocolAdapter adapter, HttpServletRequest servletRequest, Object body) {
        Map<String, Object> m = (Map<String, Object>) body;
        String fromBody = m == null ? null : adapter.modelOf(m);
        if (fromBody != null && !fromBody.isBlank()) {
            return fromBody.trim();
        }
        return modelFromPath(servletRequest);
    }

    /**
     * 从 URL 路径中解析模型名（Gemini 口径：{@code /v1beta/models/{model}:{action}}）。
     *
     * @param servletRequest HTTP 请求，可为 null
     * @return 模型名；路径里没有 {@code /models/} 段时返回 null
     */
    static String modelFromPath(HttpServletRequest servletRequest) {
        return servletRequest == null ? null : modelFromPath(servletRequest.getRequestURI());
    }

    /**
     * 从 URI 字符串解析模型名，供单元测试直接调用。
     *
     * @param uri 请求 URI，可为 null
     * @return 模型名；无法解析时返回 null
     */
    static String modelFromPath(String uri) {
        if (uri == null) {
            return null;
        }
        String marker = "/models/";
        int idx = uri.indexOf(marker);
        if (idx < 0) {
            return null;
        }
        String tail = uri.substring(idx + marker.length());
        int cut = tail.length();
        int colon = tail.indexOf(':');
        if (colon >= 0 && colon < cut) {
            cut = colon;
        }
        int slash = tail.indexOf('/');
        if (slash >= 0 && slash < cut) {
            cut = slash;
        }
        int query = tail.indexOf('?');
        if (query >= 0 && query < cut) {
            cut = query;
        }
        String model = tail.substring(0, cut).trim();
        return model.isEmpty() ? null : model;
    }

    // ==================== Claude 消息 ====================

    /**
     * Claude 消息 协议入口。
     *
     * <p>请求体遵循 Anthropic Messages API 结构：</p>
     * <pre>{@code
     * {
     *   "model": "claude-sonnet-4-20250514",
     *   "system": "你是助手",
     *   "messages": [{"role": "user", "content": "你好"}],
     *   "max_tokens": 1024
     * }
     * }</pre>4
     * }
     * }
     * </pre>
     *
     * @param body           请求体
     * @param servletRequest HTTP 请求
     * @return Claude 消息 响应
     * @param false false
     */
    @Operation(summary = "Claude Messages", description = "Anthropic Claude Messages 标准协议入口")
    @PostMapping("/v1/messages")
    public Object messages(@RequestBody(required = false) Map<String, Object> body,
                           HttpServletRequest servletRequest) {
        return route(servletRequest, body, new ClaudeAdapter());
    }

    /**
     * Claude 消息 协议适配器：Anthropic Messages 请求/响应形态。
     *
     * <p>覆盖 Claude 消息 要求的 {@code message_start}、{@code content_block_start}、
     * {@code content_block_delta}、{@code content_block_stop}、{@code message_delta}、
     * {@code message_stop} 事件序列，以及 {@code message} 响应体。</p>
     */
    private class ClaudeAdapter implements ProtocolAdapter {

        @Override
        public String family() {
            return "claude";
        }

        @Override
        public String modelOf(Map<String, Object> body) {
            return AiProtocolController.toString(body.get("model"));
        }

        @Override
        public boolean stream(HttpServletRequest servletRequest, Object body) {
            return body instanceof Map<?, ?> b && Boolean.TRUE.equals(b.get("stream"));
        }

        @Override
        public boolean requestBodyRequired() {
            return true;
        }

        @Override
        public AiChatRequest request(HttpServletRequest servletRequest, Object body) {
            return buildChatRequest((Map<String, Object>) body, servletRequest);
        }

        @Override
        public Object success(Map<String, Object> result, String model) {
            return buildClaudeResponse(result, model);
        }

        @Override
        public Object stream(Object body, String model, HttpServletRequest servletRequest) {
            return claudeStream((Map<String, Object>) body, model, servletRequest);
        }

        @Override
        public Object failure(String model, String errorMessage) {
            return buildClaudeError(model, errorMessage);
        }

        @Override
        public Object disabled() {
            return Map.of("type", "error", "error", Map.of("type", "not_found_error", "message", "REST 端口未启用"));
        }

        @Override
        public Object authFailed() {
            return Map.of("type", "error", "error", Map.of("type", "authentication_error", "message", "AI AppKey 校验失败"));
        }
    }

    /**
     * Claude 流式响应（SSE）。
     *
     * <p>按 Claude 协议顺序发送 {@code message_start}、{@code content_block_start}、
     * {@code content_block_delta}、{@code content_block_stop}、{@code message_delta}、
     * {@code message_stop} 事件。</p>
     *
     * @param body           请求体
     * @param model          模型名称
     * @param servletRequest HTTP 请求
     * @return SSE 发射器
     */
    private SseEmitter claudeStream(Map<String, Object> body, String model, HttpServletRequest servletRequest) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MILLIS);
        CompletableFuture.runAsync(() -> {
            try {
                // 发送消息开始事件
                emitter.send(SseEmitter.event().name("message_start")
                        .data("{\"type\":\"message_start\",\"message\":"
                                + toJson(buildClaudeStartResponse(model)) + "}", MediaType.APPLICATION_JSON));
                // 发送内容块开始事件
                emitter.send(SseEmitter.event().name("content_block_start")
                        .data("{\"type\":\"content_block_start\",\"index\":0,\"content_block\":{\"type\":\"text\",\"text\":\"\"}}", MediaType.APPLICATION_JSON));
                // 执行对话
                AiChatRequest chatRequest = buildChatRequest(body, servletRequest);
                Map<String, Object> result = aiChatFacadeService.call(chatRequest);
                String text = resolveResultText(result);
                // 发送内容增量事件
                if (text != null && !text.isEmpty()) {
                    emitter.send(SseEmitter.event().name("content_block_delta")
                            .data("{\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"text_delta\",\"text\":"
                                    + toJson(text) + "}}", MediaType.APPLICATION_JSON));
                }
                // 发送内容块结束事件
                emitter.send(SseEmitter.event().name("content_block_stop")
                        .data("{\"type\":\"content_block_stop\",\"index\":0}", MediaType.APPLICATION_JSON));
                // 发送消息增量事件
                emitter.send(SseEmitter.event().name("message_delta")
                        .data("{\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"end_turn\",\"stop_sequence\":null},\"usage\":{\"output_tokens\":0}}", MediaType.APPLICATION_JSON));
                // 发送消息结束事件
                emitter.send(SseEmitter.event().name("message_stop")
                        .data("{\"type\":\"message_stop\"}", MediaType.APPLICATION_JSON));
                emitter.complete();
            } catch (Exception ex) {
                try {
                    emitter.send(SseEmitter.event().name("error").data(ex.getMessage(), MediaType.TEXT_PLAIN));
                } catch (IOException ignored) {
                    // 忽略发送失败
                }
                emitter.completeWithError(ex);
            }
        });
        return emitter;
    }

    // ==================== 打开AI 响应 (Codex/CCS) ====================

    /**
     * 打开AI 响应 协议。
     *
     * <p>请求体遵循 OpenAI Responses API 结构：</p>
     * <pre>{@code
     * {
     *   "model": "gpt-4o",
     *   "instructions": "你是助手",
     *   "input": [{"type": "message", "role": "user", "content": "你好"}]
     * }
     * }</pre>
     * }
     * }
     * </pre>
     *
     * @param body           请求体
     * @param servletRequest HTTP 请求
     * @return Responses 响应
     * @param false false
     */
    @Operation(summary = "OpenAI Responses", description = "OpenAI Responses API（Codex CLI 兼容）入口")
    @PostMapping("/v1/responses")
    public Object responses(@RequestBody(required = false) Map<String, Object> body,
                          HttpServletRequest servletRequest) {
        return route(servletRequest, body, new ResponsesAdapter());
    }

    /**
     * Responses 协议适配器：OpenAI Responses 请求/响应形态。
     *
     * <p>覆盖 Responses 要求的 {@code response.created}、{@code response.output_item.added}、
     * {@code response.content_part.added}、{@code response.output_text.delta}、
     * {@code response.output_text.done}、{@code response.content_part.done}、
     * {@code response.output_item.done}、{@code response.completed} 事件序列。</p>
     */
    private class ResponsesAdapter implements ProtocolAdapter {

        @Override
        public String family() {
            return "responses";
        }

        @Override
        public String modelOf(Map<String, Object> body) {
            return AiProtocolController.toString(body.get("model"));
        }

        @Override
        public boolean stream(HttpServletRequest servletRequest, Object body) {
            return body instanceof Map<?, ?> b && Boolean.TRUE.equals(b.get("stream"));
        }

        @Override
        public boolean requestBodyRequired() {
            return true;
        }

        @Override
        public AiChatRequest request(HttpServletRequest servletRequest, Object body) {
            return buildResponsesChatRequest((Map<String, Object>) body, servletRequest);
        }

        @Override
        public Object success(Map<String, Object> result, String model) {
            return buildResponsesResponse(result, model);
        }

        @Override
        public Object stream(Object body, String model, HttpServletRequest servletRequest) {
            return responsesStream((Map<String, Object>) body, model, servletRequest);
        }

        @Override
        public Object failure(String model, String errorMessage) {
            return buildResponsesError(model, errorMessage);
        }

        @Override
        public Object disabled() {
            return Map.of("object", "response", "status", "disabled",
                    "output", List.of(), "output_text", "REST 协议未启用");
        }

        @Override
        public Object authFailed() {
            return Map.of("object", "response",
                    "error", Map.of("type", "authentication_error", "message", "AI AppKey 校验失败"));
        }
    }

    /**
     * 响应 流式响应（SSE）。
     *
     * <p>按 Responses 协议顺序发送 {@code response.created}、{@code response.output_item.added}、
     * {@code response.content_part.added}、{@code response.output_text.delta}、
     * {@code response.output_text.done}、{@code response.content_part.done}、
     * {@code response.output_item.done}、{@code response.completed} 事件。</p>
     *
     * @param body           请求体
     * @param model          模型名称
     * @param servletRequest HTTP 请求
     * @return SSE 发射器
     */
    private SseEmitter responsesStream(Map<String, Object> body, String model, HttpServletRequest servletRequest) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MILLIS);
        CompletableFuture.runAsync(() -> {
            try {
                String responseId = "resp_" + UUID.randomUUID().toString().replace("-", "");
                String messageId = "msg_" + UUID.randomUUID().toString().replace("-", "");
                // 执行对话
                AiChatRequest chatRequest = buildResponsesChatRequest(body, servletRequest);
                Map<String, Object> result = aiChatFacadeService.call(chatRequest);
                String text = resolveResultText(result);

                Map<String, Object> started = buildCcsResponse(responseId, messageId, model, "", "in_progress");
                started.put("output", List.of());
                // 发送响应创建事件
                emitter.send(SseEmitter.event().name("response.created")
                        .data("{\"type\":\"response.created\",\"response\":"
                                + toJson(started) + "}", MediaType.APPLICATION_JSON));
                // 发送输出项添加事件
                emitter.send(SseEmitter.event().name("response.output_item.added")
                        .data("{\"type\":\"response.output_item.added\",\"output_index\":0,\"item\":"
                                + toJson(buildCcsMessage(messageId, "", "in_progress")) + "}", MediaType.APPLICATION_JSON));
                // 发送内容部分添加事件
                emitter.send(SseEmitter.event().name("response.content_part.added")
                        .data("{\"type\":\"response.content_part.added\",\"item_id\":\"" + messageId
                                + "\",\"output_index\":0,\"content_index\":0,\"part\":{\"type\":\"output_text\",\"text\":\"\",\"annotations\":[]}}", MediaType.APPLICATION_JSON));

                // 分块发送文本增量事件
                if (text != null && !text.isEmpty()) {
                    int chunkSize = 80;
                    for (int i = 0; i < text.length(); i += chunkSize) {
                        int end = Math.min(i + chunkSize, text.length());
                        String chunk = text.substring(i, end);
                        emitter.send(SseEmitter.event().name("response.output_text.delta")
                                .data("{\"type\":\"response.output_text.delta\",\"item_id\":\"" + messageId
                                        + "\",\"output_index\":0,\"content_index\":0,\"delta\":"
                                        + toJson(chunk) + "}", MediaType.APPLICATION_JSON));
                    }
                }
                // 发送文本完成事件
                emitter.send(SseEmitter.event().name("response.output_text.done")
                        .data("{\"type\":\"response.output_text.done\",\"item_id\":\"" + messageId
                                + "\",\"output_index\":0,\"content_index\":0,\"text\":"
                                + toJson(text != null ? text : "") + "}", MediaType.APPLICATION_JSON));
                // 发送内容部分完成事件
                emitter.send(SseEmitter.event().name("response.content_part.done")
                        .data("{\"type\":\"response.content_part.done\",\"item_id\":\"" + messageId
                                + "\",\"output_index\":0,\"content_index\":0,\"part\":"
                                + toJson(Map.of("type", "output_text", "text", text != null ? text : "", "annotations", List.of()))
                                + "}", MediaType.APPLICATION_JSON));
                // 发送输出项完成事件
                emitter.send(SseEmitter.event().name("response.output_item.done")
                        .data("{\"type\":\"response.output_item.done\",\"output_index\":0,\"item\":"
                                + toJson(buildCcsMessage(messageId, text != null ? text : "", "completed")) + "}", MediaType.APPLICATION_JSON));
                // 发送响应完成事件
                emitter.send(SseEmitter.event().name("response.completed")
                        .data("{\"type\":\"response.completed\",\"response\":"
                                + toJson(buildCcsResponse(responseId, messageId, model, text != null ? text : "", "completed"))
                                + "}", MediaType.APPLICATION_JSON));
                emitter.complete();
            } catch (Exception ex) {
                try {
                    emitter.send(SseEmitter.event().name("error").data(ex.getMessage(), MediaType.TEXT_PLAIN));
                } catch (IOException ioex) {
                    // 忽略发送失败
                }
                emitter.completeWithError(ex);
            }
        });
        return emitter;
    }

    // ==================== Gemini ====================

    /**
     * Gemini 模型列表 —— 「模型获取」在 Gemini 协议里就是这条 GET。
     *
     * <p>与 {@code /v1/models}、{@code /v1/cc/switch/models} 同口径：先鉴权，再按
     * 「分组清单 ∩ Key 限定模型」过滤，只列这把 Key 真能调的模型。缺了这条端点，
     * Gemini SDK 的模型探测会直接 404/405，客户端只能盲猜模型名。</p>
     *
     * @param servletRequest HTTP 请求
     * @return Gemini 形状的模型列表 {@code {"models":[{"name":"models/xxx",...}]}}
     */
    @Operation(summary = "Gemini Models", description = "获取支持的模型列表")
    @GetMapping("/v1beta/models")
    public ResponseEntity<Map<String, Object>> geminiModels(HttpServletRequest servletRequest) {
        if (!restEnabled()) {
            return ResponseEntity.ok(Map.of("models", List.of()));
        }
        if (!authCheck(servletRequest)) {
            return ResponseEntity.status(401).body(Map.of("error",
                    Map.of("code", 401, "message", "AI AppKey 校验失败", "status", "UNAUTHENTICATED")));
        }
        Integer groupId = resolveGroupId(servletRequest);
        Set<String> scope = requestModelScope(servletRequest);
        List<Map<String, Object>> models = new ArrayList<>();
        for (String id : AppKeyModelScope.filter(scope, aiChatFacadeService.models(groupId))) {
            models.add(Map.of(
                    "name", "models/" + id,
                    "displayName", id,
                    "supportedGenerationMethods", List.of("generateContent", "streamGenerateContent")));
        }
        return ResponseEntity.ok(Map.of("models", models));
    }

    /**
     * Gemini 协议入口。
     *
     * <p>支持 {@code /v1beta/models/{model}:generateContent} 与流式
     * {@code streamGenerateContent} 变体。</p>
     *
     * @param request HTTP 请求（路径以 {@code request.getRequestURI()} 解析）
     * @param body    请求体
     * @return Gemini 响应
     */
    @Operation(summary = "Gemini", description = "Google Gemini 1beta 协议入口")
    @PostMapping("/v1beta/models/**")
    public Object gemini(HttpServletRequest request,
                         @RequestBody(required = false) Map<String, Object> body) {
        return route(request, body, new GeminiAdapter());
    }

    /**
     * Gemini 协议适配器：Google generateContent 请求/响应形态。
     *
     * <p>通过 {@code /v1beta/models/{model}:{action}} 路径指定模型与动作（
     * {@code generateContent} / {@code streamGenerateContent}）。</p>
     */
    private class GeminiAdapter implements ProtocolAdapter {

        @Override
        public String family() {
            return "gemini";
        }

        @Override
        public String modelOf(Map<String, Object> body) {
            return AiProtocolController.toString(body.get("model"));
        }

        @Override
        public boolean stream(HttpServletRequest servletRequest, Object body) {
            // Gemini 的流向由路径后缀定义：/v1beta/models/{model}:streamGenerateContent
            String fullPath = servletRequest != null ? servletRequest.getRequestURI() : null;
            return fullPath != null && fullPath.endsWith("streamGenerateContent");
        }

        @Override
        public boolean requestBodyRequired() {
            return false;
        }

        @Override
        public AiChatRequest request(HttpServletRequest servletRequest, Object body) {
            return buildGeminiChatRequest((Map<String, Object>) body, servletRequest);
        }

        @Override
        public Object success(Map<String, Object> result, String model) {
            return buildGeminiResponse(model, resolveResultText(result));
        }

        @Override
        public Object stream(Object body, String model, HttpServletRequest servletRequest) {
            try {
                SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MILLIS);
                CompletableFuture.runAsync(() -> {
                    try {
                        AiChatRequest chatRequest = buildGeminiChatRequest((Map<String, Object>) body, servletRequest);
                        Map<String, Object> result = aiChatFacadeService.call(chatRequest);
                        String text = resolveResultText(result);
                        Map<String, Object> candidate = buildGeminiCandidate(model, text);
                        emitter.send(SseEmitter.event().data(toJson(candidate), MediaType.APPLICATION_JSON));
                        emitter.send(SseEmitter.event().data("[DONE]", MediaType.APPLICATION_JSON));
                        emitter.complete();
                    } catch (IOException ex) {
                        emitter.completeWithError(ex);
                    }
                });
                return emitter;
            } catch (Exception ex) {
                log.error("[ai] Gemini 流式异常: {}", ex.getMessage(), ex);
                return ResponseEntity.ok(Map.of(
                        "error", Map.of("code", 500, "message", ex.getMessage(), "status", "INTERNAL")));
            }
        }

        @Override
        public Object failure(String model, String errorMessage) {
            return Map.of("error", Map.of("code", 500, "message", errorMessage, "status", "INTERNAL"));
        }

        @Override
        public Object disabled() {
            return Map.of("error", Map.of("code", 400, "message", "REST 协议未启用", "status", "INVALID_ARGUMENT"));
        }

        @Override
        public Object authFailed() {
            return Map.of("error", Map.of("code", 401, "message", "AI AppKey 校验失败", "status", "UNAUTHENTICATED"));
        }
    }

    // ==================== 提示符 抽取 ====================

    /**
     * 构建通用对话请求（提示符 提取优先使用 消息）。
     *
     * @param body           请求体
     * @param servletRequest HTTP 请求
     * @return 对话请求
     */
    private AiChatRequest buildChatRequest(Map<String, Object> body, HttpServletRequest servletRequest) {
        AiChatRequest request = new AiChatRequest();
        request.setModel(toString(body.get("model")));
        request.setPrompt(extractClaudePrompt(body));
        request.setSystemPrompt(extractSystem(body));
        request.setStream(Boolean.TRUE.equals(body.get("stream")));
        /**
         * apply群体。
         * @param request 请求
         * @param servletRequest servlet请求
         */
        applyGroup(request, servletRequest);
        return request;
    }

    /**
     * 构建 响应 对话请求。
     *
     * @param body           请求体
     * @param servletRequest HTTP 请求
     * @return 对话请求
     */
    private AiChatRequest buildResponsesChatRequest(Map<String, Object> body, HttpServletRequest servletRequest) {
        AiChatRequest request = new AiChatRequest();
        request.setModel(toString(body.get("model")));
        request.setPrompt(extractResponsesPrompt(body));
        request.setStream(Boolean.TRUE.equals(body.get("stream")));
        /**
         * apply群体。
         * @param request 请求
         * @param servletRequest servlet请求
         */
        applyGroup(request, servletRequest);
        return request;
    }

    /**
     * 构建 Gemini 对话请求。
     *
     * @param body           请求体
     * @param servletRequest HTTP 请求
     * @return 对话请求
     */
    private AiChatRequest buildGeminiChatRequest(Map<String, Object> body, HttpServletRequest servletRequest) {
        AiChatRequest request = new AiChatRequest();
        // 模型名优先取请求体，Gemini 正式调用则来自路径 /v1beta/models/{model}:xxx
        String model = toString(body.get("model"));
        if (model == null || model.isBlank()) {
            model = modelFromPath(servletRequest);
        }
        request.setModel(model);
        request.setPrompt(extractGeminiPrompt(body));
        /**
         * apply群体。
         * @param request 请求
         * @param servletRequest servlet请求
         */
        applyGroup(request, servletRequest);
        return request;
    }

    /**
     * 注入分组 标识 到 参数，供 facade 模型隔离。
     *
     * @param request         对话请求
     * @param servletRequest  HTTP 请求
     */
    private void applyGroup(AiChatRequest request, HttpServletRequest servletRequest) {
        Integer groupId = resolveGroupId(servletRequest);
        String appKey = resolveAppKey(servletRequest);
        if (groupId != null || StringUtils.isNotBlank(appKey)) {
            request.setParameters(new LinkedHashMap<>());
            if (groupId != null) {
                request.getParameters().put("_groupId", groupId);
            }
            if (StringUtils.isNotBlank(appKey)) {
                request.getParameters().put("_appKey", appKey);
            }
        }
    }

    /**
     * 从 {@code messages: []} 提取 系统 与 用户 内容。
     *
     * @param body 请求体
     * @return 组合提示词
     */
    private static String extractClaudePrompt(Map<String, Object> body) {
        StringBuilder sb = new StringBuilder();
        String system = toString(body.get("system"));
        if (system != null && !system.isBlank()) {
            sb.append(system).append("\n");
        }
        Object messages = body.get("messages");
        if (messages instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> m && "user".equals(toString(m.get("role")))) {
                    String content = extractText(m.get("content"));
                    if (content != null && !content.isBlank()) {
                        sb.append(content.trim()).append("\n");
                    }
                }
            }
        }
        return sb.toString().trim();
    }

    /**
     * 从 主体 提取 系统 提示词。
     *
     * @param body 请求体
     * @return 系统提示词
     */
    private static String extractSystem(Map<String, Object> body) {
        return toString(body.get("system"));
    }

    /**
     * 从 响应 请求体提取提示词。
     *
     * @param body 请求体
     * @return 组合提示词
     */
    private static String extractResponsesPrompt(Map<String, Object> body) {
        StringBuilder sb = new StringBuilder();
        String instructions = toString(body.get("instructions"));
        if (instructions != null && !instructions.isBlank()) {
            sb.append(instructions).append("\n");
        }
        Object input = body.get("input");
        if (input instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> m) {
                    if ("message".equals(toString(m.get("type"))) || m.containsKey("role")) {
                        String content = extractText(m.get("content"));
                        if (content != null && !content.isBlank()) {
                            sb.append(content.trim()).append("\n");
                        }
                    }
                }
            }
        }
        String prompt = toString(body.get("prompt"));
        if (prompt != null && !prompt.isBlank()) {
            sb.append(prompt.trim()).append("\n");
        }
        return sb.toString().trim();
    }

    /**
     * 从 Gemini 请求体提取提示词。
     *
     * @param body 请求体
     * @return 组合提示词
     */
    private static String extractGeminiPrompt(Map<String, Object> body) {
        StringBuilder sb = new StringBuilder();
        Object systemInstruction = body.get("systemInstruction");
        if (systemInstruction instanceof Map<?, ?> map) {
            String text = extractPartsText(map);
            if (text != null && !text.isBlank()) {
                sb.insert(0, text.trim() + "\n");
            }
        }
        Object contents = body.get("contents");
        if (contents instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> m) {
                    String text = extractPartsText(m);
                    if (text != null && !text.isBlank()) {
                        sb.append(text.trim()).append("\n");
                    }
                }
            }
        }
        return sb.toString().trim();
    }

    /**
     * 提取 parts.文本 内容。
     *
     * @param obj 对象
     * @return 文本内容
     */
    private static String extractPartsText(Map<?, ?> obj) {
        Object parts = obj.get("parts");
        if (parts instanceof List<?> list) {
            for (Object part : list) {
                if (part instanceof Map<?, ?> m) {
                    String text = toString(m.get("text"));
                    if (text != null && !text.isEmpty()) {
                        return text;
                    }
                }
            }
        }
        return null;
    }

    /**
     * 递归提取文本内容（支持 字符串 / 列表 / 映射）。
     *
     * @param value 内容对象
     * @return 文本
     */
    private static String extractText(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String) {
            return (String) value;
        }
        if (value instanceof List<?> list) {
            StringBuilder sb = new StringBuilder();
            for (Object item : list) {
                String t = extractText(item);
                if (t != null && !t.isBlank()) {
                    if (sb.length() > 0) {
                        sb.append('\n');
                    }
                    sb.append(t.trim());
                }
            }
            return sb.toString();
        }
        if (value instanceof Map<?, ?> map) {
            String t = toString(map.get("text"));
            if (t != null) {
                return t;
            }
            Object c = map.get("content");
            if (c != null) {
                return extractText(c);
            }
            Object p = map.get("parts");
            if (p != null) {
                return extractText(p);
            }
        }
        return String.valueOf(value);
    }

    // ==================== 响应构建器 ====================

    /**
     * 构建 Claude 起始响应（流式 消息_启动 事件）。
     *
     * @param model 模型名称
     * @return 起始响应体
     */
    private Map<String, Object> buildClaudeStartResponse(String model) {
        String id = "msg_" + UUID.randomUUID().toString().replace("-", "");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", id);
        body.put("type", "message");
        body.put("role", "assistant");
        body.put("model", echoModel(model));
        body.put("content", List.of());
        body.put("stop_reason", null);
        body.put("stop_sequence", null);
        body.put("usage", Map.of("input_tokens", 0, "output_tokens", 0));
        return body;
    }

    /**
     * 构建 Claude 响应。
     *
     * @param result 对话结果
     * @param model  模型名称
     * @return Claude 响应体
     */
    private Map<String, Object> buildClaudeResponse(Map<String, Object> result, String model) {
        String id = "msg_" + UUID.randomUUID().toString().replace("-", "");
        String text = result != null ? toString(result.get("text")) : "";
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", id);
        body.put("type", "message");
        body.put("role", "assistant");
        body.put("model", echoModel(model));
        body.put("content", List.of(Map.of("type", "text", "text", text != null ? text : "")));
        body.put("stop_reason", "end_turn");
        body.put("stop_sequence", null);
        body.put("usage", Map.of("input_tokens", 0, "output_tokens", 0));
        return body;
    }

    /**
     * 构建 Claude 错误响应。
     *
     * @param model        模型名称
     * @param errorMessage 错误消息
     * @return 错误响应体
     */
    private static Map<String, Object> buildClaudeError(String model, String errorMessage) {
        String id = "msg_" + UUID.randomUUID().toString().replace("-", "");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", id);
        body.put("type", "error");
        body.put("error", Map.of("type", "api_error", "message", errorMessage));
        return body;
    }

    /**
     * 构建 响应 响应。
     *
     * @param result 对话结果
     * @param model  模型名称
     * @return Responses 响应体
     */
    private static Map<String, Object> buildResponsesResponse(Map<String, Object> result, String model) {
        String responseId = "resp_" + UUID.randomUUID().toString().replace("-", "");
        String messageId = "msg_" + UUID.randomUUID().toString().replace("-", "");
        String text = result != null ? toString(result.get("text")) : "";
        return buildCcsResponse(responseId, messageId, model, text != null ? text : "", "completed");
    }

    /**
     * 构建 响应 错误响应。
     *
     * @param model        模型名称
     * @param errorMessage 错误消息
     * @return 错误响应体
     */
    private static Map<String, Object> buildResponsesError(String model, String errorMessage) {
        String responseId = "resp_" + UUID.randomUUID().toString().replace("-", "");
        String messageId = "msg_" + UUID.randomUUID().toString().replace("-", "");
        Map<String, Object> body = buildCcsResponse(responseId, messageId, model, errorMessage, "failed");
        body.put("error", Map.of("type", "api_error", "message", errorMessage));
        return body;
    }

    /**
     * 构建 CCS / 响应 标准响应。
     *
     * @param responseId 响应 标识
     * @param messageId  消息 标识
     * @param model      模型名称
     * @param text       文本内容
     * @param status     状态
     * @return 响应体
     */
    private static Map<String, Object> buildCcsResponse(String responseId, String messageId, String model, String text, String status) {
        String resolvedModel = StringUtils.isNotBlank(model) ? model : "unknown";
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", responseId);
        body.put("object", "response");
        body.put("created_at", (int) (System.currentTimeMillis() / 1000));
        body.put("status", status);
        body.put("model", resolvedModel);
        body.put("output", List.of(buildCcsMessage(messageId, text, status)));
        body.put("output_text", text);
        body.put("parallel_tool_calls", true);
        body.put("usage", Map.of("input_tokens", 0, "output_tokens", 0, "total_tokens", 0));
        return body;
    }

    /**
     * 构建 CCS 消息对象。
     *
     * @param messageId 消息 标识
     * @param text      文本
     * @param status    状态
     * @return 消息体
     */
    private static Map<String, Object> buildCcsMessage(String messageId, String text, String status) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("id", messageId);
        message.put("type", "message");
        message.put("status", status);
        message.put("role", "assistant");
        message.put("content", List.of(Map.of(
                "type", "output_text",
                "text", text,
                "annotations", List.of()
        )));
        return message;
    }

    /**
     * 构建 Gemini 候选体。
     *
     * @param model 模型名称
     * @param text  文本
     * @return candidate 体
     */
    private static Map<String, Object> buildGeminiCandidate(String model, String text) {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("role", "model");
        content.put("parts", List.of(Map.of("text", text != null ? text : "")));
        Map<String, Object> candidate = new LinkedHashMap<>();
        candidate.put("content", content);
        candidate.put("finishReason", "STOP");
        candidate.put("index", 0);
        return candidate;
    }

    /**
     * 构建 Gemini 标准响应。
     *
     * @param model 模型名称
     * @param text  文本内容
     * @return Gemini 响应体
     */
    private static Map<String, Object> buildGeminiResponse(String model, String text) {
        Map<String, Object> geminiResp = new LinkedHashMap<>();
        geminiResp.put("candidates", List.of(buildGeminiCandidate(model, text)));
        Map<String, Object> usage = new LinkedHashMap<>();
        usage.put("promptTokenCount", 0);
        usage.put("candidatesTokenCount", 0);
        usage.put("totalTokenCount", 0);
        geminiResp.put("usageMetadata", usage);
        return geminiResp;
    }

    // ==================== 工具方法 ====================

    /**
     * 对象转字符串。
     *
     * @param value 对象
     * @return 字符串
     */
    private static String toString(Object value) {
        if (value == null) {
            return null;
        }
        return String.valueOf(value);
    }

    /**
     * 从 {@link AiChatFacadeService#call} 的返回结果中提取文本。
     *
     * <p>调用失败时 call() 返回 {@code {error:{message:...}}} 结构，
     * 这里把错误信息作为文本返回，避免前端收到空内容。</p>
     *
     * @param result call 返回结果
     * @return 文本内容（错误时返回错误信息）
     */
    private static String resolveResultText(Map<String, Object> result) {
        if (result == null) {
            return "";
        }
        if (result.containsKey("error")) {
            Object error = result.get("error");
            if (error instanceof Map) {
                Object msg = ((Map<?, ?>) error).get("message");
                return toString(msg != null ? msg : "模型调用失败");
            }
            return toString(error);
        }
        return toString(result.get("text"));
    }

    /**
     * 对象转 JSON 字符串。
     *
     * @param value 对象
     * @return JSON 字符串
     */
    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }

    /**
     * 兼容协议响应里的 model 字段：优先回显请求模型，其次配置中心默认对话模型。
     *
     * <p>以前在没有请求模型时写死 {@code claude-3-haiku-20240307}：
     * 响应会声称用了某个未必存在、也未必真被调用的模型，误导排障。</p>
     *
     * @param model 请求携带的模型名，可为空
     * @return 回显或解析出的模型名；都拿不到时返回空串
     */
    private String echoModel(String model) {
        if (StringUtils.isNotBlank(model)) {
            return model;
        }
        String resolved = effectiveChatModelResolver.resolve(null);
        return resolved == null ? "" : resolved;
    }
}
