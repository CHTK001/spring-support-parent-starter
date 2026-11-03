package com.chua.starter.socket.websocket.room;

import com.chua.starter.socket.websocket.room.model.RoomSignal;
import com.chua.starter.socket.websocket.room.model.RoomState;
import com.chua.starter.socket.websocket.room.model.SlotState;
import com.chua.starter.socket.websocket.template.WebSocketTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * P2P 房间信令引擎（ZestSend Room 协议服务端实现）。
 *
 * <p>实现 ZestSend {@code src/worker/room.ts} 的房间协议核心：
 * <ul>
 *   <li>每房间最多 2 个参与端点（offerer + initiator）</li>
 *   <li>信令分发：Offer / Answer / Candidate 转发到对端</li>
 *   <li>心跳与续期：端点未在 lease-expires-ms 内心跳则断开</li>
 *   <li>空闲房间回收：一定时间无参与者则回收房间</li>
 *   <li>续练：端点掉线后重连时通过 resumeToken 恢复旧会话</li>
 * </ul>
 *
 * @author CH
 * @since 2026-11-03
 */
@Slf4j
public class RoomService {

    /** 恢复（续练）令牌有效时间（ms） */
    private static final long TOKEN_LIFETIME_MS = 240_000L;

    /** hello 信令代码 */
    public static final String TYPE_HELLO = "hello";
    /** signal 信令代码 */
    public static final String TYPE_SIGNAL = "signal";
    /** heartbeat 信令代码 */
    public static final String TYPE_HEARTBEAT = "heartbeat";
    /** leave 信令代码 */
    public static final String TYPE_LEAVE = "leave";

    private final RoomProperties properties;

    /** 信令信封序列化工具 */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 信令下发模板（通过 connectionId 路由） */
    private final WebSocketTemplate sender;

    /** 活跃房间台账：房间编号 -> 房间状态 */
    private final Map<String, RoomState> rooms = new ConcurrentHashMap<>();

    /** 连接（session_id） -> 端点：从 WebSocket 连接起的接管 */
    private final Map<String, PeerSession> peers = new ConcurrentHashMap<>();

    /** 恢复令牌台账：令牌 -> 房间编号/端点/代数/到期 */
    private final Map<String, ResumeToken> resumeTokens = new ConcurrentHashMap<>();

    /** 清理过期的续期与房间（守护线程） */
    private final ScheduledExecutorService cleaner = Executors.newScheduledThreadPool(1, r -> {
        Thread thread = new Thread(r, "room-signaling-cleaner");
        thread.setDaemon(true);
        return thread;
    });

    public RoomService(RoomProperties properties, WebSocketTemplate sender) {
        this.properties = properties;
        this.sender = sender;
    }

