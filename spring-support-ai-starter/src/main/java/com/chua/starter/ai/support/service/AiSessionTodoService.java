// [P3C A20 豁免] A20/A30/A31: 含日志字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.ai.support.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.starter.ai.support.entity.SysAiSession;
import com.chua.starter.ai.support.entity.SysAiSessionTodo;
import com.chua.starter.ai.support.manage.common.ApiErrorCode;
import com.chua.starter.ai.support.manage.common.ApiException;
import com.chua.starter.ai.support.mapper.SysAiSessionMapper;
import com.chua.starter.ai.support.mapper.SysAiSessionTodoMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 会话待办服务（opencode {@code todowrite} 镜像持久化 + 实时事件广播）。
 *
 * <p>职责（G2）：</p>
 * <ul>
 *   <li>镜像：Agent 运行时每次调用 {@code todowrite}，把入参的**全量**待办列表
 *       整表替换进 {@code sys_ai_session_todo}（与 opencode 的整表替换语义一致）；
 *       工具入参经 {@code AgentChatClient} 的工具观察者钩子透传到这里；</li>
 *   <li>查询：{@code GET /v2/sessions/{id}/todo} 按 sort_no 恢复列表，
 *       前端「待办」面板刷新 / 重开标签页后仍可显示；</li>
 *   <li>广播：每次镜像落库后发布 {@code todo_updated}（G1 事件总线），
 *       该用户的所有标签页实时刷新；载荷只带 count，明细由前端按需回查；</li>
 *   <li>鉴权：查询要求「当前登录用户 == 主会话归属人」；镜像写入由后端
 *       Agent 链路发起（写入前已按会话归属构建），不暴露给前端写。</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.52
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiSessionTodoService {

    /**
     * opencode 待办写入工具名（工具观察者按它过滤）
     */
    public static final String TOOL_NAME_TODOWRITE = "todowrite";

    /**
     * 待办状态：待办
     */
    public static final String STATUS_PENDING = "pending";

    /**
     * 待办状态：进行中
     */
    public static final String STATUS_IN_PROGRESS = "in_progress";

    /**
     * 待办状态：已完成
     */
    public static final String STATUS_COMPLETED = "completed";

    /**
     * 待办状态：已取消
     */
    public static final String STATUS_CANCELLED = "cancelled";

    /**
     * 合法状态集合（运行时给了其它值时回落 pending）
     */
    private static final Set<String> VALID_STATUSES =
            Set.of(STATUS_PENDING, STATUS_IN_PROGRESS, STATUS_COMPLETED, STATUS_CANCELLED);

    /**
     * 合法优先级集合（其它值置空）
     */
    private static final Set<String> VALID_PRIORITIES = Set.of("high", "medium", "low");

    /**
     * 单次镜像的待办条数上限（防御异常超大列表拖垮落库）
     */
    private static final int MAX_TODO_ITEMS = 200;

    /**
     * 待办镜像映射器
     */
    private final SysAiSessionTodoMapper todoMapper;

    /**
     * 会话映射器（归属校验）
     */
    private final SysAiSessionMapper sessionMapper;

    /**
     * 会话事件广播器（G1）
     */
    private final AiSessionEventBroadcaster broadcaster;

    /**
     * 项目统一 ObjectMapper（解析 Agent 工具入参 JSON）
     */
    private final ObjectMapper objectMapper;

    /**
     * 解析后的单条待办（工具入参 → 镜像行的中间载体）
     *
     * @param todoId   运行时待办项标识，可为空
     * @param content  待办内容（非空）
     * @param status   状态（已归一到词表）
     * @param priority 优先级，可为空
     */
    private record ParsedTodo(String todoId, String content, String status, String priority) {
    }

    /**
     * 会话待办列表（按 Agent 下发顺序）。
     *
     * @param sessionId 主会话标识
     * @param userId    当前登录用户
     * @return {items:[{id,todoId,content,status,priority,sortNo}], total}
     */
    public Map<String, Object> listBySession(String sessionId, String userId) {
        SysAiSession session = requireOwnedSession(sessionId, userId);
        List<SysAiSessionTodo> rows = todoMapper.selectList(new LambdaQueryWrapper<SysAiSessionTodo>()
                .eq(SysAiSessionTodo::getSessionId, session.getId())
                .orderByAsc(SysAiSessionTodo::getSortNo)
                .orderByAsc(SysAiSessionTodo::getId));
        List<Map<String, Object>> items = new ArrayList<>(rows.size());
        for (SysAiSessionTodo row : rows) {
            items.add(toResponse(row));
        }
        Map<String, Object> result = new LinkedHashMap<>(4);
        result.put("items", items);
        result.put("total", items.size());
        return result;
    }

    /**
     * 镜像 Agent 运行时的一次 {@code todowrite}：整表替换该会话的待办并广播。
     *
     * <p>由 {@code AgentChatClient} 的工具观察者在收到 {@code TOOL_START}
     * （toolName=todowrite）时回调，可能发生在 Reactor 流线程上；任何异常都
     * 只记日志不影响对话流（调用方已兜底捕获）。{@code toolInput} 形如
     * {@code {"todos":[{"id","content","status","priority"}...]}}。</p>
     *
     * @param sessionId 主会话数字标识
     * @param userId    归属用户（构建会话 Agent 时已捕获）
     * @param toolInput 工具入参 JSON 字符串
     */
    @Transactional
    public void replaceFromAgent(Long sessionId, String userId, String toolInput) {
        List<ParsedTodo> todos = parseTodos(toolInput);
        if (todos == null) {
            // 不是可识别的 todowrite 入参（空/截断/形不符），静默忽略，不动画
            return;
        }
        todoMapper.delete(new LambdaQueryWrapper<SysAiSessionTodo>()
                .eq(SysAiSessionTodo::getSessionId, sessionId));
        int sort = 0;
        for (ParsedTodo todo : todos) {
            SysAiSessionTodo row = new SysAiSessionTodo();
            row.setSessionId(sessionId);
            row.setTodoId(todo.todoId());
            row.setContent(todo.content());
            row.setStatus(todo.status());
            row.setPriority(todo.priority());
            row.setSortNo(sort++);
            row.setSysUserId(userId);
            row.setCreateTime(LocalDateTime.now());
            row.setUpdateTime(LocalDateTime.now());
            todoMapper.insert(row);
        }
        Map<String, Object> payload = new LinkedHashMap<>(4);
        payload.put("count", todos.size());
        broadcaster.publish(AiSessionEventBroadcaster.TYPE_TODO_UPDATED,
                userId, String.valueOf(sessionId), payload);
        log.info("[SessionTodo] 镜像待办 sessionId={} count={}", sessionId, todos.size());
    }

    /**
     * 解析 todowrite 工具入参为待办列表。
     *
     * @param toolInput 工具入参 JSON 字符串
     * @return 待办列表；入参不是可识别的 todowrite 形态时返回 {@code null}
     *（空数组是合法形态，返回空列表=清空）
     */
    private List<ParsedTodo> parseTodos(String toolInput) {
        if (toolInput == null || toolInput.isBlank()) {
            return null;
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(toolInput);
        } catch (Exception e) {
            log.debug("[SessionTodo] todowrite 入参不是合法 JSON（可能被截断），忽略: {}", e.getMessage());
            return null;
        }
        JsonNode todos = root == null ? null : root.get("todos");
        if (todos == null || !todos.isArray()) {
            return null;
        }
        List<ParsedTodo> parsed = new ArrayList<>();
        int limit = Math.min(todos.size(), MAX_TODO_ITEMS);
        for (int i = 0; i < limit; i++) {
            JsonNode node = todos.get(i);
            if (node == null || !node.isObject()) {
                continue;
            }
            String content = node.path("content").asText("").trim();
            if (content.isEmpty()) {
                continue;
            }
            String status = node.path("status").asText(STATUS_PENDING).trim();
            if (!VALID_STATUSES.contains(status)) {
                status = STATUS_PENDING;
            }
            String priority = node.path("priority").asText("").trim();
            if (!VALID_PRIORITIES.contains(priority)) {
                priority = null;
            }
            String todoId = node.path("id").asText("").trim();
            parsed.add(new ParsedTodo(todoId.isEmpty() ? null : todoId, content, status, priority));
        }
        return parsed;
    }

    /**
     * 归属校验：主会话必须存在且属于当前登录用户。
     *
     * @param sessionId 主会话标识
     * @param userId    当前登录用户
     * @return 校验通过的会话
     */
    private SysAiSession requireOwnedSession(String sessionId, String userId) {
        Long id;
        try {
            id = Long.parseLong(sessionId);
        } catch (NumberFormatException e) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "会话不存在");
        }
        SysAiSession session = sessionMapper.selectById(id);
        if (session == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "会话不存在");
        }
        if (!userId.equals(session.getSysUserId())) {
            throw new ApiException(ApiErrorCode.FORBIDDEN, "无权访问该会话");
        }
        return session;
    }

    /**
     * 实体转响应（字段名对齐前端契约：item 至少含 {content, status}）。
     *
     * @param row 待办记录
     * @return 响应 map
     */
    private Map<String, Object> toResponse(SysAiSessionTodo row) {
        Map<String, Object> item = new LinkedHashMap<>(12);
        item.put("id", String.valueOf(row.getId()));
        item.put("todoId", row.getTodoId());
        item.put("content", row.getContent());
        item.put("status", row.getStatus());
        item.put("priority", row.getPriority());
        item.put("sortNo", row.getSortNo());
        return item;
    }
}
