package com.chua.starter.socket.websocket.room;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

import com.chua.starter.socket.websocket.handler.WebSocketMessageHandler;
import com.chua.starter.socket.websocket.room.model.RoomSignal;
import com.chua.starter.socket.websocket.template.WebSocketTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

/**
 * 房间信令 WebSocket 处理器。
 *
 * <p>处理 `/ws` 端点上的房间信令（Hello/Signal/Heartbeat/Leave），委托给
 * {@link RoomService}，并将回执信封发送回连接。
 *
 * <p>接入规则（ZestSend 协议）：
 * <ul>
 *   <li>{@code hello} - 加入房间或续接旧连接 → 回执 welcome</li>
 *   <li>{@code signal} - WebRTC offer / answer / candidate 转发给对端</li>
 *   <li>{@code heartbeat} - 心跳续期 → 回执 pong</li>
 *   <li>{@code leave} - 主动离开房间 → 回执 left，通知对端 peer-left</li>
 * </ul>
 *
 * @author CH
 * @since 2026-11-03
 */
@Service
public class RoomWebSocketHandler implements WebSocketMessageHandler {

    /**
     * 日志
     */
    private static final Logger log = LoggerFactory.getLogger(RoomWebSocketHandler.class);

    /**
     * 房间信令引擎
     */
    private final RoomService roomService;

    /**
     * 信令下发模板
     */
    private final WebSocketTemplate sender;

    /**
     * JSON 序列化器
     */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 构造器
     *
     * @param roomService 房间信令引擎
     * @param sender      信令下发模板
     */
    public RoomWebSocketHandler(RoomService roomService, WebSocketTemplate sender) {
        this.roomService = roomService;
        this.sender = sender;
    }

    @Override
    /**
     * 连接建立时调用
     *
     * @param session 参数
     */
    public void onConnect(WebSocketSession session) {
        log.info("[Room] 连接建立: sessionId={}", session.getId());
    }

    @Override
    /**
     * 收到消息时调用
     *
     * @param session 参数
     * @param message 参数
     */
    public void onMessage(WebSocketSession session, String message) {
        String connectionId = session.getId();
        try {
            RoomSignal signal = parse(message);
            if (signal == null) {
                return;
            }
            log.debug("[Room] 收到信令: sessionId={}, type={}, roomId={}", connectionId, signal.getType(), signal.getRoomId());
            switch (signal.getType()) {
                case RoomService.TYPE_HELLO:
                    onHelloSignal(session, connectionId, signal);
                    break;
                case RoomService.TYPE_SIGNAL:
                    onSignalSignal(connectionId, signal);
                    break;
                case RoomService.TYPE_HEARTBEAT:
                    onHeartbeatSignal(connectionId);
                    break;
                case RoomService.TYPE_LEAVE:
                    onLeaveSignal(connectionId);
                    break;
                default:
                    log.warn("[Room] 未知信令类型: {}", signal.getType());
            }
        } catch (Exception e) {
            log.error("[Room] 处理信令失败: sessionId={}", connectionId, e);
            sendError(connectionId, "parse.signal.failed", e.getMessage());
        }
    }

    @Override
    /**
     * 连接关闭时调用
     *
     * @param session 参数
     * @param code    参数
     * @param reason  参数
     */
    public void onClose(WebSocketSession session, int code, String reason) {
        log.info("[Room] 连接关闭: sessionId={}, code={}", session.getId(), code);
        roomService.onDisconnect(session.getId());
    }

    @Override
    /**
     * 发生错误时调用
     *
     * @param session   参数
     * @param throwable 参数
     */
    public void onError(WebSocketSession session, Throwable throwable) {
        log.error("[Room] 连接错误: sessionId={}", session.getId(), throwable);
        roomService.onDisconnect(session.getId());
    }

    /**
     * hello 信令事件
     *
     * @param session     参数
     * @param connectionId 参数
     * @param signal      参数
     */
    private void onHelloSignal(WebSocketSession session, String connectionId, RoomSignal signal) {
        RoomMessage welcome = roomService.onHello(
                signal.getRoomId(),
                signal.getSlotId(),
                signal.getPeerSessionId(),
                signal.getResumeToken(),
                signal.getEpoch(),
                connectionId
        );
        if (welcome != null) {
            sender.send(connectionId, welcome.toJson());
        }
    }

    /**
     * signal 信令事件（WebRTC 信令）
     *
     * @param connectionId 参数
     * @param signal       参数
     */
    private void onSignalSignal(String connectionId, RoomSignal signal) {
        boolean forwarded = roomService.onSignal(signal.getRoomId(), connectionId, signal);
        if (!forwarded) {
            log.warn("[Room] 信令转发失败: roomId={}, connectionId={}", signal.getRoomId(), connectionId);
        }
    }

    /**
     * heartbeat 信令事件（心跳续期）
     *
     * @param connectionId 参数
     */
    private void onHeartbeatSignal(String connectionId) {
        RoomMessage pong = roomService.onHeartbeat(connectionId);
        if (pong != null) {
            sender.send(connectionId, pong.toJson());
        }
    }

    /**
     * leave 信令事件（主动离开房间）
     *
     * @param connectionId 参数
     */
    private void onLeaveSignal(String connectionId) {
        roomService.onLeave(connectionId);
    }

    /**
     * 发送错误信封
     *
     * @param connectionId 参数
     * @param reason       参数
     * @param message      参数
     */
    private void sendError(String connectionId, String reason, String message) {
        RoomMessage error = RoomMessage.builder(RoomMessage.TYPE_ERROR)
                .reason(reason)
                .build();
        sender.send(connectionId, error.toJson());
    }

    /**
     * 解析上来的信令信封
     *
     * @param message 原始 JSON
     * @return 解析后的信令信封
     */
    private RoomSignal parse(String message) {
        try {
            JsonNode node = objectMapper.readTree(message);
            if (node == null || !node.has("type")) {
                log.warn("[Room] 无效信令: {}", message);
                return null;
            }
            RoomSignal signal = new RoomSignal();
            signal.setType(node.get("type").asText());
            signal.setRoomId(node.has("roomId") ? node.get("roomId").asText() : null);
            signal.setSlotId(node.has("slotId") ? node.get("slotId").asText() : null);
            signal.setPeerSessionId(node.has("peerSessionId") ? node.get("peerSessionId").asText() : null);
            signal.setPeerId(node.has("peerId") ? node.get("peerId").asText() : null);
            signal.setOffererSlotId(node.has("offererSlotId") ? node.get("offererSlotId").asText() : null);
            signal.setResumeToken(node.has("resumeToken") ? node.get("resumeToken").asText() : null);
            signal.setEpoch(node.has("epoch") ? node.get("epoch").asLong() : null);
            signal.setPayload(node.has("payload") ? node.get("payload").asText() : null);
            signal.setReason(node.has("reason") ? node.get("reason").asText() : null);
            return signal;
        } catch (Exception e) {
            log.error("[Room] 解析信令失败", e);
            return null;
        }
    }
}
