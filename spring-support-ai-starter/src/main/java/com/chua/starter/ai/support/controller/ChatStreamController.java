package com.chua.starter.ai.support.controller;

import com.chua.starter.ai.support.chat.ChatMessage;
import com.chua.starter.ai.support.entity.AIDialog;
import com.chua.starter.ai.support.model.AiChatRequest;
import com.chua.starter.ai.support.search.WebResult;
import com.chua.starter.ai.support.search.WebSearchService;
import com.chua.starter.ai.support.service.AIDialogRecordService;
import com.chua.starter.ai.support.service.AiChatFacadeService;
import com.chua.starter.ai.support.service.SysAiGlobalSettingService;
import com.chua.starter.ai.support.manage.mapper.SysAiProviderModelMapper;
import com.chua.starter.ai.support.manage.store.SysAiProviderModel;
import com.chua.starter.ai.support.util.AiUserContext;
import com.chua.starter.common.support.api.annotations.ApiReturnFormatIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Guada 前端聊天流式控制器。
 *
 * <p>对接前端 /v2/chat/stream（SSE）裸 data 帧协议：
 * create -> 多个 text(增量) -> finish -> turn_end -> [DONE]。</p>
 *
 * <p>复用 {@link AiChatFacadeService} 走 agnes-2.5-flash 模型，
 * 用户消息与助手回复按 sessionId 落 MySQL（{@link AIDialogRecordService}）。</p>
 *
 * @author CH
 */
@RestController
@RequestMapping("/v2/chat")
@ApiReturnFormatIgnore
@RequiredArgsConstructor
@Slf4j
public class ChatStreamController {

    /**
     * 默认模型（fallback）
    */
    private static final String DEFAULT_MODEL_FALLBACK = "agnes-2.5-flash";

    private final AiChatFacadeService aiChatFacadeService;
    private final AIDialogRecordService aiDialogRecordService;
    private final ObjectMapper objectMapper;
    private final SysAiGlobalSettingService sysAiGlobalSettingService;
    private final SysAiProviderModelMapper sysAiProviderModelMapper;
    private final WebSearchService webSearchService;

    /**
     * 前端 sessionId(UUID) -> 后端 sys_ai_session 主键(Long) 的进程内映射
    */
    private final Map<String, Long> sessionDialogMap = new ConcurrentHashMap<>();

    /**
     * 活跃流式连接，供 /stop 主动结束
    */
    private final Map<String, SseEmitter> activeStreams = new ConcurrentHashMap<>();

    /**
     * 原始输出流（绕过 SseEmitter 自动加 data: 前缀，严格控制帧格式为 "data: <json>\\n\\n"）
    */
    private final Map<String, java.io.OutputStream> rawOut = new ConcurrentHashMap<>();

    /**
     * 当前活跃 sessionId（用于 writeRaw 精准写入）
    */
    private final ThreadLocal<String> currentSessionId = new ThreadLocal<>();

    /**
     * 已请求取消的流式会话集合
    */
    private final java.util.Set<String> cancelledStreams = ConcurrentHashMap.newKeySet();

