package com.chua.starter.socket.websocket.room;

import com.chua.starter.socket.websocket.room.model.RoomSignal;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

/**
 * 房间信令分发器（传输无关）。
 *
 * <p>把「收到一段 JSON → 解析成 {@link RoomSignal} → 交给 {@link RoomService} →
 * 把回执发回连接」这段逻辑单独拎出来，供两种接入方式共用：</p>
 * <ul>
 *   <li>{@link RoomWebSocketHandler}：与应用同端口的 Spring WebSocket；</li>
 *   <li>{@link RoomServer}：服务管理里独立端口的房间节点。</li>
 * </ul>
 *
 * <p>此前这段逻辑写死在 handler 里，节点化时若复制一份，两条链路（hello 的 welcome、
 * heartbeat 的 pong、signal 的失败告警）就会各自漂移；这里只保留一份。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Slf4j
public class RoomSignalProcessor {

    /** 房间信令引擎 */
    private final RoomService roomService;

    /** 回执下发出口 */
    private final RoomSender sender;

    /** JSON 解析器 */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 构造器。
     *
     * @param roomService 房间信令引擎
     * @param sender      回执下发出口
     */
    public RoomSignalProcessor(RoomService roomService, RoomSender sender) {
        this.roomService = roomService;
        this.sender = sender;
    }

    /**
     * 处理一条上行信令。
     *
     * @param connectionId 连接标识
     * @param message      原始 JSON
     */
    public void onMessage(String connectionId, String message) {
        try {
            RoomSignal signal = parse(message);
            if (signal == null) {
                return;
            }
            log.debug("[Room] 收到信令: connectionId={}, type={}, roomId={}",
                    connectionId, signal.getType(), signal.getRoomId());
            switch (signal.getType()) {
                case RoomService.TYPE_HELLO -> onHello(connectionId, signal);
                case RoomService.TYPE_SIGNAL -> onSignal(connectionId, signal);
                case RoomService.TYPE_HEARTBEAT -> onHeartbeat(connectionId);
                case RoomService.TYPE_LEAVE -> roomService.onLeave(connectionId);
                default -> log.warn("[Room] 未知信令类型: {}", signal.getType());
            }
        } catch (Exception e) {
            log.error("[Room] 处理信令失败: connectionId={}", connectionId, e);
            sendError(connectionId, "parse.signal.failed");
        }
    }

    /**
     * hello 信令：加入房间 / 续接旧连接，回执 welcome。
     *
     * @param connectionId 连接标识
     * @param signal       上行信令
     */
    private void onHello(String connectionId, RoomSignal signal) {
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
     * signal 信令：WebRTC offer / answer / candidate 转发给对端。
     *
     * @param connectionId 连接标识
     * @param signal       上行信令
     */
    private void onSignal(String connectionId, RoomSignal signal) {
        boolean forwarded = roomService.onSignal(signal.getRoomId(), connectionId, signal);
        if (!forwarded) {
            log.warn("[Room] 信令转发失败: roomId={}, connectionId={}",
                    signal.getRoomId(), connectionId);
        }
    }

    /**
     * heartbeat 信令：续期并回执 pong。
     *
     * @param connectionId 连接标识
     */
    private void onHeartbeat(String connectionId) {
        RoomMessage pong = roomService.onHeartbeat(connectionId);
        if (pong != null) {
            sender.send(connectionId, pong.toJson());
        }
    }

    /**
     * 回执错误信封。
     *
     * @param connectionId 连接标识
     * @param reason       错误原因
     */
    private void sendError(String connectionId, String reason) {
        RoomMessage error = RoomMessage.builder(RoomMessage.TYPE_ERROR)
                .reason(reason)
                .build();
        sender.send(connectionId, error.toJson());
    }

    /**
     * 解析上行信令信封。
     *
     * @param message 原始 JSON
     * @return 解析后的信令；无 type 或解析失败返回 null
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
