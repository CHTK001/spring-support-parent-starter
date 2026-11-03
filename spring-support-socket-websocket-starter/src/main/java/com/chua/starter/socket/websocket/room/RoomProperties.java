package com.chua.starter.socket.websocket.room;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * P2P 房间信令配置（ZestSend Room 信令引擎）。
 *
 * @author CH
 * @since 2026-11-03
 */
@Data
@ConfigurationProperties(prefix = "room")
public class RoomProperties {

    /** 是否启用房间信令模块 */
    private boolean enable = false;

    /** 房间信令 WebSocket 端点（由协议层使用，实际端点由 plugin.websocket.endpoints 控制） */
    private String endpoint = "/api/rooms/{roomId}/ws";

    /** 每房间最大参与端点数（P2P = 2） */
    private int maxPeers = 2;

    /** 房间超时配置 */
    private RoomTimeouts timeouts = new RoomTimeouts();

    /** STUN 服务器（内网发现） */
    private String stunServer = "";

    /** TURN 服务器 URI（中继失败时备用） */
    private String turnServer = "";

    /** TURN 用户 */
    private String turnUser = "";

    /** TURN 密码 */
    private String turnPassword = "";

    /**
     * 房间超时配置。
     */
    public static class RoomTimeouts {

        /** 端点续期时间（ms） - 心跳未到达则端点断线 */
        private long leaseExpiresMs = 30_000;

        /** 无参与者房间回收时间（ms） */
        private long removeEmptyRoomMs = 240_000;

        public long getLeaseExpiresMs() {
            return leaseExpiresMs;
        }

        public void setLeaseExpiresMs(long leaseExpiresMs) {
            this.leaseExpiresMs = leaseExpiresMs;
        }

        public long getRemoveEmptyRoomMs() {
            return removeEmptyRoomMs;
        }

        public void setRemoveEmptyRoomMs(long removeEmptyRoomMs) {
            this.removeEmptyRoomMs = removeEmptyRoomMs;
        }
    }
}