    /**
     * SSE 流式聊天。
     * @param body 请求体，不允许为 null
     * @param response 响应，不允许为 null
     * @return SseEmitter 对象
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestBody Map<String, Object> body, HttpServletResponse response) {
        String sessionId = str(body.get("sessionId"));
        Map<?, ?> userMessage = asMap(body.get("userMessage"));
        String content = userMessage != null ? str(userMessage.get("content")) : "";
        if (content == null || content.isBlank()) {
            content = str(body.get("content"));
        }
        
        // 从请求中获取用户选择的模型
        String selectedModel = str(body.get("modelId"));
        if (selectedModel == null || selectedModel.isBlank()) {
            selectedModel = str(body.get("model"));
        }

        
        // 解析用户选择的模型ID为实际模型名称
        // 前端传的 modelId 格式：providerId:modelName（如 1789547286889:agnes-3.0-flash）
        // 或纯 providerId（如 1789547286889）
        if (selectedModel != null && !selectedModel.isBlank()) {
            try {
                if (selectedModel.contains(":")) {
                    // 格式：providerId:modelName，直接取冒号后面的模型名称
                    String modelName = selectedModel.substring(selectedModel.indexOf(":") + 1);
                    log.info("[ChatStream] 模型ID {} 解析为模型名称: {}", selectedModel, modelName);
                    selectedModel = modelName;
                } else if (selectedModel.matches("\\d+")) {
                    // 纯数字 = providerId，查询该 provider 下第一个启用的模型
                    com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysAiProviderModel> wrapper =
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
                    wrapper.eq(SysAiProviderModel::getProviderId, selectedModel)
                           .eq(SysAiProviderModel::getEnabled, true)
                           .orderByAsc(SysAiProviderModel::getSortOrder)
                           .last("LIMIT 1");
                    SysAiProviderModel pm = sysAiProviderModelMapper.selectOne(wrapper);
                    if (pm != null && pm.getModel() != null) {
                        log.info("[ChatStream] providerId {} 映射到模型名称: {}", selectedModel, pm.getModel());
                        selectedModel = pm.getModel();
                    } else {
                        log.warn("[ChatStream] providerId {} 下未找到启用的模型", selectedModel);
                    }
                }
                // 否则直接当作模型名称使用
            } catch (Exception e) {
                log.warn("[ChatStream] 模型ID解析失败 {}: {}", selectedModel, e.getMessage());
            }
        }
        // 1. 解析会话并落库本轮用户消息：这条写路径同时是会话归属校验（他人会话 403、不存在 404），
        //    必须放在声明 SSE 响应头之前，越权请求才能拿到标准 JSON 错误而不是半个 event-stream
        final Long dialogId = resolveDialogId(sessionId);
        final Long[] userMessageIdHolder = new Long[1];
        ChatMessage userChatMsg = new ChatMessage("user", content);
        aiDialogRecordService.addMessage(dialogId, userChatMsg);
        userMessageIdHolder[0] = userChatMsg.getId();
        log.info("[ChatStream] 用户消息已保存 id={}", userMessageIdHolder[0]);

// 0L = 不超时，避免前端报异步请求超时
        SseEmitter emitter = new SseEmitter(0L);
        if (sessionId != null) {
            activeStreams.put(sessionId, emitter);
            try {
                response.setContentType("text/event-stream;charset=UTF-8");
                response.setHeader("Cache-Control", "no-cache");
                response.setHeader("X-Accel-Buffering", "no");
                rawOut.put(sessionId, response.getOutputStream());
            } catch (Exception ignored) {
            }
        }
        registerCleanup(emitter, sessionId);

        try {
            // 2. 组装历史消息（含本轮用户消息，参考 AIChatController）
            List<Map<String, Object>> msgList = new ArrayList<>();
            try {
                List<ChatMessage> history = aiDialogRecordService.getDialogMessages(dialogId);
                if (history != null) {
                    for (ChatMessage m : history) {
                        Map<String, Object> mm = new LinkedHashMap<>();
                        mm.put("role", m.getRole() != null ? m.getRole() : "user");
                        mm.put("content", m.getContent() != null ? m.getContent() : "");
                        msgList.add(mm);
                    }
                }
            } catch (Exception e) {
                log.warn("[ChatStream] 加载历史失败 sessionId={}: {}", sessionId, e.getMessage());
            }
            if (msgList.isEmpty()) {
                Map<String, Object> mm = new LinkedHashMap<>();
                mm.put("role", "user");
                mm.put("content", content != null ? content : "");
                msgList.add(mm);
            }

            // 3. 构建模型请求，优先使用用户选择的模型，否则使用默认模型
            String modelToUse;
            if (selectedModel != null && !selectedModel.isBlank()) {
                modelToUse = selectedModel;
                log.info("[ChatStream] 使用用户选择的模型: {}", modelToUse);
            } else {
                modelToUse = getDefaultModel();
                log.info("[ChatStream] 使用默认模型: {}", modelToUse);
            }
            AiChatRequest request = new AiChatRequest();
            request.setModel(modelToUse);
            request.setStream(true);
            request.setPrompt(content != null ? content : "");
            request.setMessages(msgList);
            // 从模型表读取采样参数
            try {
                SysAiProviderModel modelInfo = sysAiProviderModelMapper.selectById(modelToUse);
                if (modelInfo != null) {
                    if (modelInfo.getTemperature() != null) {
                        request.setTemperature(modelInfo.getTemperature());
                    }
                    if (modelInfo.getMaxTokens() != null) {
                        request.setMaxTokens(modelInfo.getMaxTokens());
                    }
                    if (modelInfo.getTopP() != null) {
                        request.setTopP(modelInfo.getTopP());
                    }
                    if (modelInfo.getFrequencyPenalty() != null) {
                        request.setFrequencyPenalty(modelInfo.getFrequencyPenalty());
                    }
                }
            } catch (Exception e) {
                log.warn("[ChatStream] 读取模型参数失败 model={}: {}", modelToUse, e.getMessage());
            }
            // 读取前端传来的 thinking / web_search 开关
            Object thinkObj = body.get("think");
            if (thinkObj != null) {
                request.setThink(Boolean.parseBoolean(String.valueOf(thinkObj)));
            }
            Object searchObj = body.get("search");
            if (searchObj != null) {
                request.setSearch(Boolean.parseBoolean(String.valueOf(searchObj)));
            }

            // 4. 生成本轮消息标识
            String messageId = UUID.randomUUID().toString();
            String turnsId = UUID.randomUUID().toString();
            String contentId = UUID.randomUUID().toString();

            // 5. 所有写 SSE 的动作放到后台线程，确保 emitter 已被 Spring 初始化后再写
            final String sid = sessionId;
            final String finalContent = content;
            final String userMsgId = userMessage != null && str(userMessage.get("id")) != null ? str(userMessage.get("id")) : UUID.randomUUID().toString();
            CompletableFuture.runAsync(AiUserContext.capturing(() -> {
                currentSessionId.set(sid);
                try {
                    // 等待 Spring 把 emitter 绑定到响应（方法返回后才初始化 handler），避免首帧 send 丢失
                    Thread.sleep(120);
                    // 先发 user_message 事件，把用户消息加入前端消息列表
                    Map<String, Object> userContent = new LinkedHashMap<>();
                    userContent.put("id", contentId);
                    userContent.put("content", finalContent != null ? finalContent : "");
                    userContent.put("state", Map.of("isStreaming", false));
                    Map<String, Object> userMsg = new LinkedHashMap<>();
                    userMsg.put("id", userMsgId);
                    userMsg.put("role", "user");
                    userMsg.put("contents", java.util.List.of(userContent));
                    userMsg.put("state", Map.of("isStreaming", false));
                    Map<String, Object> userEvent = new LinkedHashMap<>();
                    userEvent.put("type", "user_message");
                    userEvent.put("userMessage", userMsg);
                    sendEvent(emitter, userEvent);

                    Map<String, Object> createEvent = new LinkedHashMap<>();
                    createEvent.put("type", "create");
                    createEvent.put("messageId", messageId);
                    createEvent.put("turnsId", turnsId);
                    createEvent.put("contentId", contentId);
                    createEvent.put("modelName", modelToUse);
                    createEvent.put("parentId", userMsgId);
                    sendEvent(emitter, createEvent);
                    log.info("[ChatStream] 已发送 create sessionId={}", sid);
                } catch (Exception e) {
                    log.warn("[ChatStream] 发送 create 失败: {}", e.getMessage());
                }

                // 在线检索：联网抓取实时资料并注入上下文（RAG），不依赖模型自带联网
                if (Boolean.TRUE.equals(request.getSearch())) {
                    try {
                        String searchQuery = WebSearchService.refineQuery(finalContent);
                        Map<String, Object> searchingEvent = new LinkedHashMap<>();
                        searchingEvent.put("type", "search");
                        searchingEvent.put("status", "searching");
                        searchingEvent.put("query", searchQuery);
                        sendEvent(emitter, searchingEvent);

                        List<WebResult> results = webSearchService.search(searchQuery);
                        List<Map<String, Object>> sourceList = new ArrayList<>();
                        for (WebResult r : results) {
                            Map<String, Object> src = new LinkedHashMap<>();
                            src.put("title", safe(r.getTitle()));
                            src.put("url", safe(r.getUrl()));
                            sourceList.add(src);
                        }
                        if (!results.isEmpty()) {
                            StringBuilder reference = new StringBuilder();
                            reference.append("以下是通过联网检索获取的、与用户问题相关的实时网页资料（检索时间：")
                                    .append(java.time.LocalDate.now()).append("）：\n");
                            for (int i = 0; i < results.size(); i++) {
                                WebResult r = results.get(i);
                                reference.append('[').append(i + 1).append("] 标题：").append(safe(r.getTitle())).append('\n');
                                if (r.getSnippet() != null && !r.getSnippet().isBlank()) {
                                    reference.append("    摘要：").append(safe(r.getSnippet())).append('\n');
                                }
                                reference.append("    来源链接：").append(safe(r.getUrl())).append('\n');
                            }
                            reference.append("\n请严格依据上述资料回答用户问题：在关键事实后用 [n] 标注来源编号，")
                                    .append("并在回答末尾以“参考来源”列出编号与对应链接；若资料不足以回答，请明确说明，不要编造。");

                            Map<String, Object> systemReference = new LinkedHashMap<>();
                            systemReference.put("role", "system");
                            systemReference.put("content", reference.toString());
                            request.getMessages().add(0, systemReference);
                        }
                        Map<String, Object> searchDoneEvent = new LinkedHashMap<>();
                        searchDoneEvent.put("type", "search");
                        searchDoneEvent.put("status", "done");
                        searchDoneEvent.put("query", searchQuery);
                        searchDoneEvent.put("results", sourceList);
                        sendEvent(emitter, searchDoneEvent);
                        log.info("[ChatStream] 在线检索完成 sessionId={}, 来源 {} 条", sid, sourceList.size());
                    } catch (Exception e) {
                        log.warn("[ChatStream] 在线检索失败 sessionId={}: {}", sid, e.getMessage());
                    }
                }

                StringBuilder fullReply = new StringBuilder();
                final boolean[] firstChunkLogged = { false };

                // 6. 调模型流式
                aiChatFacadeService.streamWithUsage(request,
                        chunk -> {
                            // 检查是否已取消
                            if (cancelledStreams.contains(sid)) {
                                return;
                            }
                            if (chunk != null && !chunk.isEmpty()) {
                                if (!firstChunkLogged[0]) {
                                    firstChunkLogged[0] = true;
                                    log.info("[ChatStream] 收到首个 chunk sessionId={}, len={}", sid, chunk.length());
                                } else {
                                    log.info("[ChatStream] 收到 chunk sessionId={}, len={}, preview={}", sid, chunk.length(), chunk.length() > 20 ? chunk.substring(0, 20) : chunk);
                                }
                                fullReply.append(chunk);
                                Map<String, Object> textEvent = new LinkedHashMap<>();
                                textEvent.put("type", "text");
                                textEvent.put("content", chunk);
                                sendEvent(emitter, textEvent);
                            }
                        },
                        (reply, usage) -> {
                            String finalText = reply != null ? reply : fullReply.toString();
                            log.info("[ChatStream] 流式完成 sessionId={}, 回复长度={}", sid, finalText.length());
                            // 计量落库用的是模型回传的真实用量，气泡与消息行必须同源：
                            // 字符数估算（×1.5）会把同一轮对话的用量算成两套数
                            int promptTokens;
                            int completionTokens;
                            Integer reportedPrompt = usage == null ? null : usage.getInputTokens();
                            Integer reportedCompletion = usage == null ? null : usage.getOutputTokens();
                            if (reportedPrompt != null && reportedCompletion != null) {
                                promptTokens = reportedPrompt;
                                completionTokens = reportedCompletion;
                            } else {
                                promptTokens = finalContent != null ? (int) (finalContent.length() * 1.5) : 0;
                                completionTokens = (int) (finalText.length() * 1.5);
                                log.info("[ChatStream] 模型未回传用量，按字符数估算 tokens sessionId={}", sid);
                            }
                            int totalTokens = promptTokens + completionTokens;
                            try {
                                ChatMessage assistantMsg = new ChatMessage("assistant", finalText);
                                assistantMsg.setParentId(userMessageIdHolder[0]);
                                assistantMsg.setModelName(modelToUse);
                                assistantMsg.setPromptTokens(promptTokens);
                                assistantMsg.setCompletionTokens(completionTokens);
                                assistantMsg.setTotalTokens(totalTokens);
                                aiDialogRecordService.addMessage(dialogId, assistantMsg);
                                log.info("[ChatStream] 助手消息已保存 parentId={}, tokens={}", userMessageIdHolder[0], totalTokens);
                            } catch (Exception e) {
                                log.warn("[ChatStream] 保存助手消息失败 sessionId={}: {}", sid, e.getMessage());
                            }
                            Map<String, Object> finishEvent = new LinkedHashMap<>();
                            finishEvent.put("type", "finish");
                            finishEvent.put("finishReason", "stop");
                            Map<String, Object> usageMap = new java.util.HashMap<>();
                            usageMap.put("promptTokens", promptTokens);
                            usageMap.put("completionTokens", completionTokens);
                            usageMap.put("totalTokens", totalTokens);
                            finishEvent.put("usage", usageMap);
                            sendEvent(emitter, finishEvent);
                            Map<String, Object> turnEndEvent = new LinkedHashMap<>();
                            turnEndEvent.put("type", "turn_end");
                            turnEndEvent.put("finishReason", "stop");
                            turnEndEvent.put("content", finalText);
                            turnEndEvent.put("messageId", messageId);
                            turnEndEvent.put("contentId", contentId);
                            sendEvent(emitter, turnEndEvent);
                            sendDone(emitter);
                            activeStreams.remove(sid);
                            cancelledStreams.remove(sid);
                            emitter.complete();
                            currentSessionId.remove();
                        },
                        error -> {
                            String msg = error != null && error.getMessage() != null ? error.getMessage() : "未知错误";
                            log.error("[ChatStream] 流式失败 sessionId={}", sid, error);
                            Map<String, Object> finishEvent = new LinkedHashMap<>();
                            finishEvent.put("type", "finish");
                            finishEvent.put("finishReason", "error");
                            finishEvent.put("error", msg);
                            sendEvent(emitter, finishEvent);
                            Map<String, Object> turnEndEvent = new LinkedHashMap<>();
                            turnEndEvent.put("type", "turn_end");
                            turnEndEvent.put("finishReason", "error");
                            turnEndEvent.put("error", msg);
                            turnEndEvent.put("messageId", messageId);
                            turnEndEvent.put("contentId", contentId);
                            sendEvent(emitter, turnEndEvent);
                            sendDone(emitter);
                            activeStreams.remove(sid);
                            emitter.complete();
                            currentSessionId.remove();
                        },
                        thinking -> {
                            if (thinking != null && !thinking.isEmpty()) {
                                Map<String, Object> thinkEvent = new LinkedHashMap<>();
                                thinkEvent.put("type", "think");
                                thinkEvent.put("reasoningContent", thinking);
                                sendEvent(emitter, thinkEvent);
                            }
                        }
                );
            }));
        } catch (Exception e) {
            log.error("[ChatStream] 初始化流式失败 sessionId={}", sessionId, e);
            Map<String, Object> finishEvent = new LinkedHashMap<>();
            finishEvent.put("type", "finish");
            finishEvent.put("finishReason", "error");
            finishEvent.put("error", e.getMessage() != null ? e.getMessage() : "初始化失败");
            sendEvent(emitter, finishEvent);
            Map<String, Object> turnEndEvent = new LinkedHashMap<>();
            turnEndEvent.put("type", "turn_end");
            turnEndEvent.put("finishReason", "error");
            sendEvent(emitter, turnEndEvent);
            sendDone(emitter);
            activeStreams.remove(sessionId);
            emitter.complete();
        }

        return emitter;
    }

    /**
     * 停止指定会话的流式输出。
     * @param sessionId 会话ID，不允许为 null
     * @return 结果映射，无数据时为空映射
     */
    @PostMapping("/stream/{sessionId}/stop")
    public Map<String, Object> stop(@PathVariable String sessionId) {
        SseEmitter emitter = activeStreams.get(sessionId);
        if (emitter != null) {
            try {
                Map<String, Object> finishEvent = new LinkedHashMap<>();
                finishEvent.put("type", "finish");
                finishEvent.put("finishReason", "stop");
                sendEvent(emitter, finishEvent);
                Map<String, Object> turnEndEvent = new LinkedHashMap<>();
                turnEndEvent.put("type", "turn_end");
                turnEndEvent.put("finishReason", "stop");
                sendEvent(emitter, turnEndEvent);
                sendDone(emitter);
            } catch (Exception ignored) {
            }
            try {
                emitter.complete();
            } catch (Exception ignored) {
            }
            activeStreams.remove(sessionId);
            cancelledStreams.add(sessionId);
            log.info("[ChatStream] 已停止流式 sessionId={}", sessionId);
        }
        return Map.of("success", true);
    }

