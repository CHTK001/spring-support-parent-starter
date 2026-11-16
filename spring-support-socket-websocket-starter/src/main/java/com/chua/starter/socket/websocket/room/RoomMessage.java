package com.chua.starter.socket.websocket.room;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Objects;

/**
 * 房间信令信封（服务端 → 客户端）。
 *
 * <p>对应 ZestSend {@code src/worker/room.ts} 下发的信令信封：
 * <ul>
 *   <li>{@code welcome} - 端点加入房间时下发（含 resumeToken / peerId / offererSlotId / epoch）</li>
 *   <li>{@code negotiate} - 新端点加入、通知已有对端生成 Offer 时下发</li>
 *   <li>{@code peer-ready} - 对端已准备好收发消息</li>
 *   <li>{@code peer-disconnected} - 对端非主动断开</li>
 *   <li>{@code peer-left} - 对端主动离开房间</li>
 *   <li>{@code peer-reconnected} - 对端久别重逢（resumeToken 续接）</li>
 *   <li>{@code replaced} - 同一端点被新的连接替换</li>
 *   <li>{@code left} / {@code pong} - 活动通知</li>
 * </ul>
 *
 * @author CH
 * @since 2026-11-03
 */
public class RoomMessage {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**欢迎信封（第一个端点加入房间）*/
    public static final String TYPE_WELCOME = "welcome";
    /**协商信封（对端开始交换信令）*/
    public static final String TYPE_NEGOTIATE = "negotiate";
    /**对端就绪信封*/
    public static final String TYPE_PEER_READY = "peer-ready";
    /**对端掉线信封*/
    public static final String TYPE_PEER_DISCONNECTED = "peer-disconnected";
    /**对端主动离开信封*/
    public static final String TYPE_PEER_LEFT = "peer-left";
    /**对端重连信封*/
    public static final String TYPE_PEER_RECONNECTED = "peer-reconnected";
    /**对端连接被替换信封*/
    public static final String TYPE_REPLACED = "replaced";
    /**离开房间信封*/
    public static final String TYPE_LEFT = "left";
    /**心跳回应信封*/
    public static final String TYPE_PONG = "pong";
    /**对端信号转发信封*/
    public static final String TYPE_SIGNAL = "signal";
    /**错误信封*/
    public static final String TYPE_ERROR = "error";

    /** 信封类型 */
    private String type;

    /** 信封标识（UUID，对端回复 echo 用） */
    private String id;

    /** 房间编号（四位数字） */
    private String roomId;

    /** 信令负载（WebRTC offer / answer / candidate） */
    private String payload;

    /** 当前端点的 SlotID */
    private String slotId;

    /** 来源端点的 SlotID（发起方） */
    private String fromSlotId;

    /** 对等端点的 SessionID */
    private String peerSessionId;

    /** 当前对端的 SlotID（对端） */
    private String peerId;

    /** 发起端点的 SlotID */
    private String offererSlotId;

    /** 房间投入的端点总数 */
    private Integer peerCount;

    /** 是否已经恢复（续接旧连接） */
    private Boolean resumed;

    /** 是否正在重新加入 */
    private Boolean initiator;

    /** 当前房间代数 */
    private Long epoch;

    /** 恢复令牌（后续续连/重连用） */
    private String resumeToken;

    /**
     * 心跳协议版本（v3 内部格式，不对外暴露）
     */
    private String heartbeatProtocol;

    /** 离开原因 */
    private String reason;

    /** 重试时间（ms） */
    private Long retryAfterMs;

    /** 创建信封 */
    public RoomMessage() {
        this(null);
    }

    /**
     * 创建信封
     *
     * @param type 信封类型
     */
    public RoomMessage(String type) {
        this.type = type;
        this.id = java.util.UUID.randomUUID().toString();
    }

    /**
     * 创建信封构建器
     *
     * @param type 信封类型
     * @return 信封构建器
     */
    public static RoomMessageBuilder builder(String type) {
        return new RoomMessageBuilder(type);
    }

    /**
     * 获取信封类型
     *
     * @return 信封类型
     */
    public String getType() {
        return type;
    }

