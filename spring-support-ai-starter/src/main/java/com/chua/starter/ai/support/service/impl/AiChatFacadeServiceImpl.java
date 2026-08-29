package com.chua.starter.ai.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.common.support.ai.chat.ModelDefinition;
import com.chua.starter.ai.support.chat.ChatClient;
import com.chua.starter.ai.support.chat.ChatContext;
import com.chua.starter.ai.support.chat.ChatMessage;
import com.chua.starter.ai.support.chat.ChatScope;
import com.chua.starter.ai.support.entity.SysAiGroup;
import com.chua.starter.ai.support.entity.SysAiGroupModel;
import com.chua.starter.ai.support.mapper.SysAiGroupMapper;
import com.chua.starter.ai.support.mapper.SysAiGroupModelMapper;
import com.chua.starter.ai.support.model.AiChatRequest;
import com.chua.starter.ai.support.registry.ChatClientRegistry;
import com.chua.starter.ai.support.service.AiChatFacadeService;
import com.chua.starter.ai.support.service.SysAiPricingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * AI 对话门面服务实现。
 *
 * <p>通过 {@link ChatClientRegistry} 路由到对应的 {@link ChatClient}，
 * 完成同步/流式对话、模型列表查询与模型定义查询。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiChatFacadeServiceImpl implements AiChatFacadeService {

    private static final long SSE_TIMEOUT_MILLIS = 60_000L;

    private final ChatClientRegistry chatClientRegistry;
    private final SysAiGroupMapper sysAiGroupMapper;
    private final SysAiGroupModelMapper sysAiGroupModelMapper;
    private final SysAiPricingService sysAiPricingService;

    // ==================== 同步对话 ====================

    @Override
    public Map<String, Object> call(AiChatRequest request) {
        ChatClient client = resolveChatClient(request);
        if (client == null) {
            throw new IllegalStateException("No available ChatClient for model: " + request.getModel());
        }
        ChatScope scope = buildChatScope(request);
        try {
            com.chua.common.support.ai.chat.ChatResponse response = client.chat(scope);
            return buildResult(response);
        } catch (Exception e) {
            log.error("[AiChat] call failed: model={}", request.getModel(), e);
            return buildErrorResult(e);
        }
    }

    // ==================== 流式对话 ====================

    @Override
    public SseEmitter stream(AiChatRequest request, Consumer<String> onChunk, Consumer<String> onComplete) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MILLIS);

        ChatClient client = resolveChatClient(request);
        if (client == null) {
            emitter.completeWithError(new IllegalStateException("No available ChatClient for model: " + request.getModel()));
            return emitter;
        }

        ChatScope scope = buildChatScope(request);
        StringBuilder fullResponse = new StringBuilder();

        CompletableFuture.runAsync(() -> {
            try {
                client.chat(scope,
                        chunk -> {
                            fullResponse.append(chunk);
                            try {
                                emitter.send(SseEmitter.event().name("message").data(chunk));
                            } catch (Exception e) {
                                log.warn("[AiChat] SSE send failed: {}", e.getMessage());
                            }
                            if (onChunk != null) {
                                try {
                                    onChunk.accept(chunk);
                                } catch (Exception ignored) {
                                }
                            }
                        },
                        () -> {
                            String reply = fullResponse.toString();
                            if (onComplete != null) {
                                try {
                                    onComplete.accept(reply);
                                } catch (Exception ignored) {
                                }
                            }
                            emitter.complete();
                        },
                        error -> {
                            log.error("[AiChat] stream error: model={}", request.getModel(), error);
                            emitter.completeWithError(error);
                        }
                );
            } catch (Exception e) {
                log.error("[AiChat] stream failed: model={}", request.getModel(), e);
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    // ==================== 模型列表 ====================

    @Override
    public List<String> models(Integer groupId) {
        if (groupId == null) {
            return new ArrayList<>(chatClientRegistry.getModels());
        }

        SysAiGroup group = sysAiGroupMapper.selectById(groupId);
        if (group == null || group.getStatus() == null || group.getStatus() != 1) {
            return Collections.emptyList();
        }

        List<SysAiGroupModel> groupModels = sysAiGroupModelMapper.selectList(
                new LambdaQueryWrapper<SysAiGroupModel>()
                        .eq(SysAiGroupModel::getSysAiGroupId, groupId));

        if (groupModels.isEmpty()) {
            return new ArrayList<>(chatClientRegistry.getModels());
        }

        Set<Integer> moduleIds = groupModels.stream()
                .map(SysAiGroupModel::getSysAiModuleId)
                .collect(Collectors.toSet());

        return chatClientRegistry.getModels().stream()
                .collect(Collectors.toList());
    }

    // ==================== 模型定义 ====================

    @Override
    public List<ModelDefinition> modelDefinitions(Integer groupId) {
        List<ModelDefinition> allDefs = chatClientRegistry.getModelDefinitions();
        if (groupId == null) {
            return allDefs;
        }

        List<String> groupModels = models(groupId);
        if (groupModels.isEmpty()) {
            return allDefs;
        }

        Set<String> modelSet = new HashSet<>(groupModels);
        return allDefs.stream()
                .filter(d -> d.getId() != null && modelSet.contains(d.getId()))
                .collect(Collectors.toList());
    }

    // ==================== 内部方法 ====================

    private ChatClient resolveChatClient(AiChatRequest request) {
        String model = request.getModel();
        String provider = request.getProvider();

        if (model != null && !model.isBlank()) {
            com.chua.common.support.ai.chat.ChatClient client = chatClientRegistry.getByModel(model);
            if (client instanceof ChatClient cc) {
                return cc;
            }
        }
        if (provider != null && !provider.isBlank()) {
            com.chua.common.support.ai.chat.ChatClient client = chatClientRegistry.getByProvider(provider);
            if (client instanceof ChatClient cc) {
                return cc;
            }
        }
        com.chua.common.support.ai.chat.ChatClient defaultClient = chatClientRegistry.getDefault();
        return defaultClient instanceof ChatClient cc ? cc : null;
    }

    private ChatScope buildChatScope(AiChatRequest request) {
        Map<String, Object> params = request.getParameters() != null
                ? new LinkedHashMap<>(request.getParameters())
                : new LinkedHashMap<>();
        if (request.getThink() != null) {
            params.put("think", request.getThink());
        }
        if (request.getSearch() != null) {
            params.put("search", request.getSearch());
        }

        ChatScope.ChatScopeBuilder builder = ChatScope.builder()
                .model(request.getModel())
                .systemPrompt(request.getSystemPrompt())
                .input(request.getPrompt())
                .temperature(request.getTemperature())
                .maxTokens(request.getMaxTokens())
                .imageUrls(request.getImageUrls())
                .parameters(params);

        if (request.getMessages() != null && !request.getMessages().isEmpty()) {
            ChatContext context = new ChatContext();
            List<ChatMessage> history = new ArrayList<>();
            for (Map<String, Object> msg : request.getMessages()) {
                String role = msg.get("role") != null ? msg.get("role").toString() : "user";
                String content = msg.get("content") != null ? msg.get("content").toString() : "";
                history.add(new ChatMessage(role, content));
            }
            context.setHistory(history);
            builder.context(context);
        }

        return builder.build();
    }

    private Map<String, Object> buildResult(com.chua.common.support.ai.chat.ChatResponse response) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("text", response.getContent() != null ? response.getContent() : "");
        if (response.getUsage() != null) {
            result.put("promptTokens", response.getUsage().getInputTokens());
            result.put("completionTokens", response.getUsage().getOutputTokens());
            result.put("totalTokens", response.getUsage().getTotalTokens());
        } else {
            result.put("promptTokens", 0);
            result.put("completionTokens", 0);
            result.put("totalTokens", 0);
        }
        return result;
    }

    private Map<String, Object> buildErrorResult(Exception e) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("text", "");
        result.put("error", Map.of("message", e.getMessage() != null ? e.getMessage() : "Chat failed"));
        result.put("promptTokens", 0);
        result.put("completionTokens", 0);
        result.put("totalTokens", 0);
        return result;
    }
}