    // ==================== 内部方法 ====================

    /**
     * 从系统设置读取默认聊天模型。
     * 优先级：配置的默认模型 → 第一个可用模型 → fallback
     * @return 结果字符串
     */
    private String getDefaultModel() {
        // 1. 依次从系统设置读取默认模型（兼容多个字段名）
        String[][] keys = {
            {"runtime", "default_model"},
            {"models", "defaultChatModelId"},
            {"runtime", "default_chat_model"}
        };
        for (String[] k : keys) {
            String configured = sysAiGlobalSettingService.getValue(k[0], k[1])
                    .map(String::trim)
                    .filter(value -> !value.isEmpty())
                    .orElse(null);
            if (configured != null) {
                // 统一去掉 providerId: 前缀，返回纯模型名（与用户手动选模型的解析结果一致）
                int idx = configured.indexOf(':');
                return idx >= 0 ? configured.substring(idx + 1) : configured;
            }
        }
        // 2. 都没配置才 fallback
        log.warn("[ChatStream] 未配置默认模型，使用 fallback: {}", DEFAULT_MODEL_FALLBACK);
        return DEFAULT_MODEL_FALLBACK;
    }

    /**
     * 注册Cleanup。
     *
     * @param emitter 方法入参 emitter
     * @param sessionId 会话ID，不允许为 null
     */
    private void registerCleanup(SseEmitter emitter, String sessionId) {
        emitter.onCompletion(() -> {
            if (sessionId != null) {
                activeStreams.remove(sessionId);
                rawOut.remove(sessionId);
            }
        });
        emitter.onTimeout(() -> {
            if (sessionId != null) {
                activeStreams.remove(sessionId);
                rawOut.remove(sessionId);
            }
            try {
                emitter.complete();
            } catch (Exception ignored) {
            }
        });
        emitter.onError(t -> {
            if (sessionId != null) {
                activeStreams.remove(sessionId);
                rawOut.remove(sessionId);
            }
        });
    }

