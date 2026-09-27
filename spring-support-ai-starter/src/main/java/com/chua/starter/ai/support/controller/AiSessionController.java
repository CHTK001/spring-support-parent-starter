package com.chua.starter.ai.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
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
