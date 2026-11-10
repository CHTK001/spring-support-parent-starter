package com.chua.starter.server.support.sse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 发布管理全局 SSE 通道。
 *
 * <p>发布管理（发布卡片、项目管理、任务浮层、实时日志）共用<b>一条</b>长连接，
 * 与「主机资产」的 {@code HostAssetsSseHub} 完全独立：两者端点、clientId 前缀、
 * 事件名都不相同，互不影响。</p>
 *
 * <p>连接按 {@code clientId} 管理。前端在发布管理页面挂载时用固定 clientId 建连，
 * 之后所有推送都走这条连接：</p>
 * <ul>
 *   <li>{@code project-task}：异步任务步骤/进度/日志/结果（替代 Socket.IO 广播）；</li>
 *   <li>{@code project-log}：实时日志行（服务端按日志会话绑定的 clientId 定向推送）；</li>
 *   <li>{@code op-log}：项目操作日志新增/结束（日志管理面板据此即时刷新）。</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.52
 */
@Slf4j
@Service
public class ReleaseSseHub {

    /**
     * 建连事件名（建连后立即下发一次）
     */
    public static final String EVENT_CONNECTED = "connected";

    /**
     * 项目异步任务事件名
     */
    public static final String EVENT_PROJECT_TASK = "project-task";

    /**
     * 项目实时日志事件名
     */
    public static final String EVENT_PROJECT_LOG = "project-log";

    /**
     * 项目操作日志事件名
     */
    public static final String EVENT_OP_LOG = "op-log";

    /**
     * Actuator 定时采集事件名：全局时间轮采集完成后广播，页面据此刷新底部指标条
     */
    public static final String EVENT_PROJECT_ENDPOINTS = "project-endpoints";

    /**
     * SSE 连接超时（0 表示不超时）
     */
    private static final long SSE_TIMEOUT_MS = 0L;

    /**
     * 心跳间隔（毫秒），避免代理把空闲长连接断开
     */
    private static final long HEARTBEAT_INTERVAL_MS = 25_000L;

    /**
     * 客户端标识 → SSE 连接
     */
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    /**
     * 建立（或替换）指定客户端的 SSE 连接。
     *
     * @param clientId 前端生成的客户端标识；为空时自动生成
     * @return Spring MVC 的 SSE 发射器
     */
    public SseEmitter connect(String clientId) {
        String id = clientId == null || clientId.isBlank()
                ? UUID.randomUUID().toString().replace("-", "")
                : clientId.trim();
        SseEmitter previous = emitters.remove(id);
        if (previous != null) {
            try {
                previous.complete();
            } catch (Exception e) {
                log.debug("[release-sse] 关闭旧连接失败 clientId={}: {}", id, e.getMessage());
            }
        }
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        emitters.put(id, emitter);
        emitter.onCompletion(() -> remove(id, emitter));
        emitter.onTimeout(() -> remove(id, emitter));
        emitter.onError(e -> remove(id, emitter));
        sendEvent(id, emitter, EVENT_CONNECTED, "{\"clientId\":\"" + id + "\"}");
        log.info("[release-sse] 连接建立 clientId={} 当前连接数={}", id, emitters.size());
        return emitter;
    }

    /**
     * 判断客户端连接是否在线。
     *
     * @param clientId 客户端标识
     * @return 在线返回 true
     */
    public boolean isConnected(String clientId) {
        return clientId != null && emitters.containsKey(clientId);
    }

    /**
     * 向指定客户端推送事件。
     *
     * @param clientId 客户端标识
     * @param event    事件名
     * @param data     已序列化的数据
     */
    public void send(String clientId, String event, String data) {
        if (clientId == null || event == null) {
            return;
        }
        SseEmitter emitter = emitters.get(clientId);
        if (emitter == null) {
            return;
        }
        sendEvent(clientId, emitter, event, data);
    }

    /**
     * 向所有在线客户端广播事件。
     *
     * @param event 事件名
     * @param data  已序列化的数据
     */
    public void broadcast(String event, String data) {
        if (emitters.isEmpty()) {
            return;
        }
        emitters.forEach((clientId, emitter) -> sendEvent(clientId, emitter, event, data));
    }

    /**
     * 关闭指定客户端的连接。
     *
     * @param clientId 客户端标识
     */
    public void close(String clientId) {
        if (clientId == null) {
            return;
        }
        SseEmitter emitter = emitters.remove(clientId);
        if (emitter != null) {
            try {
                emitter.complete();
            } catch (Exception e) {
                log.debug("[release-sse] 主动关闭失败 clientId={}: {}", clientId, e.getMessage());
            }
        }
    }

    /**
     * 保活心跳。
     */
    @Scheduled(fixedDelay = HEARTBEAT_INTERVAL_MS)
    public void heartbeat() {
        if (emitters.isEmpty()) {
            return;
        }
        for (Map.Entry<String, SseEmitter> entry : emitters.entrySet()) {
            try {
                entry.getValue().send(SseEmitter.event().comment("ping"));
            } catch (Exception e) {
                remove(entry.getKey(), entry.getValue());
                log.debug("[release-sse] 心跳失败，清理连接 clientId={}: {}", entry.getKey(), e.getMessage());
            }
        }
    }

    /**
     * 发送事件；写失败说明连接已断，清理该连接。
     *
     * @param clientId 客户端标识
     * @param emitter  目标连接
     * @param event    事件名
     * @param data     事件数据
     */
    private void sendEvent(String clientId, SseEmitter emitter, String event, String data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data == null ? "" : data));
        } catch (Exception e) {
            remove(clientId, emitter);
            log.debug("[release-sse] 推送失败，清理连接 clientId={} event={}: {}", clientId, event, e.getMessage());
        }
    }

    /**
     * 移除连接（两参 remove 避免误删同 id 的新连接）。
     *
     * @param clientId 客户端标识
     * @param emitter  连接
     */
    private void remove(String clientId, SseEmitter emitter) {
        if (emitters.remove(clientId, emitter)) {
            log.debug("[release-sse] 连接释放 clientId={} 当前连接数={}", clientId, emitters.size());
        }
    }
}
