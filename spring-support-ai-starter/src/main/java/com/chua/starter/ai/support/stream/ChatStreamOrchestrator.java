// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
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
import com.chua.starter.ai.support.service.SysAiRolePromptResolver;
import com.chua.starter.ai.support.configuration.client.EffectiveChatModelResolver;
import com.chua.starter.ai.support.manage.mapper.SysAiProviderModelMapper;
import com.chua.starter.ai.support.manage.mapper.SysAiProviderMapper;
import com.chua.starter.ai.support.manage.store.SysAiProvider;
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

    /** 供应商查询：用于把供应商标识补成可读的供应商名/平台/baseUrl，供路由日志与前端提示使用 */
    private final com.chua.starter.ai.support.manage.mapper.SysAiProviderMapper sysAiProviderMapper;

    /**
     * 会话压缩记录服务：把摘要覆盖的历史替换成一条摘要
     */
    private final com.chua.starter.ai.support.service.SysAiSessionCompressionService sessionCompressionService;
    private final SysAiRoleMapper sysAiRoleMapper;
    private final com.chua.starter.ai.support.service.SysAiRolePromptResolver sysAiRolePromptResolver;
    private final SysAiSessionMapper sysAiSessionMapper;
    private final WebSearchService webSearchService;
    private final ModelAliasService modelAliasService;
    private final ChatMemorySupport chatMemorySupport;
    private final ChatStreamRegistry registry;

    /**
     * 会话执行轨迹服务：把 Agent 执行轨迹异步落库，供会话顶部「轨迹」页签回放
     */
    private final com.chua.starter.ai.support.service.SysAiSessionTraceService sessionTraceService;

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

    /**
     * last User Message Id
     * @param dialogId 长整数
     * @return 返回值
    */
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

        // 选定供应商 id：前端模型下拉的 model.id 形态是 <providerId>:<model>，
        // 拆开后必须把 providerId 一侧单独带下去。只留裸模型名的话，下游只能按名字反查
        // 供应商，而不同供应商允许同名模型（两家都叫 dpv4），命中哪一家由数据库决定 ——
        // 于是「选了商汤，实际发到别家接口」，别家的配额报错（周卡/套餐过期）就冒到了这次会话。
        String selectedProviderId = null;
        if (selectedModel != null && !selectedModel.isBlank()) {
            try {
                if (selectedModel.contains(":")) {
                    String rawProviderId = selectedModel.substring(0, selectedModel.indexOf(":")).trim();
                    String modelName = selectedModel.substring(selectedModel.indexOf(":") + 1).trim();
                    log.info("[ChatStream] 模型ID {} 解析为供应商={} 模型名称: {}", selectedModel, rawProviderId, modelName);
                    selectedModel = modelName;
                    if (!rawProviderId.isEmpty()) {
                        selectedProviderId = rawProviderId;
                    }
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
                        selectedProviderId = selectedModel;
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
            log.info("[ChatStream] 使用用户选择的模型: {} (供应商={})", modelToUse, selectedProviderId);
        } else {
            modelToUse = getDefaultModel();
            log.info("[ChatStream] 使用默认模型: {}", modelToUse);
        }
        // 默认模型配置同样是 <providerId>:<model> 形态（见 getDefaultModel），
        // 在这里统一补齐供应商一侧，避免默认对话也走「按名反查」而落到别家供应商。
        if ((selectedProviderId == null || selectedProviderId.isBlank()) && modelToUse != null) {
            int sep = modelToUse.indexOf(':');
            if (sep > 0) {
                String prefix = modelToUse.substring(0, sep).trim();
                String bare = modelToUse.substring(sep + 1).trim();
                if (!prefix.isEmpty() && !bare.isEmpty()) {
                    selectedProviderId = prefix;
                    modelToUse = bare;
                    log.info("[ChatStream] 默认模型限定名拆分 -> 供应商={} 模型={}", prefix, bare);
                }
            }
        }
        // 本轮路由信息：既进 create 事件（前端实时提示），也随助手消息落库（刷新后仍在）
        final Map<String, Object> routeInfo = describeRoute(selectedProviderId, modelToUse);
        AiChatRequest request = new AiChatRequest();
        request.setModel(modelToUse);
        request.setProviderId(selectedProviderId);
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
        // 形态 key 透传（形态可自定义），只做合法性归一，不在这里枚举形态。
        // 原来这里写死 "plan".equals(...) ? plan : build，会把任何自定义形态都悄悄
        // 压成 build，解析器永远拿不到真实形态——形态自定义就无从生效。
        Object modeObj = body.get("mode");
        final String formKey = SysAiRolePromptResolver.normalize(
                modeObj == null ? null : String.valueOf(modeObj));
        // request.mode 是 **agent 能力**开关（AiChatFacadeServiceImpl 据此决定是否注册
        // 规划能力），与「提示词形态」是两件事：只有内置 plan 形态开启规划能力，
        // 其余形态（含自定义的）都按构建处理。这是安全默认值——未知形态不会意外
        // 拿到规划那套审批流。
        request.setMode(FORM_KEY_PLAN.equals(formKey) ? AGENT_MODE_PLAN : AGENT_MODE_BUILD);
        request.setSessionId(sessionId);
        // 模型登记里的上下文窗口：准备阶段顺带取出，供 turn_end 的 contextStats 当占用率分母。
        // 模型没登记（或没填 contextWindow）时保持 null，前端据此显示「—」而不是 0%。
        Integer modelContextWindow = null;
        try {
            // 按 (供应商, 模型) 精确取参数。SysAiProviderModel 的 @TableId 是 model，
            // 单用 selectById(modelToUse) 等于 WHERE model=? 无供应商约束，同名模型时
            // 会捞到别家的 temperature/maxTokens/top_p 覆盖本次请求。
            SysAiProviderModel modelInfo = selectedProviderId == null || selectedProviderId.isBlank()
                    ? sysAiProviderModelMapper.selectById(modelToUse)
                    : sysAiProviderModelMapper.selectOne(
                            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysAiProviderModel>()
                                    .eq(SysAiProviderModel::getProviderId, selectedProviderId)
                                    .eq(SysAiProviderModel::getModel, modelToUse)
                                    .last("LIMIT 1"));
            if (modelInfo != null) {
                modelContextWindow = modelInfo.getContextWindow();
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
        // 提示词：分形态表 → 历史单条列（仅 build）→ 全局默认
        String rolePrompt = sysAiRolePromptResolver.resolve(boundRole, formKey);
        if (rolePrompt != null && !rolePrompt.isBlank()) {
            request.setSystemPrompt(rolePrompt);
        } else {
            applyDefaultSystemPrompt(request);
        }
        if (boundRole != null && boundRole.getTemperature() != null) {
            request.setTemperature(boundRole.getTemperature());
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

        // 思考强度档位：body 传 thinkingEffort（兼容 effectLevel / thinking_effort 别名）。
        // 值为 'default' 或空 = 「不传」——交由各厂商默认（OpenAI 未传落 high、Claude 未传落 1024）。
        // 真正生效还要求 think=true，由 AiChatFacadeServiceImpl.configureClient 统一判定；
        // 这里只负责把档位带进 request.parameters["thinking_effort"]。
        Object effortObj = body.get("thinkingEffort");
        if (effortObj == null) {
            effortObj = body.get("effectLevel");
        }
        if (effortObj == null) {
            effortObj = body.get("thinking_effort");
        }
        if (effortObj != null) {
            String effortLevel = String.valueOf(effortObj).trim();
            // 'default'/'none' 都表示「不传」——交由各厂商默认；只有真实档位才写进参数
            if (!effortLevel.isEmpty()
                    && !"default".equalsIgnoreCase(effortLevel)
                    && !"none".equalsIgnoreCase(effortLevel)) {
                Map<String, Object> requestParams = request.getParameters();
                if (requestParams == null) {
                    requestParams = new LinkedHashMap<>(4);
                    request.setParameters(requestParams);
                }
                requestParams.put("thinking_effort", effortLevel);
                log.info("[ChatStream] 应用思考强度档位: {}", effortLevel);
            }
        }

        String messageId = UUID.randomUUID().toString();
        String turnsId = UUID.randomUUID().toString();
        String contentId = UUID.randomUUID().toString();
        String chatRequestId = body.get("chatRequestId") != null
                ? str(body.get("chatRequestId")) : UUID.randomUUID().toString();
        String userMsgId = userMessage != null && str(userMessage.get("id")) != null
                ? str(userMessage.get("id")) : UUID.randomUUID().toString();

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
        return new PreparedChat(sessionId, content, modelToUse, routeInfo, dialogId,
                parentUserMessageId, messageId, turnsId, contentId, userMsgId, chatRequestId,
                request, memoryUser, modelContextWindow, null, null, null);
    }

    /**
     * 异步执行：经帧槽逐帧产出。
     *
     * @param p    准备结果
     * @param sink 帧槽
     */
    public void execute(PreparedChat p, ChatFrameSink sink) {
        final String sid = p.sessionId();
        final String finalContent = p.content();
        final AiChatRequest request = p.request();
        final Long dialogId = p.dialogId();
        final String modelToUse = p.modelToUse();
        final String memoryUser = p.memoryUser();
        final String messageId = p.messageId();
        final String contentId = p.contentId();
        final Long userMessageId = p.userMessageId();

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
                userMsg.put("id", userMessageId != null ? String.valueOf(userMessageId) : p.userMsgId());
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
                createEvent.put("turnsId", p.turnsId());
                createEvent.put("contentId", contentId);
                createEvent.put("modelName", modelToUse);
                // 实际应答的供应商信息：前端据此在回复下方显示提示（排查路由问题用）
                createEvent.put("route", p.routeInfo());
                createEvent.put("parentId", userMessageId != null
                        ? String.valueOf(userMessageId) : p.userMsgId());
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
                        src.put("title", safe(r.title()));
                        src.put("url", safe(r.url()));
                        sourceList.add(src);
                    }
                    if (!results.isEmpty()) {
                        StringBuilder reference = new StringBuilder();
                        reference.append("以下是通过联网检索获取的、与用户问题相关的实时网页资料（检索时间：")
                                .append(java.time.LocalDate.now()).append("）：\n");
                        for (int i = 0; i < results.size(); i++) {
                            WebResult r = results.get(i);
                            reference.append('[').append(i + 1).append(" 标题：").append(safe(r.title())).append('\n');
                            if (r.snippet() != null && !r.snippet().isBlank()) {
                                reference.append("    摘要：").append(safe(r.snippet())).append('\n');
                            }
                            reference.append("    来源链接：").append(safe(r.url())).append('\n');
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
            // 首字耗时兜底。
            //
            // 为什么要自己记：TTFT 目前只在 lastUsage 非空时才有值，而 lastUsage 仅在
            // provider 发来带用量的 STOP 帧、且 response.getUsage() 非空时才会被赋值。
            // 供应商不回传用量时（OpenAI 兼容网关常见），整项 TTFT 缺失，界面上
            // 「首 token 平均（TTFT）」永远显示「—」——但首个 chunk 的到达时刻我们
            // 自己就能观测到，没有任何理由让它缺。
            final long streamStartAt = System.currentTimeMillis();
            final long[] firstChunkAt = {0L};
            // 人工确认请求（plan_exit）：agent 暂停时持有，随助手消息一并落库
            final com.chua.common.support.ai.agent.ConfirmationRequest[] pendingConfirmation = {null};
            // 工具调用轨迹：随助手消息落库 —— 否则「工具行」只在流式过程中有数据，
            // 刷新/重载后整段消失（用户口径：每个工具调用要单独一行、默认折叠）。
            final java.util.List<Object> collectedToolCalls = new java.util.ArrayList<>();
            // 智能体（AgentScope）路径的工具信息走 trace（kind/toolName/text），单独收一份
            final java.util.List<Map<String, Object>> collectedToolTraces = new java.util.ArrayList<>();

            aiChatFacadeService.streamWithUsage(request,
                    chunk -> {
                        if (sink.isCancelled()) {
                            return;
                        }
                        if (chunk != null && !chunk.isEmpty()) {
                            if (firstChunkAt[0] == 0L) {
                                firstChunkAt[0] = System.currentTimeMillis();
                            }
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
                        // 首字耗时兜底值：provider 没回传用量时也能给出 TTFT
                        final Long localTtft = firstChunkAt[0] > 0
                                ? Math.max(0L, firstChunkAt[0] - streamStartAt) : null;
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
                            } else if (!collectedToolCalls.isEmpty() || !collectedToolTraces.isEmpty()) {
                                // 工具轨迹落库：confirmation 是唯一的结构化长文本列；
                                // 消费方 parsePendingConfirmation 以 status:"pending" 为闸，
                                // 这类 JSON 不会被误认成待确认请求。
                                Map<String, Object> toolTrace = new LinkedHashMap<>();
                                if (!collectedToolCalls.isEmpty()) {
                                    toolTrace.put("toolCalls", collectedToolCalls);
                                }
                                if (!collectedToolTraces.isEmpty()) {
                                    toolTrace.put("toolTraces", collectedToolTraces);
                                }
                                assistantMsg.setConfirmation(objectMapper.writeValueAsString(toolTrace));
                                log.info("[ChatStream] 工具轨迹已随助手消息落库 sessionId={}, calls={}, traces={}",
                                        sid, collectedToolCalls.size(), collectedToolTraces.size());
                            }
                            assistantMsg.setConfirmation(attachRouteInfo(
                                    assistantMsg.getConfirmation(), effectiveRoute(p)));
                            // 缓存命中量 / 首字耗时同样并入 confirmation：
                            // sys_ai_message 没有对应列，加列要动存量表；这两个值只服务
                            // 「会话底部统计条」，走已有的 JSON 载体最省事，且与 route 的
                            // 合并顺序无关（attachUsageExtra 只补键、不覆盖）。
                            assistantMsg.setConfirmation(attachUsageExtra(
                                    assistantMsg.getConfirmation(), usageStatsOf(usage, localTtft)));
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
                        // 缓存命中 / 首字耗时：会话底部统计条要用，随 usage 一并下发，
                        // 避免前端再为这两个数多发一次请求。
                        usageMap.putAll(usageStatsOf(usage, localTtft));
                        finishEvent.put("usage", usageMap);
                        sink.sendEvent(finishEvent);
                        Map<String, Object> turnEndEvent = new LinkedHashMap<>();
                        turnEndEvent.put("type", "turn_end");
                        turnEndEvent.put("finishReason", "stop");
                        turnEndEvent.put("content", finalText);
                        turnEndEvent.put("messageId", messageId);
                        turnEndEvent.put("contentId", contentId);
                        // 上下文占用 + token 构成：底部统计条的「上下文已用」卡片要用。
                        // 放在 turn_end 是因为前端在 turn_end 处理器里落库（saveSessionTokenStats），
                        // 刷新后由 GET /token-stats 读回；放 finish 会早于落库、白丢一份。
                        turnEndEvent.put("contextStats", contextStatsOf(request, usage, p.contextWindow()));
                        sink.sendEvent(turnEndEvent);
                        sink.sendDone();
                        registry.remove(sid);
                        sink.close();
                    },
                    error -> {
                        String msg = error != null && error.getMessage() != null ? error.getMessage() : "未知错误";
                        log.error("[ChatStream] 流式失败 sessionId={}", sid, error);
                        // 失败的助手回合同样要落库。此前只发 SSE 事件、不写消息，
                        // 导致刷新后这一轮整个消失，只剩下孤零零的用户消息（数据不成对）。
                        // 内容优先用已经流出来的片段（有内容才能看出失败在第几步），
                        // 为空才退回错误文案，保证「每条用户消息都有一条对应的助手消息」。
                        persistErroredAssistantMessage(sid, dialogId, userMessageId, modelToUse,
                                fullReply.toString(), msg, collectedToolCalls, collectedToolTraces,
                                effectiveRoute(p));
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
                    trace -> {
                        // 工具类 trace 额外收一份，随助手消息落库（供刷新后渲染工具行）；
                        // 其余 trace 仍原样转发给前端。
                        if (trace != null) {
                            // 全量轨迹另存一份到轨迹表：助手消息里的 confirmation 只保留工具类，
                            // 承载不了模型调用/思考这类时序信息，而「轨迹」页签要靠它画甘特图。
                            sessionTraceService.append(sid, p.chatRequestId(), trace);
                            // 落库口径（2026-10-24 扩）：工具类 trace 之外，把 thinking 也收进来。
                            // 原来只收「有 toolName」的，思考段（kind=thinking，无 toolName）
                            // 整段被丢掉，于是刷新后思考行既没有耗时也没有可展示的时序。
                            String traceKind = trace.getKind();
                            boolean keepTrace = (trace.getToolName() != null && !trace.getToolName().isBlank())
                                    || "thinking".equals(traceKind);
                            if (keepTrace) {
                                Map<String, Object> t = new LinkedHashMap<>();
                                t.put("kind", traceKind);
                                t.put("toolName", trace.getToolName());
                                t.put("text", trace.getText());
                                t.put("timestamp", trace.getTimestamp());
                                // 时序字段必须一起落库：原来只存 timestamp，endTimestamp / spanId
                                // 被丢掉，历史行因此永远算不出「26.1s」这类耗时
                                // （用户 2026-10-24 要求：图标、命令、动态计时、命令摘要都要持久化）。
                                Long traceStart = trace.getTimestamp();
                                Long traceEnd = trace.getEndTimestamp();
                                t.put("endTimestamp", traceEnd);
                                t.put("spanId", trace.getSpanId());
                                t.put("spanEnd", trace.isSpanEnd());
                                if (traceStart != null && traceEnd != null && traceEnd > traceStart) {
                                    t.put("durationMs", traceEnd - traceStart);
                                }
                                collectedToolTraces.add(t);
                            }
                            sink.sendTrace(trace);
                        }
                    },
                    toolCalls -> {
                        if (toolCalls == null || toolCalls.isEmpty()) {
                            return;
                        }
                        // 累积轨迹（落库用），再照常转发给前端
                        collectedToolCalls.addAll(toolCalls);
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
        // 同 prepare：拆出供应商一侧，否则恢复轮也会在同名模型下走错供应商
        String selectedProviderId = null;
        if (selectedModel != null && selectedModel.contains(":")) {
            String rawProviderId = selectedModel.substring(0, selectedModel.indexOf(":")).trim();
            selectedModel = selectedModel.substring(selectedModel.indexOf(":") + 1).trim();
            if (!rawProviderId.isEmpty()) {
                selectedProviderId = rawProviderId;
            }
        }
        if (selectedModel == null || selectedModel.isBlank()) {
            selectedModel = getDefaultModel();
        }
        if ((selectedProviderId == null || selectedProviderId.isBlank()) && selectedModel.contains(":")) {
            int sep = selectedModel.indexOf(':');
            String prefix = selectedModel.substring(0, sep).trim();
            String bare = selectedModel.substring(sep + 1).trim();
            if (!prefix.isEmpty() && !bare.isEmpty()) {
                selectedProviderId = prefix;
                selectedModel = bare;
            }
        }
        String aliasResolved = modelAliasService.resolveInbound(selectedModel);
        if (aliasResolved != null && !aliasResolved.isBlank()) {
            selectedModel = aliasResolved;
        }
        final Map<String, Object> resumeRoute = describeRoute(selectedProviderId, selectedModel);

        AiChatRequest request = new AiChatRequest();
        request.setModel(selectedModel);
        request.setProviderId(selectedProviderId);
        request.setStream(true);
        request.setRoleSettings(roleSettings);
        request.setSessionId(sessionId);
        // 恢复规划审批必须重新注册 Plan 能力（否则 maybeWrapAgent 会退回标准非 Agent 客户端）。
        // 设为 plan 仅用于注册能力；drive 恢复轮(initialRun=false)不会重新 enterPlanMode，
        // 批准/拒绝后由 plan_exit 工具自身决定进入 BUILD 或留在 PLAN。
        request.setMode("plan");
        // 提示词走 build 形态。注意：上面的 setMode("plan") 只是注册 Plan 能力，
        // 不代表这一轮在「规划」——规划轮早已产出结果，本轮是批准后的执行续跑
        // （见 727-728 注释：批准/拒绝由 plan_exit 自行决定进入 BUILD 或留在 PLAN）。
        // 若此处按 plan 取提示词，批准后真正执行时会被套上规划阶段的约束。
        String resumePrompt = sysAiRolePromptResolver.resolve(boundRole,
                SysAiRolePromptResolver.MODE_BUILD);
        if (resumePrompt != null && !resumePrompt.isBlank()) {
            request.setSystemPrompt(resumePrompt);
        } else {
            applyDefaultSystemPrompt(request);
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

        // create 帧携带 DB 父消息作为兜底；前端会按当前渲染的用户消息 id 覆盖
        // 用户修改意见（规划模式「需要修改」）：随恢复注入 Agent 上下文
        // 恢复轮同样要带上上下文窗口：审批续跑结束后也要下发 contextStats，
        // 否则「批准」那一轮的占用率会从有值跌回 0。
        final Integer resumeContextWindow = resolveModelContextWindow(selectedProviderId, selectedModel);
        return new PreparedChat(sessionId, null, selectedModel, resumeRoute, dialogId, resumeParentDbId,
                messageId, turnsId, contentId, resumeParentId, null, request,
                AiUserContext.currentUserId(), resumeContextWindow, resumeReplyId, decisions,
                str(body.get("feedback")));
    }

    /**
     * 异步执行人工确认恢复：经帧槽逐帧产出恢复后的内容。
     *
     * @param p    准备结果
     * @param sink 帧槽
     */
    public void executeResume(PreparedChat p, ChatFrameSink sink) {
        final String sid = p.sessionId();
        final AiChatRequest request = p.request();
        final Long dialogId = p.dialogId();
        final String modelToUse = p.modelToUse();
        final String memoryUser = p.memoryUser();
        final String messageId = p.messageId();
        final String contentId = p.contentId();

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
                final String resumeParentId = p.userMessageId() != null
                        ? String.valueOf(p.userMessageId()) : p.userMsgId();
                Map<String, Object> createEvent = new LinkedHashMap<>();
                createEvent.put("type", "create");
                createEvent.put("messageId", messageId);
                createEvent.put("turnsId", p.turnsId());
                createEvent.put("contentId", contentId);
                createEvent.put("modelName", modelToUse);
                createEvent.put("parentId", resumeParentId);
                sink.sendEvent(createEvent);

                final StringBuilder fullReply = new StringBuilder();
                final com.chua.common.support.ai.AiUsage[] usageHolder =
                        new com.chua.common.support.ai.AiUsage[1];
                // 与主流程同一套首字耗时兜底：恢复轮同样可能拿不到 provider 用量
                final long resumeStartAt = System.currentTimeMillis();
                final long[] resumeFirstChunkAt = {0L};

                // 把旧助手消息上的 pending 确认标记为终态（approved/rejected）
                String confirmDecision = p.decisions() != null
                        && p.decisions().stream()
                                .anyMatch(com.chua.common.support.ai.agent.ConfirmationDecision::isConfirmed)
                        ? "approved" : "rejected";
                aiDialogRecordService.resolveConfirmation(dialogId, p.replyId(), confirmDecision);

                // 恢复轮可能再次产生确认（如拒绝后继续修订），持有并随新助手消息落库
                final com.chua.common.support.ai.agent.ConfirmationRequest[] resumeConfirmation = {null};

                wrapper.resume(p.replyId(), p.decisions(), p.feedback(),
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
                            if (resumeFirstChunkAt[0] == 0L) {
                                resumeFirstChunkAt[0] = System.currentTimeMillis();
                            }
                            fullReply.append(chunk);
                            Map<String, Object> textEvent = new LinkedHashMap<>();
                            textEvent.put("type", "text");
                            textEvent.put("content", chunk);
                            sink.sendEvent(textEvent);
                        },
                        () -> {
                            String finalText = fullReply.toString();
                            final Long resumeTtft = resumeFirstChunkAt[0] > 0
                                    ? Math.max(0L, resumeFirstChunkAt[0] - resumeStartAt) : null;
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
                                assistantMsg.setParentId(p.userMessageId());
                                assistantMsg.setModelName(modelToUse);
                                assistantMsg.setPromptTokens(promptTokens);
                                assistantMsg.setCompletionTokens(completionTokens);
                                assistantMsg.setTotalTokens(totalTokens);
                                if (resumeConfirmation[0] != null) {
                                    assistantMsg.setConfirmation(buildConfirmationJson(resumeConfirmation[0]));
                                }
                                // 缓存命中 / 首字耗时随消息落库（同主流程口径）
                                assistantMsg.setConfirmation(attachUsageExtra(
                                        assistantMsg.getConfirmation(), usageStatsOf(usageHolder[0], resumeTtft)));
                                aiDialogRecordService.addMessage(dialogId, assistantMsg);
                                log.info("[ChatStream] 恢复助手消息已保存 sessionId={}, parentId={}, len={}",
                                        sid, p.userMessageId(), finalText.length());
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
                            usageMap.putAll(usageStatsOf(usageHolder[0], resumeTtft));
                            finishEvent.put("usage", usageMap);
                            sink.sendEvent(finishEvent);
                            Map<String, Object> turnEndEvent = new LinkedHashMap<>();
                            turnEndEvent.put("type", "turn_end");
                            turnEndEvent.put("finishReason", "stop");
                            turnEndEvent.put("content", finalText);
                            turnEndEvent.put("messageId", messageId);
                            turnEndEvent.put("contentId", contentId);
                            // 恢复轮（审批续跑）同样下发：否则「批准」之后占用率会从有值跌回 0
                            turnEndEvent.put("contextStats",
                                    contextStatsOf(request, usageHolder[0], p.contextWindow()));
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
                } catch (Exception sendEx) {
                    log.debug("[ai-stream] 下发结束帧失败，已忽略: {}", sendEx.getMessage());
                }
            } finally {
                if (wrapper != null) {
                    wrapper.close();
                }
            }
        }));
    }

    // ==================== 内部方法 ====================

    /**
     * 取本轮最终生效的路由信息，落库前用它校正 prepare 阶段的那份。
     *
     * <p>prepare 只能看到请求里是否带了供应商限定；请求没带时（裸模型名）它记
     * {@code providerResolved=false}。而实际下游 {@code resolveChatClient} 会按模型名
     * 反查出一个供应商来用 —— 不校正的话，落库的「未解析」与实际应答方对不上，
     * 排查时等于白记一条。</p>
     *
     * <p>下游命中 DB 供应商时会把供应商回填到 {@code request.provider}，据此重算即可。</p>
     *
     * @param p 准备结果
     * @return 校正后的路由信息
     */
    private Map<String, Object> effectiveRoute(PreparedChat p) {
        Map<String, Object> route = p.routeInfo();
        boolean resolved = route != null && Boolean.TRUE.equals(route.get("providerResolved"));
        if (resolved || p.request() == null) {
            return route;
        }
        String actualProviderId = p.request().getProvider();
        if (actualProviderId == null || actualProviderId.isBlank()
                || actualProviderId.equals(route.get("providerId"))) {
            return route;
        }
        String model = route.get("model") instanceof String s ? s : p.modelToUse();
        log.info("[ChatStream] 路由校正：请求未限定供应商，下游实际解析为 {} model={}", actualProviderId, model);
        return describeRoute(actualProviderId, model);
    }

    /**
     * 把路由信息并入已有的 confirmation JSON。
     *
     * <p>{@code confirmation} 是助手消息唯一的结构化长文本列，工具轨迹、待确认请求、
     * 失败轮错误态都塞在这里。路由信息走同一个键而不是新开一列，是为了不引入 schema 变更；
     * 合并而非覆盖，是为了让「有工具轨迹的那一轮」也能带上路由信息 —— 恰恰是这类长轮次
     * 最容易走错供应商。解析失败时原样返回，宁可少一条提示信息也不能弄丢工具轨迹。</p>
     *
     * @param json      原有 confirmation JSON（可为空）
     * @param routeInfo 路由信息（可为空）
     * @return 合并后的 JSON；无路由信息时原样返回
     */
    private String attachRouteInfo(String json, Map<String, Object> routeInfo) {
        if (routeInfo == null || routeInfo.isEmpty()) {
            return json;
        }
        try {
            Map<String, Object> merged = new LinkedHashMap<>();
            if (json != null && !json.isBlank()) {
                Object parsed = objectMapper.readValue(json, Map.class);
                if (parsed instanceof Map<?, ?> src) {
                    src.forEach((k, v) -> merged.put(String.valueOf(k), v));
                }
            }
            merged.put("route", routeInfo);
            return objectMapper.writeValueAsString(merged);
        } catch (Exception e) {
            log.warn("[ChatStream] 附加路由信息失败，保留原 confirmation: {}", e.getMessage());
            return json;
        }
    }

    /**
     * 把「本轮上下文占用 + token 构成」整理成可下发的 contextStats。
     *
     * <p>会话底部统计条的「上下文已用」卡片要用它：分母是模型窗口，
     * 分子是本轮真正送进模型的输入量，明细则是这份上下文由哪几块拼成
     * （系统提示词 / 摘要 / 用户输入 / 历史消息 / 工具定义）。</p>
     *
     * <p><b>为什么分子取 {@code usage.inputTokens}。</b>它不是「这个会话一共烧了多少」，
     * 而是「这一次请求真正塞了多少进上下文」—— 正是占用率要的口径。
     * provider 不回传用量时退回字符数估算（与失败分支同一套 {@code length * 1.5} 口径）。</p>
     *
     * <p><b>明细为什么是估算。</b>各部分在送到 provider 前还会被 agent 框架加工
     * （补工具 schema、注入规划约束等），链路上拿不到加工后的最终拼装结果，
     * 所以按「准备阶段组装出来的 request」逐块估字符数。因此五分量之和
     * <b>不等于</b> {@code usedTokens}（存在未归类余量），这是刻意保留的诚实口径：
     * 前端用分量之和当分母算占比，不拿 0 去凑满。</p>
     *
     * @param request       本轮组装好的请求（可为空）
     * @param usage         模型回传用量（可为空）
     * @param contextWindow 模型上下文窗口上限（可为空）
     * @return 至少含 usedTokens 的 map；窗口未知时不下发 effectiveContextWindow
     */
    private Map<String, Object> contextStatsOf(AiChatRequest request,
                                               com.chua.common.support.ai.AiUsage usage,
                                               Integer contextWindow) {
        Map<String, Object> stats = new LinkedHashMap<>(4);

        Integer reportedInput = usage == null ? null : usage.getInputTokens();
        int usedTokens = reportedInput != null && reportedInput > 0
                ? reportedInput
                : estimateTokens(request == null ? null : request.getPrompt());
        stats.put("usedTokens", usedTokens);
        if (contextWindow != null && contextWindow > 0) {
            stats.put("effectiveContextWindow", contextWindow);
        }

        Map<String, Object> breakdown = new LinkedHashMap<>(6);
        breakdown.put("systemPrompt", estimateTokens(request == null ? null : request.getSystemPrompt()));
        breakdown.put("tools", estimateTokens(serializeQuietly(request == null ? null : request.getTools())));

        // 对话消息按角色归位：最后一条用户消息算「用户输入」，其余算「历史消息」，
        // 夹在中间的 system 条目（历史压缩产物）单独算「摘要」。
        int userPrompt = 0;
        int history = 0;
        int summary = 0;
        List<Map<String, Object>> messages = request == null ? null : request.getMessages();
        if (messages != null && !messages.isEmpty()) {
            int lastUserIndex = -1;
            for (int i = messages.size() - 1; i >= 0; i--) {
                Map<String, Object> m = messages.get(i);
                if (m != null && "user".equals(String.valueOf(m.get("role")))) {
                    lastUserIndex = i;
                    break;
                }
            }
            for (int i = 0; i < messages.size(); i++) {
                Map<String, Object> m = messages.get(i);
                if (m == null) {
                    continue;
                }
                int t = estimateTokens(String.valueOf(m.get("content")));
                if ("system".equals(String.valueOf(m.get("role")))) {
                    summary += t;
                } else if (i == lastUserIndex) {
                    userPrompt += t;
                } else {
                    history += t;
                }
            }
        }
        breakdown.put("summary", summary);
        breakdown.put("userPrompt", userPrompt);
        breakdown.put("history", history);

        stats.put("breakdown", breakdown);
        return stats;
    }

    /**
     * 字符数 → Token 估算。
     *
     * <p>沿用本类既有的 {@code length * 1.5} 口径（见「模型未回传用量」分支），
     * 不引入新的分词器依赖：这里只服务一个占比条形图，量级正确即可。</p>
     *
     * @param text 文本（可为空）
     * @return 估算 Token 数
     */
    private int estimateTokens(String text) {
        return text == null || text.isEmpty() ? 0 : (int) (text.length() * 1.5);
    }

    /**
     * 把任意对象序列化成 JSON 字符串用于估长；失败时返回空串。
     *
     * <p>工具定义是结构化对象（name/description/parameters schema），
     * 直接取 {@code toString()} 会低估 schema 的体积，故走真实的 JSON 序列化。</p>
     *
     * @param value 待序列化对象
     * @return JSON 字符串；失败或为空时返回 {@code null}
     */
    private String serializeQuietly(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.debug("[ChatStream] 上下文构成估算序列化失败，按空处理: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 按 (供应商, 模型) 查上下文窗口上限。
     *
     * <p>恢复轮（审批续跑）走不到 prepare 里的模型参数查询，故单独补一次；
     * 查不到或未登记时返回 {@code null}，由前端显示「—」。</p>
     *
     * @param providerId 供应商 ID（可为空）
     * @param model      模型名
     * @return 上下文窗口（Token）；未知时为 {@code null}
     */
    private Integer resolveModelContextWindow(String providerId, String model) {
        if (model == null || model.isBlank()) {
            return null;
        }
        try {
            SysAiProviderModel modelInfo = providerId == null || providerId.isBlank()
                    ? sysAiProviderModelMapper.selectById(model)
                    : sysAiProviderModelMapper.selectOne(
                            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysAiProviderModel>()
                                    .eq(SysAiProviderModel::getProviderId, providerId)
                                    .eq(SysAiProviderModel::getModel, model)
                                    .last("LIMIT 1"));
            return modelInfo == null ? null : modelInfo.getContextWindow();
        } catch (Exception e) {
            log.warn("[ChatStream] 读取模型上下文窗口失败 model={}: {}", model, e.getMessage());
            return null;
        }
    }

    /**
     * 把缓存命中量与首字耗时整理成可下发的字段。
     *
     * <p>这两个数在 {@link com.chua.common.support.ai.AiUsage} 上已有，但历史上只落到
     * {@code sys_ai_usage} 计量表，会话消息里拿不到 —— 于是「会话底部统计条」只能算
     * 出速度，算不出缓存命中率与 TTFT。这里统一口径：
     * {@code cachedTokens} 为命中量（子集，不超过输入量），{@code firstTokenLatencyMs}
     * 为首字耗时；缺值时不放键，前端据此显示「—」而不是 0。</p>
     *
     * <p><b>首字耗时为什么要兜底。</b>{@code usage} 上的首字耗时只在 provider 回传了
     * 带用量的 STOP 帧时才有值。供应商不回传用量（OpenAI 兼容网关很常见）时 usage
     * 整个为 null，TTFT 就没了 —— 但首个 chunk 的到达时刻调用方自己看得到，
     * 传进来即可用，没有任何理由让这一项空着。provider 给了值就以其为准
     * （它更接近真实的首字时刻），只在缺失或非正数时才用兜底值。</p>
     *
     * @param usage                    模型回传用量（可为 空）
     * @param fallbackFirstTokenMillis 首字耗时兜底值（毫秒，可为 空）
     * @return 至少为空 Map；无有效值时不带任何键
     */
    private Map<String, Object> usageStatsOf(com.chua.common.support.ai.AiUsage usage,
                                             Long fallbackFirstTokenMillis) {
        Map<String, Object> stats = new LinkedHashMap<>(4);
        if (usage != null) {
            Integer cached = usage.getCacheTokens();
            if (cached != null && cached > 0) {
                stats.put("cachedTokens", cached);
            }
        }
        Long firstToken = usage == null ? null : usage.getFirstTokenLatencyMillis();
        if (firstToken == null || firstToken <= 0) {
            firstToken = fallbackFirstTokenMillis;
        }
        if (firstToken != null && firstToken > 0) {
            stats.put("firstTokenLatencyMs", firstToken);
        }
        return stats;
    }

    /**
     * 把用量补充字段并入已有的 confirmation JSON。
     *
     * <p>与 {@link #attachRouteInfo} 同样的理由：{@code confirmation} 已是工具轨迹、待确认、
     * 错误态、路由信息的公共载体，再加一列会动存量表。合并而非覆盖 —— 有工具轨迹的那一轮
     * 也必须保住轨迹；解析失败时原样返回，宁可少两个统计字段也不能弄丢工具轨迹。</p>
     *
     * @param json  原有 confirmation JSON（可为空）
     * @param extra 待补字段（可为空）
     * @return 合并后的 JSON；extra 为空时原样返回
     */
    private String attachUsageExtra(String json, Map<String, Object> extra) {
        if (extra == null || extra.isEmpty()) {
            return json;
        }
        try {
            Map<String, Object> merged = new LinkedHashMap<>();
            if (json != null && !json.isBlank()) {
                Object parsed = objectMapper.readValue(json, Map.class);
                if (parsed instanceof Map<?, ?> src) {
                    src.forEach((k, v) -> merged.put(String.valueOf(k), v));
                }
            }
            // 只补缺：路由/错误态已有同名键时不覆盖
            extra.forEach(merged::putIfAbsent);
            return objectMapper.writeValueAsString(merged);
        } catch (Exception e) {
            log.warn("[ChatStream] 附加用量字段失败，保留原 confirmation: {}", e.getMessage());
            return json;
        }
    }

    /**
     * 落库一条「失败」的助手消息。
     *
     * <p>为什么必须有：成功分支会 {@code addMessage}，失败分支原先只发 SSE 事件，
     * 于是「用户提问 + 模型报错」这一轮在刷新后整条消失，只剩下用户消息，
     * 数据不成对（实测 429 限流后 session 只剩 1 条 user 记录）。
     * 用户看到的现象是「AI 报错的信息没了」，而实际上是整轮对话丢失。</p>
     *
     * <p>内容取已流出的片段：有内容才能看出失败发生在第几步；片段为空才退回错误文案，
     * 保证任何情况下都会留下一条 assistant 消息与用户消息配对。</p>
     *
     * <p>错误详情写入 {@code confirmation} 列（该列已是工具轨迹的载体）：
     * {@code parsePendingConfirmation} 以 {@code status=="pending"} 为闸，
     * 本结构没有 status 字段，不会被误判成待确认请求。</p>
     *
     * @param sid            会话 ID
     * @param dialogId       对话 ID
     * @param userMessageId  父消息（用户消息）ID
     * @param modelToUse     本轮使用的模型
     * @param streamedText   已流出的文本片段（可为空）
     * @param errorMessage   错误文案
     * @param toolCalls      已收集的工具调用（可为空）
     * @param toolTraces     已收集的工具轨迹（可为空）
     * @param routeInfo      本轮路由信息（供应商/模型/基址，可为空）
     */
    private void persistErroredAssistantMessage(String sid, Long dialogId, Long userMessageId,
                                                String modelToUse, String streamedText, String errorMessage,
                                                List<Object> toolCalls,
                                                List<Map<String, Object>> toolTraces,
                                                Map<String, Object> routeInfo) {
        try {
            String partial = streamedText == null ? "" : streamedText.trim();
            String content = !partial.isEmpty() ? partial : ("[error] " + errorMessage);
            ChatMessage assistantMsg = new ChatMessage("assistant", content);
            assistantMsg.setParentId(userMessageId);
            assistantMsg.setModelName(modelToUse);
            // 失败轮没有 usage，沿用成功分支的估算口径，避免前端显示 null/NaN
            int promptTokens = 0;
            int completionTokens = (int) (content.length() * 1.5);
            assistantMsg.setPromptTokens(promptTokens);
            assistantMsg.setCompletionTokens(completionTokens);
            assistantMsg.setTotalTokens(promptTokens + completionTokens);

            Map<String, Object> errorMeta = new LinkedHashMap<>();
            errorMeta.put("finishReason", "error");
            errorMeta.put("errorDetail", errorMessage);
            if (toolCalls != null && !toolCalls.isEmpty()) {
                errorMeta.put("toolCalls", toolCalls);
            }
            if (toolTraces != null && !toolTraces.isEmpty()) {
                errorMeta.put("toolTraces", toolTraces);
            }
            // 失败轮更要记下路由信息：报错往往正是发到了预期之外的供应商，
            // 刷新后仍能对照「当时到底是哪家供应商应答的」来定位
            if (routeInfo != null && !routeInfo.isEmpty()) {
                errorMeta.put("route", routeInfo);
            }
            assistantMsg.setConfirmation(objectMapper.writeValueAsString(errorMeta));

            aiDialogRecordService.addMessage(dialogId, assistantMsg);
            log.info("[ChatStream] 失败助手消息已落库（保证与用户消息成对）sessionId={}, parentId={}, 片段长度={}",
                    sid, userMessageId, partial.length());
        } catch (Exception e) {
            log.warn("[ChatStream] 落库失败助手消息失败 sessionId={}: {}", sid, e.getMessage());
        }
    }

    /**
     * 描述本轮实际命中的供应商，供日志与前端提示共用同一份数据。
     *
     * <p>刻意只查一次就同时用于日志与 SSE：若前端提示和后端日志各查一次，
     * 两者可能因并发改动而说法不一致，排查时反而多一个疑点。</p>
     *
     * @param providerId 供应商标识（可空）
     * @param model      裸模型名
     * @return 至少含 {@code model}；查不到供应商时只有 {@code model} 与 {@code providerResolved=false}
     */
    private Map<String, Object> describeRoute(String providerId, String model) {
        Map<String, Object> route = new LinkedHashMap<>(8);
        route.put("model", model);
        route.put("providerId", providerId);
        SysAiProvider provider = null;
        if (providerId != null && !providerId.isBlank()) {
            try {
                provider = sysAiProviderMapper.selectById(providerId);
            } catch (Exception e) {
                log.debug("[ChatStream] 查询供应商 {} 失败: {}", providerId, e.getMessage());
            }
        }
        if (provider != null) {
            route.put("providerName", provider.getName());
            route.put("platform", provider.getPlatform());
            route.put("baseUrl", provider.getBaseUrl());
            route.put("providerResolved", true);
        } else {
            route.put("providerResolved", false);
        }
        // 路由唯一一处权威日志：选型、供应商、实际 baseUrl 一次说清
        log.info("[ChatStream] 路由选定 model={} providerId={} 供应商={} platform={} baseUrl={}",
                model, providerId,
                provider != null ? provider.getName() : "<未解析>",
                provider != null ? provider.getPlatform() : "<未解析>",
                provider != null ? provider.getBaseUrl() : "<未解析>");
        return route;
    }

    /**
     * get Default Model
     * @return 返回值
    */
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
                // 原样返回（可能带 <providerId>: 前缀），由 prepare 统一拆出供应商一侧。
                // 以前在这里就把前缀切掉，等于让默认模型也退化成「按名反查」，
                // 同名模型时默认对话同样可能落到别家供应商上。
                return configured;
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

    /**
     * 套用「设置中心 → 系统提示词」作为**默认**提示词。
     *
     * <p><b>为什么之前是坏的。</b>该配置被 {@code ChatClientSettingsResolver} 读进
     * {@code ChatClientSettings.systemPrompt}，但对话链路上没有任何一处再读它：
     * {@code AiChatFacadeServiceImpl#configureClient} 只认 {@code request.getSystemPrompt()}。
     * 结果「未选角色」的会话一路空到 {@code AiChatFacadeServiceImpl} 里硬编码的英文
     * {@code "You are a helpful assistant."} —— 在全中文产品里既不生效、也不一致。
     * 设置中心里那个框能看、能改、能存，改了没有任何效果。</p>
     *
     * <p>现在补齐回退链：<b>角色提示词 &gt; 全局默认提示词 &gt; 门面兜底</b>。
     * 与角色已有的 model / temperature 回退语义保持一致（角色为空即沿用全局）。</p>
     *
     * @param request 待填充的对话请求
     */
    private void applyDefaultSystemPrompt(AiChatRequest request) {
        String globalPrompt = sysAiGlobalSettingService
                .getValue(SETTINGS_GROUP_RUNTIME, GLOBAL_FIELD_SYSTEM_PROMPT)
                .orElse(null);
        if (globalPrompt != null && !globalPrompt.isBlank()) {
            request.setSystemPrompt(globalPrompt);
        }
    }

    /**
     * 全局设置分组（与 ChatClientSettingsResolver.GROUP 保持一致）。
     */
    private static final String SETTINGS_GROUP_RUNTIME = "runtime";

    /**
     * 内置「规划」形态标识。只有这个形态会开启 agent 的规划能力。
     */
    private static final String FORM_KEY_PLAN = "plan";

    /**
     * agent 构建能力模式
     */
    private static final String AGENT_MODE_BUILD = "build";

    /**
     * agent 规划能力模式
     */
    private static final String AGENT_MODE_PLAN = "plan";

    /**
     * 全局默认系统提示词字段名（与 ChatClientSettingsResolver 读取的键一致）。
     */
    private static final String GLOBAL_FIELD_SYSTEM_PROMPT = "system_prompt";

    /**
     * load Role
     * @param characterId 字符串
     * @return 返回值
    */
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

    /**
     * parse Role Settings
     * @param role 参数
     * @return 返回值
    */
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

    /**
     * parse Skill Names
     * @param raw 对象
     * @return 返回值
    */
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

    /**
     * resolve Dialog Id
     * @param sessionId 字符串
     * @return 返回值
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
     * 前端 sessionId(UUID) -> 后端会话主键(Long) 的进程内映射
     */
    private final Map<String, Long> sessionDialogMap = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * persist Session Mcp
     * @param csv 字符串
     * @param dialogId 长整数
     * @return 返回值
    */
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

    /**
     * load Session Mcp Ids
     * @param dialogId 长整数
     * @return 返回值
    */
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
    /**
     * merge Session Mcp
     * @param Map<String 参数
     * @param ids 参数
     * @param roleSettings 参数
     * @return 返回值
    */
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

    /**
     * str
     * @param o 对象
     * @return 返回值
    */
    private static String str(Object o) {
        return o != null ? String.valueOf(o) : null;
    }

    /**
     * safe
     * @param s 字符串
     * @return 返回值
    */
    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static Map<?, ?> asMap(Object o) {
        return o instanceof Map ? (Map<?, ?>) o : null;
    }
}