    @PostConstruct
    /**
     * 启动连接续期 / 房间回收的守护清理
     */
    public void init() {
        cleaner.scheduleWithFixedDelay(this::cleanExpired, 15_000, 15_000, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    /**
     * 结束守护清理
     */
    public void shutdown() {
        cleaner.shutdownNow();
    }

    /**
     * hello 信令：加入房间 / 续接旧连接。
     *
     * @param roomId 房间编号
     * @param slotId 当前端点的 SlotID
     * @param peerSessionId 对等端点的 SessionID
     * @param resumeToken 恢复令牌（续接旧连接时使用）
     * @param epoch 客户端的认知房间代数
     * @param connectionId 当前 WebSocket 会话 ID
     * @return 下传到连接舱的信封（welcome / error）
     */
    public RoomMessage onHello(String roomId, String slotId, String peerSessionId, String resumeToken, Long epoch, String connectionId) {
        // 房间台账（不存在新建）
        boolean fresh = rooms.containsKey(roomId);
        RoomState room = Optional.ofNullable(rooms.get(roomId)).orElseGet(() -> {
            RoomState state = new RoomState(roomId);
            rooms.put(roomId, state);
            return state;
        });
        room.setLastActiveAt(System.currentTimeMillis());

        // 续接旧连接（同房间/同代数/同端点/续练未过期）
        ResumeToken token = resumeTokens.get(resumeToken);
        if (token != null && token.getEpoch().equals(room.getEpoch())) {
            SlotState alive = room.findSlot(token.getSlotId()).orElse(null);
            if (alive != null && alive.isActive()) {
                log.info("[Room] 续接连接: roomId={}, slotId={}, connectionId={}", roomId, slotId, connectionId);
                resumeConnection(room, token, alive, connectionId);
                return buildWelcome(room, alive.getSlotId(), true, resumeToken);
            }
        }

        // 加入房间（新接入 / 续练不通过）
        joinRoom(room, slotId, peerSessionId, connectionId, fresh);
        String resumedToken = emitResumeToken(room, slotId);
        return buildWelcome(room, slotId, false, resumedToken);
    }

    /**
     * signal 信令：WebRTC offer / answer / candidate 转发给房间内的另一个端点。
     *
     * @param roomId 房间编号
     * @param connectionId 发送端连接 ID
     * @param signal 信令信封
     * @return 是否转发给对端
     */
    public boolean onSignal(String roomId, String connectionId, RoomSignal signal) {
        SlotState slot = findSlotByConnection(connectionId);
        if (slot == null) {
            return false;
        }
        RoomState room = Optional.ofNullable(rooms.get(roomId)).orElse(null);
        if (room == null) {
            return false;
        }
        String peerId = room.getOtherSlotId(slot.getSlotId());
        if (peerId == null) {
            return false;
        }
        Optional<PeerSession> peer = findPeer(room, peerId);
        if (!peer.isPresent()) {
            return false;
        }
        RoomMessage message = RoomMessage.builder(RoomMessage.TYPE_SIGNAL)
                .roomId(room.getRoomId())
                .slotId(slot.getSlotId())
                .peerId(peerId)
                .epoch(room.getEpoch())
                .payload(signal.getPayload())
                .build();
        send(peer.get().connectionId(), message.toJson());
        return true;
    }

    /**
     * heartbeat 信令：心跳续期 + pong 回应。
     *
     * @param connectionId 当前 WebSocket 会话 ID
     * @return pong 信封
     */
    public RoomMessage onHeartbeat(String connectionId) {
        PeerSession peer = peers.get(connectionId);
        if (peer == null) {
            return RoomMessage.builder(RoomMessage.TYPE_PONG)
                    .reason("peer.session.not_found")
                    .build();
        }
        peer.slot().setLastSeenAt(System.currentTimeMillis());
        peer.slot().setLeaseExpiresAt(System.currentTimeMillis() + properties.getTimeouts().getLeaseExpiresMs());
        peer.room().setLastActiveAt(System.currentTimeMillis());
        return RoomMessage.builder(RoomMessage.TYPE_PONG)
                .roomId(peer.room().getRoomId())
                .epoch(peer.room().getEpoch())
                .build();
    }

    /**
     * leave 信令：主动离开房间。
     *
     * @param connectionId 当前 WebSocket 会话 ID
     */
    public void onLeave(String connectionId) {
        PeerSession peer = peers.get(connectionId);
        if (peer == null) {
            return;
        }
        RoomState room = peer.room();
        String slotId = peer.slot().getSlotId();
        // 返回 Self（room 状态）
        RoomMessage left = RoomMessage.builder(RoomMessage.TYPE_LEFT)
                .roomId(room.getRoomId())
                .slotId(slotId)
                .peerId(room.getOtherSlotId(slotId))
                .build();
        send(connectionId, left.toJson());
        // 对端通知对方主动离开
        leavePeer(room, slotId);
        // 清理本端
        disconnectPeer(connectionId, room, slotId);
        clearResumeToken(room, slotId);
    }

    /**
     * 连接断开（没有发出 leave）
     *
     * @param connectionId 当前 WebSocket 会话 ID
     */
    public void onDisconnect(String connectionId) {
        PeerSession peer = peers.get(connectionId);
        if (peer == null) {
            return;
        }
        RoomState room = peer.room();
        String slotId = peer.slot().getSlotId();
        // 对端通知对方异常拔掉
        notifyPeerDisconnect(room, slotId);
        // 释放本端
        disconnectPeer(connectionId, room, slotId);
    }

    /**
     * 清理过期（续期）的连接与空闲房间
     */
    public void cleanExpired() {
        long now = System.currentTimeMillis();
        List<String> expired = new ArrayList<>();
        for (Map.Entry<String, PeerSession> entry : peers.entrySet()) {
            PeerSession peer = entry.getValue();
            SlotState slot = peer.slot();
            if (slot != null && slot.isLeaseExpired(now)) {
                log.info("[Room] 连接过期: connectionId={}", entry.getKey());
                expirePeer(entry.getKey(), peer, now);
                expired.add(entry.getKey());
            }
        }
        for (String id : expired) {
            // 释放对端资源
            peers.remove(id);
            clearResumeToken(id);
        }
        // 回收频繁无活跃端点的房间
        List<String> empty = new ArrayList<>();
        for (Map.Entry<String, RoomState> entry : rooms.entrySet()) {
            RoomState room = entry.getValue();
            if (room.activePeerCount() == 0 && now - room.getLastActiveAt() > properties.getTimeouts().getRemoveEmptyRoomMs()) {
                empty.add(room.getRoomId());
            }
        }
        empty.forEach(rooms::remove);
        empty.forEach(roomId -> resumeTokens.values().removeIf(t -> t.getRoomId().equals(roomId)));
        log.info("[Room] 清理完成: expired={} empty={}", expired.size(), empty.size());
    }

    /**
     * 获取房间中的活跃端点
     */
    public int getActivePeerCount(String roomId) {
        RoomState room = rooms.get(roomId);
        return room == null ? 0 : room.activePeerCount();
    }

    /**
     * 获取房间台账
     */
    public Optional<RoomState> getRoom(String roomId) {
        return Optional.ofNullable(rooms.get(roomId));
    }

    /**
     * 获取通过连接 ID 关联的端点
     */
    public Optional<PeerSession> getPeer(String connectionId) {
        return Optional.ofNullable(peers.get(connectionId));
    }

    /* ---------- 内部 ---------- */

    /**
     * 通过 WebSocket 连接 ID 下发信令到会话。
     *
     * @param connectionId WebSocket 会话 ID
     * @param message      信令 JSON 字符串
     */
    private void send(String connectionId, String message) {
        sender.send(connectionId, message);
    }

    /**
     * 加入房间（新接入 / 续练不通过）
     */
    private void joinRoom(RoomState room, String slotId, String peerSessionId, String connectionId, boolean fresh) {
        SlotState slot = new SlotState(slotId);
        slot.setPeerSessionId(peerSessionId);
        slot.setConnectionId(connectionId);
        slot.setLeaseExpiresAt(System.currentTimeMillis() + properties.getTimeouts().getLeaseExpiresMs());
        slot.setLastSeenAt(System.currentTimeMillis());

        // 第一个加入的是 offerer（发起端）
        if (room.getOffererSlotId() == null) {
            room.setOffererSlotId(slotId);
        }
        // 房间已有对端时（第二人加入）→ 新增对局，代数递增
        if (!fresh && !room.hasActiveSlot()) {
            room.bumpEpoch();
        }

        boolean ok = room.addSlot(slot);
        if (!ok) {
            log.warn("[Room] 房间已满: roomId={}, slotId={}", room.getRoomId(), slotId);
            return;
        }

        // 如果房间还有对端，向对方通报新加入的伙伴
        String otherSlotId = room.getOtherSlotId(slotId);
        if (otherSlotId != null) {
            synchronized (room.getSlots()) {
                for (SlotState target : room.getSlots()) {
                    if (!target.getSlotId().equals(slotId)) {
                        PeerSession peer = findPeer(room, target.getSlotId()).orElse(null);
                        if (peer != null) {
                            // negotiate 给发起方（offerer），触发 it 生成 offer
                            RoomMessage negotiate = RoomMessage.builder(RoomMessage.TYPE_NEGOTIATE)
                                    .roomId(room.getRoomId())
                                    .slotId(slotId)
                                    .epoch(room.getEpoch())
                                    .build();
                            send(peer.connectionId(), negotiate.toJson());
                        }
                        break;
                    }
                }
            }
        }

        // peer-ready（通知双方已就绪）
        for (SlotState target : room.getSlots()) {
            if (!target.getSlotId().equals(slotId)) {
                PeerSession peer = findPeer(room, target.getSlotId()).orElse(null);
                if (peer != null) {
                    RoomMessage ready = RoomMessage.builder(RoomMessage.TYPE_PEER_READY)
                            .roomId(room.getRoomId())
                            .slotId(target.getSlotId())
                            .peerId(slotId)
                            .epoch(room.getEpoch())
                            .build();
                    send(peer.connectionId(), ready.toJson());
                }
            }
        }
        peers.put(connectionId, new PeerSession(room, slot));
    }

    /**
     * 恢复旧连接（续练）
     */
    private void resumeConnection(RoomState room, ResumeToken token, SlotState slot, String connectionId) {
        String slotId = token.getSlotId();
        // 替换已存在的旧连接（同一个 session 被两个连接同时挂靠）
        Optional<PeerSession> replaced = findPeer(room, slotId);
        if (replaced.isPresent() && !replaced.get().connectionId().equals(connectionId)) {
            RoomMessage replacement = RoomMessage.builder(RoomMessage.TYPE_REPLACED)
                    .roomId(room.getRoomId())
                    .slotId(slotId)
                    .epoch(room.getEpoch())
                    .build();
            send(replaced.get().connectionId(), replacement.toJson());
        }
        // 对端通知 peer-reconnected
        String otherSlotId = room.getOtherSlotId(slotId);
        if (otherSlotId != null) {
            RoomMessage connected = RoomMessage.builder(RoomMessage.TYPE_PEER_RECONNECTED)
                    .roomId(room.getRoomId())
                    .slotId(slotId)
                    .peerId(otherSlotId)
                    .epoch(room.getEpoch())
                    .build();
            findPeer(room, otherSlotId).ifPresent(p -> send(p.connectionId(), connected.toJson()));
        }
        updateSlotActive(room, slot, connectionId);
    }

    private void updateSlotActive(RoomState room, SlotState slot, String connectionId) {
        slot.setConnectedAt(System.currentTimeMillis());
        slot.setLeaseExpiresAt(System.currentTimeMillis() + properties.getTimeouts().getLeaseExpiresMs());
        slot.setLastSeenAt(System.currentTimeMillis());
        room.setLastActiveAt(System.currentTimeMillis());
        peers.replace(connectionId, new PeerSession(room, slot));
    }

    private void leavePeer(RoomState room, String slotId) {
        String otherSlotId = room.getOtherSlotId(slotId);
        if (otherSlotId == null) {
            return;
        }
        RoomMessage peerLeft = RoomMessage.builder(RoomMessage.TYPE_PEER_LEFT)
                .roomId(room.getRoomId())
                .slotId(slotId)
                .peerId(otherSlotId)
                .build();
        findPeer(room, otherSlotId).ifPresent(p -> send(p.connectionId(), peerLeft.toJson()));
    }

    /**
     * 对端断开（无 leave 信号）
     */
    private void notifyPeerDisconnect(RoomState room, String slotId) {
        String otherSlotId = room.getOtherSlotId(slotId);
        if (otherSlotId == null) {
            return;
        }
        RoomMessage disconnected = RoomMessage.builder(RoomMessage.TYPE_PEER_DISCONNECTED)
                .roomId(room.getRoomId())
                .slotId(slotId)
                .peerId(otherSlotId)
                .epoch(room.getEpoch())
                .build();
        findPeer(room, otherSlotId).ifPresent(p -> send(p.connectionId(), disconnected.toJson()));
    }

    private void disconnectPeer(String connectionId, RoomState room, String slotId) {
        peers.remove(connectionId);
        if (room != null && slotId != null) {
            room.disconnectSlot(slotId);
        }
    }

    private void expirePeer(String connectionId, PeerSession peer, long now) {
        RoomState room = peer.room();
        String slotId = peer.slot().getSlotId();
        peers.remove(connectionId);
        room.disconnectSlot(slotId);
        clearResumeToken(room, slotId);
    }

    private void clearResumeToken(String connectionId) {
        PeerSession peer = peers.get(connectionId);
        if (peer != null) {
            clearResumeToken(peer.room(), peer.slot().getSlotId());
        }
    }

    private void clearResumeToken(RoomState room, String slotId) {
        resumeTokens.values().removeIf(t -> t.getRoomId().equals(room.getRoomId()) && t.getSlotId().equals(slotId));
    }

    private String emitResumeToken(RoomState room, String slotId) {
        long timestamp = System.currentTimeMillis();
        String raw = Base64.getEncoder().encodeToString((room.getRoomId() + ":" + slotId + ":" + room.getEpoch() + ":" + timestamp)
                .getBytes(StandardCharsets.UTF_8));
        ResumeToken token = new ResumeToken(raw, room.getRoomId(), slotId, room.getEpoch(), timestamp + TOKEN_LIFETIME_MS);
        resumeTokens.put(raw, token);
        // 清理过期的令牌
        resumeTokens.entrySet().removeIf(e -> e.getValue().getExpiresAt() <= System.currentTimeMillis());
        return raw;
    }

    private SlotState findSlotByConnection(String connectionId) {
        PeerSession peer = peers.get(connectionId);
        if (peer == null || peer.slot() == null) {
            return null;
        }
        return peer.slot();
    }

    private Optional<PeerSession> findPeer(RoomState room, String slotId) {
        for (PeerSession peer : peers.values()) {
            if (peer.room() == room && peer.slot().getSlotId().equals(slotId)) {
                return Optional.of(peer);
            }
        }
        return Optional.empty();
    }

    /**
     * 组装 welcome 信封（当前端点加入房间以后）
     */
    private RoomMessage buildWelcome(RoomState room, String slotId, boolean resumed, String resumeToken) {
        String peerId = room.getOtherSlotId(slotId);
        boolean initiator = resumed
                ? !slotId.equals(room.getOffererSlotId())
                : room.getOffererSlotId() != null && !room.getOffererSlotId().equals(slotId);

        RoomMessage.RoomMessageBuilder builder = RoomMessage.builder(RoomMessage.TYPE_WELCOME)
                .roomId(room.getRoomId())
                .slotId(slotId)
                .offererSlotId(room.getOffererSlotId())
                .epoch(room.getEpoch())
                .initiator(initiator)
                .resumed(resumed)
                .resumeToken(resumeToken);
        if (peerId != null) {
            builder.peerId(peerId);
            builder.peerCount(room.activePeerCount());
        }
        return builder.build();
    }

    /**
     * 续练令牌
     */
    @Data
    public static class ResumeToken implements java.io.Serializable {
        private static final long serialVersionUID = 1L;

        /** 恢复令牌 */
        private String token;
        /** 房间编号 */
        private String roomId;
        /** 端点 SlotID */
        private String slotId;
        /** 房间代数 */
        private Long epoch;
        /** 截止时间（ms） */
        private Long expiresAt;

        ResumeToken(String token, String roomId, String slotId, Long epoch, Long expiresAt) {
            this.token = token;
            this.roomId = roomId;
            this.slotId = slotId;
            this.epoch = epoch;
            this.expiresAt = expiresAt;
        }
    }
}
