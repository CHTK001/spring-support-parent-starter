package com.chua.starter.socket.websocket.room.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 加入房间的结果。
 *
 * @author CH
 * @since 2026-11-03
 */
public class RoomJoinResult {

    /** 房间编号 */
    private final String roomId;

    /** 房间中的活跃端点列表 */
    private final List<Endpoint> endpoints;

    /** 是否已匹配到对等方 */
    private final boolean ready;

    public RoomJoinResult(String roomId, List<Endpoint> endpoints, boolean ready) {
        this.roomId = roomId;
        this.endpoints = endpoints;
        this.ready = ready;
    }

    public String getRoomId() {
        return roomId;
    }

    public List<Endpoint> getEndpoints() {
        return endpoints;
    }

    public boolean isReady() {
        return ready;
    }

    /**
     * 端点
     */
    public record Endpoint(String id, String slotId) {
    }

    public List<String> getEndpointIds() {
        List<String> ids = new ArrayList<>();
        for (Endpoint e : endpoints) {
            ids.add(e.id());
        }
        return ids;
    }
}
