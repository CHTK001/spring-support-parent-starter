package com.chua.starter.server.support.sse;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 主机资产全局 SSE 通道（@pages/server 唯一长连接）
 *
 * <p>整个「主机资产」页面只建立一条 SSE 长连接：底部资源摘要（句柄/inode）与
 * 邮箱新邮件推送都经这条连接下发，避免一个页签开多个 {@code SseEmitter}。
 * 连接按 {@code clientId} 管理；前端在页面挂载时用固定的 {@code clientId} 建连，
 * 再通过普通 HTTP 接口声明「当前关注哪台主机 / 哪个邮箱身份」，推送内容由服务端决定。</p>
 *
 * <p>本类只负责连接生命周期与事件下发，不关心事件含义；具体推送方见
 * {@code HostSummarySseService}（monitor-starter）与 {@code MailboxSseService}（server-starter）。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Slf4j
@Service
public class HostAssetsSseHub {

    /**
     * SSE 连接事件名（建连成功后立即下发一次，前端据此后补订阅）
     */
    public static final String EVENT_CONNECTED = "connected";

    /**
     * SSE 连接超时（0 表示不超时，由浏览器/服务端断链后清理）
     */
    private static final long SSE_TIMEOUT_MS = 0L;

    /**
     * 心跳间隔（毫秒）：没有业务推送时保活，避免代理把空闲长连接断开
     */
    private static final long HEARTBEAT_INTERVAL_MS = 25_000L;

    /**
     * JSON 序列化（仅用于 connected 事件的最小负载）
     */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 客户端标识 → SSE 连接
     */
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    /**
     * 建立（或替换）指定客户端的 SSE 连接。
     *
     * @param clientId 前端生成的客户端标识，为空时自动生成
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
                log.debug("[host-sse] 关闭旧连接失败 clientId={}: {}", id, e.getMessage());
            }
        }
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        emitters.put(id, emitter);
        emitter.onCompletion(() -> remove(id, emitter));
        emitter.onTimeout(() -> remove(id, emitter));
        emitter.onError(e -> remove(id, emitter));
        sendEvent(id, emitter, EVENT_CONNECTED, connectedPayload(id));
        log.info("[host-sse] 连接建立 clientId={} 当前连接数={}", id, emitters.size());
        return emitter;
    }

    /**
     * 判断客户端连接是否仍在。
     *
     * @param clientId 客户端标识
     * @return 连接在线返回 true
     */
    public boolean isConnected(String clientId) {
        return clientId != null && emitters.containsKey(clientId);
    }

    /**
     * 向指定客户端推送一个命名事件。
     *
     * @param clientId 客户端标识
     * @param event    事件名
     * @param data     已序列化的事件数据
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
                log.debug("[host-sse] 主动关闭失败 clientId={}: {}", clientId, e.getMessage());
            }
        }
    }

    /**
     * 保活心跳：长时间没有业务事件时发一行注释，避免网关按空闲超时断开。
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
                log.debug("[host-sse] 心跳失败，清理连接 clientId={}: {}", entry.getKey(), e.getMessage());
            }
        }
    }

    /**
     * 发送事件；写失败说明连接已断，按标识清理（只清理同一个发射器）。
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
            log.debug("[host-sse] 推送失败，清理连接 clientId={} event={}: {}", clientId, event, e.getMessage());
        }
    }

    /**
     * 移除连接（两参 remove 保证不会误删同 id 的新连接）。
     *
     * @param clientId 客户端标识
     * @param emitter  连接
     */
    private void remove(String clientId, SseEmitter emitter) {
        if (emitters.remove(clientId, emitter)) {
            log.debug("[host-sse] 连接释放 clientId={} 当前连接数={}", clientId, emitters.size());
        }
    }

    /**
     * 建连事件负载。
     *
     * @param clientId 客户端标识
     * @return JSON 文本
     */
    private String connectedPayload(String clientId) {
        try {
            return OBJECT_MAPPER.writeValueAsString(Map.of(
                    "clientId", clientId,
                    "timestamp", System.currentTimeMillis()));
        } catch (Exception e) {
            return "{}";
        }
    }
}
