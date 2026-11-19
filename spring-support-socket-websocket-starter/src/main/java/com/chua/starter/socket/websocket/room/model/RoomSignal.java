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

    /**
     * 创建空信封
     */
    public RoomSignal() {
    }

    /**
     * 创建信封（带类型与负载）
     *
     * @param type    消息类型（hello / signal / heartbeat / leave）
     * @param payload 框架负载（WebRTC offer / answer / candidate）
     */
    public RoomSignal(String type, String payload) {
        this.type = type;
        this.payload = payload;
    }

    /**
     * 创建信封（带类型、房间编号与负载）
     *
     * @param type    消息类型（hello / signal / heartbeat / leave）
     * @param roomId  房间编号
     * @param payload 框架负载（WebRTC offer / answer / candidate）
     */
    public RoomSignal(String type, String roomId, String payload) {
        this.type = type;
        this.roomId = roomId;
        this.payload = payload;
    }

    /**
     * 解析为 JSON
     *
     * @return JSON 字符串
     */
    public String toJson() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (Exception e) {
            throw new IllegalStateException("无法序列化房间信令信封", e);
        }
    }

    /**
     * 获取消息类型
     *
     * @return 消息类型（hello / signal / heartbeat / leave）
     */
    public String getType() {
        return type;
    }

    /**
     * 设置消息类型
     *
     * @param type 消息类型（hello / signal / heartbeat / leave）
     */
    public void setType(String type) {
        this.type = type;
    }

    /**
     * 获取房间编号
     *
     * @return 房间编号
     */
    public String getRoomId() {
        return roomId;
    }

    /**
     * 设置房间编号
     *
     * @param roomId 房间编号
     */
    public void setRoomId(String roomId) {
        this.roomId = roomId;
    }

    /**
     * 获取当前端点的 SlotID
     *
     * @return 当前端点 SlotID
     */
    public String getSlotId() {
        return slotId;
    }

    /**
     * 设置当前端点的 SlotID
     *
     * @param slotId 当前端点 SlotID
     */
    public void setSlotId(String slotId) {
        this.slotId = slotId;
    }

    /**
     * 获取来源端点的 SlotID（发送方）
     *
     * @return 来源端点 SlotID
     */
    public String getFromSlotId() {
        return fromSlotId;
    }

    /**
     * 设置来源端点的 SlotID（发送方）
     *
     * @param fromSlotId 来源端点 SlotID
     */
    public void setFromSlotId(String fromSlotId) {
        this.fromSlotId = fromSlotId;
    }

    /**
     * 获取对等端点的 SessionID
     *
     * @return 对等端点 SessionID
     */
    public String getPeerSessionId() {
        return peerSessionId;
    }

    /**
     * 设置对等端点的 SessionID
     *
     * @param peerSessionId 对等端点 SessionID
     */
    public void setPeerSessionId(String peerSessionId) {
        this.peerSessionId = peerSessionId;
    }

    /**
     * 获取恢复令牌（续接旧连接）
     *
     * @return 恢复令牌
     */
    public String getResumeToken() {
        return resumeToken;
    }

    /**
     * 设置恢复令牌（续接旧连接）
     *
     * @param resumeToken 恢复令牌
     */
    public void setResumeToken(String resumeToken) {
        this.resumeToken = resumeToken;
    }

    /**
     * 获取对等端点的直达 ID
     *
     * @return 对等端点直达 ID
     */
    public String getPeerId() {
        return peerId;
    }

    /**
     * 设置对等端点的直达 ID
     *
     * @param peerId 对等端点直达 ID
     */
    public void setPeerId(String peerId) {
        this.peerId = peerId;
    }

    /**
     * 获取发起端点的 SlotID
     *
     * @return 发起端点 SlotID
     */
    public String getOffererSlotId() {
        return offererSlotId;
    }

    /**
     * 设置发起端点的 SlotID
     *
     * @param offererSlotId 发起端点 SlotID
     */
    public void setOffererSlotId(String offererSlotId) {
        this.offererSlotId = offererSlotId;
    }

    /**
     * 获取房间代数
     *
     * @return 房间代数
     */
    public Long getEpoch() {
        return epoch;
    }

    /**
     * 设置房间代数
     *
     * @param epoch 房间代数
     */
    public void setEpoch(Long epoch) {
        this.epoch = epoch;
    }

    /**
     * 获取框架负载
     *
     * @return 框架负载（WebRTC offer / answer / candidate）
     */
    public String getPayload() {
        return payload;
    }

    /**
     * 设置框架负载
     *
     * @param payload 框架负载（WebRTC offer / answer / candidate）
     */
    public void setPayload(String payload) {
        this.payload = payload;
    }

    /**
     * 获取离开原因
     *
     * @return 离开原因
     */
    public String getReason() {
        return reason;
    }

    /**
     * 设置离开原因
     *
     * @param reason 离开原因
     */
    public void setReason(String reason) {
        this.reason = reason;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        RoomSignal that = (RoomSignal) o;
        return Objects.equals(type, that.type) && Objects.equals(roomId, that.roomId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, roomId);
    }
}
