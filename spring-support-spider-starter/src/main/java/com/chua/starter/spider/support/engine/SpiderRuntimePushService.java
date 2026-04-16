package com.chua.starter.spider.support.engine;

import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * SSE + Socket.IO 双通道实时推送服务。
 *
 * <p>优先通过 SSE 推送；若 Socket.IO 服务可用（可选依赖），同时通过 Socket.IO 推送，
 * 实现双通道冗余，前端可按需选择接收方式。</p>
 *
 * @author CH
 */
@Slf4j
@Service
public class SpiderRuntimePushService {

    /** 每个 taskId 对应的活跃 SseEmitter 列表 */
    private final ConcurrentHashMap<Long, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    /** SSE 超时时间：5 分钟 */
    private static final long SSE_TIMEOUT_MS = 5 * 60 * 1000L;

    /**
     * Socket.IO 服务（可选依赖）。
     * 若 lay-socket 或其他 Socket.IO 模块可用则自动注入，否则为 null。
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private Object socketIoServer;

    /**
     * 为指定 taskId 创建并注册一个新的 SseEmitter。
     *
     * @param taskId 任务 ID
     * @return 新建的 SseEmitter
     */
    public SseEmitter subscribe(Long taskId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        List<SseEmitter> list = emitters.computeIfAbsent(taskId, id -> new CopyOnWriteArrayList<>());
        list.add(emitter);

        // 完成/超时/错误时自动清理
        Runnable cleanup = () -> removeEmitter(taskId, emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(ex -> cleanup.run());

        log.debug("[SSE] taskId={} 新订阅，当前订阅数={}", taskId, list.size());
        return emitter;
    }

    /**
     * 推送节点状态变更事件。
     *
     * @param taskId  任务 ID
     * @param nodeId  节点 ID
     * @param status  节点状态（如 RUNNING、SUCCESS、FAILED）
     * @param message 附加消息
     */
    public void pushNodeStatus(Long taskId, String nodeId, String status, String message) {
        Map<String, Object> data = Map.of(
                "nodeId", nodeId,
                "status", status,
                "message", message != null ? message : ""
        );
        pushEvent(taskId, "nodeStatus", data);
    }

    /**
     * 推送通用事件到指定 taskId 的所有订阅者。
     *
     * @param taskId    任务 ID
     * @param eventType 事件类型名称
     * @param data      事件数据（将被序列化为 JSON）
     */
    public void pushEvent(Long taskId, String eventType, Object data) {
        List<SseEmitter> list = emitters.get(taskId);
        if (list == null || list.isEmpty()) {
            return;
        }

        String json = JSON.toJSONString(data);
        List<SseEmitter> dead = new CopyOnWriteArrayList<>();

        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event()
                        .name(eventType)
                        .data(json));
            } catch (IOException | IllegalStateException e) {
                log.debug("[SSE] taskId={} 推送失败，移除失效 emitter: {}", taskId, e.getMessage());
                dead.add(emitter);
            }
        }

        list.removeAll(dead);

        // Socket.IO 双通道推送（可选）
        pushViaSocketIo(taskId, eventType, json);
    }

    /**
     * 通过 Socket.IO 推送事件（若可用）。
     */
    private void pushViaSocketIo(Long taskId, String eventType, String json) {
        if (socketIoServer == null) return;
        try {
            // 通过反射调用 Socket.IO 服务，避免硬依赖
            // 期望接口：broadcastToRoom(String room, String event, String data)
            socketIoServer.getClass()
                    .getMethod("broadcastToRoom", String.class, String.class, String.class)
                    .invoke(socketIoServer, "spider-task-" + taskId, eventType, json);
        } catch (Exception e) {
            log.debug("[SocketIO] taskId={} 推送失败（可忽略）: {}", taskId, e.getMessage());
        }
    }

    // ── 内部工具 ──────────────────────────────────────────────────────────────

    private void removeEmitter(Long taskId, SseEmitter emitter) {
        List<SseEmitter> list = emitters.get(taskId);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) {
                emitters.remove(taskId);
            }
        }
    }
}
