package com.chua.starter.ai.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.time.LocalDateTime;
import com.chua.starter.ai.support.mapper.SysAiSessionMapper;
import com.chua.starter.ai.support.mapper.SysAiMessageMapper;
import com.chua.starter.ai.support.entity.SysAiSession;
import com.chua.starter.ai.support.entity.SysAiMessage;
import com.chua.starter.ai.support.entity.SysAiSessionGroup;
import com.chua.starter.ai.support.manage.common.ApiErrorCode;
import com.chua.starter.ai.support.manage.common.ApiException;
import com.chua.starter.ai.support.util.AiUserContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

/**
 * 会话控制器
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@RestController
@RequestMapping("/v2/sessions")
@RequiredArgsConstructor
public class AiSessionController {

    private final SysAiSessionMapper sessionMapper;
    private final SysAiMessageMapper messageMapper;
    private final com.chua.starter.ai.support.mapper.SysAiSessionGroupMapper sessionGroupMapper;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    /**
     * 对话门面（压缩时用它同步调用模型生成摘要）
     */
    private final com.chua.starter.ai.support.service.AiChatFacadeService aiChatFacadeService;

    /**
     * 会话压缩记录服务
     */
    private final com.chua.starter.ai.support.service.SysAiSessionCompressionService sessionCompressionService;

    /**
     * 生效对话模型解析器（压缩时选模型，不写死）
     */
    private final com.chua.starter.ai.support.configuration.client.EffectiveChatModelResolver effectiveChatModelResolver;

    /**
     * 全局设置服务（取"系统/工作目录基路径" system.workspaceBaseDir）
     */
    private final com.chua.starter.ai.support.service.SysAiGlobalSettingService globalSettingService;

    /**
     * 将前端 settings 对象序列化为 JSON 字符串；入参为 null 或序列化失败时返回 null。
     */
    private String writeSettings(Object settingsObj) {
        if (settingsObj == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(settingsObj);
        } catch (Exception e) {
            log.warn("[Session] 序列化 settings 失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 将持久化的 settings JSON 解析为 Map；为空或解析失败时返回空 Map。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> readSettings(String json) {
        if (json == null || json.isBlank()) {
            return new HashMap<>();
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(json, Map.class);
            return parsed != null ? parsed : new HashMap<>();
        } catch (Exception e) {
            log.warn("[Session] 解析 settings 失败: {}", e.getMessage());
            return new HashMap<>();
        }
    }

    /**
     * 解析助手消息上持久化的确认请求；仅返回 status=pending 的请求，结构 {replyId, items}。
     *
     * @param json 确认请求 JSON
     * @return 待确认请求 Map；非 pending / 解析失败返回 null
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parsePendingConfirmation(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            Map<String, Object> data = objectMapper.readValue(json, Map.class);
            if (!"pending".equals(data.get("status"))) {
                return null;
            }
            Object replyId = data.get("replyId");
            if (replyId == null || String.valueOf(replyId).isBlank()) {
                return null;
            }
            Map<String, Object> req = new HashMap<>();
            req.put("replyId", String.valueOf(replyId));
            req.put("items", data.getOrDefault("items", new ArrayList<>()));
            return req;
        } catch (Exception e) {
            log.warn("[Session] 解析确认请求失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 获取会话列表
     */
    @GetMapping
    public ReturnResult<Map<String, Object>> list(
            @RequestParam(required = false) Integer skip,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String groupId,
            @RequestParam(required = false) Boolean includeArchived) {

        LambdaQueryWrapper<SysAiSession> wrapper = new LambdaQueryWrapper<>();
 // 管理员只读全量，其余仅本人会话
        if (!AiUserContext.isAdmin()) {
            wrapper.eq(SysAiSession::getSysUserId, AiUserContext.requireUserId());
        }
        wrapper.orderByDesc(SysAiSession::getCreateTime);

        List<SysAiSession> sessions = sessionMapper.selectList(wrapper);

        List<Map<String, Object>> items = new ArrayList<>();
        for (SysAiSession session : sessions) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", String.valueOf(session.getId()));
            item.put("title", session.getTitle());
            item.put("createdAt", session.getCreateTime());
            item.put("updatedAt", session.getUpdateTime());
            
            items.add(item);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("items", items);
        result.put("total", items.size());
        return ReturnResult.ok(result);
    }

    /**
     * 获取侧边栏会话列表
     *
     * @param limit 数量限制
     * @return 会话列表
     */
    @GetMapping("/sidebar")
    public ReturnResult<Map<String, Object>> sidebar(@RequestParam(required = false) Integer limit) {
        int pageSize = limit != null ? limit : 15;
        boolean admin = AiUserContext.isAdmin();
        String userId = AiUserContext.requireUserId();

        // 查询分组：管理员可见全量，其余仅本人分组
        LambdaQueryWrapper<SysAiSessionGroup> groupWrapper = new LambdaQueryWrapper<>();
        if (!admin) {
            groupWrapper.eq(SysAiSessionGroup::getUserId, userId);
        }
        groupWrapper.orderByAsc(SysAiSessionGroup::getSortOrder);
        List<SysAiSessionGroup> groups = sessionGroupMapper.selectList(groupWrapper);

        // 构造 groups 数组
        List<Map<String, Object>> groupList = new ArrayList<>();
        for (SysAiSessionGroup group : groups) {
            Map<String, Object> groupMap = new HashMap<>();
            groupMap.put("id", String.valueOf(group.getId()));
            groupMap.put("name", group.getName());
            groupMap.put("sortOrder", group.getSortOrder());
            groupList.add(groupMap);
        }

        // 查询未归档会话：管理员可见全量，其余仅本人会话
        LambdaQueryWrapper<SysAiSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysAiSession::getIsArchived, false);  // 只查询未归档的会话
        if (!admin) {
            wrapper.eq(SysAiSession::getSysUserId, userId);
        }
        wrapper.orderByDesc(SysAiSession::getCreateTime);
        wrapper.last("LIMIT " + pageSize);
        List<SysAiSession> sessions = sessionMapper.selectList(wrapper);

        // 构造 groupSessions（未分组）
        List<Map<String, Object>> groupSessions = new ArrayList<>();
        Map<String, Object> ungroupedGroup = new HashMap<>();
        ungroupedGroup.put("groupId", null);

        List<Map<String, Object>> items = new ArrayList<>();
        for (SysAiSession session : sessions) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", String.valueOf(session.getId()));
            item.put("title", session.getTitle());
            item.put("createdAt", session.getCreateTime());
            item.put("updatedAt", session.getUpdateTime());
            items.add(item);
        }
        ungroupedGroup.put("items", items);
        ungroupedGroup.put("hasMore", false);
        groupSessions.add(ungroupedGroup);

        Map<String, Object> result = new HashMap<>();
        result.put("groups", groupList);
        result.put("groupSessions", groupSessions);

        return ReturnResult.ok(result);
    }

    /**
     * 创建新会话
     *
     * @param body 会话参数
     * @return 创建的会话
     */

    /**
     * 获取会话消息列表
     */
    // 前端 UUID sessionId -> 后端 Long sessionId 的映射（与 ChatStreamController 共享）
    public static final java.util.Map<String, Long> SESSION_DIALOG_MAP = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 工作目录树最大展开深度（0 表示只列一层）
     */
    private static final int WORKSPACE_MAX_DEPTH = 2;

    /**
     * 工作目录树每个目录最多列出的条目数
     */
    private static final int WORKSPACE_MAX_ENTRIES = 200;

    @GetMapping("/{id}/messages")
    public ReturnResult<Map<String, Object>> messages(
            @PathVariable("id") String id,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String beforeMessageId) {

        log.info("[Messages] Loading messages for sessionId: {}", id);
        Long sessionId;
        // 如果是数字，直接解析
        if (id.matches("\\d+")) {
            sessionId = Long.parseLong(id);
        } else {
            // 从映射中查找
            sessionId = SESSION_DIALOG_MAP.get(id);
            if (sessionId == null) {
                // 通过会话标题查找（前端UUID前8位对应会话标题后8位）
                String titlePrefix = id != null && id.length() > 8 ? "guada-" + id.substring(0, 8) : null;
                if (titlePrefix != null) {
                    LambdaQueryWrapper<SysAiSession> titleWrapper = new LambdaQueryWrapper<>();
                    titleWrapper.likeRight(SysAiSession::getTitle, titlePrefix);
                    titleWrapper.orderByDesc(SysAiSession::getId);
                    titleWrapper.last("LIMIT 1");
                    SysAiSession existingSession = sessionMapper.selectOne(titleWrapper);
                    if (existingSession != null) {
                        sessionId = existingSession.getId();
                        SESSION_DIALOG_MAP.put(id, sessionId);
                    }
                }
                // 还是找不到，创建新会话
                if (sessionId == null) {
                    SysAiSession newSession = new SysAiSession();
                    newSession.setTitle("guada-" + (id != null && id.length() > 8 ? id.substring(0, 8) : "chat"));
                    newSession.setSysUserId(AiUserContext.requireUserId());
                    newSession.setStatus(1);
                    newSession.setIsArchived(false);
                    sessionMapper.insert(newSession);
                    sessionId = newSession.getId();
                    SESSION_DIALOG_MAP.put(id, sessionId);
                }
            }
        }

 // 归属校验：他人会话的消息不可读（管理员只读放行）
        requireReadableSession(sessionId);

        log.info("[Messages] Querying messages for sessionId (Long): {}", sessionId);
        LambdaQueryWrapper<SysAiMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysAiMessage::getSysAiSessionId, sessionId);
        
        // 如果有 beforeMessageId，查询该消息之前的消息
        if (beforeMessageId != null && beforeMessageId.matches("\\d+")) {
            Long beforeId = Long.parseLong(beforeMessageId);
            wrapper.lt(SysAiMessage::getId, beforeId);
        }
        
        wrapper.orderByDesc(SysAiMessage::getCreateTime);
        if (limit != null) {
            wrapper.last("LIMIT " + limit);
        }

        List<SysAiMessage> messages = messageMapper.selectList(wrapper);

        // 反转，按时间升序返回
        List<Map<String, Object>> items = new ArrayList<>();
        String lastUserMessageId = null;
        LocalDateTime lastUserMessageTime = null;
        for (int i = messages.size() - 1; i >= 0; i--) {
            SysAiMessage msg = messages.get(i);
            Map<String, Object> item = new HashMap<>();
            item.put("id", String.valueOf(msg.getId()));
            item.put("role", msg.getRole());
            
            // 如果是 user 消息，记录 ID 和时间；如果是 assistant，设置 parentId 和耗时
            if ("user".equals(msg.getRole())) {
                lastUserMessageId = String.valueOf(msg.getId());
                lastUserMessageTime = msg.getCreateTime();
            } else if ("assistant".equals(msg.getRole()) && lastUserMessageId != null) {
                item.put("parentId", lastUserMessageId);
                // 计算耗时（毫秒）
                if (lastUserMessageTime != null) {
                    long durationMs = java.time.Duration.between(lastUserMessageTime, msg.getCreateTime()).toMillis();
                    item.put("totalDurationMs", durationMs);
                }
            }
            
            // 构造 contents 数组
            List<Map<String, Object>> contents = new ArrayList<>();
            Map<String, Object> content = new HashMap<>();
            content.put("id", String.valueOf(msg.getId()));
            content.put("content", msg.getContent());
            content.put("state", Map.of("isStreaming", false));
            // 在 content 级别也添加 metadata，包含 usage
            Map<String, Object> contentMetadata = new HashMap<>();
            if ("assistant".equals(msg.getRole())) {
                Map<String, Object> usage = new HashMap<>();
                usage.put("promptTokens", msg.getPromptTokens());
                usage.put("completionTokens", msg.getCompletionTokens());
                usage.put("totalTokens", msg.getTotalTokens());
                contentMetadata.put("usage", usage);
                // 待人工确认（plan_exit）：刷新后仍渲染批准卡片
                Map<String, Object> pendingConfirm = parsePendingConfirmation(msg.getConfirmation());
                if (pendingConfirm != null) {
                    contentMetadata.put("confirmationRequest", pendingConfirm);
                }
            }
            content.put("metadata", contentMetadata);
            contents.add(content);
            item.put("contents", contents);
            
            item.put("state", Map.of("isStreaming", false));
            item.put("createdAt", msg.getCreateTime());
            
            // 添加 metadata，包含模型信息和 token 使用量
            Map<String, Object> metadata = new HashMap<>();
            if ("assistant".equals(msg.getRole()) && msg.getModelName() != null) {
                metadata.put("modelName", msg.getModelName());
            }
            if ("assistant".equals(msg.getRole())) {
                Map<String, Object> usage = new HashMap<>();
                usage.put("promptTokens", msg.getPromptTokens());
                usage.put("completionTokens", msg.getCompletionTokens());
                usage.put("totalTokens", msg.getTotalTokens());
                metadata.put("usage", usage);
            }
            item.put("metadata", metadata);
            items.add(item);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("items", items);
        result.put("total", items.size());
        return ReturnResult.ok(result);
    }

    /**
     * 获取单个会话详情（前端 fetchSession）
     * @param id ID，不允许为 null
     * @return Return结果 对象
     */
    @GetMapping("/{id}")
    public ReturnResult<Map<String, Object>> detail(@PathVariable("id") String id) {
        Long sessionId = Long.parseLong(id);
        SysAiSession session = sessionMapper.selectById(sessionId);
        if (session != null) {
 // 归属校验：他人会话详情不可读（管理员只读放行）
            requireReadableSession(sessionId);
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("id", String.valueOf(sessionId));
        
        if (session != null) {
            result.put("title", session.getTitle());
            result.put("modelId", session.getModelId());
            result.put("characterId", session.getCharacterId());
            result.put("mcpServerIds", parseIdList(session.getMcpServerIds()));
            result.put("groupId", session.getSysAiModuleCode());
            result.put("createdAt", session.getCreateTime() != null ? session.getCreateTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() : System.currentTimeMillis());
            result.put("updatedAt", session.getUpdateTime() != null ? session.getUpdateTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() : System.currentTimeMillis());
        } else {
            result.put("title", "会话 " + id);
            result.put("modelId", null);
            result.put("characterId", null);
            result.put("mcpServerIds", null);
            result.put("groupId", null);
            result.put("createdAt", System.currentTimeMillis());
            result.put("updatedAt", System.currentTimeMillis());
        }
        
        result.put("settings", readSettings(session == null ? null : session.getSettings()));
        return ReturnResult.ok(result);
    }

    /**
     * 更新会话（移动到分组、修改标题等）。
     *
     * @param id 会话 id（字符串形式的路径参数）
     * @param body 更新内容请求体，键值对形式
     * @return 更新结果，含 success 与 message 字段
     */
    @PutMapping("/{id}")
    public ReturnResult<Map<String, Object>> update(@PathVariable("id") String id, @RequestBody Map<String, Object> body) {
        Long sessionId = Long.parseLong(id);
        
        SysAiSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "会话不存在");
            return ReturnResult.ok(result);
        }
 // 写出口校验：仅归属人可改，管理员对他人数据只读
        requireWritableSession(sessionId);
        
        // 更新标题
        if (body.containsKey("title")) {
            session.setTitle(body.get("title").toString());
        }
        
        // 更新分组
        if (body.containsKey("groupId")) {
            Object groupId = body.get("groupId");
            if (groupId != null && !"null".equals(groupId.toString())) {
                session.setSysAiModuleCode(groupId.toString());
            } else {
                session.setSysAiModuleCode(null);
            }
        }
        
        // 更新模型ID
        if (body.containsKey("modelId")) {
            Object modelId = body.get("modelId");
            if (modelId != null && !"null".equals(modelId.toString())) {
                session.setModelId(modelId.toString());
            } else {
                session.setModelId(null);
            }
        }

        // 更新绑定角色
        if (body.containsKey("characterId")) {
            session.setCharacterId(parseLongOrNull(String.valueOf(body.get("characterId"))));
        }

        // 更新会话级 MCP 服务器列表（仅管理员可写，非管理员忽略）
        if (body.containsKey("mcpServerIds")) {
            String mcpCsvOnUpdate = normalizeIdList(body.get("mcpServerIds"));
            if (!AiUserContext.isAdmin()) {
                mcpCsvOnUpdate = null;
            }
            session.setMcpServerIds(mcpCsvOnUpdate);
        }

        // 更新会话级设置（runMode、温度、topP、知识库引用等），整体 JSON 持久化
        if (body.containsKey("settings")) {
            session.setSettings(writeSettings(body.get("settings")));
        }

        session.setUpdateTime(LocalDateTime.now());
        sessionMapper.updateById(session);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("id", String.valueOf(session.getId()));
        result.put("title", session.getTitle());
        result.put("groupId", session.getSysAiModuleCode());
        result.put("modelId", session.getModelId());
        result.put("characterId", session.getCharacterId());
        result.put("mcpServerIds", parseIdList(session.getMcpServerIds()));
        result.put("settings", readSettings(session.getSettings()));
        return ReturnResult.ok(result);
    }

    /**
     * 创建。
     *
     * @param body 请求体，不允许为 null
     * @return Return结果 对象
     */
    @PostMapping
    public ReturnResult<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        String title = body.getOrDefault("title", "新会话").toString();
        // 前端字段为 characterId，兼容旧字段 roleId
        String characterIdRaw = body.getOrDefault("characterId", body.getOrDefault("roleId", "")).toString();
        String modelId = body.getOrDefault("modelId", "").toString();
        Object mcpServerIdsRaw = body.get("mcpServerIds");

        // 真正保存到数据库
        SysAiSession session = new SysAiSession();
        session.setTitle(title);
        session.setSysAiModuleCode(null);
        session.setModelId(modelId);
        session.setCharacterId(parseLongOrNull(characterIdRaw));
        String mcpCsvOnCreate = normalizeIdList(mcpServerIdsRaw);
        // MCP 仅管理员可配置：非管理员提交一律忽略
        if (!AiUserContext.isAdmin()) {
            mcpCsvOnCreate = null;
        }
        session.setMcpServerIds(mcpCsvOnCreate);
        session.setSettings(writeSettings(body.get("settings")));
        session.setSysUserId(AiUserContext.requireUserId());
        session.setCreateTime(LocalDateTime.now());
        session.setUpdateTime(LocalDateTime.now());
        session.setStatus(1);
        session.setIsArchived(false);
        sessionMapper.insert(session);

        Map<String, Object> result = new HashMap<>();
        result.put("id", String.valueOf(session.getId()));
        result.put("title", session.getTitle());
        result.put("groupId", session.getSysAiModuleCode());
        result.put("modelId", session.getModelId());
        result.put("characterId", session.getCharacterId());
        result.put("mcpServerIds", parseIdList(session.getMcpServerIds()));
        result.put("settings", readSettings(session.getSettings()));
        result.put("createdAt", session.getCreateTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli());
        result.put("updatedAt", session.getUpdateTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli());

        log.info("[Session] 创建会话: id={}, title={}, characterId={}", session.getId(), title, session.getCharacterId());
        return ReturnResult.ok(result);
    }

    /**
     * 手动压缩会话上下文。
     *
     * <p>把「除最近保留条数外」的历史交给模型生成摘要并落库到
     * {@code sys_ai_session_compression}；后续对话会把这段历史替换成该摘要
     * （见 {@code ChatStreamOrchestrator} 组装 messages 的分支）。
     * 会话内置命令「压缩上下文」调用的就是本接口 —— 此前这个接口不存在，
     * 前端一点就只能收到 404。</p>
     *
     * <p>失败不抛异常，统一返回 {@code success=false} + 可读 message，
     * 让前端按业务提示展示而不是报「请求错误」。</p>
     *
     * @param id 会话标识（数字主键，或前端会话 UUID —— 与 /{id}/messages 取值一致）
     * @return success / removedMessages / summary
     */
    @PostMapping("/{id}/compress")
    public ReturnResult<Map<String, Object>> compress(@PathVariable("id") String id) {
        Long sessionId = resolveSessionId(id);
        if (sessionId == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "会话不存在");
        }
        requireWritableSession(sessionId);

        LambdaQueryWrapper<SysAiMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysAiMessage::getSysAiSessionId, sessionId);
        wrapper.orderByAsc(SysAiMessage::getId);
        List<SysAiMessage> all = messageMapper.selectList(wrapper);

        // 保留最近 4 条原文，更早的才值得压成摘要
        final int keep = 4;
        Map<String, Object> result = new HashMap<>();
        if (all.size() < keep + 2) {
            result.put("success", false);
            result.put("message", "消息较少，无需压缩");
            result.put("removedMessages", 0);
            return ReturnResult.ok(result);
        }

        List<SysAiMessage> older = all.subList(0, all.size() - keep);
        StringBuilder transcript = new StringBuilder();
        for (SysAiMessage m : older) {
            transcript.append("user".equals(m.getRole()) ? "[用户] " : "[助手] ")
                    .append(m.getContent() == null ? "" : m.getContent())
                    .append('\n');
        }

        String summary;
        try {
            com.chua.starter.ai.support.model.AiChatRequest request =
                    new com.chua.starter.ai.support.model.AiChatRequest();
            request.setModel(effectiveChatModelResolver.resolve(null));
            request.setStream(false);
            request.setSystemPrompt("你是一名对话摘要助手。请把用户提供的对话压缩成简洁摘要，"
                    + "保留事实、结论、待办与关键约束，直接输出摘要，不要添加解释。");
            request.setMessages(List.of(Map.of("role", "user", "content",
                    "请把以下对话压缩成摘要：\n" + transcript)));
            Map<String, Object> response = aiChatFacadeService.call(request);
            Object text = response == null ? null : response.get("text");
            summary = text == null ? "" : String.valueOf(text).trim();
        } catch (Exception e) {
            log.warn("[Session] 压缩会话失败: sessionId={}, {}", sessionId, e.getMessage());
            result.put("success", false);
            result.put("message", "压缩失败：" + e.getMessage());
            result.put("removedMessages", 0);
            return ReturnResult.ok(result);
        }
        if (summary.isEmpty()) {
            result.put("success", false);
            result.put("message", "压缩失败：模型未返回摘要");
            result.put("removedMessages", 0);
            return ReturnResult.ok(result);
        }

        com.chua.starter.ai.support.entity.SysAiSessionCompression record =
                new com.chua.starter.ai.support.entity.SysAiSessionCompression();
        record.setSysAiSessionId(sessionId);
        record.setSysAiCompressionFromMessageId(older.get(0).getId().intValue());
        record.setSysAiCompressionMessageCount((long) older.size());
        record.setSysAiCompressionContext(summary);
        record.setUserId(AiUserContext.requireUserId());
        record.setCreateTime(LocalDateTime.now());
        record.setUpdateTime(LocalDateTime.now());
        sessionCompressionService.saveOrUpdate(record);

        result.put("success", true);
        result.put("removedMessages", older.size());
        result.put("summary", summary);
        log.info("[Session] 压缩会话: sessionId={}, 压缩 {} 条, 摘要 {} 字",
                sessionId, older.size(), summary.length());
        return ReturnResult.ok(result);
    }

    /**
     * 解析会话标识为数字主键：数字直接用，前端 UUID 查会话映射表（不创建新会话）。
     *
     * @param id 会话标识
     * @return 数字主键；解析不到时返回 {@code null}
     */
    private static Long resolveSessionId(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        if (id.matches("\\d+")) {
            return Long.parseLong(id);
        }
        return SESSION_DIALOG_MAP.get(id);
    }

    /**
     * 取会话的 token 统计（上下文占用）。
     *
     * <p>两段来源：① 消息表累计的真实 token（prompt / completion）；
     * ② 会话 settings 的 {@code contextStats} —— 前端在流式 finish 事件里拿到
     * 「已用 / 有效窗口」后回写，刷新页面时靠它恢复占用率。</p>
     *
     * @param id 会话标识
     * @return usedTokens / effectiveContextWindow / promptTokens / completionTokens / totalTokens
     */
    @GetMapping("/{id}/token-stats")
    public ReturnResult<Map<String, Object>> tokenStats(@PathVariable("id") String id) {
        Long sessionId = resolveSessionId(id);
        if (sessionId == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "会话不存在");
        }
        requireReadableSession(sessionId);
        SysAiSession session = sessionMapper.selectById(sessionId);

        long usedTokens = 0L;
        long effectiveContextWindow = 0L;
        Object contextStats = readSettings(session == null ? null : session.getSettings()).get("contextStats");
        if (contextStats instanceof Map<?, ?> stats) {
            usedTokens = longOf(stats.get("usedTokens"));
            effectiveContextWindow = longOf(stats.get("effectiveContextWindow"));
        }

        LambdaQueryWrapper<SysAiMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysAiMessage::getSysAiSessionId, sessionId);
        long promptTokens = 0L;
        long completionTokens = 0L;
        for (SysAiMessage m : messageMapper.selectList(wrapper)) {
            promptTokens += m.getPromptTokens() == null ? 0 : m.getPromptTokens();
            completionTokens += m.getCompletionTokens() == null ? 0 : m.getCompletionTokens();
        }

        Map<String, Object> result = new HashMap<>();
        result.put("usedTokens", usedTokens);
        result.put("effectiveContextWindow", effectiveContextWindow);
        result.put("promptTokens", promptTokens);
        result.put("completionTokens", completionTokens);
        result.put("totalTokens", Math.max(usedTokens, promptTokens + completionTokens));
        return ReturnResult.ok(result);
    }

    /**
     * 回写会话的上下文占用（前端在流式 finish 之后调用）。
     *
     * <p>落到会话 settings 的 {@code contextStats}，刷新后由
     * {@link #tokenStats} 读回，避免占用率归零；不建新表。</p>
     *
     * @param id   会话标识
     * @param body 含 usedTokens / effectiveContextWindow
     * @return 是否写入成功
     */
    @PutMapping("/{id}/token-stats")
    public ReturnResult<Map<String, Object>> saveTokenStats(@PathVariable("id") String id,
                                                            @RequestBody Map<String, Object> body) {
        Long sessionId = resolveSessionId(id);
        SysAiSession session = sessionId == null ? null : sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "会话不存在");
        }
        requireWritableSession(sessionId);

        Map<String, Object> settings = new HashMap<>(readSettings(session.getSettings()));
        Map<String, Object> contextStats = new HashMap<>();
        contextStats.put("usedTokens", longOf(body.get("usedTokens")));
        contextStats.put("effectiveContextWindow", longOf(body.get("effectiveContextWindow")));
        settings.put("contextStats", contextStats);
        session.setSettings(writeSettings(settings));
        session.setUpdateTime(LocalDateTime.now());
        sessionMapper.updateById(session);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        return ReturnResult.ok(result);
    }

    /**
     * 取会话待办列表。
     *
     * <p>后端目前没有待办存储，这里**如实返回空列表**：前端据此显示「暂无待办」，
     * 而不是因为 404 在控制台报「资源不存在」并把面板置为错误态。
     * 待办要真做需要独立的存储与写入链路，属另一个功能。</p>
     *
     * @param id 会话标识
     * @return items（当前恒为空数组）
     */
    @GetMapping("/{id}/todo")
    public ReturnResult<Map<String, Object>> todo(@PathVariable("id") String id) {
        Long sessionId = resolveSessionId(id);
        if (sessionId == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "会话不存在");
        }
        requireReadableSession(sessionId);
        Map<String, Object> result = new HashMap<>();
        result.put("items", new ArrayList<>());
        return ReturnResult.ok(result);
    }

    /**
     * 取会话工作目录的文件树。
     *
     * <p>目录口径与对话写入侧一致：{@code <system.workspaceBaseDir>/<当前账号>/agent}
     * （按账号隔离，不按会话）；基路径未配置时返回空树。</p>
     *
     * @param id 会话标识
     * @return tree（目录/文件节点数组，结构与前端 WorkspaceSidebar 一致）
     */
    @GetMapping("/{id}/workspace/tree")
    public ReturnResult<Map<String, Object>> workspaceTree(@PathVariable("id") String id) {
        Long sessionId = resolveSessionId(id);
        if (sessionId == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "会话不存在");
        }
        requireReadableSession(sessionId);
        java.nio.file.Path root = resolveAgentWorkspace();
        Map<String, Object> result = new HashMap<>();
        result.put("tree", root == null ? new ArrayList<>() : listWorkspace(root, 0));
        return ReturnResult.ok(result);
    }

    /**
     * 取会话工作目录路径。
     *
     * <p>前端打开工作区面板时会问一次目录路径；此前该端点不存在（404）。
     * 口径与 {@link #workspaceTree} 完全一致。同时给出 {@code path} 与
     * {@code workspacePath} 两个键名，兼容前端不同版本的读取方式。</p>
     *
     * @param id 会话标识
     * @return 目录路径（未配置基路径时为空串）
     */
    @GetMapping("/{id}/workspace-path")
    public ReturnResult<Map<String, Object>> workspacePath(@PathVariable("id") String id) {
        Long sessionId = resolveSessionId(id);
        if (sessionId == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "会话不存在");
        }
        requireReadableSession(sessionId);
        java.nio.file.Path root = resolveAgentWorkspace();
        String path = root == null ? "" : root.toString();
        Map<String, Object> result = new HashMap<>();
        result.put("path", path);
        result.put("workspacePath", path);
        return ReturnResult.ok(result);
    }

    /**
     * 解析当前账号的工作目录（{@code <基路径>/<账号标识>/agent}）。
     *
     * <p>账号标识做安全清洗，并在解析后校验仍落在基路径内，防 {@code ../} 越界。</p>
     *
     * @return 工作目录；基路径未配置或越界时返回 {@code null}
     */
    private java.nio.file.Path resolveAgentWorkspace() {
        String base;
        try {
            base = globalSettingService.getValue("system", "workspaceBaseDir").orElse(null);
        } catch (Exception e) {
            log.warn("[Session] 读取工作目录基路径失败: {}", e.getMessage());
            return null;
        }
        if (base == null || base.isBlank()) {
            return null;
        }
        try {
            java.nio.file.Path basePath = java.nio.file.Paths.get(base.trim()).toAbsolutePath().normalize();
            String account = java.util.Objects.toString(AiUserContext.currentUserId(), "public")
                    .replaceAll("[^A-Za-z0-9_-]", "_");
            java.nio.file.Path workspace = basePath.resolve(account).resolve("agent").normalize();
            if (!workspace.startsWith(basePath)) {
                log.warn("[Session] 工作目录越界，已拒绝: {}", workspace);
                return null;
            }
            return workspace;
        } catch (Exception e) {
            log.warn("[Session] 解析工作目录失败 base={}: {}", base, e.getMessage());
            return null;
        }
    }

    /**
     * 递归列目录为前端可渲染的节点数组。
     *
     * <p>深度与同级条数都有上限：工作目录可能很大，全量递归会把响应与前端
     * 渲染一起拖垮，所以只展开到 {@value #WORKSPACE_MAX_DEPTH} 层、每层最多
     * {@value #WORKSPACE_MAX_ENTRIES} 条。</p>
     *
     * @param dir   目录
     * @param depth 当前深度（0 起）
     * @return 节点数组；目录不可读时返回空数组
     */
    private List<Map<String, Object>> listWorkspace(java.nio.file.Path dir, int depth) {
        List<Map<String, Object>> nodes = new ArrayList<>();
        if (depth > WORKSPACE_MAX_DEPTH || !java.nio.file.Files.isDirectory(dir)) {
            return nodes;
        }
        try (java.util.stream.Stream<java.nio.file.Path> children = java.nio.file.Files.list(dir)) {
            List<java.nio.file.Path> sorted = children
                    .sorted(Comparator.comparing((java.nio.file.Path p) -> !java.nio.file.Files.isDirectory(p))
                            .thenComparing(p -> p.getFileName().toString()))
                    .limit(WORKSPACE_MAX_ENTRIES)
                    .toList();
            for (java.nio.file.Path child : sorted) {
                Map<String, Object> node = new HashMap<>();
                node.put("name", child.getFileName().toString());
                node.put("path", child.toString());
                boolean directory = java.nio.file.Files.isDirectory(child);
                node.put("isDirectory", directory);
                if (!directory) {
                    try {
                        node.put("size", java.nio.file.Files.size(child));
                    } catch (Exception ignored) {
                        node.put("size", 0L);
                    }
                }
                if (directory) {
                    node.put("children", listWorkspace(child, depth + 1));
                }
                nodes.add(node);
            }
        } catch (Exception e) {
            log.warn("[Session] 列目录失败 dir={}: {}", dir, e.getMessage());
        }
        return nodes;
    }

    /**
     * 把对象转成 long，null / 非法值按 0。
     *
     * @param value 原值
     * @return long 值
     */
    private static long longOf(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return value == null ? 0L : Long.parseLong(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /**
     * 从此处分叉：把源会话中「分叉点及其之前」的消息复制到一个新会话。
     *
     * <p>前端会话里「从此处分叉」按钮调用本接口（此前它调的就是这个路径，但后端没有实现，
     * 点一次必然 404 并提示「分叉失败」）。复制按消息主键升序进行，并把助手消息的
     * {@code parentId} 重新指向**复制出来的**那条用户消息，这样新会话里的
     * 「多版本」归属关系依然正确；不直接沿用源消息 id，避免跨会话串父。</p>
     *
     * @param id   源会话标识
     * @param body 请求体，含分叉点消息 id（字段名 messageId）
     * @return 新会话 id、标题与复制条数
     */
    @PostMapping("/{id}/fork")
    public ReturnResult<Map<String, Object>> fork(@PathVariable("id") String id,
                                                  @RequestBody Map<String, Object> body) {
        Long sourceSessionId = parseLongOrNull(id);
        SysAiSession source = sourceSessionId == null ? null : sessionMapper.selectById(sourceSessionId);
        if (source == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "会话不存在");
        }
        // 读出口校验：他人会话不可分叉（管理员只读放行在外层统一处理）
        requireReadableSession(sourceSessionId);

        Long anchorId = parseLongOrNull(String.valueOf(body.get("messageId")));
        if (anchorId == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "缺少分叉点消息 id");
        }

        LambdaQueryWrapper<SysAiMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysAiMessage::getSysAiSessionId, sourceSessionId);
        wrapper.orderByAsc(SysAiMessage::getId);
        List<SysAiMessage> all = messageMapper.selectList(wrapper);

        // 截取到分叉点（含）为止；没截到说明分叉点不属于本会话
        List<SysAiMessage> picked = new ArrayList<>();
        for (SysAiMessage m : all) {
            picked.add(m);
            if (anchorId.equals(m.getId())) {
                break;
            }
        }
        if (picked.isEmpty() || !anchorId.equals(picked.get(picked.size() - 1).getId())) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "分叉点消息不存在");
        }

        SysAiSession target = new SysAiSession();
        String sourceTitle = source.getTitle() == null ? "" : source.getTitle();
        target.setTitle(sourceTitle + "-分叉");
        target.setSysAiModuleCode(source.getSysAiModuleCode());
        target.setModelId(source.getModelId());
        target.setCharacterId(source.getCharacterId());
        target.setMcpServerIds(source.getMcpServerIds());
        target.setSettings(source.getSettings());
        target.setSysUserId(AiUserContext.requireUserId());
        target.setCreateTime(LocalDateTime.now());
        target.setUpdateTime(LocalDateTime.now());
        target.setStatus(1);
        target.setIsArchived(false);
        sessionMapper.insert(target);

        Long lastUserId = null;
        int copied = 0;
        for (SysAiMessage src : picked) {
            SysAiMessage copy = new SysAiMessage();
            copy.setSysAiSessionId(target.getId());
            copy.setRole(src.getRole());
            copy.setType(src.getType());
            copy.setContent(src.getContent());
            copy.setAttachment(src.getAttachment());
            copy.setModelName(src.getModelName());
            copy.setPromptTokens(src.getPromptTokens());
            copy.setCompletionTokens(src.getCompletionTokens());
            copy.setTotalTokens(src.getTotalTokens());
            copy.setUserId(AiUserContext.requireUserId());
            copy.setCreateTime(src.getCreateTime());
            copy.setUpdateTime(LocalDateTime.now());
            // 用户消息不带父；助手消息挂到本次复制出来的那条用户消息上
            copy.setParentId("user".equals(src.getRole()) ? null : lastUserId);
            messageMapper.insert(copy);
            if ("user".equals(src.getRole())) {
                lastUserId = copy.getId();
            }
            copied++;
        }

        Map<String, Object> result = new HashMap<>();
        result.put("sessionId", String.valueOf(target.getId()));
        result.put("title", target.getTitle());
        result.put("copiedMessages", copied);
        log.info("[Session] 分叉会话: 源={}, 新={}, 复制消息={}", sourceSessionId, target.getId(), copied);
        return ReturnResult.ok(result);
    }

    /**
     * 解析角色 id 字符串为 Long，非数字 / 空值返回 null。
     * @param raw 原始字符串
     * @return Long 或 null
     */
    private static Long parseLongOrNull(String raw) {
        if (raw == null) {
            return null;
        }
        String v = raw.trim();
        if (v.isEmpty() || "null".equalsIgnoreCase(v) || "undefined".equalsIgnoreCase(v)) {
            return null;
        }
        try {
            return Long.parseLong(v);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 将逗号分隔的 MCP 服务器ID字符串解析为列表。
     * @param raw 原始字符串
     * @return ID 列表，永不为 null
     */
    private static List<String> parseIdList(String raw) {
        List<String> result = new ArrayList<>();
        if (raw == null) {
            return result;
        }
        for (String part : raw.split(",")) {
            String id = part.trim();
            if (!id.isEmpty() && !result.contains(id)) {
                result.add(id);
            }
        }
        return result;
    }

    /**
     * 将前端传入的 MCP 服务器ID（列表或逗号字符串）归一化为逗号分隔字符串；空集合归一化为 null。
     * @param raw 原始值
     * @return 逗号分隔字符串或 null
     */
    private static String normalizeIdList(Object raw) {
        List<String> values = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object o : list) {
                addId(values, o == null ? null : String.valueOf(o));
            }
        } else if (raw != null) {
            for (String part : String.valueOf(raw).split(",")) {
                addId(values, part);
            }
        }
        return values.isEmpty() ? null : String.join(",", values);
    }

    /**
     * 添加一个去空白、去重的 ID。
     * @param values 目标列表
     * @param part 原始片段
     */
    private static void addId(List<String> values, String part) {
        if (part == null) {
            return;
        }
        String id = part.trim();
        if (!id.isEmpty() && !"null".equalsIgnoreCase(id) && !"undefined".equalsIgnoreCase(id)
                && !values.contains(id)) {
            values.add(id);
        }
    }

    /**
     * 删除单条消息
     * @param messageId 消息ID，不允许为 null
     * @return Return结果 对象
     */
    @DeleteMapping("/messages/{messageId}")
    public ReturnResult<Map<String, Object>> deleteMessage(@PathVariable("messageId") String messageId) {
        Long mid = Long.parseLong(messageId);
        
        // 先查询这条消息
        SysAiMessage message = messageMapper.selectById(mid);
        if (message != null) {
 // 写出口校验：消息随所属会话归属
            requireWritableSession(message.getSysAiSessionId());
            // 如果是用户消息，同时删除对应的 AI 回复（通过 parent_id 关联）
            if ("user".equals(message.getRole())) {
                LambdaQueryWrapper<SysAiMessage> assistantWrapper = new LambdaQueryWrapper<>();
                assistantWrapper.eq(SysAiMessage::getParentId, mid);
                messageMapper.delete(assistantWrapper);
            }
            // 删除消息本身
            messageMapper.deleteById(mid);
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        return ReturnResult.ok(result);
    }

    /**
     * 更新消息内容
     * @param messageId 消息ID，不允许为 null
     * @param body 请求体，包含 content 字段
     * @return ReturnResult 对象
     */
    @PutMapping("/messages/{messageId}")
    public ReturnResult<Map<String, Object>> updateMessage(
            @PathVariable("messageId") String messageId,
            @RequestBody Map<String, Object> body) {
        Long mid = Long.parseLong(messageId);
        String content = (String) body.get("content");
        SysAiMessage message = messageMapper.selectById(mid);
        if (message != null) {
 // 写出口校验：消息随所属会话归属
            requireWritableSession(message.getSysAiSessionId());
            message.setContent(content);
            messageMapper.updateById(message);
        }
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        return ReturnResult.ok(result);
    }

    /**
     * 删除会话
     * @param id ID，不允许为 null
     * @return Return结果 对象
     */
    @DeleteMapping("/{id}")
    public ReturnResult<Map<String, Object>> delete(@PathVariable("id") String id) {
        Long sessionId = Long.parseLong(id);
 // 写出口校验：仅归属人可删，管理员对他人数据只读
        requireWritableSession(sessionId);
        
        // 删除会话的所有消息
        LambdaQueryWrapper<SysAiMessage> messageWrapper = new LambdaQueryWrapper<>();
        messageWrapper.eq(SysAiMessage::getSysAiSessionId, sessionId);
        messageMapper.delete(messageWrapper);
        
        // 删除会话
        sessionMapper.deleteById(sessionId);
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        return ReturnResult.ok(result);
    }

    /**
     * 批量删除会话。
     *
     * @param body 请求体，须包含 ids 列表（字符串形式的会话 id）
     * @return 删除结果，含 success 与 message 字段
     */
    @DeleteMapping("/batch")
    public ReturnResult<Map<String, Object>> batchDelete(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<String> ids = (List<String>) body.get("ids");
        
        if (ids == null || ids.isEmpty()) {
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "ids is required");
            return ReturnResult.ok(result);
        }
        
 // 先整批校验归属，再执行删除：避免中途拒绝导致部分删除
        List<Long> sessionIds = new ArrayList<>();
        for (String id : ids) {
            Long sessionId = Long.parseLong(id);
 // 写出口校验：仅归属人可删，管理员对他人数据只读
            requireWritableSession(sessionId);
            sessionIds.add(sessionId);
        }

        int deletedCount = 0;
        for (Long sessionId : sessionIds) {
            // 删除会话的所有消息
            LambdaQueryWrapper<SysAiMessage> messageWrapper = new LambdaQueryWrapper<>();
            messageWrapper.eq(SysAiMessage::getSysAiSessionId, sessionId);
            messageMapper.delete(messageWrapper);
            
            // 删除会话
            sessionMapper.deleteById(sessionId);
            deletedCount++;
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("deletedCount", deletedCount);
        return ReturnResult.ok(result);
    }

    /**
     * 读出口归属校验。
     *
     * @param sessionId 会话 标识
     * @return 校验通过的会话
     */
    private SysAiSession requireReadableSession(Long sessionId) {
        SysAiSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "会话不存在");
        }
 // 管理员只读全量
        if (AiUserContext.isAdmin()) {
            return session;
        }
        if (!AiUserContext.requireUserId().equals(session.getSysUserId())) {
            throw new ApiException(ApiErrorCode.FORBIDDEN, "无权访问他人会话");
        }
        return session;
    }

    /**
     * 写出口归属校验：只认归属人本身，管理员对他人数据只读。
     *
     * @param sessionId 会话 标识
     * @return 校验通过的会话
     */
    private SysAiSession requireWritableSession(Long sessionId) {
        SysAiSession session = requireReadableSession(sessionId);
        if (AiUserContext.isReadonly()) {
            throw new ApiException(ApiErrorCode.FORBIDDEN, "只读账号不允许执行写操作");
        }
        if (!AiUserContext.requireUserId().equals(session.getSysUserId())) {
            throw new ApiException(ApiErrorCode.FORBIDDEN, "管理员对他人数据只读，不可修改或删除");
        }
        return session;
    }
}
