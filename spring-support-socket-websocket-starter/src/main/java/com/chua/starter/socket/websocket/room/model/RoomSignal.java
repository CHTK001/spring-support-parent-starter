package com.chua.starter.socket.websocket.room.model;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Objects;

/**
 * 房间信令信封（客户端 → 服务端）。
 *
 * <p>对应 ZestSend {@code src/worker/room.ts} 的上行信令格式：
 * <ul>
 *   <li>{@code type} - 标识（hello / signal / heartbeat / leave）</li>
 *   <li>{@code payload} - 承载 WebRTC 信号（offer / answer / candidate）</li>
 *   <li>{@code epoch} - 标记房间代数（续连时用 resumeToken 校准）</li>
 *   <li>{@code resumeToken} - 恢复令牌（续接旧连接/对端）</li>
 * </ul>
 *
 * @author CH
 * @since 2026-11-03
 */
public class RoomSignal {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 消息类型：hello / signal / heartbeat / leave */
    private String type;

    /** 房间编号 */
    private String roomId;

    /** 当前端点的 SlotID */
    private String slotId;

    /** 来源端点的 SlotID（发送方） */
    private String fromSlotId;

    /** 对等端点的 SessionID */
    private String peerSessionId;

    /** 恢复令牌（续接旧连接） */
    private String resumeToken;

    /** 对等端点的直达 ID */
    private String peerId;

    /** 发起端点的 SlotID */
    private String offererSlotId;

    /** 阻塞/通道：房间代数 */
    private Long epoch;

    /** 框架负载（WebRTC offer/answer/candidate） */
    private String payload;

    /** 离开原因 */
    private String reason;

    public RoomSignal() {
    }

    public RoomSignal(String type, String payload) {
        this.type = type;
        this.payload = payload;
    }

    public RoomSignal(String type, String roomId, String payload) {
        this.type = type;
        this.roomId = roomId;
        this.payload = payload;
    }

    /**
     * 解析为 JSON
     */
    public String toJson() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (Exception e) {
            throw new IllegalStateException("无法序列化房间信令信封", e);
        }
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getRoomId() {
        return roomId;
    }

    public void setRoomId(String roomId) {
        this.roomId = roomId;
    }

    public String getSlotId() {
        return slotId;
    }

    public void setSlotId(String slotId) {
        this.slotId = slotId;
    }

    public String getFromSlotId() {
        return fromSlotId;
    }

    public void setFromSlotId(String fromSlotId) {
        this.fromSlotId = fromSlotId;
    }

    public String getPeerSessionId() {
        return peerSessionId;
    }

    public void setPeerSessionId(String peerSessionId) {
        this.peerSessionId = peerSessionId;
    }

    public String getResumeToken() {
        return resumeToken;
    }

    public void setResumeToken(String resumeToken) {
        this.resumeToken = resumeToken;
    }

    public String getPeerId() {
        return peerId;
    }

    public void setPeerId(String peerId) {
        this.peerId = peerId;
    }

    public String getOffererSlotId() {
        return offererSlotId;
    }

    public void setOffererSlotId(String offererSlotId) {
        this.offererSlotId = offererSlotId;
    }

    public Long getEpoch() {
        return epoch;
    }

    public void setEpoch(Long epoch) {
        this.epoch = epoch;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RoomSignal that = (RoomSignal) o;
        return Objects.equals(type, that.type) && Objects.equals(roomId, that.roomId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, roomId);
    }
}
