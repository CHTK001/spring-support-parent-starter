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

    /**
     * 创建端点状态
     */
    public SlotState() {
    }

    /**
     * 创建端点状态
     *
     * @param slotId 端点唯一标识（在会话生存期内唯一）
     */
    public SlotState(String slotId) {
        this.slotId = slotId;
        this.disconnectedAt = -1;
    }

    /**
     * 是否已断开
     *
     * @return true 表示已断开
     */
    public boolean isDisconnected() {
        return disconnectedAt > 0;
    }

    /**
     * 续期是否已到期
     *
     * @param now 当前时间（ms）
     * @return true 表示已到期
     */
    public boolean isLeaseExpired(long now) {
        return leaseExpiresAt != null && leaseExpiresAt <= now;
    }

    /**
     * 是否活跃（未断开且续期未到期）
     *
     * @return true 表示活跃
     */
    public boolean isActive() {
        return !isDisconnected() && !isLeaseExpired(System.currentTimeMillis());
    }

    /**
     * 获取端点唯一标识
     *
     * @return 端点唯一标识
     */
    public String getSlotId() {
        return slotId;
    }

    /**
     * 设置端点唯一标识
     *
     * @param slotId 端点唯一标识
     */
    public void setSlotId(String slotId) {
        this.slotId = slotId;
    }

    /**
     * 获取关联的对等会话 ID
     *
     * @return 对等会话 ID
     */
    public String getPeerSessionId() {
        return peerSessionId;
    }

    /**
     * 设置关联的对等会话 ID
     *
     * @param peerSessionId 对等会话 ID
     */
    public void setPeerSessionId(String peerSessionId) {
        this.peerSessionId = peerSessionId;
    }

    /**
     * 获取令牌的 SHA-256 哈希值
     *
     * @return 令牌哈希值
     */
    public String getTokenHash() {
        return tokenHash;
    }

    /**
     * 设置令牌的 SHA-256 哈希值
     *
     * @param tokenHash 令牌哈希值
     */
    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }

    /**
     * 获取关联的 WebSocket 连接 ID
     *
     * @return 连接 ID
     */
    public String getConnectionId() {
        return connectionId;
    }

    /**
     * 设置关联的 WebSocket 连接 ID
     *
     * @param connectionId 连接 ID
     */
    public void setConnectionId(String connectionId) {
        this.connectionId = connectionId;
    }

    /**
     * 获取断开时间（< 0 表示未断开）
     *
     * @return 断开时间（ms）
     */
    public long getDisconnectedAt() {
        return disconnectedAt;
    }

    /**
     * 设置断开时间（< 0 表示未断开）
     *
     * @param disconnectedAt 断开时间（ms）
     */
    public void setDisconnectedAt(long disconnectedAt) {
        this.disconnectedAt = disconnectedAt;
    }

    /**
     * 获取续期到期时间
     *
     * @return 续期到期时间（ms），null 表示未续期
     */
    public Long getLeaseExpiresAt() {
        return leaseExpiresAt;
    }

    /**
     * 设置续期到期时间
     *
     * @param leaseExpiresAt 续期到期时间（ms），null 表示未续期
     */
    public void setLeaseExpiresAt(Long leaseExpiresAt) {
        this.leaseExpiresAt = leaseExpiresAt;
    }

    /**
     * 获取最后心跳时间
     *
     * @return 最后心跳时间（ms）
     */
    public Long getLastSeenAt() {
        return lastSeenAt;
    }

    /**
     * 设置最后心跳时间
     *
     * @param lastSeenAt 最后心跳时间（ms）
     */
    public void setLastSeenAt(Long lastSeenAt) {
        this.lastSeenAt = lastSeenAt;
    }

    /**
     * 获取首次（或续期）连接建立时间
     *
     * @return 连接建立时间（ms）
     */
    public Long getConnectedAt() {
        return connectedAt;
    }

    /**
     * 设置首次（或续期）连接建立时间
     *
     * @param connectedAt 连接建立时间（ms）
     */
    public void setConnectedAt(Long connectedAt) {
        this.connectedAt = connectedAt;
    }
}
