package com.chua.starter.ai.support.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import com.chua.common.support.utils.CollectionUtils;

/**
 * 会话级事件广播器（SSE，按用户广播）。
 *
 * <p>前端经 {@code GET /v2/events/sessions} 建立长连接后，会话生命周期
 * （创建/更新/删除）、聊天流状态（开始/结束）等事件从这里按用户推送到该用户的
 * 所有标签页。事件形状由前端契约钉死：
 * {@code {type, userId, sessionId, timestamp, payload?}}，{@code timestamp} 为
 * ISO-8601 字符串；{@code type} 取值见各 {@code TYPE_*} 常量，与前端
 * {@code SessionEventsService} 的 {@code SessionEventType} 词表一一对应。</p>
 *
 * <p>资源口径与 {@link AiWorkspaceEventStreamService} 一致：共享单个守护线程发心跳，
 * 连接断开/超时/发送失败都由回调注销；单用户连接数与全局连接数设上限，
 * 超了踢最久没发过消息的那条（前端 3 秒后自动重连）。心跳/事件一律以
 * {@code byte[]} 推送——传对象或字符串会被
 * {@code BinaryHttpMessageConverter} 在无请求上下文的线程上静默丢弃
 * （详见 workspace 事件流同类注释），这是实测踩过的坑。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Slf4j
@Service
public class AiSessionEventBroadcaster {

    /**
     * 会话已创建
     */
    public static final String TYPE_SESSION_CREATED = "session_created";

    /**
     * 会话已更新（标题/分组/模型/设置等）
     */
    public static final String TYPE_SESSION_UPDATED = "session_updated";

    /**
     * 会话已删除
     */
    public static final String TYPE_SESSION_DELETED = "session_deleted";

    /**
     * 会话已重命名（保留：细分场景用，通用更新走 session_updated）
     */
    public static final String TYPE_SESSION_RENAMED = "session_renamed";

    /**
     * 某会话的聊天流开始
     */
    public static final String TYPE_STREAM_STARTED = "stream_started";

    /**
     * 某会话的聊天流结束（payload.reason: completed/error/timeout/...）
     */
    public static final String TYPE_STREAM_FINISHED = "stream_finished";

    /**
     * 用户消息已落库（保留：消息列表跨标签同步时启用）
     */
    public static final String TYPE_USER_MESSAGE_CREATED = "user_message_created";

    /**
     * 子代理会话已创建
     */
    public static final String TYPE_SUB_AGENT_CREATE = "sub_agent_create";

    /**
     * 子代理会话已关闭
     */
    public static final String TYPE_SUB_AGENT_CLOSED = "sub_agent_closed";

    /**
     * 子代理会话状态流转（payload: {subSessionId, status}）
     */
    public static final String TYPE_SUB_AGENT_STATUS = "sub_agent_status";

    /**
     * Agent 运行状态 / 步骤更新（payload: {runId, sessionId, agentId, triggerType, mode,
     * status, tokensUsed, rounds, toolCalls, error?, lastStep?}）
     */
    public static final String TYPE_AGENT_RUN_UPDATE = "agent_run_update";

    /**
     * 会话待办已变更
     */
    public static final String TYPE_TODO_UPDATED = "todo_updated";

    /**
     * 会话目标已变更（新建 / 暂停 / 恢复 / 取消销毁；payload: {@code GoalView} 全量字段）
     */
    public static final String TYPE_GOAL_UPDATED = "goal_updated";

    /**
     * 待人工审批已创建（payload: 审批单全量字段，见 {@code AgentApprovalService#toPayload}）
     */
    public static final String TYPE_AGENT_APPROVAL_REQUIRED = "agent_approval_required";

    /**
     * 待人工审批已决议（批准 / 拒绝 / 过期；payload: 审批单全量字段 + decisionReason）
     */
    public static final String TYPE_AGENT_APPROVAL_RESOLVED = "agent_approval_resolved";

    /**
     * 心跳间隔：无业务事件时保活，同时探测死连接
     */
    private static final long HEARTBEAT_MILLIS = 30_000L;

    /**
     * 连接最长存活时间；到点由服务端收流，前端 3 秒后自动重连
     */
    private static final long EMITTER_TIMEOUT_MILLIS = 1_800_000L;

    /**
     * 单用户并发连接上限（多标签页场景；超出踢最久没活跃的）
     */
    private static final int MAX_CONNECTIONS_PER_USER = 8;

    /**
     * 全局连接上限（异常场景兜底，防止无界增长）
     */
    private static final int MAX_TOTAL_CONNECTIONS = 2000;

    /**
     * 匿名连接的归属桶：收不到任何业务事件，只保留心跳
     */
    private static final String ANONYMOUS_BUCKET = "";

    /**
     * 活跃连接：key = 用户标识（匿名统一落 {@link #ANONYMOUS_BUCKET}），value = 连接ID → 连接
     */
    private final Map<String, Map<String, Connection>> userConnections = new ConcurrentHashMap<>();

    /**
     * 共享心跳线程（单线程即可：任务只是往各连接写心跳字节）
     */
    private ScheduledExecutorService scheduler;

    /**
     * 项目统一的 ObjectMapper：自己序列化成字节再发，序列化口径与普通 REST 出口一致
     */
    private final ObjectMapper objectMapper;

    /**
     * @param objectMapper 项目统一的 ObjectMapper（与普通 REST 出口同一套序列化口径）
     */
    public AiSessionEventBroadcaster(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 启动心跳
     */
    @PostConstruct
    public void start() {
// [P3C C1 豁免] 场景化创建线程，需文档化
        scheduler = new ScheduledThreadPoolExecutor(1, r -> {
// [P3C C1 豁免] 场景化创建线程，需文档化
            Thread thread = new Thread(r, "ai-session-event-heartbeat");
            thread.setDaemon(true);
            return thread;
        });
        scheduler.scheduleWithFixedDelay(this::heartbeat, HEARTBEAT_MILLIS, HEARTBEAT_MILLIS, TimeUnit.MILLISECONDS);
    }

    /**
     * 停心跳并收掉所有连接
     */
    @PreDestroy
    public void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
        for (Map<String, Connection> conns : userConnections.values()) {
            for (Connection conn : conns.values()) {
                try {
                    conn.emitter.complete();
                } catch (Exception ignore) {
                    // 已断开，无需处理
                }
            }
        }
        userConnections.clear();
    }

    /**
     * 订阅当前用户的事件流。
     *
     * <p>首帧不在这里发：返回前直接 {@code send} 还没绑定到异步响应上，
     * 是否可用取决于 Spring 内部时序，没必要赌（与 workspace 事件流同口径）。</p>
     *
     * @param userId   用户标识；{@code null}/空 表示匿名，只保留心跳连接
     * @param clientId 前端标签页标识，可为空（记录在连接上，便于排查）
     * @return SSE 流
     */
    public SseEmitter subscribe(String userId, String clientId) {
        String bucketKey = userId == null || userId.isBlank() ? ANONYMOUS_BUCKET : userId;
        Connection conn = new Connection(bucketKey, clientId);
        Map<String, Connection> conns = userConnections.computeIfAbsent(bucketKey, k -> new ConcurrentHashMap<>());
        evictIfFull(conns, MAX_CONNECTIONS_PER_USER, "单用户连接上限 " + MAX_CONNECTIONS_PER_USER);
        evictIfFull(conns, MAX_TOTAL_CONNECTIONS - (totalConnections() - conns.size()), "全局连接上限 " + MAX_TOTAL_CONNECTIONS);
        conns.put(conn.id, conn);
        conn.emitter.onCompletion(() -> drop(conn));
        conn.emitter.onTimeout(() -> {
            drop(conn);
            try {
                conn.emitter.complete();
            } catch (Exception ignore) {
                // 已断开
            }
        });
        conn.emitter.onError(e -> drop(conn));
        log.info("[session-events] 新订阅 user={} clientId={} 当前连接数={}", bucketKey, clientId, totalConnections());
        return conn.emitter;
    }

    /**
     * 向指定用户的所有连接广播一条事件（发起方标签页从当前请求线程自动读取）。
     *
     * @param type      事件类型（{@code TYPE_*} 常量）
     * @param userId    目标用户标识；空则不广播（匿名无业务事件）
     * @param sessionId 事件关联的会话标识，可为空
     * @param payload   事件载荷，可为空
     */
    public void publish(String type, String userId, String sessionId, Map<String, Object> payload) {
        publish(type, userId, sessionId, payload, currentClientId());
    }

    /**
     * 向指定用户的所有连接广播一条事件。
     *
     * @param type      事件类型（{@code TYPE_*} 常量）
     * @param userId    目标用户标识；空则不广播（匿名无业务事件）
     * @param sessionId 事件关联的会话标识，可为空
     * @param payload   事件载荷，可为空
     * @param source    发起方标签页标识（前端 X-Client-Id），可为空；前端据此忽略自己发起的动作
     */
    public void publish(String type, String userId, String sessionId, Map<String, Object> payload, String source) {
        if (userId == null || userId.isBlank()) {
            return;
        }
        Map<String, Connection> conns = userConnections.get(userId);
        if (CollectionUtils.isEmpty(conns)) {
            return;
        }
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", type);
        event.put("userId", userId);
        event.put("sessionId", sessionId);
        event.put("timestamp", Instant.now().toString());
        if (CollectionUtils.isNotEmpty(payload)) {
            event.put("payload", payload);
        }
        if (source != null && !source.isBlank()) {
            event.put("source", source);
        }
        byte[] json;
        try {
            json = objectMapper.writeValueAsBytes(event);
        } catch (Exception e) {
            log.warn("[session-events] 事件序列化失败 type={}: {}", type, e.toString());
            return;
        }
        for (Connection conn : conns.values()) {
            try {
                // 必须传 byte[]：传对象/字符串会被 BinaryHttpMessageConverter 在无请求
                // 上下文的线程上静默写出 0 字节（见类注释与 workspace 事件流同款说明）
                conn.emitter.send(SseEmitter.event().data(json));
                conn.lastSentAt = System.currentTimeMillis();
            } catch (Exception e) {
                drop(conn);
                try {
                    conn.emitter.complete();
                } catch (Exception ignore) {
                    // 已断开
                }
            }
        }
    }

    /**
     * 心跳一轮：写不出去了的连接就地注销
     */
    private void heartbeat() {
        for (Map<String, Connection> conns : userConnections.values()) {
            for (Connection conn : conns.values()) {
                try {
                    conn.emitter.send(SseEmitter.event().data(heartbeatPayload()));
                    conn.lastSentAt = System.currentTimeMillis();
                } catch (Exception e) {
                    drop(conn);
                    try {
                        conn.emitter.complete();
                    } catch (Exception ignore) {
                        // 已断开
                    }
                }
            }
        }
    }

    /**
     * 心跳载荷（JSON 字节，前端按未知类型忽略）
     */
    private byte[] heartbeatPayload() {
        try {
            Map<String, Object> beat = new LinkedHashMap<>();
            beat.put("type", "heartbeat");
            beat.put("timestamp", Instant.now().toString());
            return objectMapper.writeValueAsBytes(beat);
        } catch (Exception e) {
            return "{\"type\":\"heartbeat\"}".getBytes(StandardCharsets.UTF_8);
        }
    }

    /**
     * 连接数到上限时踢掉最久没推过消息的那条（客户端会自动重连）
     *
     * @param conns  连接表
     * @param limit  上限
     * @param reason 踢出原因（日志用）
     */
    private void evictIfFull(Map<String, Connection> conns, int limit, String reason) {
        if (limit <= 0 || conns.size() < limit) {
            return;
        }
        Connection oldest = null;
        for (Connection conn : conns.values()) {
            if (oldest == null || conn.lastSentAt < oldest.lastSentAt) {
                oldest = conn;
            }
        }
        if (oldest == null) {
            return;
        }
        drop(oldest);
        log.warn("[session-events] {}，踢掉最旧的一条 {}", reason, oldest.id);
        try {
            oldest.emitter.complete();
        } catch (Exception ignore) {
            // 已断开
        }
    }

    /**
     * 全局连接总数
     */
    private int totalConnections() {
        int total = 0;
        for (Map<String, Connection> conns : userConnections.values()) {
            total += conns.size();
        }
        return total;
    }

    /**
     * 从当前请求线程读取前端标签页标识（X-Client-Id）。
     *
     * <p>仅请求线程可用；异步线程（SSE 收尾、线程池任务）没有请求上下文，
     * 返回 {@code null}——调用方应在进入异步前先取好再显式传入。</p>
     *
     * @return 标签页标识；无请求上下文或未携带时为 {@code null}
     */
    private String currentClientId() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            return servletAttributes.getRequest().getHeader("X-Client-Id");
        }
        return null;
    }

    /**
     * 从归属桶移除连接（幂等）
     */
    private void drop(Connection conn) {
        Map<String, Connection> conns = userConnections.get(conn.userKey);
        if (conns != null) {
            conns.remove(conn.id);
            if (conns.isEmpty()) {
                userConnections.remove(conn.userKey, conns);
            }
        }
    }

    /**
     * 一条 SSE 订阅连接
     */
    private static final class Connection {
        /**
         * 连接 ID
         */
        private final String id = UUID.randomUUID().toString();
        /**
         * 归属桶（用户标识，匿名落 ANONYMOUS_BUCKET）
         */
        private final String userKey;
        /**
         * 前端标签页标识（排查用）
         */
        private final String clientId;
        /**
         * SSE 连接
         */
        private final SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MILLIS);
        /**
         * 最近一次推送时间（心跳/淘汰用）
         */
        private volatile long lastSentAt = System.currentTimeMillis();

        /**
         * 构造连接。
         *
         * @param userKey  归属桶
         * @param clientId 前端标签页标识，可为空
         */
        private Connection(String userKey, String clientId) {
            this.userKey = userKey;
            this.clientId = clientId;
        }
    }
}