    /**
     * 获取信封标识（UUID）
     *
     * @return 信封标识
     */
    public String getId() {
        return id;
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
     * 获取信令负载（WebRTC offer / answer / candidate）
     *
     * @return 信令负载
     */
    public String getPayload() {
        return payload;
    }

    /**
     * 获取当前端点的 SlotID。
     *
     * @return slot id
     */
    public String getSlotId() {
        return slotId;
    }

    /**
     * 获取来源端点的 SlotID（发起方）。
     *
     * @return from slot id
     */
    public String getFromSlotId() {
        return fromSlotId;
    }

    /**
     * 获取对等端点的 SessionID。
     *
     * @return peer session id
     */
    public String getPeerSessionId() {
        return peerSessionId;
    }

    /**
     * 获取当前对端的 SlotID（对端）。
     *
     * @return peer id
     */
    public String getPeerId() {
        return peerId;
    }

    /**
     * 获取发起端点的 SlotID。
     *
     * @return offerer slot id
     */
    public String getOffererSlotId() {
        return offererSlotId;
    }

    /**
     * 获取房间投入的端点总数。
     *
     * @return peer count
     */
    public Integer getPeerCount() {
        return peerCount;
    }

    /**
     * 获取是否已经恢复（续接旧连接）。
     *
     * @return resumed
     */
    public Boolean getResumed() {
        return resumed;
    }

    /**
     * 获取是否正在重新加入。
     *
     * @return initiator
     */
    public Boolean getInitiator() {
        return initiator;
    }

    /**
     * 获取当前房间代数。
     *
     * @return epoch
     */
    public Long getEpoch() {
        return epoch;
    }

    /**
     * 获取恢复令牌（后续续连 / 重连用）。
     *
     * @return resume token
     */
    public String getResumeToken() {
        return resumeToken;
    }

    /**
     * 心跳协议版本（v3 内部格式，不下发到客户端）。
     *
     * @return 心跳协议版本
     */
    @com.fasterxml.jackson.annotation.JsonIgnore
    public String getHeartbeatProtocol() {
        return heartbeatProtocol;
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
     * 获取重试时间（ms）
     *
     * @return 重试时间（ms）
     */
    public Long getRetryAfterMs() {
        return retryAfterMs;
    }

    /**
     * JSON 序列化
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RoomMessage that = (RoomMessage) o;
        return Objects.equals(type, that.type) && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, id);
    }

    /**
     * 信封构建器
     */
    public static class RoomMessageBuilder {

        private final RoomMessage msg;

        /**
         * 创建信封构建器
         *
         * @param type 信封类型
         */
        public RoomMessageBuilder(String type) {
            this.msg = new RoomMessage(type);
        }

        /**
         * 设置房间编号
         *
         * @param roomId 房间编号
         * @return 本构建器
         */
        public RoomMessageBuilder roomId(String roomId) {
            this.msg.roomId = roomId;
            return this;
        }

        /**
         * 设置信令负载（WebRTC offer / answer / candidate）
         *
         * @param payload 信令负载
         * @return 本构建器
         */
        public RoomMessageBuilder payload(String payload) {
            this.msg.payload = payload;
            return this;
        }

        /**
         * 设置当前端点的 SlotID
         *
         * @param slotId SlotID
         * @return 本构建器
         */
        public RoomMessageBuilder slotId(String slotId) {
            this.msg.slotId = slotId;
            return this;
        }

        /**
         * 设置当前对端的 SlotID
         *
         * @param peerId 对端 SlotID
         * @return 本构建器
         */
        public RoomMessageBuilder peerId(String peerId) {
            this.msg.peerId = peerId;
            return this;
        }

        /**
         * 设置发起端点的 SlotID
         *
         * @param offererSlotId 发起端点 SlotID
         * @return 本构建器
         */
        public RoomMessageBuilder offererSlotId(String offererSlotId) {
            this.msg.offererSlotId = offererSlotId;
            return this;
        }

        /**
         * 设置房间投入的端点总数
         *
         * @param peerCount 端点总数
         * @return 本构建器
         */
        public RoomMessageBuilder peerCount(Integer peerCount) {
            this.msg.peerCount = peerCount;
            return this;
        }

        /**
         * 设置是否已经恢复（续接旧连接）
         *
         * @param resumed 是否恢复
         * @return 本构建器
         */
        public RoomMessageBuilder resumed(Boolean resumed) {
            this.msg.resumed = resumed;
            return this;
        }

        /**
         * 设置是否正在重新加入
         *
         * @param initiator 是否重新加入
         * @return 本构建器
         */
        public RoomMessageBuilder initiator(Boolean initiator) {
            this.msg.initiator = initiator;
            return this;
        }

        /**
         * 设置当前房间代数
         *
         * @param epoch 房间代数
         * @return 本构建器
         */
        public RoomMessageBuilder epoch(Long epoch) {
            this.msg.epoch = epoch;
            return this;
        }

        /**
         * 设置恢复令牌（后续续连 / 重连用）
         *
         * @param resumeToken 恢复令牌
         * @return 本构建器
         */
        public RoomMessageBuilder resumeToken(String resumeToken) {
            this.msg.resumeToken = resumeToken;
            return this;
        }

        /**
         * 设置离开原因
         *
         * @param reason 离开原因
         * @return 本构建器
         */
        public RoomMessageBuilder reason(String reason) {
            this.msg.reason = reason;
            return this;
        }

        /**
         * 设置重试时间（ms）
         *
         * @param retryAfterMs 重试时间（ms）
         * @return 本构建器
         */
        public RoomMessageBuilder retryAfterMs(Long retryAfterMs) {
            this.msg.retryAfterMs = retryAfterMs;
            return this;
        }

        /**
         * 设置心跳协议版本（v3 内部格式，不下发到客户端）
         *
         * @param heartbeatProtocol 心跳协议版本
         * @return 本构建器
         */
        public RoomMessageBuilder heartbeatProtocol(String heartbeatProtocol) {
            this.msg.heartbeatProtocol = heartbeatProtocol;
            return this;
        }

        /**
         * 设置信封标识（UUID）
         *
         * @param id 信封标识
         * @return 本构建器
         */
        public RoomMessageBuilder id(String id) {
            this.msg.id = id;
            return this;
        }

        /**
         * 构建信封
         *
         * @return 完成的信封
         */
        public RoomMessage build() {
            return msg;
        }
    }
}