    /**
     * 发送一行裸 data 帧：data: <json>\n\n。
     * @param emitter 方法入参 emitter
     * @param event 方法入参 event
     */
    private void sendEvent(SseEmitter emitter, Map<String, Object> event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            writeRaw("data: " + json + "\n\n");
        } catch (Exception e) {
            log.debug("[ChatStream] 发送事件失败: {}", e.getMessage());
        }
    }

    /**
     * 发送 data: [DONE] 结束帧。
     * @param emitter 方法入参 emitter
     */
    private void sendDone(SseEmitter emitter) {
        try {
            writeRaw("data: [DONE]\n\n");
        } catch (Exception e) {
            log.debug("[ChatStream] 发送 DONE 失败: {}", e.getMessage());
        }
    }

    /**
     * 直接写原始字节到当前 session 的响应输出流，flush 刷新。
     * @param s 方法入参 s
     */
    private void writeRaw(String s) throws Exception {
        String sid = currentSessionId.get();
        if (sid != null) {
            java.io.OutputStream os = rawOut.get(sid);
            if (os != null) {
                os.write(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                os.flush();
                return;
            }
        }
        // fallback：遍历所有活跃流（兼容旧逻辑）
        for (java.io.OutputStream os : rawOut.values()) {
            os.write(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            os.flush();
        }
    }

    /**
     * 解析前端 sessionId 到后端 dialogId。
     * 数字 sessionId 直接使用；UUID 则首次落库一个会话并缓存映射。
     * @param sessionId 会话ID，不允许为 null
     * @return Long 对象
     */
    private Long resolveDialogId(String sessionId) {
        if (sessionId != null && sessionId.matches("\\d+")) {
            return Long.parseLong(sessionId);
        }
        if (sessionId != null && !sessionId.isBlank()) {
            Long cached = sessionDialogMap.get(sessionId);
            if (cached != null) {
                return cached;
            }
        }
        String title = "guada-" + (sessionId != null && sessionId.length() > 8 ? sessionId.substring(0, 8) : "chat");
        AIDialog dialog = aiDialogRecordService.createDialog(title);
        Long id = dialog.getId();
        if (sessionId != null && !sessionId.isBlank()) {
            sessionDialogMap.put(sessionId, id);
        }
        log.info("[ChatStream] 映射 sessionId={} -> dialogId={}", sessionId, id);
        return id;
    }

    /**
     * 字符串。
     *
     * @param o 方法入参 o
     * @return 结果字符串
     */
    private static String str(Object o) {
        return o != null ? String.valueOf(o) : null;
    }

    /**
     * null 安全转字符串。
     *
     * @param s 方法入参 s
     * @return 结果字符串，null 返回空串
     */
    private static String safe(String s) {
        return s == null ? "" : s;
    }

    /**
     * as映射。
     *
     * @param o 方法入参 o
     * @return 结果映射，无数据时为空映射
     */
    @SuppressWarnings("unchecked")
    private static Map<?, ?> asMap(Object o) {
        return o instanceof Map ? (Map<?, ?>) o : null;
    }
}
