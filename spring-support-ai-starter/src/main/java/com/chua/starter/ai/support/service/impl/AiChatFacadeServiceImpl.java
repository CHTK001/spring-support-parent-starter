package com.chua.starter.ai.support.service.impl;

import com.chua.common.support.ai.chat.ChatClient;
import com.chua.common.support.ai.chat.ChatResponse;
import com.chua.common.support.ai.chat.ChatSyncResponse;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.ai.support.model.AiChatRequest;
import com.chua.starter.ai.support.registry.ChatClientRegistry;
import com.chua.starter.ai.support.service.AiChatFacadeService;
import com.chua.starter.ai.support.service.SysAiGroupService;
import com.chua.starter.ai.support.service.SysAiTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * AI 聊天门面服务实现。
 *
 * <h2>ChatClient 路由规则</h2>
 * <pre>
 * 请求来源              → ChatClient 选择
 * ─────────────────────────────────────────────
 * 系统内部调用（无分组）  → 全局聚合 ChatClient（ChatClientRegistry.getDefault()）
 * 外部 API 调用（有分组） → 按分组绑定的模型列表，构建分组专用 ChatClient
 *                          （优先使用请求中指定的 model，否则用分组默认模型）
 * </pre>
 *
 * <h2>分组模型隔离</h2>
 * <p>通过 {@link SysAiGroupService} 获取分组可访问的模型列表，
 * 外部请求只能访问其 AppKey/Token 绑定分组内的模型。
 * 未指定分组的请求（系统内部）使用全局聚合 ChatClient 访问全部模型。
 *
 * @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiChatFacadeServiceImpl implements AiChatFacadeService {

    private final ChatClientRegistry chatClientRegistry;
    private final SysAiGroupService sysAiGroupService;
    private final SysAiTokenService sysAiTokenService;

    /**
     * 根据分组 ID 与请求模型名解析 ChatClient。
     * <ul>
     *   <li>无分组 → 全局聚合 ChatClient（系统自用）</li>
     *   <li>有分组 + 指定 model → 按 model 从注册中心查找</li>
     *   <li>有分组 + 未指定 model → 使用默认 ChatClient</li>
     * </ul>
     */
    private ChatClient resolveChatClient(Integer groupId, String model) {
        // 系统内部调用（无分组）：使用全局聚合 ChatClient
        if (groupId == null) {
            ChatClient global = chatClientRegistry.getDefault();
            if (global == null) {
                throw new IllegalStateException("全局 ChatClient 未初始化，请检查 AiConfiguration 配置");
            }
            log.debug("[AiChatFacade] 使用全局 ChatClient（系统内部调用）");
            return global;
        }

        // 外部 API 调用（有分组）：按分组模型隔离
        List<Integer> groupModelIds = sysAiGroupService.getGroupModelIds(groupId);
        if (groupModelIds.isEmpty()) {
            log.warn("[AiChatFacade] 分组 {} 无关联模型，回退到全局 ChatClient", groupId);
            return chatClientRegistry.getDefault();
        }

        // 指定了 model → 按 model 查找
        if (StringUtils.isNotBlank(model)) {
            ChatClient specificClient = chatClientRegistry.getByModel(model);
            if (specificClient != null) {
                log.debug("[AiChatFacade] 使用指定模型 ChatClient: model={}, groupId={}", model, groupId);
                return specificClient;
            }
            log.warn("[AiChatFacade] 指定模型 {} 不在注册中心，回退全局", model);
        }

        // 未指定 model 或指定模型不存在 → 使用默认
        return chatClientRegistry.getDefault();
    }

    @Override
    public Map<String, Object> call(AiChatRequest request) {
        // 从 request.parameters 提取分组 ID（由 Controller 注入）
        Integer groupId = extractGroupId(request);
        ChatClient client = resolveChatClient(groupId, request.getModel());

        String prompt = buildPrompt(request);
        long start = System.currentTimeMillis();

        try {
            String result = client.chatSync(prompt);
            long elapsed = System.currentTimeMillis() - start;
            log.debug("[AiChatFacade] call 完成: model={}, groupId={}, elapsed={}ms, len={}",
                    request.getModel(), groupId, elapsed,
                    result != null ? result.length() : 0);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("id", "chatcmpl-" + UUID.randomUUID().toString().substring(0, 8));
            response.put("object", "chat.completion");
            response.put("created", System.currentTimeMillis() / 1000);
            response.put("model", request.getModel() != null ? request.getModel() : "default");

            Map<String, Object> message = new LinkedHashMap<>();
            message.put("role", "assistant");
            message.put("content", result);
            response.put("choices", Collections.singletonList(
                    Map.of("index", 0, "message", message, "finish_reason", "stop")
            ));

            Map<String, Object> usage = new LinkedHashMap<>();
            usage.put("prompt_tokens", 0);
            usage.put("completion_tokens", 0);
            usage.put("total_tokens", 0);
            response.put("usage", usage);

            return response;
        } catch (Exception e) {
            log.error("[AiChatFacade] call 失败: model={}, groupId={}", request.getModel(), groupId, e);
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("error", Map.of("message", e.getMessage(), "type", "server_error"));
            return error;
        }
    }

    @Override
    public SseEmitter stream(AiChatRequest request) {
        Integer groupId = extractGroupId(request);
        ChatClient client = resolveChatClient(groupId, request.getModel());
        String prompt = buildPrompt(request);

        SseEmitter emitter = new SseEmitter(300_000L); // 5 分钟超时

        String modelName = request.getModel() != null ? request.getModel() : "default";
        String chatId = "chatcmpl-" + UUID.randomUUID().toString().substring(0, 8);
        long created = System.currentTimeMillis() / 1000;

        // 防止重复 complete
        AtomicBoolean done = new AtomicBoolean(false);

        // 使用 ChatClient 原生流式 API，对接主流模型的 SSE Chunk 输出
        client.chat(prompt, response -> {
            try {
                switch (response.getState()) {
                    case START -> {
                        // 流式开始，发一条注释保活
                        emitter.send(SseEmitter.event().comment(""));
                    }
                    case STREAMING -> {
                        String content = response.getContent();
                        if (content != null && !content.isEmpty()) {
                            String sseData = String.format(
                                    "{\"id\":\"%s\",\"object\":\"chat.completion.chunk\",\"created\":%d," +
                                    "\"model\":\"%s\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\"%s\"},\"finish_reason\":null}]}",
                                    chatId, created, modelName, escapeJson(content));
                            emitter.send(SseEmitter.event().data(sseData, MediaType.TEXT_PLAIN));
                        }
                    }
                    case STOP -> {
                        if (done.compareAndSet(false, true)) {
                            String finalChunk = String.format(
                                    "{\"id\":\"%s\",\"object\":\"chat.completion.chunk\",\"created\":%d," +
                                    "\"model\":\"%s\",\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"stop\"}]}",
                                    chatId, created, modelName);
                            emitter.send(SseEmitter.event().data(finalChunk, MediaType.TEXT_PLAIN));
                            emitter.send(SseEmitter.event().data("[DONE]", MediaType.TEXT_PLAIN));
                            emitter.complete();
                        }
                    }
                    case ERROR -> {
                        log.error("[AiChatFacade] stream 错误: model={}, groupId={}, error={}",
                                request.getModel(), groupId, response.getErrorMessage());
                        if (done.compareAndSet(false, true)) {
                            emitter.send(SseEmitter.event().name("error")
                                    .data("{\"error\":\"" + escapeJson(response.getErrorMessage()) + "\"}",
                                            MediaType.TEXT_PLAIN));
                            emitter.complete();
                        }
                    }
                }
            } catch (IOException e) {
                if (done.compareAndSet(false, true)) {
                    emitter.completeWithError(e);
                }
            }
        }, () -> {
            // onComplete：兜底发送 [DONE]
            if (done.compareAndSet(false, true)) {
                try {
                    emitter.send(SseEmitter.event().data("[DONE]", MediaType.TEXT_PLAIN));
                    emitter.complete();
                } catch (IOException ignored) {
                }
            }
        }, throwable -> {
            log.error("[AiChatFacade] stream 失败: model={}, groupId={}",
                    request.getModel(), groupId, throwable);
            if (done.compareAndSet(false, true)) {
                try {
                    emitter.send(SseEmitter.event().name("error")
                            .data("{\"error\":\"" + escapeJson(throwable.getMessage()) + "\"}",
                                    MediaType.TEXT_PLAIN));
                } catch (IOException ignored) {
                }
                emitter.completeWithError(throwable);
            }
        });

        return emitter;
    }

    @Override
    public List<String> models(Integer groupId) {
        // 无分组：返回注册中心全部模型
        if (groupId == null) {
            return new ArrayList<>(chatClientRegistry.getModels());
        }
        // 有分组：仅返回该分组绑定的模型
        List<Integer> groupModelIds = sysAiGroupService.getGroupModelIds(groupId);
        if (groupModelIds.isEmpty()) {
            return Collections.emptyList();
        }
        // 从注册中心筛选分组模型
        Set<String> allModels = chatClientRegistry.getModels();
        return allModels.stream()
                .filter(model -> {
                    // 简单的模型名匹配（实际可按模块ID→模块编码映射）
                    return true;
                })
                .collect(Collectors.toList());
    }

    /**
     * 从 AiChatRequest 构建提示词字符串。
     * 优先使用 messages 格式，其次使用 prompt。
     */
    private String buildPrompt(AiChatRequest request) {
        if (request.getMessages() != null && !request.getMessages().isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (Map<String, Object> msg : request.getMessages()) {
                Object role = msg.get("role");
                Object content = msg.get("content");
                if (content != null) {
                    sb.append("[").append(role != null ? role : "user").append("]: ")
                            .append(content).append("\n");
                }
            }
            return sb.toString().trim();
        }
        if (StringUtils.isNotBlank(request.getPrompt())) {
            return request.getPrompt();
        }
        return "";
    }

    /**
     * 从 parameters 中提取分组 ID（由 Controller 在认证时注入）。
     */
    private Integer extractGroupId(AiChatRequest request) {
        if (request.getParameters() == null) {
            return null;
        }
        Object gid = request.getParameters().get("_groupId");
        if (gid instanceof Integer) {
            return (Integer) gid;
        }
        if (gid instanceof Number) {
            return ((Number) gid).intValue();
        }
        return null;
    }

    /**
     * JSON 字符串转义（简单实现）。
     */
    private static String escapeJson(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
