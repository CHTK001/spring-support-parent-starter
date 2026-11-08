package com.chua.starter.cdp.ws;

import com.chua.common.support.lang.json.Json;
import com.chua.starter.cdp.conn.CdpWsClient;
import com.chua.starter.cdp.service.CdpEngineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CDP 调试代理 WebSocket 端点
 *
 * <p>这是一个 DevTools Protocol 透传层，替前端连上浏览器的
 * {@code ws://127.0.0.1:{port}/devtools/browser/<uuid>}（浏览器级通道），涵盖了
 * {console / network / page / target} 四类事件向后推送：</p>
 * <ul>
 *   <li>前端发来的 CDP 命令通过 {@link #handleTextMessage(WebSocketSession, TextMessage)}
 *       代理到浏览器级 / 页面级通道；</li>
 *   <li>来自浏览器的 Console / Network / Page / Target 事件由
 *       {@link CdpEngineService#subscribeAll(java.util.function.Consumer)} 接收，
 *       再广播给所有已连接的前端。</li>
 * </ul>
 *
 * <p>指令格式（JSON-RPC 风格）：<pre>{@code
 * // 浏览器级（目标管理）
 * {"method": "Target.createTarget", "params": {"url": "about:blank"}}
 * // 页面级
 * {"method": "Runtime.evaluate", "params": {"expression": "1+1"}, "targetId": "<targetId>"}
 * // 需要回复的命令（附带 id）
 * {"method": "Runtime.evaluate", "params": {...}, "id": 1}
 * }</pre> 回复走：{ "id": 1, "result": {...} }（发给请求者，或被广播到所有会话）。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CdpWebSocketHandler extends TextWebSocketHandler {

    /**
     * CDP 引擎服务
     */
    private final CdpEngineService engine;

    /**
     * 连接上来的前端（会话）
     */
    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();

    /**
     * 会话启动后：广播一条连接事件，并开始接管控制台 / 网络 / 页面事件
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
        // 推送一条 "connected" 事件
        send(session, Json.toJson(Map.of("event", "connected", "connectedAt", System.currentTimeMillis())));
        // 开始接管控制台 / 网络 / 页面事件
        engine.subscribeAll(msg -> {
            try {
                // 用 LinkedHashMap 逐字段放入：Map.of 不接受 null 值，
                // 一旦某个事件没有 targetId 就直接抛 NPE，整条推送会被静默丢掉。
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("event", "Push");
                payload.put("method", msg.get("method"));
                payload.put("params", msg.get("params"));
                payload.put("targetId", msg.get("targetId"));
                broadcast(Json.toJson(payload));
            } catch (Exception e) {
                log.warn("[cdp] 推送事件失败: {}", e.getMessage());
            }
        });
        log.info("[cdp] 开发者面板已连接: {}", session.getId());
    }

    /**
     * 会话关闭后：断开控制通道
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
        log.info("[cdp] 开发者面板已断开: {} ({})", session.getId(), status);
    }

    /**
     * 连接发生异常（不向这里推出异常，拯救握手，无法阻止连接）
     */
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        sessions.remove(session);
        log.warn("[cdp] 开发者面板错误: {} {}", session.getId(), exception.getMessage());
    }

    /**
     * 收到指令
     */
    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) {
        Map<String, Object> body;
        try {
            body = Json.fromJson(message.getPayload(), Map.class);
        } catch (Exception e) {
            log.warn("[cdp] 无法解析的指令: {}", e.getMessage());
            return;
        }
        if (body == null) {
            return;
        }
        String method = (String) body.get("method");
        if (method == null) {
            return;
        }
        Object targetId = body.get("targetId");
        String tid = targetId == null ? null : String.valueOf(targetId);
        Object payload = body.get("params");
        Map<String, Object> params = payload instanceof Map ? (Map<String, Object>) payload : Map.of();
        Long requestId = body.get("id") != null ? ((Number) body.get("id")).longValue() : null;

        try {
            if (tid == null || tid.isBlank()) {
                // 浏览器级调用（Target 服务）
                Map<String, Object> result = engine.browserClient().call(method, params, null);
                sendResult(session, requestId, result);
            } else {
                // 页面级调用（devtools/page）
                CdpWsClient client = engine.pageClient(tid);
                if (client == null) {
                    // recover
                    sendError(session, requestId, "页面目标已丢失: " + tid);
                } else {
                    Map<String, Object> result = client.call(method, params, null);
                    sendResult(session, requestId, result);
                }
            }
        } catch (Exception e) {
            sendError(session, requestId, e.getMessage());
        }
    }

    /**
     * 通过 WS 把消息推给所有人
     */
    private void broadcast(String text) {
        for (WebSocketSession session : sessions) {
            try {
                session.sendMessage(new TextMessage(text));
            } catch (Exception e) {
                log.warn("[cdp] 广播失败给 {} {}: {}", session.getId(), e.getClass().getSimpleName(), e.getMessage());
            }
        }
    }

    /**
     * 通过 WS 把消息推给指定的人
     */
    private void send(WebSocketSession session, String text) {
        try {
            session.sendMessage(new TextMessage(text));
        } catch (Exception e) {
            log.warn("[cdp] 推送到 {} 失败: {}", session.getId(), e.getMessage());
        }
    }

    /**
     * 把结果推给请求者（JSON-RPC 风格）
     */
    private void sendResult(WebSocketSession session, Long id, Map<String, Object> result) {
        Map<String, Object> resp;
        if (id == null) {
            resp = Map.of();
            broadcast(Json.toJson(resp));
            return;
        }
        resp = new LinkedHashMap<>();
        resp.put("id", id);
        if (result != null) {
            resp.put("result", result);
        } else {
            resp.put("result", Map.of());
        }
        send(session, Json.toJson(resp));
    }

    /**
     * 把错误推给请求者（JSON-RPC 风格）
     */
    private void sendError(WebSocketSession session, Long id, String error) {
        if (id == null) {
            return;
        }
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("id", id);
        resp.put("error", Map.of("message", error));
        send(session, Json.toJson(resp));
    }
}
