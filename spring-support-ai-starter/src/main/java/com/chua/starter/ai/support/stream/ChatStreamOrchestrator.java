package com.chua.starter.ai.support.stream;

import com.chua.starter.ai.support.chat.ChatMemorySupport;
import com.chua.starter.ai.support.chat.ChatMessage;
import com.chua.starter.ai.support.entity.AIDialog;
import com.chua.starter.ai.support.entity.SysAiRole;
import com.chua.starter.ai.support.mapper.SysAiRoleMapper;
import com.chua.starter.ai.support.mapper.SysAiSessionMapper;
import com.chua.starter.ai.support.model.AiChatRequest;
import com.chua.starter.ai.support.search.WebResult;
import com.chua.starter.ai.support.search.WebSearchService;
import com.chua.starter.ai.support.service.AIDialogRecordService;
import com.chua.starter.ai.support.service.AiChatFacadeService;
import com.chua.starter.ai.support.service.ModelAliasService;
import com.chua.starter.ai.support.service.SysAiGlobalSettingService;
import com.chua.starter.ai.support.configuration.client.EffectiveChatModelResolver;
import com.chua.starter.ai.support.manage.mapper.SysAiProviderModelMapper;
import com.chua.starter.ai.support.manage.store.SysAiProviderModel;
import com.chua.starter.ai.support.util.AiUserContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 聊天流式编排（传输无关）。
 *
 * <p>{@link #prepare(Map)} 在请求线程内完成模型/角色解析、会话归属校验、用户消息落库、历史与请求组装；
 * {@link #execute(PreparedChat, ChatFrameSink)} 提交异步任务，经帧槽逐帧产出（思考/正文/检索/trace）。
 * SSE 控制器与 WebSocket 处理器共用本编排，保证两种传输的帧完全一致、同样实时。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatStreamOrchestrator {

    /**
     * 默认模型（fallback）
     */
    /**
     * 生效对话模型解析器（连接器级 → Bot 默认 → 配置中心默认）
     */
    private final EffectiveChatModelResolver effectiveChatModelResolver;

    private final AiChatFacadeService aiChatFacadeService;
    private final AIDialogRecordService aiDialogRecordService;
    private final ObjectMapper objectMapper;
    private final SysAiGlobalSettingService sysAiGlobalSettingService;
    private final SysAiProviderModelMapper sysAiProviderModelMapper;

    /**
     * 会话压缩记录服务：把摘要覆盖的历史替换成一条摘要
     */
    private final com.chua.starter.ai.support.service.SysAiSessionCompressionService sessionCompressionService;
    private final SysAiRoleMapper sysAiRoleMapper;
    private final SysAiSessionMapper sysAiSessionMapper;
    private final WebSearchService webSearchService;
    private final ModelAliasService modelAliasService;
    private final ChatMemorySupport chatMemorySupport;
    private final ChatStreamRegistry registry;

    /**
     * 把前端带来的消息 id 解析为数据库自增主键。
     *
     * <p>前端在重答时只传既有用户消息的 id，形如 "13"（后端用 String.valueOf(msg.getId()) 产出）。
     * 非数字或长度超过 18 位（会溢出 long）时返回 null，由调用方按「没有父消息」处理。
     *
     * @param raw 原始 id 文本
     * @return 解析出的 id，无法解析时为 null
     */
    private static Long parseNumericId(String raw) {
        if (raw == null || raw.isBlank() || raw.length() > 18) {
            return null;
        }
        try {
            return Long.valueOf(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 取会话内最后一条用户消息的数据库 id。
     *
     * <p>用于「重新回答」时前端传来的是本地消息 id（刚流式产出那一轮用的是前端 UUID，
     * 不是数据库数字主键）的场景：重新回答只作用于最后一轮，
     * 所以该会话的最后一条用户消息就是这一轮的提问。</p>
     *
     * @param dialogId 会话标识
     * @return 最后一条用户消息 id；会话内没有用户消息时返回 {@code null}
     */
    /**
     * 读取会话的压缩记录（无记录或读取失败时返回 {@code null}）。
     *
     * @param dialogId 会话标识
     * @return 压缩记录或 {@code null}
     */
    private com.chua.starter.ai.support.entity.SysAiSessionCompression loadCompression(Long dialogId) {
        try {
            return sessionCompressionService.getBySessionId(dialogId);
        } catch (Exception e) {
            log.warn("[ChatStream] 读取压缩记录失败 sessionId={}: {}", dialogId, e.getMessage());
            return null;
        }
    }

    private Long lastUserMessageId(Long dialogId) {
        try {
            List<ChatMessage> history = aiDialogRecordService.getDialogMessages(dialogId);
            if (history == null) {
                return null;
            }
            for (int i = history.size() - 1; i >= 0; i--) {
                ChatMessage m = history.get(i);
                if (!"user".equals(m.getRole())) {
                    continue;
                }
                Object rawId = m.getId();
                return rawId == null ? null : parseNumericId(String.valueOf(rawId));
            }
        } catch (Exception e) {
            log.warn("[ChatStream] 解析最后一条用户消息失败 dialogId={}: {}", dialogId, e.getMessage());
        }
        return null;
    }

    /**
     * 同步准备：解析模型/角色、校验会话归属、落库用户消息（内容为空则不落库）、组装请求。
     *
     * @param body 请求体
     * @return 准备结果
     */
    public PreparedChat prepare(Map<String, Object> body) {
        String sessionId = str(body.get("sessionId"));
        Map<?, ?> userMessage = asMap(body.get("userMessage"));
        String content = userMessage != null ? str(userMessage.get("content")) : "";
        if (content == null || content.isBlank()) {
            content = str(body.get("content"));
        }

        String selectedModel = str(body.get("modelId"));
        if (selectedModel == null || selectedModel.isBlank()) {
            selectedModel = str(body.get("model"));
        }

        final String characterId = str(body.get("characterId"));
        final SysAiRole boundRole = loadRole(characterId);
        final Map<String, Object> roleSettings = parseRoleSettings(boundRole);
        if (boundRole != null) {
            log.info("[ChatStream] 命中角色 id={} name={}", boundRole.getId(), boundRole.getName());
            if ((selectedModel == null || selectedModel.isBlank())
                    && boundRole.getModel() != null && !boundRole.getModel().isBlank()) {
                selectedModel = boundRole.getModel();
            }
        }

        if (selectedModel != null && !selectedModel.isBlank()) {
            try {
                if (selectedModel.contains(":")) {
                    String modelName = selectedModel.substring(selectedModel.indexOf(":") + 1);
                    log.info("[ChatStream] 模型ID {} 解析为模型名称: {}", selectedModel, modelName);
                    selectedModel = modelName;
                } else if (selectedModel.matches("\\d+")) {
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
            } catch (Exception e) {
                log.warn("[ChatStream] 模型ID解析失败 {}: {}", selectedModel, e.getMessage());
            }
        }
        if (selectedModel != null && !selectedModel.isBlank()) {
            String resolved = modelAliasService.resolveInbound(selectedModel);
            if (!selectedModel.equals(resolved)) {
                log.info("[ChatStream] 模型别名解析: {} -> {}", selectedModel, resolved);
                selectedModel = resolved;
            }
        }

        final Long dialogId = resolveDialogId(sessionId);
        final String incomingMcp = body.containsKey("mcpServerIds") ? str(body.get("mcpServerIds")) : null;
        if (incomingMcp != null && AiUserContext.isAdmin()) {
            persistSessionMcp(dialogId, incomingMcp);
        }
        final List<String> sessionMcpIds = loadSessionMcpIds(dialogId);
        if (!sessionMcpIds.isEmpty()) {
            log.info("[ChatStream] 会话级 MCP 服务器: {}", sessionMcpIds);
        }

        // 只在真正带了用户输入时才落库。
        //
        // 重新回答（multi_version）时前端只传 userMessage.id 作为父消息、不传 content，
        // 若照样落库就会插入一条空内容的用户消息：它随后被 getDialogMessages 读进 LLM 请求，
        // 被上游以 "messages: Validation error: message content cannot be empty" 400 拒绝，
        // 并且新回复会挂到这条空消息上，UI 因此多出一个空的用户轮次。
        final ChatMessage userChatMsg = (content == null || content.isBlank())
                ? null
                : new ChatMessage("user", content);
        if (userChatMsg != null) {
            aiDialogRecordService.addMessage(dialogId, userChatMsg);
            log.info("[ChatStream] 用户消息已保存 id={}", userChatMsg.getId());
        }
        final String memoryUser = AiUserContext.currentUserId();

        // 上下文压缩：命中压缩记录时，把「摘要覆盖的那段历史」替换成一条 system 摘要 ——
        // 既省 token 又保留结论。会话内置命令「压缩上下文」写入的记录就在这里生效。
        final com.chua.starter.ai.support.entity.SysAiSessionCompression compression =
                loadCompression(dialogId);
        final Long compressionUpTo =
                compression == null || compression.getSysAiCompressionFromMessageId() == null
                        ? null
                        : compression.getSysAiCompressionFromMessageId().longValue();

        List<Map<String, Object>> msgList = new ArrayList<>();
        if (compression != null && compression.getSysAiCompressionContext() != null
                && !compression.getSysAiCompressionContext().isBlank()) {
            Map<String, Object> summaryMessage = new LinkedHashMap<>();
            summaryMessage.put("role", "system");
            summaryMessage.put("content", "以下为本会话较早内容的压缩摘要：\n"
                    + compression.getSysAiCompressionContext());
            msgList.add(summaryMessage);
        }
        int compressedOut = 0;
        try {
            List<ChatMessage> history = aiDialogRecordService.getDialogMessages(dialogId);
            if (history != null) {
                for (ChatMessage m : history) {
                    // 已被摘要覆盖的那段历史不再逐条送入模型
                    Long messageId = m.getId() == null ? null : parseNumericId(String.valueOf(m.getId()));
                    if (compressionUpTo != null && messageId != null && messageId <= compressionUpTo) {
                        compressedOut++;
                        continue;
                    }
                    Map<String, Object> mm = new LinkedHashMap<>();
                    mm.put("role", m.getRole() != null ? m.getRole() : "user");
                    mm.put("content", m.getContent() != null ? m.getContent() : "");
                    msgList.add(mm);
                }
            }
        } catch (Exception e) {
            log.warn("[ChatStream] 加载历史失败 sessionId={}: {}", sessionId, e.getMessage());
        }
        if (compressedOut > 0) {
            log.info("[ChatStream] 已用压缩摘要替代 {} 条历史消息 sessionId={}", compressedOut, sessionId);
        }
        if (msgList.isEmpty()) {
            Map<String, Object> mm = new LinkedHashMap<>();
            mm.put("role", "user");
            mm.put("content", content != null ? content : "");
            msgList.add(mm);
        }
        chatMemorySupport.inject(memoryUser, sessionId, content, msgList);

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
        final List<String> pickedSkillNames = parseSkillNames(body.get("skillNames"));
        final List<String> roleDefaultSkills = parseSkillNames(roleSettings.get("defaultSkillNames"));
        final List<String> effectiveSkillNames = new ArrayList<>(pickedSkillNames);
        for (String n : roleDefaultSkills) {
            if (!effectiveSkillNames.contains(n)) {
                effectiveSkillNames.add(n);
            }
        }
        request.setUseSkill(!effectiveSkillNames.isEmpty());
        if (!effectiveSkillNames.isEmpty()) {
            request.setSkillNames(effectiveSkillNames);
            log.info("[ChatStream] 本轮技能: 显式={} 角色默认={} 合并={}",
                    pickedSkillNames, roleDefaultSkills, effectiveSkillNames);
        }
        request.setPrompt(content != null ? content : "");
        request.setMessages(msgList);
        mergeSessionMcp(roleSettings, sessionMcpIds);
        request.setRoleSettings(roleSettings);
        Object modeObj = body.get("mode");
        if (modeObj != null) {
            String runMode = String.valueOf(modeObj).trim().toLowerCase();
            request.setMode("plan".equals(runMode) ? "plan" : "build");
        }
        request.setSessionId(sessionId);
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
        if (boundRole != null) {
            if (boundRole.getSystemPrompt() != null && !boundRole.getSystemPrompt().isBlank()) {
                request.setSystemPrompt(boundRole.getSystemPrompt());
            }
            if (boundRole.getTemperature() != null) {
                request.setTemperature(boundRole.getTemperature());
            }
        }
        final Map<?, ?> roleCapabilities = asMap(roleSettings.get("capabilities"));
        Object thinkObj = body.get("think");
        if (thinkObj != null) {
            request.setThink(Boolean.parseBoolean(String.valueOf(thinkObj)));
        } else if (roleCapabilities != null
                && Boolean.parseBoolean(String.valueOf(roleCapabilities.get("defaultThink")))) {
            request.setThink(true);
            log.info("[ChatStream] 应用角色默认深度思考");
        }
        Object searchObj = body.get("search");
        if (searchObj != null) {
            request.setSearch(Boolean.parseBoolean(String.valueOf(searchObj)));
        } else if (roleCapabilities != null
                && Boolean.parseBoolean(String.valueOf(roleCapabilities.get("defaultSearch")))) {
            request.setSearch(true);
            log.info("[ChatStream] 应用角色默认在线检索");
        }

        String messageId = UUID.randomUUID().toString();
        String turnsId = UUID.randomUUID().toString();
        String contentId = UUID.randomUUID().toString();
        String chatRequestId = body.get("chatRequestId") != null
                ? str(body.get("chatRequestId")) : UUID.randomUUID().toString();
        String userMsgId = userMessage != null && str(userMessage.get("id")) != null
                ? str(userMessage.get("id")) : UUID.randomUUID().toString();

        PreparedChat p = new PreparedChat();
        p.setSessionId(sessionId);
        p.setContent(content);
        p.setModelToUse(modelToUse);
        p.setDialogId(dialogId);
        Long parentUserMessageId = userChatMsg != null ? userChatMsg.getId() : null;
        if (parentUserMessageId == null && userMessage != null) {
            // 重答场景：复用前端带来的既有用户消息 id 作为新回复的父消息，
            // 否则新回复会失去父消息，UI 无法把它归回原来那一轮。
            parentUserMessageId = parseNumericId(str(userMessage.get("id")));
        }
        if (parentUserMessageId == null && userChatMsg == null) {
            // 兜底：重答时前端可能给的是「本地消息 id」（刚流式产出的那一轮用的是前端 UUID，
            // 不是数据库数字主键），此时按会话取「最后一条用户消息」当父消息 ——
            // 重新回答只作用于最后一轮，这与 /{id}/messages 列表接口的归属规则一致。
            parentUserMessageId = lastUserMessageId(dialogId);
            if (parentUserMessageId != null) {
                log.info("[ChatStream] 重答未取到数字父消息 id，回落为该会话最后一条用户消息 id={}", parentUserMessageId);
            }
        }
        p.setUserMessageId(parentUserMessageId);
        p.setMessageId(messageId);
        p.setTurnsId(turnsId);
        p.setContentId(contentId);
        p.setUserMsgId(userMsgId);
        p.setChatRequestId(chatRequestId);
        p.setRequest(request);
        p.setMemoryUser(memoryUser);
        return p;
    }

    /**
     * 异步执行：经帧槽逐帧产出。
     *
     * @param p    准备结果
     * @param sink 帧槽
     */
    public void execute(PreparedChat p, ChatFrameSink sink) {
        final String sid = p.getSessionId();
        final String finalContent = p.getContent();
        final AiChatRequest request = p.getRequest();
        final Long dialogId = p.getDialogId();
        final String modelToUse = p.getModelToUse();
        final String memoryUser = p.getMemoryUser();
        final String messageId = p.getMessageId();
        final String contentId = p.getContentId();
        final Long userMessageId = p.getUserMessageId();

        CompletableFuture.runAsync(AiUserContext.capturing(() -> {
            try {
                Map<String, Object> userContent = new LinkedHashMap<>();
                userContent.put("id", contentId);
                userContent.put("content", finalContent != null ? finalContent : "");
                userContent.put("state", Map.of("isStreaming", false));
                Map<String, Object> userMsg = new LinkedHashMap<>();
                // 重答回放用户消息时必须沿用既有用户消息的真实 id（数字主键）：
                // 若按 userMsgId 照发，前端按 id 去重后，补推的副本就是既有轮次本身，
                // 不会多出一轮；id 对不上则是重复的根因（点一次重新生成多出一对消息）。
                userMsg.put("id", userMessageId != null ? String.valueOf(userMessageId) : p.getUserMsgId());
                userMsg.put("role", "user");
                userMsg.put("contents", List.of(userContent));
                userMsg.put("state", Map.of("isStreaming", false));
                Map<String, Object> userEvent = new LinkedHashMap<>();
                userEvent.put("type", "user_message");
                userEvent.put("userMessage", userMsg);
                sink.sendEvent(userEvent);

                Map<String, Object> createEvent = new LinkedHashMap<>();
                createEvent.put("type", "create");
                createEvent.put("messageId", messageId);
                createEvent.put("turnsId", p.getTurnsId());
                createEvent.put("contentId", contentId);
                createEvent.put("modelName", modelToUse);
                createEvent.put("parentId", p.getUserMsgId());
                sink.sendEvent(createEvent);
                log.info("[ChatStream] 已发送 create sessionId={}", sid);
            } catch (Exception e) {
                log.warn("[ChatStream] 发送 create 失败: {}", e.getMessage());
            }

            if (Boolean.TRUE.equals(request.getSearch())) {
                try {
                    String searchQuery = WebSearchService.refineQuery(finalContent);
                    Map<String, Object> searchingEvent = new LinkedHashMap<>();
                    searchingEvent.put("type", "search");
                    searchingEvent.put("status", "searching");
                    searchingEvent.put("query", searchQuery);
                    sink.sendEvent(searchingEvent);

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
                            reference.append('[').append(i + 1).append(" 标题：").append(safe(r.getTitle())).append('\n');
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
                    sink.sendEvent(searchDoneEvent);
                    log.info("[ChatStream] 在线检索完成 sessionId={}, 来源 {} 条", sid, sourceList.size());
                } catch (Exception e) {
                    log.warn("[ChatStream] 在线检索失败 sessionId={}: {}", sid, e.getMessage());
                }
            }

            StringBuilder fullReply = new StringBuilder();
            final boolean[] firstChunkLogged = {false};
            // 人工确认请求（plan_exit）：agent 暂停时持有，随助手消息一并落库
            final com.chua.common.support.ai.agent.ConfirmationRequest[] pendingConfirmation = {null};

            aiChatFacadeService.streamWithUsage(request,
                    chunk -> {
                        if (sink.isCancelled()) {
                            return;
                        }
                        if (chunk != null && !chunk.isEmpty()) {
                            if (!firstChunkLogged[0]) {
                                firstChunkLogged[0] = true;
                                log.info("[ChatStream] 收到首个 chunk sessionId={}, len={}", sid, chunk.length());
                            } else {
                                log.info("[ChatStream] 收到 chunk sessionId={}, len={}, preview={}",
                                        sid, chunk.length(), chunk.length() > 20 ? chunk.substring(0, 20) : chunk);
                            }
                            fullReply.append(chunk);
                            Map<String, Object> textEvent = new LinkedHashMap<>();
                            textEvent.put("type", "text");
                            textEvent.put("content", chunk);
                            sink.sendEvent(textEvent);
                        }
                    },
                    (reply, usage) -> {
                        String finalText = reply != null ? reply : fullReply.toString();
                        log.info("[ChatStream] 流式完成 sessionId={}, 回复长度={}", sid, finalText.length());
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
                            assistantMsg.setParentId(userMessageId);
                            assistantMsg.setModelName(modelToUse);
                            assistantMsg.setPromptTokens(promptTokens);
                            assistantMsg.setCompletionTokens(completionTokens);
                            assistantMsg.setTotalTokens(totalTokens);
                            if (pendingConfirmation[0] != null) {
                                assistantMsg.setConfirmation(buildConfirmationJson(pendingConfirmation[0]));
                            }
                            aiDialogRecordService.addMessage(dialogId, assistantMsg);
                            log.info("[ChatStream] 助手消息已保存 parentId={}, tokens={}", userMessageId, totalTokens);
                            chatMemorySupport.remember(memoryUser, sid, finalContent, finalText, modelToUse);
                        } catch (Exception e) {
                            log.warn("[ChatStream] 保存助手消息失败 sessionId={}: {}", sid, e.getMessage());
                        }
                        Map<String, Object> finishEvent = new LinkedHashMap<>();
                        finishEvent.put("type", "finish");
                        finishEvent.put("finishReason", "stop");
                        Map<String, Object> usageMap = new LinkedHashMap<>();
                        usageMap.put("promptTokens", promptTokens);
                        usageMap.put("completionTokens", completionTokens);
                        usageMap.put("totalTokens", totalTokens);
                        finishEvent.put("usage", usageMap);
                        sink.sendEvent(finishEvent);
                        Map<String, Object> turnEndEvent = new LinkedHashMap<>();
                        turnEndEvent.put("type", "turn_end");
                        turnEndEvent.put("finishReason", "stop");
                        turnEndEvent.put("content", finalText);
                        turnEndEvent.put("messageId", messageId);
                        turnEndEvent.put("contentId", contentId);
                        sink.sendEvent(turnEndEvent);
                        sink.sendDone();
                        registry.remove(sid);
                        sink.close();
                    },
                    error -> {
                        String msg = error != null && error.getMessage() != null ? error.getMessage() : "未知错误";
                        log.error("[ChatStream] 流式失败 sessionId={}", sid, error);
                        Map<String, Object> finishEvent = new LinkedHashMap<>();
                        finishEvent.put("type", "finish");
                        finishEvent.put("finishReason", "error");
                        finishEvent.put("error", msg);
                        sink.sendEvent(finishEvent);
                        Map<String, Object> turnEndEvent = new LinkedHashMap<>();
                        turnEndEvent.put("type", "turn_end");
                        turnEndEvent.put("finishReason", "error");
                        turnEndEvent.put("error", msg);
                        turnEndEvent.put("messageId", messageId);
                        turnEndEvent.put("contentId", contentId);
                        sink.sendEvent(turnEndEvent);
                        sink.sendDone();
                        registry.remove(sid);
                        sink.close();
                    },
                    thinking -> {
                        if (thinking != null && !thinking.isEmpty()) {
                            Map<String, Object> thinkEvent = new LinkedHashMap<>();
                            thinkEvent.put("type", "think");
                            thinkEvent.put("reasoningContent", thinking);
                            sink.sendEvent(thinkEvent);
                        }
                    },
                    sink::sendTrace,
                    toolCalls -> {
                        if (toolCalls == null || toolCalls.isEmpty()) {
                            return;
                        }
                        Map<String, Object> tcEvent = new LinkedHashMap<>();
                        tcEvent.put("type", "tool_calls");
                        tcEvent.put("toolCalls", toolCalls);
                        sink.sendEvent(tcEvent);
                    },
                    confirmation -> {
                        pendingConfirmation[0] = confirmation;
                        Map<String, Object> cEvent = new LinkedHashMap<>();
                        cEvent.put("type", "confirmation");
                        cEvent.put("replyId", confirmation.getReplyId());
                        cEvent.put("confirmations", confirmation.getItems());
                        sink.sendEvent(cEvent);
                    }
            );
        }));
    }

    // ==================== 人工确认恢复 ====================

    /**
     * 同步准备人工确认（human-in-the-loop）恢复：解析会话/角色/模型、组装请求，不新增用户消息。
     *
     * @param body 请求体（sessionId/replyId/modelId/characterId/decisions）
     * @return 准备结果
     */
    public PreparedChat prepareResume(Map<String, Object> body) {
        String sessionId = str(body.get("sessionId"));
        final Long dialogId = resolveDialogId(sessionId);

        final String characterId = str(body.get("characterId"));
        final SysAiRole boundRole = loadRole(characterId);
        final Map<String, Object> roleSettings = parseRoleSettings(boundRole);

        String selectedModel = str(body.get("modelId"));
        if (selectedModel == null || selectedModel.isBlank()) {
            selectedModel = str(body.get("model"));
        }
        if (selectedModel != null && selectedModel.contains(":")) {
            selectedModel = selectedModel.substring(selectedModel.indexOf(":") + 1);
        }
        if (selectedModel == null || selectedModel.isBlank()) {
            selectedModel = getDefaultModel();
        }
        String aliasResolved = modelAliasService.resolveInbound(selectedModel);
        if (aliasResolved != null && !aliasResolved.isBlank()) {
            selectedModel = aliasResolved;
        }

        AiChatRequest request = new AiChatRequest();
        request.setModel(selectedModel);
        request.setStream(true);
        request.setRoleSettings(roleSettings);
        request.setSessionId(sessionId);
        // 恢复规划审批必须重新注册 Plan 能力（否则 maybeWrapAgent 会退回标准非 Agent 客户端）。
        // 设为 plan 仅用于注册能力；drive 恢复轮(initialRun=false)不会重新 enterPlanMode，
        // 批准/拒绝后由 plan_exit 工具自身决定进入 BUILD 或留在 PLAN。
        request.setMode("plan");
        if (boundRole != null && boundRole.getSystemPrompt() != null
                && !boundRole.getSystemPrompt().isBlank()) {
            request.setSystemPrompt(boundRole.getSystemPrompt());
        }
        Object thinkObj = body.get("think");
        if (thinkObj != null) {
            request.setThink(Boolean.parseBoolean(String.valueOf(thinkObj)));
        }

        List<com.chua.common.support.ai.agent.ConfirmationDecision> decisions = new ArrayList<>();
        Object rawDecisions = body.get("decisions");
        if (rawDecisions instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> dm) {
                    com.chua.common.support.ai.agent.ConfirmationDecision d =
                            new com.chua.common.support.ai.agent.ConfirmationDecision();
                    d.setToolCallId(str(dm.get("toolCallId")));
                    Object c = dm.get("confirmed");
                    d.setConfirmed(c != null && Boolean.parseBoolean(String.valueOf(c)));
                    decisions.add(d);
                }
            }
        }

        String messageId = UUID.randomUUID().toString();
        String turnsId = UUID.randomUUID().toString();
        String contentId = UUID.randomUUID().toString();

        // 恢复轮新助手消息需挂到原用户消息下：解析 DB 父消息（持久化分组用）
        String resumeReplyId = str(body.get("replyId"));
        String resumeParentId = aiDialogRecordService.findParentMessageIdByReplyId(dialogId, resumeReplyId);
        Long resumeParentDbId = null;
        if (resumeParentId != null && resumeParentId.matches("\\d+")) {
            resumeParentDbId = Long.parseLong(resumeParentId);
        }

        PreparedChat p = new PreparedChat();
        p.setSessionId(sessionId);
        p.setModelToUse(selectedModel);
        p.setDialogId(dialogId);
        p.setMessageId(messageId);
        p.setTurnsId(turnsId);
        p.setContentId(contentId);
        p.setRequest(request);
        p.setMemoryUser(AiUserContext.currentUserId());
        p.setReplyId(resumeReplyId);
        p.setUserMessageId(resumeParentDbId);
        // create 帧携带 DB 父消息作为兜底；前端会按当前渲染的用户消息 id 覆盖
        p.setUserMsgId(resumeParentId);
        p.setDecisions(decisions);
        // 用户修改意见（规划模式「需要修改」）：随恢复注入 Agent 上下文
        p.setFeedback(str(body.get("feedback")));
        return p;
    }

    /**
     * 异步执行人工确认恢复：经帧槽逐帧产出恢复后的内容。
     *
     * @param p    准备结果
     * @param sink 帧槽
     */
    public void executeResume(PreparedChat p, ChatFrameSink sink) {
        final String sid = p.getSessionId();
        final AiChatRequest request = p.getRequest();
        final Long dialogId = p.getDialogId();
        final String modelToUse = p.getModelToUse();
        final String memoryUser = p.getMemoryUser();
        final String messageId = p.getMessageId();
        final String contentId = p.getContentId();

        CompletableFuture.runAsync(AiUserContext.capturing(() -> {
            com.chua.starter.ai.support.chat.AgentChatClient wrapper = null;
            try {
                com.chua.common.support.ai.chat.ChatClient configured =
                        aiChatFacadeService.prepareConfigured(request);
                if (!(configured instanceof com.chua.starter.ai.support.chat.AgentChatClient agentClient)) {
                    throw new IllegalStateException("当前会话不是 Agent 运行时，无法恢复人工确认");
                }
                wrapper = agentClient;

                // 人工确认恢复的 create 也必须挂到真实用户消息 id 上：
                // 否则新回复归不回原来那一轮，界面会多出一轮。
                final String resumeParentId = p.getUserMessageId() != null
                        ? String.valueOf(p.getUserMessageId()) : p.getUserMsgId();
                Map<String, Object> createEvent = new LinkedHashMap<>();
                createEvent.put("type", "create");
                createEvent.put("messageId", messageId);
                createEvent.put("turnsId", p.getTurnsId());
                createEvent.put("contentId", contentId);
                createEvent.put("modelName", modelToUse);
                createEvent.put("parentId", resumeParentId);
                sink.sendEvent(createEvent);

                final StringBuilder fullReply = new StringBuilder();
                final com.chua.common.support.ai.AiUsage[] usageHolder =
                        new com.chua.common.support.ai.AiUsage[1];

                // 把旧助手消息上的 pending 确认标记为终态（approved/rejected）
                String confirmDecision = p.getDecisions() != null
                        && p.getDecisions().stream()
                                .anyMatch(com.chua.common.support.ai.agent.ConfirmationDecision::isConfirmed)
                        ? "approved" : "rejected";
                aiDialogRecordService.resolveConfirmation(dialogId, p.getReplyId(), confirmDecision);

                // 恢复轮可能再次产生确认（如拒绝后继续修订），持有并随新助手消息落库
                final com.chua.common.support.ai.agent.ConfirmationRequest[] resumeConfirmation = {null};

                wrapper.resume(p.getReplyId(), p.getDecisions(), p.getFeedback(),
                        response -> {
                            if (sink.isCancelled()) {
                                return;
                            }
                            if (response.getTrace() != null) {
                                sink.sendTrace(response.getTrace());
                            }
                            if (response.getConfirmationRequest() != null) {
                                com.chua.common.support.ai.agent.ConfirmationRequest cr =
                                        response.getConfirmationRequest();
                                resumeConfirmation[0] = cr;
                                Map<String, Object> cEvent = new LinkedHashMap<>();
                                cEvent.put("type", "confirmation");
                                cEvent.put("replyId", cr.getReplyId());
                                cEvent.put("confirmations", cr.getItems());
                                sink.sendEvent(cEvent);
                            }
                            String reasoning = response.getReasoningContent();
                            if (reasoning != null && !reasoning.isEmpty()) {
                                Map<String, Object> thinkEvent = new LinkedHashMap<>();
                                thinkEvent.put("type", "think");
                                thinkEvent.put("reasoningContent", reasoning);
                                sink.sendEvent(thinkEvent);
                            }
                            if (response.getState() == com.chua.common.support.ai.chat.ChatResponse.State.STOP
                                    && usageHolder[0] == null && response.getUsage() != null) {
                                usageHolder[0] = response.getUsage();
                            }
                            String chunk = response.getContent();
                            if (chunk == null || chunk.isEmpty()) {
                                return;
                            }
                            fullReply.append(chunk);
                            Map<String, Object> textEvent = new LinkedHashMap<>();
                            textEvent.put("type", "text");
                            textEvent.put("content", chunk);
                            sink.sendEvent(textEvent);
                        },
                        () -> {
                            String finalText = fullReply.toString();
                            int promptTokens = 0;
                            int completionTokens;
                            if (usageHolder[0] != null && usageHolder[0].getOutputTokens() != null) {
                                completionTokens = usageHolder[0].getOutputTokens();
                                if (usageHolder[0].getInputTokens() != null) {
                                    promptTokens = usageHolder[0].getInputTokens();
                                }
                            } else {
                                completionTokens = (int) (finalText.length() * 1.5);
                            }
                            int totalTokens = promptTokens + completionTokens;
                            try {
                                ChatMessage assistantMsg = new ChatMessage("assistant", finalText);
                                assistantMsg.setParentId(p.getUserMessageId());
                                assistantMsg.setModelName(modelToUse);
                                assistantMsg.setPromptTokens(promptTokens);
                                assistantMsg.setCompletionTokens(completionTokens);
                                assistantMsg.setTotalTokens(totalTokens);
                                if (resumeConfirmation[0] != null) {
                                    assistantMsg.setConfirmation(buildConfirmationJson(resumeConfirmation[0]));
                                }
                                aiDialogRecordService.addMessage(dialogId, assistantMsg);
                                log.info("[ChatStream] 恢复助手消息已保存 sessionId={}, parentId={}, len={}",
                                        sid, p.getUserMessageId(), finalText.length());
                            } catch (Exception e) {
                                log.warn("[ChatStream] 保存恢复助手消息失败 sessionId={}: {}",
                                        sid, e.getMessage());
                            }
                            Map<String, Object> finishEvent = new LinkedHashMap<>();
                            finishEvent.put("type", "finish");
                            finishEvent.put("finishReason", "stop");
                            Map<String, Object> usageMap = new LinkedHashMap<>();
                            usageMap.put("promptTokens", promptTokens);
                            usageMap.put("completionTokens", completionTokens);
                            usageMap.put("totalTokens", totalTokens);
                            finishEvent.put("usage", usageMap);
                            sink.sendEvent(finishEvent);
                            Map<String, Object> turnEndEvent = new LinkedHashMap<>();
                            turnEndEvent.put("type", "turn_end");
                            turnEndEvent.put("finishReason", "stop");
                            turnEndEvent.put("content", finalText);
                            turnEndEvent.put("messageId", messageId);
                            turnEndEvent.put("contentId", contentId);
                            sink.sendEvent(turnEndEvent);
                            sink.sendDone();
                            registry.remove(sid);
                            sink.close();
                        },
                        error -> {
                            log.error("[ChatStream] 恢复失败 sessionId={}", sid, error);
                            String msg = error.getMessage() != null ? error.getMessage() : "未知错误";
                            Map<String, Object> finishEvent = new LinkedHashMap<>();
                            finishEvent.put("type", "finish");
                            finishEvent.put("finishReason", "error");
                            finishEvent.put("error", msg);
                            sink.sendEvent(finishEvent);
                            Map<String, Object> turnEndEvent = new LinkedHashMap<>();
                            turnEndEvent.put("type", "turn_end");
                            turnEndEvent.put("finishReason", "error");
                            turnEndEvent.put("error", msg);
                            turnEndEvent.put("messageId", messageId);
                            turnEndEvent.put("contentId", contentId);
                            sink.sendEvent(turnEndEvent);
                            sink.sendDone();
                            registry.remove(sid);
                            sink.close();
                        });
            } catch (Throwable e) {
                log.error("[ChatStream] executeResume失败 sessionId={}", sid, e);
                try {
                    Map<String, Object> finishEvent = new LinkedHashMap<>();
                    finishEvent.put("type", "finish");
                    finishEvent.put("finishReason", "error");
                    finishEvent.put("error", e.getMessage() != null ? e.getMessage() : "恢复失败");
                    sink.sendEvent(finishEvent);
                    sink.sendDone();
                    sink.close();
                } catch (Exception ignored) {
                }
            } finally {
                if (wrapper != null) {
                    wrapper.close();
                }
            }
        }));
    }

    // ==================== 内部方法 ====================

    private String getDefaultModel() {
        String[][] keys = {
                {"runtime", "default_model"},
                {"models", "defaultChatModelId"},
                {"runtime", "default_chat_model"}
        };
        for (String[] k : keys) {
            // 直读数据库（不走 Redis 缓存）：默认模型被改后必须立刻生效，
            // 缓存里残留的旧模型名会让整条对话链路按已废弃的模型解析，且报错与配置对不上。
            String configured = sysAiGlobalSettingService.getValueDirect(k[0], k[1])
                    .map(String::trim)
                    .filter(value -> !value.isEmpty())
                    .orElse(null);
            if (configured != null) {
                int idx = configured.indexOf(':');
                return idx >= 0 ? configured.substring(idx + 1) : configured;
            }
        }
        // 不写死兜底模型名：三级配置都不可用时直接报明确错误，让用户知道该去配哪里，
        // 而不是悄悄用一个可能根本不存在的模型名（历史问题：agnes-2.5-flash / gpt-4o 写死导致必失败）。
        String resolved = effectiveChatModelResolver.resolve(null);
        if (resolved == null) {
            throw new IllegalStateException("未配置可用的默认对话模型：请在「系统设置 → 对话路由」"
                    + "把默认对话模型设为「模型管理」中已启用模型里的一个");
        }
        log.info("[ChatStream] 使用配置中心默认对话模型: {}", resolved);
        return resolved;
    }

    private SysAiRole loadRole(String characterId) {
        if (characterId == null || characterId.isBlank()) {
            return null;
        }
        try {
            SysAiRole role;
            if (characterId.matches("\\d+")) {
                role = sysAiRoleMapper.selectById(Integer.parseInt(characterId));
            } else {
                com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysAiRole> w =
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
                w.eq(SysAiRole::getCode, characterId)
                        .eq(SysAiRole::getStatus, 1)
                        .last("LIMIT 1");
                role = sysAiRoleMapper.selectOne(w);
            }
            if (role == null) {
                return null;
            }
            if (role.getStatus() != null && role.getStatus() != 1) {
                return null;
            }
            return role;
        } catch (Exception e) {
            log.warn("[ChatStream] 加载角色失败 {}: {}", characterId, e.getMessage());
            return null;
        }
    }

    private Map<String, Object> parseRoleSettings(SysAiRole role) {
        if (role == null || role.getSettings() == null || role.getSettings().isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, Object> m = objectMapper.readValue(role.getSettings(),
                    new com.fasterxml.jackson.core.type.TypeReference<LinkedHashMap<String, Object>>() {
                    });
            return m != null ? m : new LinkedHashMap<>();
        } catch (Exception e) {
            log.warn("[ChatStream] 解析角色 settings 失败 roleId={}: {}", role.getId(), e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    private List<String> parseSkillNames(Object raw) {
        List<String> names = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object o : list) {
                if (o == null) {
                    continue;
                }
                String n = String.valueOf(o).trim();
                if (!n.isEmpty() && !names.contains(n)) {
                    names.add(n);
                }
            }
        }
        return names;
    }

    /**
     * 构建人工确认请求的持久化 JSON（status=pending）。
     *
     * @param cr 确认请求
     * @return JSON 字符串；失败返回 null
     */
    private String buildConfirmationJson(com.chua.common.support.ai.agent.ConfirmationRequest cr) {
        if (cr == null) {
            return null;
        }
        try {
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("replyId", cr.getReplyId());
            root.put("status", "pending");
            List<Map<String, Object>> items = new ArrayList<>();
            if (cr.getItems() != null) {
                for (com.chua.common.support.ai.agent.ConfirmationItem it : cr.getItems()) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("toolCallId", it.getToolCallId());
                    m.put("toolName", it.getToolName());
                    m.put("title", it.getTitle());
                    m.put("description", it.getDescription());
                    items.add(m);
                }
            }
            root.put("items", items);
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            log.warn("[ChatStream] 序列化确认请求失败 replyId={}: {}", cr.getReplyId(), e.getMessage());
            return null;
        }
    }

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
     * 前端 sessionId(UUID) -> 后端会话主键(Long) 的进程内映射
     */
    private final Map<String, Long> sessionDialogMap = new java.util.concurrent.ConcurrentHashMap<>();

    private void persistSessionMcp(Long dialogId, String csv) {
        try {
            com.chua.starter.ai.support.entity.SysAiSession row = sysAiSessionMapper.selectById(dialogId);
            if (row == null) {
                return;
            }
            String normalized = csv == null ? null : csv.trim();
            row.setMcpServerIds(normalized == null || normalized.isEmpty() ? null : normalized);
            sysAiSessionMapper.updateById(row);
            log.info("[ChatStream] 首次发送已写入会话 MCP 选择 dialogId={} mcp={}", dialogId, row.getMcpServerIds());
        } catch (Exception e) {
            log.warn("[ChatStream] 写入会话 MCP 失败 dialogId={}: {}", dialogId, e.getMessage());
        }
    }

    private List<String> loadSessionMcpIds(Long dialogId) {
        List<String> ids = new ArrayList<>();
        try {
            com.chua.starter.ai.support.entity.SysAiSession row = sysAiSessionMapper.selectById(dialogId);
            if (row != null && row.getMcpServerIds() != null) {
                for (String part : row.getMcpServerIds().split(",") ) {
                    String v = part.trim();
                    if (!v.isEmpty() && !ids.contains(v)) {
                        ids.add(v);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[ChatStream] 读取会话 MCP 失败 dialogId={}: {}", dialogId, e.getMessage());
        }
        return ids;
    }

    @SuppressWarnings("unchecked")
    private static void mergeSessionMcp(Map<String, Object> roleSettings, List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        Object pluginsObj = roleSettings.get("plugins");
        Map<String, Object> plugins = pluginsObj instanceof Map
                ? (Map<String, Object>) pluginsObj : new LinkedHashMap<>();
        Object mcpObj = plugins.get("mcp");
        Map<String, Object> mcp = mcpObj instanceof Map
                ? (Map<String, Object>) mcpObj : new LinkedHashMap<>();
        mcp.put("enabled", true);
        mcp.put("serverIds", new ArrayList<>(ids));
        plugins.put("mcp", mcp);
        roleSettings.put("plugins", plugins);
    }

    private static String str(Object o) {
        return o != null ? String.valueOf(o) : null;
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static Map<?, ?> asMap(Object o) {
        return o instanceof Map ? (Map<?, ?>) o : null;
    }
}
