package com.chua.starter.socket.websocket.room;

import com.chua.starter.socket.websocket.room.model.RoomState;
import com.chua.starter.socket.websocket.room.model.SlotState;

/**
 * 房间与连接 ID 的关联资源（连接 ID 级别）。
 *
 * @author CH
 * @since 2026-11-03
 */
public record PeerSession(

        /** 房间 */
        RoomState room,

        /** 端点状态 */
        SlotState slot,

        /** 关联的 WebSocket 连接 ID */
        String connectionId) {

    public PeerSession(RoomState room, SlotState slot) {
        this(room, slot, slot.getConnectionId());
    }
}
