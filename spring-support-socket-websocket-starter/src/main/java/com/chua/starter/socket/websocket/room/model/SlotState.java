package com.chua.starter.socket.websocket.room.model;

/**
 * 端点状态（SlotState）：一个 P2P 房间参与方在信令服务器上的记录。
 *
 * @author CH
 * @since 2026-11-03
 */
public class SlotState {

    /** 端点唯一标识（在会话生存期内唯一） */
    private String slotId;

    /** 关联的对等会话 ID */
    private String peerSessionId;

    /** 令牌的 SHA-256 哈希值 */
    private String tokenHash;

    /** 关联的 WebSocket 连接 ID */
    private String connectionId;

    /** 断开时间（< 0 表示未断开） */
    private long disconnectedAt;

    /** 续期到期时间（ms）。null 表示未续期 */
    private Long leaseExpiresAt;

    /** 最后心跳时间（ms） */
    private Long lastSeenAt;

    /** 首次（或续期）连接建立时间（ms） */
    private Long connectedAt;

    public SlotState() {
    }

    public SlotState(String slotId) {
        this.slotId = slotId;
        this.disconnectedAt = -1;
    }

    public boolean isDisconnected() {
        return disconnectedAt > 0;
    }

    public boolean isLeaseExpired(long now) {
        return leaseExpiresAt != null && leaseExpiresAt <= now;
    }

    public boolean isActive() {
        return !isDisconnected() && !isLeaseExpired(System.currentTimeMillis());
    }

    public String getSlotId() {
        return slotId;
    }

    /** setSlotId 方法 */
    public void setSlotId(String slotId) {
        this.slotId = slotId;
    }

    /** getPeerSessionId 方法 */
    public String getPeerSessionId() {
        return peerSessionId;
    }

    /** setPeerSessionId 方法 */
    public void setPeerSessionId(String peerSessionId) {
        this.peerSessionId = peerSessionId;
    }

    /** getTokenHash 方法 */
    public String getTokenHash() {
        return tokenHash;
    }

    /** setTokenHash 方法 */
    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }

    /** getConnectionId 方法 */
    public String getConnectionId() {
        return connectionId;
    }

    /** setConnectionId 方法 */
    public void setConnectionId(String connectionId) {
        this.connectionId = connectionId;
    }

    /** getDisconnectedAt 方法 */
    public long getDisconnectedAt() {
        return disconnectedAt;
    }

    /** setDisconnectedAt 方法 */
    public void setDisconnectedAt(long disconnectedAt) {
        this.disconnectedAt = disconnectedAt;
    }

    /** getLeaseExpiresAt 方法 */
    public Long getLeaseExpiresAt() {
        return leaseExpiresAt;
    }

    /** setLeaseExpiresAt 方法 */
    public void setLeaseExpiresAt(Long leaseExpiresAt) {
        this.leaseExpiresAt = leaseExpiresAt;
    }

    /** getLastSeenAt 方法 */
    public Long getLastSeenAt() {
        return lastSeenAt;
    }

    /** setLastSeenAt 方法 */
    public void setLastSeenAt(Long lastSeenAt) {
        this.lastSeenAt = lastSeenAt;
    }

    /** getConnectedAt 方法 */
    public Long getConnectedAt() {
        return connectedAt;
    }

    /** setConnectedAt 方法 */
    public void setConnectedAt(Long connectedAt) {
        this.connectedAt = connectedAt;
    }
}
