package com.chua.starter.ai.support.service.impl;

import com.chua.common.support.ai.chat.ChatClient;
import com.chua.common.support.ai.chat.ChatResponse;
import com.chua.common.support.ai.chat.ChatSyncResponse;
import com.chua.common.support.ai.chat.ModelDefinition;
import com.chua.common.support.ai.AiUsage;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.ai.support.interceptor.UsageRecorderInterceptor;
import com.chua.starter.ai.support.model.AiChatRequest;
import com.chua.starter.ai.support.registry.ChatClientRegistry;
import com.chua.starter.ai.support.service.AiChatFacadeService;
import com.chua.starter.ai.support.service.SysAiGroupService;
import com.chua.starter.ai.support.service.SysAiRoleService;
import com.chua.starter.ai.support.service.SysAiTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import com.chua.starter.ai.support.entity.SysAiModule;
import com.chua.starter.ai.support.entity.SysAiRole;
import com.chua.starter.ai.support.service.SysAiAppKeyService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
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

    /** Chat客户端注册表 */
    private final ChatClientRegistry chatClientRegistry;
    /** SYSAI分组服务 */
    private final SysAiGroupService sysAiGroupService;
    /** SYSAI令牌服务 */
    private final SysAiTokenService sysAiTokenService;
    /** SYSAIAPP密钥服务 */
    private final SysAiAppKeyService sysAiAppKeyService;
    /** SYSAIrole服务 */
    private final SysAiRoleService sysAiRoleService;
    /** Usagerecorderinterceptor */
    private final UsageRecorderInterceptor usageRecorderInterceptor;
    /** Objectmapper */
    private final ObjectMapper objectMapper;

    /**
     * 根据分组 ID 与请求模型名解析 ChatClient。
     * <ul>
     *   <li>无分组 → 全局聚合 ChatClient（系统自用）</li>
     *   <li>有分组 + 指定 model → 按 model 从注册中心查找</li>
     *   <li>有分组 + 未指定 model → 使用默认 ChatClient</li>
     * </ul>
     */
    /**
     * 根据分组 ID 与请求模型名解析 ChatClient。
     * <ul>
     *   <li>无分组 → 全局聚合 ChatClient（系统自用）</li>
     *   <li>有分组 + 指定 model → 校验 model 是否属于分组白名单，通过后按 model 查找</li>
     *   <li>有分组 + 未指定 model → 使用分组默认 ChatClient</li>
     * </ul>
     */
    private ChatClient resolveChatClient(Integer groupId, String model) {
        // 系统内部调用（无分组）：优先使用聚合 ChatClient（复用实例，按模型路由）
        if (groupId == null) {
            ChatClient aggregate = chatClientRegistry.getAggregate();
            if (aggregate instanceof com.chua.common.support.ai.chat.aggregate.AggregateChatClient ac
                    && ac.clientCount() > 0) {
                log.debug("[AiChatFacade] 使用聚合 ChatClient（系统内部调用）");
                return ac;
            }
            ChatClient global = chatClientRegistry.getDefault();
            if (global == null) {
                throw new IllegalStateException("全局 ChatClient 未初始化，请检查 AiConfiguration 配置");
            }
            log.debug("[AiChatFacade] 使用全局 ChatClient（系统内部调用，聚合客户端不可用或无底层客户端）");
            return global;
        }

        // 外部 API 调用（有分组）：按分组模型隔离
        Set<String> allowedCodes = getAllowedModelCodes(groupId);
        if (allowedCodes.isEmpty()) {
            log.warn("[AiChatFacade] 分组 {} 无关联模型，拒绝访问", groupId);
            throw new IllegalStateException("分组 " + groupId + " 未关联任何模型，无法提供服务");
        }

        // 指定了 model → 校验模型是否属于分组白名单
        if (StringUtils.isNotBlank(model)) {
            String modelLower = model.toLowerCase();
            if (!allowedCodes.contains(modelLower)) {
                log.warn("[AiChatFacade] 模型 {} 不在分组 {} 的白名单中，拒绝访问", model, groupId);
                throw new IllegalStateException("模型 " + model + " 不在当前分组的白名单中");
            }
            ChatClient specificClient = chatClientRegistry.getByModel(model);
            if (specificClient != null) {
                log.debug("[AiChatFacade] 使用指定模型 ChatClient: model={}, groupId={}", model, groupId);
                return specificClient;
            }
            // 模型在白名单但未单独注册到注册中心时，回退全局 ChatClient（该客户端按请求模型动态路由）
            ChatClient global = chatClientRegistry.getDefault();
            if (global != null) {
                log.debug("[AiChatFacade] 模型 {} 未单独注册，回退全局 ChatClient（按请求模型路由）", model);
                return global;
            }
            log.warn("[AiChatFacade] 指定模型 {} 不在注册中心且无全局客户端可用，拒绝访问", model, groupId);
            throw new IllegalStateException("模型 " + model + " 未在注册中心找到，请先配置该模型");
        }

        // 未指定 model → 拒绝（有分组时必须明确指定模型）
        log.warn("[AiChatFacade] 分组 {} 请求未指定模型，拒绝访问", groupId);
        throw new IllegalStateException("分组 " + groupId + " 的请求必须指定模型名称");
    }

    /**
     * 获取分组允许的模型编码集合。
     *
     * @param groupId 分组 ID
     * @return 模型编码集合（小写），无分组或分组无模型时返回空集合
     */
    private Set<String> getAllowedModelCodes(Integer groupId) {
        if (groupId == null) {
            return Collections.emptySet();
        }
        List<SysAiModule> groupModels = sysAiGroupService.getGroupModels(groupId);
        if (groupModels == null || groupModels.isEmpty()) {
            return Collections.emptySet();
        }
        return groupModels.stream()
                .map(SysAiModule::getSysAiModuleCode)
                .filter(Objects::nonNull)
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
    }

    @Override
    /** 调用 */
    public Map<String, Object> call(AiChatRequest request) {
        // 从 request.parameters 提取分组 ID 与 AppKey（由 Controller 注入）
        Integer groupId = extractGroupId(request);
        String appKey = extractAppKey(request);
        // 深度思考 / 智能搜索开关并入 parameters，供底层 ChatClient 消费
        injectFeatureFlags(request);
        ChatClient client = resolveChatClient(groupId, request.getModel());

        String prompt = buildPrompt(request);
        long start = System.currentTimeMillis();

        try {
            ChatSyncResponse chatSyncResponse = client.chatSyncWithResponse(prompt);
            String result = chatSyncResponse != null ? chatSyncResponse.getText() : null;
            long elapsed = System.currentTimeMillis() - start;
            log.debug("[AiChatFacade] call 完成: model={}, groupId={}, elapsed={}ms, len={}",
                    request.getModel(), groupId, elapsed,
                    result != null ? result.length() : 0);

            // 记录用量（异步）
            AiUsage usage = chatSyncResponse != null ? chatSyncResponse.getUsage() : null;
            recordUsage(groupId, client, request.getModel(), "sync", usage);
            // 更新 AppKey Token 配额（异步）
            updateTokenQuota(appKey, usage);

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
            // 顶层透传文本内容，供 OpenAI 兼容控制器直接消费（result.get("text")）
            response.put("text", result);

            Map<String, Object> usageOut = new LinkedHashMap<>();
            usageOut.put("prompt_tokens", usage != null && usage.getInputTokens() != null ? usage.getInputTokens() : 0);
            usageOut.put("completion_tokens", usage != null && usage.getOutputTokens() != null ? usage.getOutputTokens() : 0);
            usageOut.put("total_tokens", usage != null && usage.getTotalTokens() != null ? usage.getTotalTokens() : 0);
            response.put("usage", usageOut);

            return response;
        } catch (Exception e) {
            log.error("[AiChatFacade] call 失败: model={}, groupId={}", request.getModel(), groupId, e);
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("error", Map.of("message", e.getMessage(), "type", "server_error"));
            return error;
        }
    }

    @Override
    /** Stream 兼容旧调用（无逐 chunk 回调） */
    public SseEmitter stream(AiChatRequest request, Consumer<String> onComplete) {
        return stream(request, null, onComplete);
    }

    @Override
    /** Stream 逐增量文本回调版本，供 WS 流式推送使用 */
    public SseEmitter stream(AiChatRequest request, Consumer<String> onChunk, Consumer<String> onComplete) {
        Integer groupId = extractGroupId(request);
        String appKey = extractAppKey(request);
        // 深度思考 / 智能搜索开关并入 parameters，供底层 ChatClient 消费
        injectFeatureFlags(request);
        ChatClient client = resolveChatClient(groupId, request.getModel());
        String prompt = buildPrompt(request);

        // 5 分钟超时
        SseEmitter emitter = new SseEmitter(300_000L);

        String modelName = request.getModel() != null ? request.getModel() : "default";
        String chatId = "chatcmpl-" + UUID.randomUUID().toString().substring(0, 8);
        long created = System.currentTimeMillis() / 1000;

        // 防止重复 complete
        AtomicBoolean done = new AtomicBoolean(false);
        // 累积完整回复内容，供 onComplete 回调使用
        StringBuilder fullReply = new StringBuilder();

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
                            fullReply.append(content);
                            if (onChunk != null) {
                                onChunk.accept(content);
                            }
                            String sseData = String.format(
                                    "{\"id\":\"%s\",\"object\":\"chat.completion.chunk\",\"created\":%d," +
                                    "\"model\":\"%s\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\"%s\"},\"finish_reason\":null}]}",
                                    chatId, created, modelName, escapeJson(content));
                            emitter.send(SseEmitter.event().data(sseData, MediaType.TEXT_PLAIN));
                        }
                    }
                    case STOP -> {
                        // 兼容同步/聚合式 ChatClient（如 DefaultScopeChatClient）：
                        // 这类客户端不产生 STREAMING 事件，只在 STOP 一次性交付完整内容，
                        // 需在 STOP 前补发内容 delta，否则内容会丢失。
                        // 若此前已有 STREAMING 内容（真流式），STOP 的 content 与已发内容重复，跳过避免重复输出。
                        String content = response.getContent();
                        if (content != null && !content.isEmpty() && fullReply.indexOf(content) < 0) {
                            fullReply.append(content);
                            String dataChunk = String.format(
                                    "{\"id\":\"%s\",\"object\":\"chat.completion.chunk\",\"created\":%d," +
                                    "\"model\":\"%s\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\"%s\"},\"finish_reason\":null}]}",
                                    chatId, created, modelName, escapeJson(content));
                            emitter.send(SseEmitter.event().data(dataChunk, MediaType.TEXT_PLAIN));
                        }
                        // 记录用量（异步），STOP 携带完整 usage
                        recordUsage(groupId, client, modelName, "async", response.getUsage());
                        // 更新 AppKey Token 配额（异步）
                        updateTokenQuota(appKey, response.getUsage());
                        if (done.compareAndSet(false, true)) {
                            String finalChunk = String.format(
                                    "{\"id\":\"%s\",\"object\":\"chat.completion.chunk\",\"created\":%d," +
                                    "\"model\":\"%s\",\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"stop\"}]}",
                                    chatId, created, modelName);
                            emitter.send(SseEmitter.event().data(finalChunk, MediaType.TEXT_PLAIN));
                            emitter.send(SseEmitter.event().data("[DONE]", MediaType.TEXT_PLAIN));
                            emitter.complete();
                        }
                        // 流结束，回调完整回复
                        notifyComplete(onComplete, fullReply);
                    }
                    case ERROR -> {
                        log.error("[AiChatFacade] stream 错误: model={}, groupId={}, error={}",
                                request.getModel(), groupId, response.getErrorMessage());
                        // ERROR 仍记录已消耗的用量与配额
                        recordUsage(groupId, client, modelName, "async", response.getUsage());
                        updateTokenQuota(appKey, response.getUsage());
                        if (done.compareAndSet(false, true)) {
                            emitter.send(SseEmitter.event().name("error")
                                    .data("{\"error\":\"" + escapeJson(response.getErrorMessage()) + "\"}",
                                            MediaType.TEXT_PLAIN));
                            emitter.complete();
                        }
                        notifyComplete(onComplete, fullReply);
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
                // 兜底完成回调
                notifyComplete(onComplete, fullReply);
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
                notifyComplete(onComplete, fullReply);
            }
        });

        return emitter;
    }

    /**
     * 触发完成回调。
     *
     * @param onComplete 完成回调
     * @param fullReply  累积的完整回复
     */
    private static void notifyComplete(Consumer<String> onComplete, StringBuilder fullReply) {
        if (onComplete == null) {
            return;
        }
        try {
            String reply = fullReply.length() > 0 ? fullReply.toString() : null;
            onComplete.accept(reply);
        } catch (Exception ex) {
            log.warn("[AiChatFacade] 完成回调执行失败: {}", ex.getMessage());
        }
    }

    @Override
    /** Models */
    public List<String> models(Integer groupId) {
        // 无分组：返回注册中心全部模型
        if (groupId == null) {
            return new ArrayList<>(chatClientRegistry.getModels());
        }
        // 有分组：仅返回该分组白名单内的模型
        Set<String> allowedCodes = getAllowedModelCodes(groupId);
        if (allowedCodes.isEmpty()) {
            return Collections.emptyList();
        }
        // 从注册中心筛选分组白名单内的模型
        Set<String> allModels = chatClientRegistry.getModels();
        return allModels.stream()
                .filter(model -> allowedCodes.contains(model.toLowerCase()))
                .collect(Collectors.toList());
    }

    @Override
    /** ModelDefinitions */
    public List<ModelDefinition> modelDefinitions(Integer groupId) {
        // 有分组时获取分组白名单
        Set<String> allowedCodes = groupId != null ? getAllowedModelCodes(groupId) : null;
        if (allowedCodes != null && allowedCodes.isEmpty()) {
            return Collections.emptyList();
        }

        // 优先使用注册中心中的模型定义（由 AiChatClientConfiguration 按配置注册）
        List<ModelDefinition> registered = chatClientRegistry.getModelDefinitions();
        if (!registered.isEmpty()) {
            List<ModelDefinition> result = new ArrayList<>();
            for (ModelDefinition def : registered) {
                if (def == null || StringUtils.isBlank(def.getId())) {
                    continue;
                }
                if (allowedCodes != null && !allowedCodes.contains(def.getId().toLowerCase())) {
                    continue;
                }
                result.add(def);
            }
            return result;
        }

        List<ModelDefinition> result = new ArrayList<>();
        // 从全局（聚合）ChatClient 获取带能力的模型定义列表
        ChatClient global = chatClientRegistry.getDefault();
        if (global == null) {
            return result;
        }
        try {
            List<ModelDefinition> definitions = global.models();
            if (definitions == null) {
                return result;
            }
            for (ModelDefinition def : definitions) {
                if (def == null || StringUtils.isBlank(def.getId())) {
                    continue;
                }
                if (allowedCodes != null && !allowedCodes.contains(def.getId().toLowerCase())) {
                    continue;
                }
                result.add(def);
            }
        } catch (Exception e) {
            log.warn("[AiChatFacade] 模型定义列表获取失败: {}", e.getMessage());
        }
        return result;
    }

    /**
     * 从 AiChatRequest 构建提示词字符串。
     * 优先使用 messages 格式，其次使用 prompt。
     */
    private String buildPrompt(AiChatRequest request) {
        // 如果指定了角色编码，加载角色并设置系统提示词
        if (StringUtils.isNotBlank(request.getRoleCode())) {
            SysAiRole role = resolveRole(request.getRoleCode());
            if (role != null) {
                if (StringUtils.isBlank(request.getSystemPrompt())) {
                    request.setSystemPrompt(role.getSysAiRoleSystemPrompt());
                }
                if (StringUtils.isBlank(request.getModel()) && StringUtils.isNotBlank(role.getSysAiRoleModel())) {
                    request.setModel(role.getSysAiRoleModel());
                }
                if (request.getTemperature() == null && role.getSysAiRoleTemperature() != null) {
                    request.setTemperature(role.getSysAiRoleTemperature());
                }
            }
        }
        // 系统提示词前置到 prompt 中，让 ChatClient 收到完整人设
        StringBuilder sb = new StringBuilder();
        if (StringUtils.isNotBlank(request.getSystemPrompt())) {
            sb.append("[system]: ").append(request.getSystemPrompt()).append("\n");
        }
        if (request.getMessages() != null && !request.getMessages().isEmpty()) {
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
            if (sb.length() > 0) {
                sb.append(request.getPrompt());
            } else {
                return request.getPrompt();
            }
        }
        String result = sb.toString().trim();
        return result.isEmpty() ? "" : result;
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
     * 从 parameters 中提取 AppKey（由 Controller 在认证时注入）。
     *
     * @param request 对话请求
     * @return AppKey 字符串，不存在时返回 {@code null}
     */
    private String extractAppKey(AiChatRequest request) {
        if (request.getParameters() == null) {
            return null;
        }
        Object appKey = request.getParameters().get("_appKey");
        if (appKey == null) {
            return null;
        }
        return String.valueOf(appKey);
    }

    /**
     * 异步更新 AppKey 的 Token 消耗配额。
     *
     * @param appKey AppKey 值，为空时跳过
     * @param usage  本次调用用量信息
     */
    private void updateTokenQuota(String appKey, AiUsage usage) {
        if (StringUtils.isBlank(appKey) || usage == null) {
            return;
        }
        Integer total = usage.getTotalTokens();
        if (total == null || total <= 0) {
            return;
        }
        try {
            sysAiAppKeyService.updateTokenQuotaByAppKey(appKey, total.longValue());
        } catch (Exception e) {
            log.debug("[AiChatFacade] 更新 AppKey Token 配额失败: {}", e.getMessage());
        }
    }

    /**
     * 将 AiChatRequest 的深度思考 / 智能搜索开关并入 parameters，
     * 供底层 ChatClient 在构建请求时读取并透传给大模型。
     */
    private void injectFeatureFlags(AiChatRequest request) {
        if (request.getThink() == null && request.getSearch() == null) {
            return;
        }
        Map<String, Object> parameters = request.getParameters();
        if (parameters == null) {
            parameters = new HashMap<>();
            request.setParameters(parameters);
        }
        if (request.getThink() != null) {
            parameters.put("think", request.getThink());
        }
        if (request.getSearch() != null) {
            parameters.put("search", request.getSearch());
        }
    }

    /**
     * 分组名称缓存：groupId → groupName，避免每次用量记录全表查询。
     *
     * <p>首次访问时通过 {@link #loadGroupName(Integer)} 从数据库加载，
     * 后续请求直接返回缓存值，直至 JVM 重启。</p>
     */
    private final java.util.concurrent.ConcurrentHashMap<Integer, String> groupNameCache =
            new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 解析分组名称（无分组返回 null），带缓存。
     *
     * <p>优先从 {@link #groupNameCache} 获取，缓存未命中时调用
     * {@link #loadGroupName(Integer)} 加载并缓存。</p>
     *
     * @param groupId 分组 ID
     * @return 分组名称，分组不存在时返回 {@code null}
     */
    private String resolveGroupName(Integer groupId) {
        if (groupId == null) {
            return null;
        }
        return groupNameCache.computeIfAbsent(groupId, this::loadGroupName);
    }

    /**
     * 从数据库加载分组名称。
     *
     * <p>通过 {@link SysAiGroupService#list()} 全量查询分组列表，
     * 按 ID 匹配后返回分组名称。此方法仅在首次访问时调用一次，
     * 后续命中缓存避免重复全表查询。</p>
     *
     * @param groupId 分组 ID
     * @return 分组名称，分组不存在时返回 {@code null}
     */
    private String loadGroupName(Integer groupId) {
        try {
            return sysAiGroupService.list().stream()
                    .filter(g -> groupId.equals(g.getSysAiGroupId()))
                    .map(g -> g.getSysAiGroupName())
                    .findFirst()
                    .orElse(null);
        } catch (Exception e) {
            log.debug("[AiChatFacade] 解析分组名失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 异步记录本次调用用量。
     *
     * @param groupId 分组 ID（无分组为 null）
     * @param client  消息客户端
     * @param model   模型名称
     * @param callType 调用类型（sync/async）
     * @param usage   用量信息
     */
    private void recordUsage(Integer groupId, ChatClient client, String model, String callType, AiUsage usage) {
        if (usageRecorderInterceptor == null) {
            return;
        }
        AiUsage effective = usage;
        if (effective == null) {
            effective = AiUsage.builder().build();
        }
        String provider = effective.getProvider();
        if (provider == null && client instanceof com.chua.starter.ai.support.chat.ChatClient sc) {
            provider = sc.getProvider();
        }
        if (provider == null) {
            provider = "unknown";
        }
        try {
            usageRecorderInterceptor.record(
                    resolveGroupName(groupId),
                    provider,
                    model,
                    callType,
                    effective,
                    null,
                    null);
        } catch (Exception e) {
            log.debug("[AiChatFacade] 记录用量失败: {}", e.getMessage());
        }
    }

    /**
     * 根据角色编码解析角色（人设）。
     *
     * @param roleCode 角色编码
     * @return 角色实体，不存在时返回 {@code null}
     */
    private SysAiRole resolveRole(String roleCode) {
        try {
            return sysAiRoleService.getByCode(roleCode);
        } catch (Exception e) {
            log.debug("[AiChatFacade] 角色解析失败: code={}, {}", roleCode, e.getMessage());
            return null;
        }
    }

    /**
     * JSON 字符串转义。
     *
     * <p>返回不含外层引号的纯转义文本，供外层 {@code "content":"%s"} 模板二次包裹，
     * 避免产生 {@code "content":""hello""} 的非法 JSON。</p>
     *
     * @param s 原始文本
     * @return 已转义且不带外层引号的文本；为 null 时返回空串
     */
    private String escapeJson(String s) {
        if (s == null) {
            return "";
        }
        try {
            String encoded = objectMapper.writeValueAsString(s);
            if (encoded.length() >= 2 && encoded.startsWith("\"") && encoded.endsWith("\"")) {
                return encoded.substring(1, encoded.length() - 1);
            }
            return encoded;
        } catch (Exception e) {
            return "";
        }
    }
}

