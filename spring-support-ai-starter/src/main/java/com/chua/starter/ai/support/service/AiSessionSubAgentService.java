package com.chua.starter.ai.support.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.starter.ai.support.entity.SysAiSession;
import com.chua.starter.ai.support.entity.SysAiSessionSubAgent;
import com.chua.starter.ai.support.manage.common.ApiErrorCode;
import com.chua.starter.ai.support.manage.common.ApiException;
import com.chua.starter.ai.support.manage.mapper.SysAiAgentMapper;
import com.chua.starter.ai.support.manage.store.SysAiAgent;
import com.chua.starter.ai.support.mapper.SysAiSessionMapper;
import com.chua.starter.ai.support.mapper.SysAiSessionSubAgentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 会话子代理运行记录服务（主/子会话绑定持久化 + 生命周期事件广播）。
 *
 * <p>职责（G4）：</p>
 * <ul>
 *   <li>持久化：主会话下每次 spawn / 手动 invoke 子代理，都在
 *       {@code sys_ai_session_sub_agent} 落一行，刷新 / 跨标签页后可恢复；</li>
 *   <li>广播：创建 / 关闭 / 状态流转分别发布 {@code sub_agent_create} /
 *       {@code sub_agent_closed} / {@code sub_agent_status}（依赖 G1 事件总线，
 *       事件 {@code sessionId} 取子会话记录标识，与前端 Tab id 对齐）；</li>
 *   <li>鉴权：所有操作要求「当前登录用户 == 主会话归属人 == 记录归属人」。</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.52
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiSessionSubAgentService {

    /**
     * 状态：运行中（已 spawn / invoke 开始，尚未结束）
     */
    public static final String STATUS_RUNNING = "RUNNING";

    /**
     * 状态：正常完成
     */
    public static final String STATUS_COMPLETED = "COMPLETED";

    /**
     * 状态：执行失败
     */
    public static final String STATUS_FAILED = "FAILED";

    /**
     * 状态：已关闭（前端主动关闭 / 上层收流）
     */
    public static final String STATUS_CLOSED = "CLOSED";

    /**
     * 合法状态集合（updateStatus 入参校验）
     */
    private static final Set<String> VALID_STATUSES = Set.of(STATUS_RUNNING, STATUS_COMPLETED, STATUS_FAILED, STATUS_CLOSED);

    /**
     * 时间输出格式（ISO-8601，与前端 lastActive 等格式化器兼容）
     */
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    /**
     * 子代理运行记录映射器
     */
    private final SysAiSessionSubAgentMapper subAgentMapper;

    /**
     * 会话映射器（归属校验）
     */
    private final SysAiSessionMapper sessionMapper;

    /**
     * Agent 映射器（spawn/invoke 时取子 Agent 名称与主 Agent 快照）
     */
    private final SysAiAgentMapper agentMapper;

    /**
     * 会话事件广播器（G1）
     */
    private final AiSessionEventBroadcaster broadcaster;

    /**
     * 主会话下的子代理运行记录列表（时间正序，供 Tab 恢复与侧边栏展示）。
     *
     * @param sessionId 主会话标识
     * @param userId    当前登录用户
     * @return {items:[...], total}
     */
    public Map<String, Object> listBySession(String sessionId, String userId) {
        SysAiSession session = requireOwnedSession(sessionId, userId);
        List<SysAiSessionSubAgent> rows = subAgentMapper.selectList(new LambdaQueryWrapper<SysAiSessionSubAgent>()
                .eq(SysAiSessionSubAgent::getSessionId, session.getId())
                .orderByAsc(SysAiSessionSubAgent::getId));
        List<Map<String, Object>> items = new ArrayList<>(rows.size());
        for (SysAiSessionSubAgent row : rows) {
            items.add(toResponse(row));
        }
        Map<String, Object> result = new LinkedHashMap<>(4);
        result.put("items", items);
        result.put("total", items.size());
        return result;
    }

    /**
     * 主会话关联的智能体列表（主 + 子，去重），供侧边栏「子会话 / 智能体」展示。
     *
     * <p>数据全部来自 {@code sys_ai_session_sub_agent} 运行记录：子 Agent 按
     * {@code agent_id} 去重（状态取该 Agent 最近一次记录）；主 Agent 取记录里的
     * {@code master_agent_id} 快照（同会话主 Agent 固定）。无记录时返回空列表，
     * 展开区只显示元信息与操作。</p>
     *
     * @param sessionId 主会话标识
     * @param userId    当前登录用户
     * @return [{agent_id, name, role: master|sub, status}]，主 Agent 在前
     */
    public List<Map<String, Object>> listAgents(String sessionId, String userId) {
        SysAiSession session = requireOwnedSession(sessionId, userId);
        List<SysAiSessionSubAgent> rows = subAgentMapper.selectList(new LambdaQueryWrapper<SysAiSessionSubAgent>()
                .eq(SysAiSessionSubAgent::getSessionId, session.getId())
                .orderByAsc(SysAiSessionSubAgent::getId));
        List<Map<String, Object>> items = new ArrayList<>();
        if (rows.isEmpty()) {
            return items;
        }
        Set<String> seen = new LinkedHashSet<>();
        for (SysAiSessionSubAgent row : rows) {
            String agentId = row.getAgentId();
            if (agentId == null || agentId.isBlank() || !seen.add(agentId)) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>(8);
            item.put("agent_id", agentId);
            item.put("name", row.getAgentName());
            item.put("role", "sub");
            item.put("status", latestStatusOf(rows, agentId));
            items.add(item);
        }
        String masterId = rows.stream()
                .map(SysAiSessionSubAgent::getMasterAgentId)
                .filter(Objects::nonNull)
                .filter(id -> !id.isBlank())
                .findFirst()
                .orElse(null);
        if (masterId != null) {
            Map<String, Object> master = new LinkedHashMap<>(8);
            master.put("agent_id", masterId);
            master.put("name", agentNameOf(masterId, "主智能体"));
            master.put("role", "master");
            master.put("status", "idle");
            items.add(0, master);
        }
        return items;
    }

    /**
     * 指定子 Agent 在记录集中最近一次的状态（rows 已按 id 正序）。
     *
     * @param rows    运行记录（时间正序）
     * @param agentId 子 Agent 标识
     * @return 最近状态；无记录时 RUNNING
     */
    private String latestStatusOf(List<SysAiSessionSubAgent> rows, String agentId) {
        String status = STATUS_RUNNING;
        for (SysAiSessionSubAgent row : rows) {
            if (agentId.equals(row.getAgentId()) && row.getStatus() != null) {
                status = row.getStatus();
            }
        }
        return status;
    }

    /**
     * 查询 Agent 名称，查不到时回落默认名。
     *
     * @param agentId  Agent 标识
     * @param fallback 默认名
     * @return Agent 名称
     */
    private String agentNameOf(String agentId, String fallback) {
        SysAiAgent agent = agentMapper.selectById(agentId);
        return agent == null || agent.getName() == null || agent.getName().isBlank() ? fallback : agent.getName();
    }

    /**
     * 为主会话 spawn 一个子代理会话（不执行，只建绑定并广播创建事件）。
     *
     * @param sessionId 主会话标识
     * @param body      {agentId: 必填, name?: 展示名覆盖, input?: 初始输入}
     * @param userId    当前登录用户
     * @return 新建的运行记录
     */
    public Map<String, Object> spawn(String sessionId, Map<String, Object> body, String userId) {
        String agentId = body.get("agentId") == null ? "" : String.valueOf(body.get("agentId")).trim();
        if (agentId.isBlank()) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "agentId 不能为空");
        }
        String name = body.get("name") == null ? null : String.valueOf(body.get("name")).trim();
        String input = body.get("input") == null ? "" : String.valueOf(body.get("input"));
        SysAiSession session = requireOwnedSession(sessionId, userId);
        SysAiAgent agent = requireAgent(agentId);
        SysAiSessionSubAgent row = insertRunningRow(session, agent, input, userId);
        publishCreate(row);
        log.info("[SubAgentSession] spawn 主会话={}, subSessionId={}, agentId={}, user={}",
                session.getId(), row.getId(), agentId, userId);
        return toResponse(row);
    }

    /**
     * 关闭一条子代理运行记录（广播 sub_agent_closed，前端据此摘除对应 Tab）。
     *
     * @param sessionId    主会话标识
     * @param subSessionId 子会话记录标识
     * @param reason       关闭原因（completed/failed/canceled/...，仅透传到事件）
     * @param userId       当前登录用户
     * @return 关闭后的运行记录
     */
    public Map<String, Object> close(String sessionId, String subSessionId, String reason, String userId) {
        SysAiSession session = requireOwnedSession(sessionId, userId);
        SysAiSessionSubAgent row = requireOwnedRow(subSessionId, userId);
        if (!session.getId().equals(row.getSessionId())) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "子会话不属于该主会话");
        }
        row.setStatus(STATUS_CLOSED);
        if (reason != null && !reason.isBlank()) {
            row.setError(reason);
        }
        subAgentMapper.updateById(row);
        Map<String, Object> payload = new LinkedHashMap<>(4);
        payload.put("subSessionId", String.valueOf(row.getId()));
        payload.put("status", STATUS_CLOSED);
        payload.put("reason", reason == null ? "" : reason);
        broadcaster.publish(AiSessionEventBroadcaster.TYPE_SUB_AGENT_CLOSED,
                userId, String.valueOf(row.getId()), payload);
        log.info("[SubAgentSession] close 主会话={}, subSessionId={}, reason={}", session.getId(), row.getId(), reason);
        return toResponse(row);
    }

    /**
     * 更新子代理运行状态（广播 sub_agent_status）。
     *
     * @param subSessionId 子会话记录标识
     * @param status       目标状态（RUNNING/COMPLETED/FAILED/CLOSED）
     * @param userId       当前登录用户
     * @return 更新后的运行记录
     */
    public Map<String, Object> updateStatus(String subSessionId, String status, String userId) {
        String target = status == null ? "" : status.trim().toUpperCase();
        if (!VALID_STATUSES.contains(target)) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "非法状态: " + status);
        }
        SysAiSessionSubAgent row = requireOwnedRow(subSessionId, userId);
        row.setStatus(target);
        subAgentMapper.updateById(row);
        Map<String, Object> payload = new LinkedHashMap<>(4);
        payload.put("subSessionId", String.valueOf(row.getId()));
        payload.put("status", target);
        broadcaster.publish(AiSessionEventBroadcaster.TYPE_SUB_AGENT_STATUS,
                userId, String.valueOf(row.getId()), payload);
        return toResponse(row);
    }

    /**
     * invoke 生命周期·开始：为一次手动/自动子代理调用建 RUNNING 记录并广播创建事件。
     *
     * <p>由 {@code AiSessionCapabilityController#invokeSubAgent} 与后续 Agent 运行时
     * 的子代理派发路径调用；记录落库后即使调用失败也保留现场（FAILED + error）。</p>
     *
     * @param sessionId 主会话标识
     * @param agentId   子 Agent 标识
     * @param input     调用输入
     * @param userId    当前登录用户
     * @return 新建的运行记录
     */
    public SysAiSessionSubAgent beginInvoke(String sessionId, String agentId, String input, String userId) {
        SysAiSession session = requireOwnedSession(sessionId, userId);
        SysAiAgent agent = requireAgent(agentId);
        SysAiSessionSubAgent row = insertRunningRow(session, agent, input, userId);
        publishCreate(row);
        log.info("[SubAgentSession] invoke begin 主会话={}, subSessionId={}, agentId={}", session.getId(), row.getId(), agentId);
        return row;
    }

    /**
     * invoke 生命周期·结束：回填输出 / 失败原因并广播关闭事件。
     *
     * @param subSessionId 子会话记录标识
     * @param userId       归属用户（记录创建者）
     * @param output       调用输出（失败时可为空）
     * @param error        失败原因（成功传 null）
     */
    public void finishInvoke(String subSessionId, String userId, String output, String error) {
        SysAiSessionSubAgent row = requireOwnedRow(subSessionId, userId);
        boolean failed = error != null && !error.isBlank();
        row.setStatus(failed ? STATUS_FAILED : STATUS_COMPLETED);
        row.setOutput(output == null ? "" : output);
        if (failed) {
            row.setError(error);
        }
        subAgentMapper.updateById(row);
        Map<String, Object> payload = new LinkedHashMap<>(6);
        payload.put("subSessionId", String.valueOf(row.getId()));
        payload.put("status", row.getStatus());
        payload.put("reason", failed ? "failed" : "completed");
        if (failed) {
            payload.put("error", error);
        }
        broadcaster.publish(AiSessionEventBroadcaster.TYPE_SUB_AGENT_CLOSED,
                userId, String.valueOf(row.getId()), payload);
        log.info("[SubAgentSession] invoke finish 主会话={}, subSessionId={}, status={}", row.getSessionId(), row.getId(), row.getStatus());
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
     * 归属校验：运行记录必须存在且属于当前登录用户。
     *
     * @param subSessionId 子会话记录标识
     * @param userId       当前登录用户
     * @return 校验通过的记录
     */
    private SysAiSessionSubAgent requireOwnedRow(String subSessionId, String userId) {
        Long id;
        try {
            id = Long.parseLong(subSessionId);
        } catch (NumberFormatException e) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "子会话不存在");
        }
        SysAiSessionSubAgent row = subAgentMapper.selectById(id);
        if (row == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "子会话不存在");
        }
        if (!userId.equals(row.getSysUserId())) {
            throw new ApiException(ApiErrorCode.FORBIDDEN, "无权访问该子会话");
        }
        return row;
    }

    /**
     * 校验子 Agent 存在。
     *
     * @param agentId 子 Agent 标识
     * @return Agent 实体
     */
    private SysAiAgent requireAgent(String agentId) {
        SysAiAgent agent = agentMapper.selectById(agentId);
        if (agent == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "子 Agent 不存在: " + agentId);
        }
        return agent;
    }

    /**
     * 建 RUNNING 记录（名称 / 主 Agent 取库内快照）。
     *
     * @param session 主会话
     * @param agent   子 Agent
     * @param input   调用输入
     * @param userId  归属用户
     * @return 已落库的记录
     */
    private SysAiSessionSubAgent insertRunningRow(SysAiSession session, SysAiAgent agent, String input, String userId) {
        SysAiSessionSubAgent row = new SysAiSessionSubAgent();
        row.setSessionId(session.getId());
        row.setAgentId(agent.getAgentId());
        row.setAgentName(agent.getName());
        row.setMasterAgentId(agent.getParentId());
        row.setStatus(STATUS_RUNNING);
        row.setInput(input == null ? "" : input);
        row.setSysUserId(userId);
        row.setCreateTime(LocalDateTime.now());
        row.setUpdateTime(LocalDateTime.now());
        subAgentMapper.insert(row);
        return row;
    }

    /**
     * 广播子代理创建事件（事件 sessionId 取子会话记录标识，与前端 Tab id 对齐）。
     *
     * @param row 已落库的运行记录
     */
    private void publishCreate(SysAiSessionSubAgent row) {
        Map<String, Object> sessionRef = new LinkedHashMap<>(8);
        sessionRef.put("id", String.valueOf(row.getId()));
        sessionRef.put("title", row.getAgentName());
        sessionRef.put("type", "sub_agent");
        sessionRef.put("status", row.getStatus());
        Map<String, Object> payload = new LinkedHashMap<>(8);
        payload.put("subSessionId", String.valueOf(row.getId()));
        payload.put("name", row.getAgentName());
        payload.put("agentId", row.getAgentId());
        payload.put("masterId", row.getMasterAgentId());
        payload.put("status", row.getStatus());
        payload.put("session", sessionRef);
        broadcaster.publish(AiSessionEventBroadcaster.TYPE_SUB_AGENT_CREATE,
                row.getSysUserId(), String.valueOf(row.getId()), payload);
    }

    /**
     * 实体转响应（字段名对齐前端契约：sidebar 的 {id,title,updatedAt}、
     * ChatPage Tab 恢复的 {id,title,status,isStreaming} 等）。
     *
     * @param row 运行记录
     * @return 响应 map
     */
    private Map<String, Object> toResponse(SysAiSessionSubAgent row) {
        Map<String, Object> item = new LinkedHashMap<>(16);
        item.put("id", String.valueOf(row.getId()));
        item.put("sessionId", row.getSessionId() == null ? null : String.valueOf(row.getSessionId()));
        item.put("agentId", row.getAgentId());
        item.put("agentName", row.getAgentName());
        item.put("masterAgentId", row.getMasterAgentId());
        item.put("status", row.getStatus());
        item.put("title", row.getAgentName());
        item.put("input", row.getInput());
        item.put("output", row.getOutput());
        item.put("error", row.getError());
        item.put("isStreaming", STATUS_RUNNING.equals(row.getStatus()));
        item.put("createdAt", row.getCreateTime() == null ? null : TIME_FORMAT.format(row.getCreateTime()));
        item.put("updatedAt", row.getUpdateTime() == null ? null : TIME_FORMAT.format(row.getUpdateTime()));
        return item;
    }
}
